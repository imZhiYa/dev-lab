package com.zhiya.mq.benchmark;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * Kafka 实验共享工具：bootstrap 地址（env KAFKA_BOOTSTRAP，默认 localhost:9092）、建 topic、producer 配置。
 */
public final class KafkaLabBase {

    public static final String BOOTSTRAP =
            System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092");

    private KafkaLabBase() {
    }

    /** 先删后建 topic（每轮实验数据干净）；分区数按名字规则：idem=10、backlog=5、其余=1 */
    public static void createTopics(String... names) {
        List<String> nameList = java.util.Arrays.asList(names);
        try (AdminClient admin = AdminClient.create(Map.of(
                org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP))) {
            // 只删存在的（Kafka Admin API 对不存在的 topic 会抛 UnknownTopicOrPartitionException）
            var existingNames = admin.listTopics().names().get();
            List<String> toDelete = nameList.stream().filter(existingNames::contains).toList();
            if (!toDelete.isEmpty()) {
                admin.deleteTopics(toDelete).all().get();
                long deadline = System.currentTimeMillis() + 30_000;
                while (System.currentTimeMillis() < deadline) {
                    var existing = admin.listTopics().names().get();
                    if (nameList.stream().noneMatch(existing::contains)) {
                        break;
                    }
                    Thread.sleep(200);
                }
                // metadata 传播缓冲：删除刚完成时 create 会撞 UnknownTopicOrPartition
                Thread.sleep(1500);
            }
            List<NewTopic> topics = nameList.stream()
                    .map(n -> new NewTopic(n,
                            n.contains("idem") ? 10 : n.contains("backlog") ? 5 : 1,
                            (short) 1))
                    .toList();
            admin.createTopics(topics).all().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException)) {
                throw new IllegalStateException("重建 topic 失败: " + e.getCause().getMessage(), e);
            }
        }
    }

    public static Properties producerProps(String acks) {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        p.put(ProducerConfig.ACKS_CONFIG, acks);
        p.put(ProducerConfig.BATCH_SIZE_CONFIG, 65536);
        p.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        return p;
    }

    public static Properties consumerProps(String groupId, int maxPollRecords, long maxPollIntervalMs) {
        Properties p = new Properties();
        p.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        p.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        p.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        p.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, groupId);
        p.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        p.put(org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, (int) maxPollIntervalMs);
        return p;
    }

    /** 100B / 1KB / 10KB 负载 */
    public static String payload(int size) {
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        return sb.toString();
    }
}
