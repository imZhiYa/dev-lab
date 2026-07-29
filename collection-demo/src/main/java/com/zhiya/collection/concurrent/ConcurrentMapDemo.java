package com.zhiya.collection.concurrent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Level 6：ConcurrentHashMap 并发容器演示
 * <p>
 * 核心结论（来自文档）：
 * - JDK 7：分段锁（Segment），最多 16 个线程并发
 * - JDK 8+：CAS + synchronized（桶级别），并发度 = 桶数量
 * - 不允许 null key 和 null value
 * - 复合操作不是原子的，要用 putIfAbsent / compute
 */
public class ConcurrentMapDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Level 6：ConcurrentHashMap 并发容器 ===\n");

        nullNotAllowed();
        compositeOperationTrap();
        concurrentSafetyComparison();
        counterCellDemo();
    }

    // ──────────────────────────────────────
    // 1. 不允许 null key/value
    // ──────────────────────────────────────
    static void nullNotAllowed() {
        System.out.println("--- 1. 不允许 null key/value ---");

        // HashMap 允许 null
        Map<String, String> hashMap = new HashMap<>();
        hashMap.put(null, "value");
        hashMap.put("key", null);
        System.out.println("HashMap 允许 null key 和 null value: " + hashMap);

        // ConcurrentHashMap 不允许 null
        Map<String, String> concurrentMap = new ConcurrentHashMap<>();
        try {
            concurrentMap.put(null, "value");
        } catch (NullPointerException e) {
            System.out.println("ConcurrentHashMap.put(null, ...) → NPE（设计决策）");
        }

        try {
            concurrentMap.put("key", null);
        } catch (NullPointerException e) {
            System.out.println("ConcurrentHashMap.put(\"key\", null) → NPE（设计决策）");
        }

        System.out.println("原因：并发环境下 null 语义模糊");
        System.out.println("  get(key) 返回 null → 是 key 不存在？还是 value 就是 null？");
        System.out.println("  单线程可以用 containsKey() 区分，并发下不行");
        System.out.println();
    }

    // ──────────────────────────────────────
    // 2. 复合操作陷阱
    // ──────────────────────────────────────
    static void compositeOperationTrap() throws Exception {
        System.out.println("--- 2. 复合操作不是原子的 ---");

        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        map.put("count", 0);

        // 错误：check-then-act 不是原子的
        // if (!map.containsKey("count")) map.put("count", 0);  ← 竞态条件

        // 正确：使用原子操作
        map.putIfAbsent("count", 0);  // 原子的
        map.compute("count", (k, v) -> v == null ? 1 : v + 1);  // 原子的
        map.merge("count", 1, Integer::sum);  // 原子的

        System.out.println("putIfAbsent / compute / merge 都是原子操作");
        System.out.println("当前 count = " + map.get("count"));
        System.out.println();
    }

    // ──────────────────────────────────────
    // 3. 并发安全性对比
    // ──────────────────────────────────────
    static void concurrentSafetyComparison() throws Exception {
        System.out.println("--- 3. 并发安全性对比 ---");

        int threadCount = 10;
        int opsPerThread = 10_000;

        // HashMap（不安全）
        Map<Integer, Integer> unsafeMap = new HashMap<>();
        runConcurrent(unsafeMap, threadCount, opsPerThread);
        System.out.printf("HashMap 并发写入：期望 %d，实际 %d ← 数据丢失！%n",
                threadCount * opsPerThread, unsafeMap.size());

        // Collections.synchronizedMap（安全但慢）
        Map<Integer, Integer> syncMap = Collections.synchronizedMap(new HashMap<>());
        runConcurrent(syncMap, threadCount, opsPerThread);
        System.out.printf("synchronizedMap 并发写入：期望 %d，实际 %d ← 正确但串行化%n",
                threadCount * opsPerThread, syncMap.size());

        // ConcurrentHashMap（安全且快）
        ConcurrentHashMap<Integer, Integer> concurrentMap = new ConcurrentHashMap<>();
        runConcurrent(concurrentMap, threadCount, opsPerThread);
        System.out.printf("ConcurrentHashMap 并发写入：期望 %d，实际 %d ← 正确且高效%n",
                threadCount * opsPerThread, concurrentMap.size());
        System.out.println();
    }

    static void runConcurrent(Map<Integer, Integer> map, int threadCount, int opsPerThread) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        int key = counter.getAndIncrement();
                        map.put(key, key);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
    }

    // ──────────────────────────────────────
    // 4. CounterCell 计数器演示
    // ──────────────────────────────────────
    static void counterCellDemo() {
        System.out.println("--- 4. CounterCell 计数器（减少 CAS 竞争）---");
        System.out.println("单变量 CAS（高竞争）：");
        System.out.println("  Thread1 → CAS(size) → 失败，重试");
        System.out.println("  Thread2 → CAS(size) → 失败，重试");
        System.out.println("  Thread3 → CAS(size) → 成功");
        System.out.println();
        System.out.println("CounterCell 数组（分散竞争）：");
        System.out.println("  Thread1 → CAS(counter[0]) → 成功");
        System.out.println("  Thread2 → CAS(counter[1]) → 成功");
        System.out.println("  Thread3 → CAS(counter[2]) → 成功");
        System.out.println("  汇总：size = counter[0] + counter[1] + counter[2]");
        System.out.println();

        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        for (int i = 0; i < 10000; i++) {
            map.put("key" + i, i);
        }
        System.out.println("map.size() = " + map.size()); // 近似值
        System.out.println("map.mappingCount() = " + map.mappingCount()); // 推荐用这个
        System.out.println();
    }
}
