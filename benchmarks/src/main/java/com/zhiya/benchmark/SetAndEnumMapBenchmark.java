package com.zhiya.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 🎯 HashSet vs TreeSet vs EnumSet & HashMap vs EnumMap 全维度纳秒级基准测试套件
 *
 * 知识库对应：
 * - Level 7.6.3 · EnumSet —— 枚举专用的位运算 Set
 * - Level 7.6.4 · EnumMap —— 枚举专用的高性能 Map
 *
 * 验证人：imZhiYa
 * 运行方式：
 *   java -jar target/benchmarks.jar SetAndEnumMapBenchmark
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class SetAndEnumMapBenchmark {

    enum Permission {
        READ, WRITE, EXECUTE, DELETE, CREATE, UPDATE, ADMIN, GUEST,
        UPLOAD, DOWNLOAD, SHARE, COMMENT, LIKE, FOLLOW, REPORT, BAN
    }

    enum Status {
        PENDING, PROCESSING, SUCCESS, FAILED, CANCELLED, TIMEOUT
    }

    @Param({"100", "1000"})
    private int size;

    private HashSet<String> hashSet;
    private TreeSet<String> treeSet;
    private EnumSet<Permission> enumSet;
    private String[] keys;
    private Permission[] permissions;

    private HashMap<Status, Integer> hashMap;
    private EnumMap<Status, Integer> enumMap;
    private Status[] statuses;

    private Random random;

    @Setup
    public void setup() {
        random = new Random(42);

        hashSet = new HashSet<>();
        treeSet = new TreeSet<>();
        enumSet = EnumSet.allOf(Permission.class);
        keys = new String[size];
        permissions = Permission.values();

        for (int i = 0; i < size; i++) {
            String key = String.format("key-%06d", i);
            keys[i] = key;
            hashSet.add(key);
            treeSet.add(key);
        }

        hashMap = new HashMap<>();
        enumMap = new EnumMap<>(Status.class);
        statuses = Status.values();

        for (Status s : statuses) {
            hashMap.put(s, s.ordinal());
            enumMap.put(s, s.ordinal());
        }
    }

    @Benchmark
    public boolean hashSet_Contains(Blackhole bh) {
        boolean result = hashSet.contains(keys[random.nextInt(size)]);
        bh.consume(result);
        return result;
    }

    @Benchmark
    public boolean treeSet_Contains(Blackhole bh) {
        boolean result = treeSet.contains(keys[random.nextInt(size)]);
        bh.consume(result);
        return result;
    }

    @Benchmark
    public boolean enumSet_Contains(Blackhole bh) {
        boolean result = enumSet.contains(permissions[random.nextInt(permissions.length)]);
        bh.consume(result);
        return result;
    }

    @Benchmark
    public int hashMap_Get(Blackhole bh) {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += hashMap.get(statuses[random.nextInt(statuses.length)]);
        }
        bh.consume(sum);
        return sum;
    }

    @Benchmark
    public int enumMap_Get(Blackhole bh) {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += enumMap.get(statuses[random.nextInt(statuses.length)]);
        }
        bh.consume(sum);
        return sum;
    }

    @Benchmark
    public int hashMap_Iterate(Blackhole bh) {
        int sum = 0;
        for (Map.Entry<Status, Integer> entry : hashMap.entrySet()) {
            sum += entry.getValue();
        }
        bh.consume(sum);
        return sum;
    }

    @Benchmark
    public int enumMap_Iterate(Blackhole bh) {
        int sum = 0;
        for (Map.Entry<Status, Integer> entry : enumMap.entrySet()) {
            sum += entry.getValue();
        }
        bh.consume(sum);
        return sum;
    }
}
