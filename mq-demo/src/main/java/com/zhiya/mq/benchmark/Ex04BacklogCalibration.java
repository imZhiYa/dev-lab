package com.zhiya.mq.benchmark;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

/**
 * EX-04 积压恢复校准（mq-10 L2）：backlog = 缺口 × 时长的线性公式验证。
 * <p>FAST 档：5 分区定死；生产 2000/s、消费 1000/s（缺口 D=1000/s）持续 40s
 * → 预期 backlog ≈ 40,000；停产后提速清空，比对清空时间与 Lag/消费速率推算。
 */
public class Ex04BacklogCalibration {

    private static final String TOPIC = "ex04-backlog";
    private static final String PAYLOAD = KafkaLabBase.payload(1024);
    private static final int PARTITIONS = 5;
    private static final int PRODUCE_RATE = 2000;  // msg/s 总计
    private static final int CONSUME_RATE = 1000;  // msg/s 总计（5 分区每分区 200/s）
    private static final int GAP_S = 40;

    public static void main(String[] args) throws Exception {
        KafkaLabBase.createTopics(TOPIC);

        System.out.println("============ EX-04 积压恢复校准 ============");
        System.out.println("分区=" + PARTITIONS + " 生产=" + PRODUCE_RATE + "/s 消费=" + CONSUME_RATE + "/s 缺口 D=1000/s 持续 " + GAP_S + "s");

        // 生产者：发满 80,000 条为止（200/100ms = 2000/s，sleep 开销致实际 ~42s）
        java.util.concurrent.atomic.AtomicLong produced = new java.util.concurrent.atomic.AtomicLong();
        long target = (long) PRODUCE_RATE * GAP_S;
        Thread producerThread = Thread.ofPlatform().start(() -> {
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(KafkaLabBase.producerProps("1"))) {
                long i = 0;
                while (produced.get() < target) {
                    for (int b = 0; b < 200 && produced.get() < target; b++) {
                        producer.send(new ProducerRecord<>(TOPIC, "k" + (i++ % 1000), PAYLOAD));
                        produced.incrementAndGet();
                    }
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 消费者：1000/s（每 100 条 sleep 100ms 精确控速），慢速制造缺口
        long consumedDuringGap = consumePhase("ex04-slow", 1000, GAP_S * 1000L + 5000, 100);
        producerThread.join();

        // 缺口结束时测 Lag（用慢消费组自己的 committed 位置，不是新组）
        long lag = lag("ex04-slow");
        long expected = (PRODUCE_RATE - CONSUME_RATE) * GAP_S;
        System.out.println("\n| 项 | 值 |");
        System.out.println("| --- | ---: |");
        System.out.printf("| 实际生产 | %,d 条（设计 80,000） |%n", produced.get());
        System.out.printf("| 缺口期消费 | %,d 条 |%n", consumedDuringGap);
        System.out.printf("| 缺口结束 Lag（实测） | %,d 条 |%n", lag);
        System.out.printf("| backlog 推算（D×40s=1000×40） | %,d 条 |%n", expected);
        System.out.printf("| 偏差 | %.1f%% |%n", Math.abs(lag - expected) * 100.0 / expected);
        System.out.println("机制解读：Lag 是消费速率 < 生产速率的直接信号；backlog = 缺口 × 时长是线性关系，偏差来自消费速率波动与重启重读。");

        // 清空：提速（sleep 去掉）
        System.out.println("\n--- 清空阶段：消费提速（无 sleep） ---");
        long t0 = System.currentTimeMillis();
        long cleared = drainFast();
        System.out.printf("| 清空量 | %,d 条（%,d ms） |%n", cleared, System.currentTimeMillis() - t0);
        System.out.println("机制解读：清空时间 ≈ Lag /（消费速率 − 生产速率）；此处停产后分母 = 全速消费速率，实测与推算同量级即验证公式成立（教学量级）。");
    }

    /** 全速消费直到 Lag=0 */
    private static long drainFast() {
        Properties props = KafkaLabBase.consumerProps("ex04-fast", 5000, 60_000);
        long count = 0;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(TOPIC));
            while (true) {
                var records = consumer.poll(Duration.ofMillis(200));
                count += records.count();
                if (records.isEmpty()) {
                    boolean allDone = true;
                    for (var p : consumer.assignment()) {
                        if (consumer.position(p) < consumer.endOffsets(List.of(p)).get(p)) {
                            allDone = false;
                            break;
                        }
                    }
                    if (allDone) {
                        break;
                    }
                }
            }
        }
        return count;
    }

    /** 消费：perBatchDelayMs=0 全速；否则每处理 100 条 sleep 一次（精确控速 = 100000/perBatchDelayMs 条/s） */
    private static long consumePhase(String group, int maxPollRecords, long durationMs, long perBatchDelayMs) {
        Properties props = KafkaLabBase.consumerProps(group, maxPollRecords, 60_000);
        long count = 0;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.currentTimeMillis() + durationMs;
            while (System.currentTimeMillis() < deadline) {
                var records = consumer.poll(Duration.ofMillis(200));
                for (var r : records) {
                    count++;
                    if (perBatchDelayMs > 0 && count % 100 == 0) {
                        try {
                            Thread.sleep(perBatchDelayMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return count;
                        }
                    }
                }
            }
        }
        return count;
    }

    private static long lag(String group) {
        Properties props = KafkaLabBase.consumerProps(group, 500, 60_000);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(TOPIC));
            consumer.poll(Duration.ofMillis(1000));
            long lag = 0;
            for (var p : consumer.assignment()) {
                long end = consumer.endOffsets(List.of(p)).get(p);
                lag += Math.max(0, end - consumer.position(p));
            }
            return lag;
        }
    }
}
