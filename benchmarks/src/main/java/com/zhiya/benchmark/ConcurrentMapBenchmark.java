package com.zhiya.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 🔒 ConcurrentHashMap vs Collections.synchronizedMap 全维度纳秒级并发基准测试套件
 *
 * 知识库对应：
 * - Level 6 · 并发容器 —— 从 Collections.synchronizedMap 到 ConcurrentHashMap
 *
 * 验证人：imZhiYa
 * 运行方式：
 *   java -jar target/benchmarks.jar ConcurrentMapBenchmark
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(8)
public class ConcurrentMapBenchmark {

    @Param({"1000", "10000"})
    private int size;

    private ConcurrentHashMap<String, Integer> concurrentMap;
    private Map<String, Integer> synchronizedMap;
    private String[] keys;
    private AtomicInteger counter;

    @Setup
    public void setup() {
        concurrentMap = new ConcurrentHashMap<>();
        synchronizedMap = Collections.synchronizedMap(new HashMap<>());
        keys = new String[size];
        counter = new AtomicInteger(0);

        for (int i = 0; i < size; i++) {
            String key = String.format("key-%06d", i);
            keys[i] = key;
            concurrentMap.put(key, i);
            synchronizedMap.put(key, i);
        }
    }

    @Benchmark
    public int concurrentMap_Read(Blackhole bh) {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += concurrentMap.get(keys[i % size]);
        }
        bh.consume(sum);
        return sum;
    }

    @Benchmark
    public int synchronizedMap_Read(Blackhole bh) {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += synchronizedMap.get(keys[i % size]);
        }
        bh.consume(sum);
        return sum;
    }

    @Benchmark
    public void concurrentMap_Write(Blackhole bh) {
        int id = counter.getAndIncrement();
        concurrentMap.put("thread-" + Thread.currentThread().getId() + "-" + id, id);
        bh.consume(id);
    }

    @Benchmark
    public void synchronizedMap_Write(Blackhole bh) {
        int id = counter.getAndIncrement();
        synchronizedMap.put("thread-" + Thread.currentThread().getId() + "-" + id, id);
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
}
