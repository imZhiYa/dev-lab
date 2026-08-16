package com.zhiya.mq.orderflow;

import com.zhiya.mq.benchmark.KafkaLabBase;
import com.zhiya.mq.core.IdempotencyKey;
import com.zhiya.mq.core.OrderStateMachine;
import com.zhiya.mq.core.RetryPolicy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * EX-06 乱序与补偿注入（mq-10 L3/L4）：
 * 1. 注入 PAID 先于 CREATED（同 orderId，同分区保证消费序 = 生产序）→ 状态机拒绝 →
 *    延迟重试（10ms/20ms/40ms 指数退避，上限 3）——重试 = 放回 pending 队列而非线程内 sleep，
 *    主循环继续 poll，CREATED 到达后 PAID 恢复 APPLIED
 * 2. 活锁对照组：无上限重试 → 观察 20s 内重试次数持续增长（乱序可以等，但必须有上限）
 * 3. 3 次失败 → DLQ 兜底（DLQ 命中 = 重试上限耗尽的消息数）
 */
public class Ex06DisorderCompensation {

    private static final String TOPIC = "ex06-order";
    private static final String DLQ = "ex06-dlq";
    private static final String ORDER_ID = "O-1001";

    // 每订单的内存状态表（教学量级，生产应落 DB + 唯一索引，见 EX-03）
    private static final Map<String, OrderStateMachine.State> STATE = new ConcurrentHashMap<>();
    private static final AtomicInteger RECOVERED = new AtomicInteger();
    private static final AtomicInteger DLQ_HIT = new AtomicInteger();
    private static final AtomicLong RETRY_COUNT = new AtomicLong();

    public static void main(String[] args) throws Exception {
        KafkaLabBase.createTopics(TOPIC, DLQ);

        System.out.println("============ EX-06 乱序与补偿注入 ============");
        System.out.println("注入：先 PAID 后 50ms CREATED（同分区，key=orderId 保序）→ 状态机 REJECTED_WAITING → 延迟重试恢复");

        // 注入：PAID 先到，CREATED 在 50ms 后（落在 PAID 的退避窗口内，3 次上限内可恢复）
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(KafkaLabBase.producerProps("1"))) {
            producer.send(new ProducerRecord<>(TOPIC, ORDER_ID, "PAID")).get();
            Thread.sleep(50);
            producer.send(new ProducerRecord<>(TOPIC, ORDER_ID, "CREATED")).get();
        }

        // 消费者：状态机 + pending 延迟重试（上限 3 次，10/20/40ms 退避）
        Thread consumer = Thread.ofPlatform().start(Ex06DisorderCompensation::consumeWithRetry);
        consumer.join(15_000);
        consumer.interrupt();

        System.out.println("| 指标 | 值 |");
        System.out.println("| --- | ---: |");
        System.out.printf("| 恢复成功（PAID 最终 APPLIED） | %d |%n", RECOVERED.get());
        System.out.printf("| DLQ 命中（3 次上限耗尽） | %d |%n", DLQ_HIT.get());
        System.out.printf("| 总重试次数 | %,d |%n", RETRY_COUNT.get());
        System.out.println("\n机制解读：同分区内 PAID 先到 → 状态机拒绝（不丢不误处理）→ 放 pending 队列延迟重试，主循环继续 poll → CREATED 到达推进状态 → PAID 到期重试恢复；若前置永远不来，3 次上限耗尽进 DLQ，防止活锁。");

        liveLockDemo();
    }

    private record PendingEvent(String orderId, String event, int nextAttempt, long dueNanos) implements Delayed {
        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(dueNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(dueNanos, ((PendingEvent) o).dueNanos);
        }
    }

    private static void consumeWithRetry() {
        Properties props = KafkaLabBase.consumerProps("ex06-group", 500, 60_000);
        RetryPolicy retry = new RetryPolicy(3, 10);
        DelayQueue<PendingEvent> pending = new DelayQueue<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(TOPIC, DLQ));
            long deadline = System.currentTimeMillis() + 14_000;
            while (System.currentTimeMillis() < deadline) {
                // 1. 到期 pending 优先重试
                PendingEvent e;
                while ((e = pending.poll()) != null) {
                    retryEvent(e, retry, pending);
                }
                // 2. poll 新消息（CREATED 在此期间到达）
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(100))) {
                    if (r.topic().equals(DLQ)) {
                        System.out.println("[EX-06] DLQ 兜底命中：缺口事件 " + r.value() + " 重试上限耗尽，转人工/对账");
                        DLQ_HIT.incrementAndGet();
                        continue;
                    }
                    retryEvent(new PendingEvent(r.key(), r.value(), 1, System.nanoTime()), retry, pending);
                }
            }
        }
    }

    private static void retryEvent(PendingEvent e, RetryPolicy retry, DelayQueue<PendingEvent> pending) {
        RETRY_COUNT.incrementAndGet();
        var outcome = OrderStateMachine.apply(
                STATE.getOrDefault(e.orderId(), OrderStateMachine.State.NONE),
                OrderStateMachine.Event.valueOf(e.event()));
        switch (outcome.result()) {
            case APPLIED -> {
                STATE.put(e.orderId(), outcome.state());
                if (e.event().equals("PAID")) {
                    RECOVERED.incrementAndGet();
                    System.out.printf("[EX-06] 恢复：PAID 在第 %d 次尝试后 APPLIED（前置 CREATED 已到达，幂等键 %s 不变）%n",
                            e.nextAttempt(), IdempotencyKey.of(e.orderId(), e.event(), 1));
                }
            }
            case REJECTED_WAITING -> {
                long delay = retry.delayFor(e.nextAttempt());
                if (delay < 0) {
                    System.out.println("[EX-06] " + e.event() + " 重试 3 次仍未等到前置 → 放 DLQ（乱序可以等，但必须有上限）");
                    try (KafkaProducer<String, String> p = new KafkaProducer<>(KafkaLabBase.producerProps("1"))) {
                        p.send(new ProducerRecord<>(DLQ, e.orderId(), e.event()));
                    }
                    return;
                }
                System.out.printf("[EX-06] 乱序拒绝：%s 等待前置状态，第 %d 次重试延迟 %d ms%n",
                        e.event(), e.nextAttempt(), delay);
                pending.add(new PendingEvent(e.orderId(), e.event(), e.nextAttempt() + 1,
                        System.nanoTime() + delay * 1_000_000L));
            }
            case DUPLICATE -> System.out.println("[EX-06] DUPLICATE 拦截（幂等键不变，重放不重复处理）: " + e.event());
            case ILLEGAL -> System.out.println("[EX-06] ILLEGAL 转移（状态机定义之外的边）: " + e.event());
        }
    }

    private static void liveLockDemo() {
        System.out.println("\n--- 活锁对照组：无上限重试（观察 20s） ---");
        AtomicLong spins = new AtomicLong();
        Thread spinner = Thread.ofPlatform().start(() -> {
            RetryPolicy unbounded = new RetryPolicy(Integer.MAX_VALUE, 10);
            long deadline = System.currentTimeMillis() + 20_000;
            int attempt = 1;
            while (System.currentTimeMillis() < deadline) {
                long delay = unbounded.delayFor(attempt++);
                spins.incrementAndGet();
                try {
                    Thread.sleep(Math.min(delay, 1000));
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        long t0 = System.currentTimeMillis();
        while (spinner.isAlive()) {
            if (System.currentTimeMillis() - t0 >= 21_000) {
                spinner.interrupt();
                break;
            }
        }
        System.out.printf("| 无上限重试 20s 内重试次数 | %,d 次（指数退避下仍在增长） |%n", spins.get());
        System.out.println("机制解读：没有上限的等待 = 活锁——前置事件永远不来时，消息在队列里无限打转、消费线程被永久占用。每个\"等\"必须有上限（重试次数 + 退避 + DLQ/对账兜底）。");
    }
}
