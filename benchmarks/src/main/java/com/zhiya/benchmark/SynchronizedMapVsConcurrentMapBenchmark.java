package com.example.collection.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 🔒 Collections.synchronizedMap(new HashMap<>()) vs ConcurrentHashMap 全维度纳秒级并发基准测试套件
 *
 * 知识库对应：
 * - Level 6 · 并发容器 —— 从 Collections.synchronizedMap 到 ConcurrentHashMap
 *   - synchronizedMap：方法级 synchronized 锁，所有操作串行化
 *   - ConcurrentHashMap：
 *     - JDK 7：分段锁（Segment），最多 16 个线程并发
 *     - JDK 8+：CAS + synchronized（桶级别），并发度 = 桶数量
 *     - 不允许 null key 和 null value
 *     - 复合操作不是原子的，要用 putIfAbsent / compute
 *     - 扩容时多线程协助搬迁（helpTransfer + ForwardingNode）
 *     - CounterCell 数组分散 CAS 竞争（类似 LongAdder）
 *
 * 【测试维度与全量方法矩阵】
 * 1. 纯读高并发组 (100% Read):
 *    - synchronizedMap_Read    : synchronized 方法级锁，串行化读
 *    - concurrentMap_Read      : CAS volatile 读，无锁
 *
 * 2. 纯写高并发组 (100% Write):
 *    - synchronizedMap_Write   : synchronized 方法级锁，串行化写
 *    - concurrentMap_Write     : 空桶 CAS 无锁 / 非空桶 synchronized 桶级锁
 *
 * 3. 读多写少组 (JMH Group: 9 读 1 写 —— 模拟本地缓存/配置中心):
 *    - synchronizedMap_91Read / synchronizedMap_91Write
 *    - concurrentMap_91Read / concurrentMap_91Write
 *
 * 4. 读写均衡组 (JMH Group: 5 读 5 写 —— 高争用压测):
 *    - synchronizedMap_55Read / synchronizedMap_55Write
 *    - concurrentMap_55Read / concurrentMap_55Write
 *
 * 验证人：imZhiYa
 * 运行方式：
 *   cd benchmarks
 *   mvn clean package -DskipTests
 *   java -jar target/benchmarks.jar SynchronizedMapVsConcurrentMapBenchmark
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class SynchronizedMapVsConcurrentMapBenchmark {

    @Param({"1000", "10000"})
    private int size;

    private Map<String, Integer> synchronizedMap;
    private ConcurrentHashMap<String, Integer> concurrentMap;
    private String[] keys;
    private AtomicInteger counter;

    @Setup
    public void setup() {
        synchronizedMap = Collections.synchronizedMap(new HashMap<>());
        concurrentMap = new ConcurrentHashMap<>();
        keys = new String[size];
        counter = new AtomicInteger(0);

        for (int i = 0; i < size; i++) {
            String key = String.format("key-%06d", i);
            keys[i] = key;
            synchronizedMap.put(key, i);
            concurrentMap.put(key, i);
        }
    }

    // =========================================================================
    // 模块一：纯读高并发组 (100% Read) —— synchronized 串行 vs CAS volatile 读
    // =========================================================================

    /**
     * synchronizedMap 读：synchronized 方法级锁，串行化
     * 即使是读操作也需要获取锁，所有读操作排队等待
     * 锁粒度 = 整个 Map，并发度 = 1
     */
    @Benchmark
    @Threads(8)
    public int synchronizedMap_Read(Blackhole bh) {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += synchronizedMap.get(keys[i % size]);
        }
        bh.consume(sum);
        return sum;
    }

    /**
     * ConcurrentHashMap 读：CAS volatile 读，无锁
     * 8 线程并发读，吞吐量随线程数近似线性增长
     * 底层：volatile Node[] table，保证可见性
     */
    @Benchmark
    @Threads(8)
    public int concurrentMap_Read(Blackhole bh) {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += concurrentMap.get(keys[i % size]);
        }
        bh.consume(sum);
        return sum;
    }

    // =========================================================================
    // 模块二：纯写高并发组 (100% Write) —— 方法级锁 vs 桶级锁
    // =========================================================================

    /**
     * synchronizedMap 写：synchronized 方法级锁，锁住整个 Map
     * 所有写操作串行化，并发度 = 1
     * 高争用下锁竞争激烈，吞吐量受限
     */
    @Benchmark
    @Threads(8)
    public void synchronizedMap_Write(Blackhole bh) {
        int id = counter.getAndIncrement();
        synchronizedMap.put("thread-" + Thread.currentThread().getId() + "-" + id, id);
        bh.consume(id);
    }

    /**
     * ConcurrentHashMap 写：空桶 CAS 无锁 / 非空桶 synchronized 桶级锁
     * 锁粒度 = 单个桶，不同桶的操作完全并行
     * 并发度 = 桶数量（通常远大于线程数）
     */
    @Benchmark
    @Threads(8)
    public void concurrentMap_Write(Blackhole bh) {
        int id = counter.getAndIncrement();
        concurrentMap.put("thread-" + Thread.currentThread().getId() + "-" + id, id);
        bh.consume(id);
    }

    // =========================================================================
    // 模块三：读多写少组 (JMH Group: 9 读 1 写) —— 模拟本地缓存场景
    // =========================================================================

    @Benchmark
    @Group("synchronizedMap_91")
    @GroupThreads(9)
    public int synchronizedMap_91Read(Blackhole bh) {
        int sum = synchronizedMap.get(keys[counter.get() % size]);
        bh.consume(sum);
        return sum;
    }

    @Benchmark
    @Group("synchronizedMap_91")
    @GroupThreads(1)
    public void synchronizedMap_91Write(Blackhole bh) {
        int id = counter.getAndIncrement();
        synchronizedMap.put("key-" + id, id);
        bh.consume(id);
    }

    @Benchmark
    @Group("concurrentMap_91")
    @GroupThreads(9)
    public int concurrentMap_91Read(Blackhole bh) {
        int sum = concurrentMap.get(keys[counter.get() % size]);
        bh.consume(sum);
        return sum;
    }

    @Benchmark
    @Group("concurrentMap_91")
    @GroupThreads(1)
    public void concurrentMap_91Write(Blackhole bh) {
        int id = counter.getAndIncrement();
        concurrentMap.put("key-" + id, id);
        bh.consume(id);
    }

    // =========================================================================
    // 模块四：读写均衡组 (JMH Group: 5 读 5 写) —— 高争用压测
    // =========================================================================

    @Benchmark
    @Group("synchronizedMap_55")
    @GroupThreads(5)
    public int synchronizedMap_55Read(Blackhole bh) {
        int sum = synchronizedMap.get(keys[counter.get() % size]);
        bh.consume(sum);
        return sum;
    }

    @Benchmark
    @Group("synchronizedMap_55")
    @GroupThreads(5)
    public void synchronizedMap_55Write(Blackhole bh) {
        int id = counter.getAndIncrement();
        synchronizedMap.put("key-" + id, id);
        bh.consume(id);
    }

    @Benchmark
    @Group("concurrentMap_55")
    @GroupThreads(5)
    public int concurrentMap_55Read(Blackhole bh) {
        int sum = concurrentMap.get(keys[counter.get() % size]);
        bh.consume(sum);
        return sum;
    }

    @Benchmark
    @Group("concurrentMap_55")
    @GroupThreads(5)
    public void concurrentMap_55Write(Blackhole bh) {
        int id = counter.getAndIncrement();
        concurrentMap.put("key-" + id, id);
        bh.consume(id);
    }
}
