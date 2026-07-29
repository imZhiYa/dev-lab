package com.zhiya.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 🗺️ HashMap vs TreeMap vs LinkedHashMap 全维度纳秒级基准测试套件
 *
 * 知识库对应：
 * - Level 3 · 哈希的本质 —— 从数组到 HashMap 的演进
 * - Level 4 · HashMap 源码级拆解 —— 从 put() 到红黑树
 * - Level 5 · TreeMap 与排序 —— 红黑树的另一面
 * - Level 5.5 · LinkedHashMap 与 LRU
 *
 * 验证人：imZhiYa
 * 运行方式：
 *   java -jar target/benchmarks.jar MapBenchmark
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class MapBenchmark {

    @Param({"100", "1000", "10000"})
    private int size;

    private HashMap<String, Integer> hashMap;
    private TreeMap<String, Integer> treeMap;
    private LinkedHashMap<String, Integer> linkedHashMap;
    private String[] keys;
    private Random random;

    @Setup
    public void setup() {
        hashMap = new HashMap<>();
        treeMap = new TreeMap<>();
        linkedHashMap = new LinkedHashMap<>();
        keys = new String[size];
        random = new Random(42);

        for (int i = 0; i < size; i++) {
            String key = String.format("key-%06d", i);
            keys[i] = key;
            hashMap.put(key, i);
            treeMap.put(key, i);
            linkedHashMap.put(key, i);
        }
    }

    @Benchmark
    public void hashMap_Put(Blackhole bh) {
        HashMap<String, Integer> copy = new HashMap<>(hashMap);
        copy.put("new-key", 999);
        bh.consume(copy);
    }

    @Benchmark
    public void treeMap_Put(Blackhole bh) {
        TreeMap<String, Integer> copy = new TreeMap<>(treeMap);
        copy.put("new-key", 999);
        bh.consume(copy);
    }

    @Benchmark
    public void linkedHashMap_Put(Blackhole bh) {
        LinkedHashMap<String, Integer> copy = new LinkedHashMap<>(linkedHashMap);
        copy.put("new-key", 999);
        bh.consume(copy);
    }

    @Benchmark
    public int hashMap_Get(Blackhole bh) {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += hashMap.get(keys[random.nextInt(size)]);
        }
        bh.consume(sum);
        return sum;
    }

    @Benchmark
    public int treeMap_Get(Blackhole bh) {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += treeMap.get(keys[random.nextInt(size)]);
        }
        bh.consume(sum);
        return sum;
    }

    @Benchmark
    public int linkedHashMap_Get(Blackhole bh) {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += linkedHashMap.get(keys[random.nextInt(size)]);
        }
        bh.consume(sum);
        return sum;
    }

    @Benchmark
    public int hashMap_Iterate(Blackhole bh) {
        int sum = 0;
        for (Map.Entry<String, Integer> entry : hashMap.entrySet()) {
            sum += entry.getValue();
        }
        bh.consume(sum);
        return sum;
    }

    @Benchmark
    public int treeMap_Iterate(Blackhole bh) {
        int sum = 0;
        for (Map.Entry<String, Integer> entry : treeMap.entrySet()) {
            sum += entry.getValue();
        }
        bh.consume(sum);
        return sum;
    }

    @Benchmark
    public int linkedHashMap_Iterate(Blackhole bh) {
        int sum = 0;
        for (Map.Entry<String, Integer> entry : linkedHashMap.entrySet()) {
            sum += entry.getValue();
        }
        bh.consume(sum);
        return sum;
    }
}
