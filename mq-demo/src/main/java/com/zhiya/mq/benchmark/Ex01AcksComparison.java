package com.zhiya.mq.benchmark;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * EX-01 单分区确认代价：acks=1 vs acks=all（mq-02/mq-08 的确认级别机制）。
 * <p>测法：单线程 + 同步 send().get()（in-flight=1，一次只等一个确认）——确认延迟直接成为吞吐分母，
 * 避免异步缓冲吸收 acks 差异（缓冲写满前 acks 差异不可见）。
 * <p>拓扑：单 broker 单副本 → ISR=1 → acks=all 退化为 acks=1，两档差异 ≈ 0 恰是机制正确性证据：
 * acks=all 的代价来自"等 ISR 全部副本"，副本数为 1 时没有可等的对象（真实代价需多副本，见 mq-08 容错预算）。
 * <p>FAST 档：5s 预热 + 15s 测量，1KB 消息，batch=64K，linger=5ms。
 */
public class Ex01AcksComparison {

    private static final String TOPIC = "ex01-acks";
    private static final String PAYLOAD = KafkaLabBase.payload(1024);
    private static final int WARMUP_S = 5;
    private static final int MEASURE_S = 15;

    public static void main(String[] args) throws Exception {
        KafkaLabBase.createTopics(TOPIC);

        System.out.println("============ EX-01 单分区确认代价：acks=1 vs acks=all ============");
        System.out.println("测法：单线程同步 send().get()（in-flight=1，确认延迟=吞吐分母）；1KB 消息；拓扑=单 broker 单副本（ISR=1）");
        System.out.println("每档: 预热 " + WARMUP_S + "s + 测量 " + MEASURE_S + "s（教学量级，机制验证级）\n");

        LatencyStats s1 = run("acks=1", KafkaLabBase.producerProps("1"));
        LatencyStats sall = run("acks=all", KafkaLabBase.producerProps("all"));

        System.out.println("\n| 档位 | 确认吞吐 (msg/s) | 确认延迟 P50 | 确认延迟 P99 |");
        System.out.println("| --- | ---: | ---: | ---: |");
        System.out.printf("| acks=1  | %,d | %.2f ms | %.2f ms |%n", s1.rate(), s1.p50(), s1.p99());
        System.out.printf("| acks=all | %,d | %.2f ms | %.2f ms |%n", sall.rate(), sall.p50(), sall.p99());

        double diff = Math.abs(s1.rate() - sall.rate()) * 100.0 / Math.max(s1.rate(), sall.rate());
        System.out.println("\n机制解读：单副本（ISR=1）下两档差异 " + String.format("%.1f", diff) + "% ≈ 0——acks=all 要等 ISR 全部副本，而 ISR 只有 1 个副本时没有可等的对象，退化为 acks=1。"
                + "这恰好是机制正确性的证据：acks=all 的吞吐代价随副本数增长（mq-08 容错预算 = write quorum − ack quorum），单节点拓扑测不到它。");
    }

    private record LatencyStats(long rate, double p50, double p99) {
    }

    private static LatencyStats run(String label, Properties props) throws Exception {
        List<Long> samples = new ArrayList<>();
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            long warmupUntil = System.currentTimeMillis() + WARMUP_S * 1000L;
            long measureUntil = warmupUntil + MEASURE_S * 1000L;
            long measured = 0;
            long sendStart = System.nanoTime();
            while (System.currentTimeMillis() < measureUntil) {
                long t0 = System.nanoTime();
                RecordMetadata meta = producer.send(new ProducerRecord<>(TOPIC, PAYLOAD)).get();
                long latencyNs = System.nanoTime() - t0;
                if (System.currentTimeMillis() > warmupUntil) {
                    measured++;
                    samples.add(latencyNs);
                }
            }
            Collections.sort(samples);
            double p50 = samples.isEmpty() ? 0 : samples.get(samples.size() / 2) / 1_000_000.0;
            double p99 = samples.isEmpty() ? 0 : samples.get((int) (samples.size() * 0.99)) / 1_000_000.0;
            long rate = measured / MEASURE_S;
            System.out.printf("[EX-01] %s: 确认 %,d 次 → %,d msg/s，P50=%.2f ms，P99=%.2f ms%n",
                    label, measured, rate, p50, p99);
            return new LatencyStats(rate, p50, p99);
        }
    }
}
