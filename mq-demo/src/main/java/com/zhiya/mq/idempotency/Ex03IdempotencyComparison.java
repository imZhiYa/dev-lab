package com.zhiya.mq.idempotency;

import com.zhiya.mq.benchmark.KafkaLabBase;
import com.zhiya.mq.core.IdempotencyKey;
import com.zhiya.mq.core.OrderStateMachine;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import redis.clients.jedis.Jedis;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EX-03 幂等三方案对比（mq-10 L3）：正确性必须落在 DB 事务，Redis 只做性能层。
 * <p>注入：10 个 order × 同一事件重复 300 次（共 3000 条，10 分区并发消费），正确处理数应为 10。
 * 方案 A：DB 唯一索引（INSERT 冲突 = 重复）
 * 方案 B：Redis SETNX（键存在 = 重复）
 * 方案 C：状态机 + DB 唯一索引（内存状态拦一层，DB 硬闸兜底）
 * <p>故障注入：Redis maxmemory 1MB + allkeys-lru 灌大 key 触发淘汰 → 重放 → 方案 B 漏幂等率上升。
 */
public class Ex03IdempotencyComparison {

    private static final String TOPIC = "ex03-idem";
    private static final int ORDERS = 10;
    private static final int DUPS = 300;
    private static final int TOTAL = ORDERS * DUPS;
    private static final String DB_URL = "jdbc:mysql://localhost:3306/mqlab?connectTimeout=2000";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "mqlab";
    private static final String REDIS_HOST = "localhost";

    private static final Map<String, OrderStateMachine.State> STATE = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        KafkaLabBase.createTopics(TOPIC);
        initDb();
        Class.forName("com.mysql.cj.jdbc.Driver");
        // 重置 Redis 到干净状态（上一轮可能残留 maxmemory 限制与键）
        try (Jedis jedis = new Jedis(REDIS_HOST, 6379)) {
            jedis.configSet("maxmemory", "0");
            jedis.flushAll();
        }

        System.out.println("============ EX-03 幂等三方案对比 ============");
        System.out.println("注入：" + ORDERS + " order × 同事件重复 " + DUPS + " 次 = " + TOTAL + " 条（10 分区并发），正确处理数应为 " + ORDERS);

        inject();

        System.out.println("\n--- 第一轮：正常态（理想处理数 = " + ORDERS + "） ---");
        Result round1 = consumeAndCount("ex03-r1");
        printTable(round1, ORDERS, TOTAL);

        System.out.println("\n--- 第二轮：Redis 淘汰注入（maxmemory 16MB + allkeys-lru，灌大 key 触发淘汰）后重放（理想处理数 = 0，全部是重复） ---");
        evictRedis();
        Result round2 = consumeAndCount("ex03-r2");
        printTable(round2, 0, ORDERS);

        System.out.println("\n机制解读：DB 唯一索引跨两轮零漏（淘汰不动 DB）；Redis 方案淘汰后幂等键丢失 → 重复放行 → 漏幂等。Redis 只能做性能层前置过滤，最终正确性必须落在 DB 事务。");
    }

    private static void initDb() throws SQLException {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS idem_a (k VARCHAR(100) PRIMARY KEY, n INT)");
            s.execute("CREATE TABLE IF NOT EXISTS idem_c (k VARCHAR(100) PRIMARY KEY, n INT)");
            s.execute("TRUNCATE TABLE idem_a");
            s.execute("TRUNCATE TABLE idem_c");
        }
    }

    private static void inject() {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(KafkaLabBase.producerProps("1"))) {
            for (int order = 0; order < ORDERS; order++) {
                String key = "order-" + order;
                for (int d = 0; d < DUPS; d++) {
                    producer.send(new ProducerRecord<>(TOPIC, key, IdempotencyKey.of(key, "CREATED", 1)));
                }
            }
        }
        System.out.println("[EX-03] 注入完成");
    }

    private record Result(int processedA, int processedB, int processedC) {
    }

    private static Result consumeAndCount(String group) {
        Properties props = KafkaLabBase.consumerProps(group, 500, 60_000);
        int a = 0, b = 0, c = 0;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
             Jedis jedis = new Jedis(REDIS_HOST, 6379);
             Connection db = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            consumer.subscribe(List.of(TOPIC));
            int polled = 0;
            long deadline = System.currentTimeMillis() + 120_000;
            while (polled < TOTAL && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                    polled++;
                    String orderId = r.key();
                    // 方案 A：DB 唯一索引（INSERT 冲突 = 重复）
                    try (var ps = db.prepareStatement("INSERT INTO idem_a (k, n) VALUES (?, 1)")) {
                        ps.setString(1, r.value());
                        ps.executeUpdate();
                        a++;
                    } catch (SQLException dup) {
                        // 唯一键冲突 = 重复，正确拦截
                    }
                    // 方案 B：Redis SETNX（键存在 = 重复）；OOM/连接异常 = 幂等层失效 → 放行处理（=漏幂等）
                    try {
                        if (jedis.setnx(r.value(), "1") == 1) {
                            jedis.expire(r.value(), 60);
                            b++;
                        }
                    } catch (redis.clients.jedis.exceptions.JedisException redisDown) {
                        b++;
                    }
                    // 方案 C：状态机 + DB 唯一索引
                    var outcome = OrderStateMachine.apply(
                            STATE.getOrDefault(orderId, OrderStateMachine.State.NONE),
                            OrderStateMachine.Event.CREATED);
                    if (outcome.result() == OrderStateMachine.Result.APPLIED) {
                        try (var ps = db.prepareStatement("INSERT INTO idem_c (k, n) VALUES (?, 1)")) {
                            ps.setString(1, r.value());
                            ps.executeUpdate();
                            STATE.put(orderId, outcome.state());
                            c++;
                        } catch (SQLException dup) {
                            // DB 硬闸兜底：状态机漏判时唯一索引拦截
                        }
                    }
                }
            }
            System.out.printf("[EX-03] group=%s 消费 %d 条：A(DB 唯一索引)=%d 次处理 B(Redis SETNX)=%d 次处理 C(状态机+DB)=%d 次处理（理想各 10）%n",
                    group, polled, a, b, c);
            return new Result(a, b, c);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void evictRedis() {
        try (Jedis jedis = new Jedis(REDIS_HOST, 6379)) {
            jedis.configSet("maxmemory", "16mb");
            jedis.configSet("maxmemory-policy", "allkeys-lru");
            // 第一轮留下的 10 个幂等键（最久未用）应最先被 LRU 淘汰
            List<String> idemKeys = java.util.stream.IntStream.range(0, ORDERS)
                    .mapToObj(o -> IdempotencyKey.of("order-" + o, "CREATED", 1))
                    .toList();
            long before = jedis.dbSize();
            long aliveBefore = idemKeys.stream().filter(jedis::exists).count();
            String big = "x".repeat(2048);
            int filled = 0;
            int oomRejected = 0;
            for (int i = 0; i < 20000 && oomRejected < 100; i++) {
                try {
                    jedis.set("evict:" + i, big);
                    filled++;
                } catch (redis.clients.jedis.exceptions.JedisDataException oom) {
                    oomRejected++;
                }
            }
            long aliveAfter = idemKeys.stream().filter(jedis::exists).count();
            System.out.printf("[EX-03] 淘汰注入：maxmemory=16MB 灌大 key %d 条（OOM 拒绝 %d 次=内存已满）；幂等键存活 %d/%d → %d/%d（LRU 先踢最久未用）%n",
                    filled, oomRejected, aliveBefore, idemKeys.size(), aliveAfter, idemKeys.size());
        }
    }

    /** ideal：本轮的理想处理次数；denominator：漏幂等率分母（第一轮=总投递数，第二轮=业务事件数） */
    private static void printTable(Result r, int ideal, int denominator) {
        double leakA = Math.max(0, r.processedA() - ideal) * 100.0 / denominator;
        double leakB = Math.max(0, r.processedB() - ideal) * 100.0 / denominator;
        double leakC = Math.max(0, r.processedC() - ideal) * 100.0 / denominator;
        System.out.println("| 方案 | 实际处理次数（理想 " + ideal + "） | 漏幂等率 |");
        System.out.println("| --- | ---: | ---: |");
        System.out.printf("| A：DB 唯一索引 | %d | %.1f%% |%n", r.processedA(), leakA);
        System.out.printf("| B：Redis SETNX | %d | %.1f%% |%n", r.processedB(), leakB);
        System.out.printf("| C：状态机 + DB 唯一索引 | %d | %.1f%% |%n", r.processedC(), leakC);
    }
}
