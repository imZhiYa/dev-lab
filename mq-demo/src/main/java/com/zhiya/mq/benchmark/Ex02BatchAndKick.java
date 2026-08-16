package com.zhiya.mq.benchmark;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EX-02 批量消费与"被踢"边界（mq-05 L3 线程模型 + max.poll.interval.ms 联动机制）。
 * <p>FAST 档：
 * 1. 快灌 5 万条 1KB（acks=1）
 * 2. 纯拉取速率对比：records=500 vs 5000 各拉完 5 万条计时（poll 往返次数 100 vs 10）
 * 3. 被踢演示：interval=5s + 5000 条 × sleep 2ms = 批 10s > 5s → 必被踢 → rebalance 日志现身
 */
public class Ex02BatchAndKick {

    private static final String TOPIC = "ex02-batch";
    private static final String PAYLOAD = KafkaLabBase.payload(1024);
    private static final int PRELOAD = 50_000;
    private static final AtomicInteger REBALANCE_COUNT = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        KafkaLabBase.createTopics(TOPIC);

        System.out.println("============ EX-02 批量消费与踢边界 ============");
        preload();

        System.out.println("\n--- 场景 1：纯拉取速率（无处理开销，只看 poll 往返次数差异） ---");
        long t500 = drain("ex02-g-500", 500);
        long t5000 = drain("ex02-g-5000", 5000);

        System.out.println("| 场景 | max.poll.records | 拉完 5 万条耗时 | poll 往返次数 |");
        System.out.println("| --- | ---: | ---: | ---: |");
        System.out.printf("| 小批次 | 500  | %,d ms | %d 次 |%n", t500, (PRELOAD + 499) / 500);
        System.out.printf("| 大批次 | 5000 | %,d ms | %d 次 |%n", t5000, (PRELOAD + 4999) / 5000);
        System.out.println("机制解读：单次 poll 往返在本拓扑 ~8ms（EX-01 确认延迟同源）；小批次 100 次往返 vs 大批次 10 次，多出的 ~700ms 是纯开销。"
                + "若单条处理有成本（如逐条写 DB），大批次还能换取下游批量写；但批处理时长 = 条数 × 单条耗时，逼近 interval 即被踢（场景 2）。");

        System.out.println("\n--- 场景 2：被踢演示（interval=5s + 5000 条 × sleep 2ms = 批 10s > 5s） ---");
        kickDemo();
    }

    private static void preload() {
        Properties props = KafkaLabBase.producerProps("1");
        long t0 = System.currentTimeMillis();
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i = 0; i < PRELOAD; i++) {
                producer.send(new ProducerRecord<>(TOPIC, "k" + (i % 100), PAYLOAD));
            }
        }
        System.out.printf("[EX-02] 预灌 %,d 条 1KB 完成，耗时 %,d ms%n", PRELOAD, System.currentTimeMillis() - t0);
    }

    /** 无处理开销拉完 5 万条，返回耗时 ms */
    private static long drain(String group, int maxPollRecords) {
        Properties props = KafkaLabBase.consumerProps(group, maxPollRecords, 60_000);
        long t0 = System.currentTimeMillis();
        int drained = 0;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(TOPIC));
            while (drained < PRELOAD) {
                var records = consumer.poll(Duration.ofMillis(500));
                drained += records.count();
            }
        }
        return System.currentTimeMillis() - t0;
    }

    private static void kickDemo() throws InterruptedException {
        Properties props = KafkaLabBase.consumerProps("ex02-kick", 5000, 5_000);
        props.put("fetch.max.bytes", 10 * 1024 * 1024);
        props.put("max.partition.fetch.bytes", 6 * 1024 * 1024);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(TOPIC), new ConsumerRebalanceListener() {
                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                    REBALANCE_COUNT.incrementAndGet();
                    System.out.println("[EX-02] 🔥 rebalance 触发（第 " + REBALANCE_COUNT.get() + " 次）——分区被剥夺 = 被踢已发生");
                }

                @Override
                public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                }
            });

            System.out.println("[EX-02] 开始消费：批 5000 条 × 2ms = 10s 批时长 > 5s interval，等待被踢...");
            long deadline = System.currentTimeMillis() + 60_000;
            int batches = 0;
            while (System.currentTimeMillis() < deadline && REBALANCE_COUNT.get() < 2) {
                var records = consumer.poll(Duration.ofMillis(1000));
                if (!records.isEmpty()) {
                    batches++;
                    if (batches <= 2) {
                        System.out.printf("[EX-02] 第 %d 批：%d 条，处理预计 %.1fs（> 5s interval）%n",
                                batches, records.count(), records.count() * 2 / 1000.0);
                    }
                    for (var r : records) {
                        Thread.sleep(2);
                    }
                }
            }
            System.out.println("机制解读：单批处理时长超过 max.poll.interval.ms → 消费者来不及心跳 → coordinator 判死 → rebalance。"
                    + "被踢后 offset 回退，消息被重复消费（重复/乱序放大器）。批量参数与 interval 必须联动。");
        }
    }
}
