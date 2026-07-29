package com.zhiya.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 📝 CopyOnWriteArrayList 读写代价 & ConcurrentSkipListMap vs TreeMap 全维度纳秒级基准测试套件
 *
 * 知识库对应：
 * - Level 7.6.1 · CopyOnWriteArrayList —— 读多写少的并发 List
 * - Level 7.6.6 · ConcurrentSkipListMap —— 无锁并发有序 Map
 *
 * 验证人：imZhiYa
 * 运行方式：
 *   java -jar target/benchmarks.jar CopyOnWriteAndSkipListBenchmark
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CopyOnWriteAndSkipListBenchmark {

    @Param({"100", "1000"})
    private int size;

    private CopyOnWriteArrayList<Integer> cowList;
    private ArrayList<Integer> arrayList;
    private Random random;

    private ConcurrentSkipListMap<String, Integer> skipListMap;
    private TreeMap<String, Integer> treeMap;
    private String[] keys;

    @Setup
    public void setup() {
        random = new Random(42);

        cowList = new CopyOnWriteArrayList<>();
        arrayList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            cowList.add(i);
            arrayList.add(i);
        }

        skipListMap = new ConcurrentSkipListMap<>();
        treeMap = new TreeMap<>();
        keys = new String[size];
        for (int i = 0; i < size; i++) {
            String key = String.format("key-%06d", i);
            keys[i] = key;
            skipListMap.put(key, i);
            treeMap.put(key, i);
        }
    }

    @Benchmark
    public int cowList_Read(Blackhole bh) {
        int sum = 0;
        for (int i = 0; i < size; i++) {
            sum += cowList.get(i);
        }
        bh.consume(sum);
        return sum;
    }

    @Benchmark
    public int arrayList_Read(Blackhole bh) {
        int sum = 0;
        for (int i = 0; i < size; i++) {
            sum += arrayList.get(i);
        }
        bh.consume(sum);
        return sum;
    }

    @Benchmark
    public void cowList_Write(Blackhole bh) {
        CopyOnWriteArrayList<Integer> copy = new CopyOnWriteArrayList<>(cowList);
        copy.add(999);
        bh.consume(copy);
    }

    @Benchmark
    public void arrayList_Write(Blackhole bh) {
        ArrayList<Integer> copy = new ArrayList<>(arrayList);
        copy.add(999);
        bh.consume(copy);
    }

    @Benchmark
    public int skipListMap_Get(Blackhole bh) {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += skipListMap.get(keys[random.nextInt(size)]);
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
    public void skipListMap_Put(Blackhole bh) {
        ConcurrentSkipListMap<String, Integer> copy = new ConcurrentSkipListMap<>(skipListMap);
        copy.put("new-key-" + random.nextInt(), random.nextInt());
        bh.consume(copy);
    }

    @Benchmark
    public void treeMap_Put(Blackhole bh) {
        TreeMap<String, Integer> copy = new TreeMap<>(treeMap);
        copy.put("new-key-" + random.nextInt(), random.nextInt());
        bh.consume(copy);
    }
}
