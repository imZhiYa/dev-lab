package com.zhiya.collection.concurrent;

import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Level 7.6.6：ConcurrentSkipListMap 演示
 * <p>
 * 核心结论（来自文档）：
 * - 底层：跳表（不是红黑树），CAS 无锁
 * - O(log n) 插入/查找/删除
 * - 替代 Collections.synchronizedSortedMap(new TreeMap<>())
 * - 实现了 ConcurrentNavigableMap 接口，API 和 TreeMap 几乎一样
 */
public class ConcurrentSkipListMapDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Level 7.6.6：ConcurrentSkipListMap（跳表）===\n");

        basicDemo();
        compareWithTreeMap();
        skipListStructureExplain();
        concurrentDemo();
    }

    // ──────────────────────────────────────
    // 1. 基本用法（API 和 TreeMap 几乎一样）
    // ──────────────────────────────────────
    static void basicDemo() {
        System.out.println("--- 1. 基本用法 ---");

        ConcurrentSkipListMap<String, Integer> map = new ConcurrentSkipListMap<>();
        map.put("Charlie", 3);
        map.put("Alice", 1);
        map.put("Bob", 2);
        map.put("David", 4);

        // 有序遍历（按 key 排序）
        System.out.println("遍历（按 key 排序）:");
        map.forEach((k, v) -> System.out.printf("  %s → %d%n", k, v));

        // NavigableMap 近邻查询
        NavigableMap<String, Integer> nav = map;
        System.out.println("\n近邻查询:");
        System.out.println("  floorKey(\"C\")     = " + nav.floorKey("C"));     // Bob
        System.out.println("  ceilingKey(\"C\")   = " + nav.ceilingKey("C"));   // Charlie
        System.out.println("  lowerKey(\"Charlie\")   = " + nav.lowerKey("Charlie")); // Bob
        System.out.println("  higherKey(\"Charlie\")  = " + nav.higherKey("Charlie")); // David
        System.out.println();
    }

    // ──────────────────────────────────────
    // 2. 与 TreeMap 对比
    // ──────────────────────────────────────
    static void compareWithTreeMap() {
        System.out.println("--- 2. 与 TreeMap 对比 ---");

        // TreeMap：红黑树，不支持并发
        TreeMap<String, Integer> treeMap = new TreeMap<>();
        treeMap.put("Alice", 1);
        treeMap.put("Bob", 2);

        // ConcurrentSkipListMap：跳表，支持并发
        ConcurrentSkipListMap<String, Integer> skipMap = new ConcurrentSkipListMap<>();
        skipMap.put("Alice", 1);
        skipMap.put("Bob", 2);

        System.out.println("TreeMap:                  " + treeMap);
        System.out.println("ConcurrentSkipListMap:    " + skipMap);
        System.out.println();
        System.out.println("关键区别：");
        System.out.println("  +------------------------+--------------+------------------------+");
        System.out.println("  |                        | TreeMap      | ConcurrentSkipListMap  |");
        System.out.println("  +------------------------+--------------+------------------------+");
        System.out.println("  | 底层结构               │ 红黑树       │ 跳表                   |");
        System.out.println("  | 线程安全               │ ❌           │ ✅（CAS 无锁）          |");
        System.out.println("  | 时间复杂度             │ O(log n)     │ O(log n)               |");
        System.out.println("  | 空间                   │ O(n)         │ O(n)（常数因子更大）    |");
        System.out.println("  | 并发插入               │ 需要外部加锁 │ CAS 局部修改，无锁      |");
        System.out.println("  | null key               │ ❌（NPE）    │ ❌（NPE）              |");
        System.out.println("  +------------------------+--------------+------------------------+");
        System.out.println();
    }

    // ──────────────────────────────────────
    // 3. 跳表结构解释
    // ──────────────────────────────────────
    static void skipListStructureExplain() {
        System.out.println("--- 3. 跳表结构解释 ---");
        System.out.println("跳表（Skip List）= 多层有序链表 + 随机化索引");
        System.out.println();
        System.out.println("  Level 3:  head ──────────────────────────────────────> [200] → null");
        System.out.println("  Level 2:  head ──────────────> [150] ────────────────> [200] → null");
        System.out.println("  Level 1:  head ───> [100] ──> [150] ───> [170] ─────> [200] → null");
        System.out.println("  Level 0:  head ──> [100] ──> [130] ──> [150] ──> [170] ──> [200] → null");
        System.out.println("                            ↑");
        System.out.println("                       数据层（完整有序链表）");
        System.out.println();
        System.out.println("  查找 150：");
        System.out.println("    ① 从 Level 3 的 head 开始");
        System.out.println("    ② Level 3: head → 200（超过 150，下降）");
        System.out.println("    ③ Level 2: head → 150（找到！）");
        System.out.println("    查找路径：head → 200(跳过) → 150(命中)，只访问 2 个节点");
        System.out.println();
        System.out.println("  空间复杂度：");
        System.out.println("    每个节点期望层数 = 1/(1-p) ≈ 2（p=0.5）");
        System.out.println("    总节点数 = n + n·p + n·p² + ... = n/(1-p) = O(n)（等比级数收敛）");
        System.out.println("    层数 O(log n) 决定的是查找路径长度，不是总节点数");
        System.out.println();
    }

    // ──────────────────────────────────────
    // 4. 并发演示
    // ──────────────────────────────────────
    static void concurrentDemo() throws Exception {
        System.out.println("--- 4. 并发演示 ---");

        ConcurrentSkipListMap<String, Integer> map = new ConcurrentSkipListMap<>();
        int threadCount = 4;
        int opsPerThread = 1000;

        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < opsPerThread; i++) {
                    map.put("thread" + threadId + "-key" + i, i);
                }
            });
            threads[t].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        System.out.printf("4 线程各写入 %d 个 → map.size() = %d%n", opsPerThread, map.size());
        System.out.println("前 5 个 entry（有序）:");
        map.entrySet().stream().limit(5).forEach(e ->
                System.out.printf("  %s → %d%n", e.getKey(), e.getValue()));
        System.out.println("...");
        System.out.println();
    }
}

