package com.zhiya.collection.map;

import java.util.*;

/**
 * Level 5 + 5.5：TreeMap 与 LinkedHashMap（LRU）演示
 *
 * 核心结论（来自文档）：
 * - TreeMap：红黑树，O(log n)，key 必须实现 Comparable 或传入 Comparator
 * - LinkedHashMap：HashMap + 双向链表，O(1)，支持插入顺序和访问顺序（LRU）
 * - accessOrder=true + 重写 removeEldestEntry = LRU 缓存
 */
public class TreeMapLinkedHashMapDemo {

    public static void main(String[] args) {
        System.out.println("=== Level 5 + 5.5：TreeMap & LinkedHashMap（LRU）===\n");

        treeMapBasics();
        linkedHashMapInsertOrder();
        lruCacheDemo();
        navigableMapDemo();
    }

    // ──────────────────────────────────────
    // 1. TreeMap 基础：有序 + 近邻查询
    // ──────────────────────────────────────
    static void treeMapBasics() {
        System.out.println("--- 1. TreeMap 基础 ---");

        // TreeMap：红黑树，按 key 排序
        TreeMap<Integer, String> scores = new TreeMap<>();
        scores.put(85, "Bob");
        scores.put(90, "Alice");
        scores.put(70, "Charlie");
        scores.put(95, "David");

        System.out.println("遍历顺序（按 key 升序）:");
        scores.forEach((k, v) -> System.out.printf("  %d → %s%n", k, v));

        // NavigableMap 近邻查询（SortedMap 没有的能力）
        System.out.println("\n近邻查询（NavigableMap 新增）:");
        System.out.println("  floorKey(80)   = " + scores.floorKey(80));    // 70（≤80 的最大 key）
        System.out.println("  ceilingKey(80) = " + scores.ceilingKey(80));  // 85（≥80 的最小 key）
        System.out.println("  lowerKey(90)   = " + scores.lowerKey(90));    // 85（<90 的最大 key）
        System.out.println("  higherKey(90)  = " + scores.higherKey(90));   // 95（>90 的最小 key）

        // TreeMap 不允许 null key（comparator.compare(null, x) 会 NPE）
        try {
            scores.put(null, "test");
        } catch (NullPointerException e) {
            System.out.println("\n  TreeMap.put(null, ...) → NPE");
        }
        System.out.println();
    }

    // ──────────────────────────────────────
    // 2. LinkedHashMap：插入顺序
    // ──────────────────────────────────────
    static void linkedHashMapInsertOrder() {
        System.out.println("--- 2. LinkedHashMap：插入顺序（默认） ---");

        // accessOrder=false（默认）：按插入顺序遍历
        Map<String, Integer> insertOrder = new LinkedHashMap<>(); // 等价于 new LinkedHashMap<>(16, 0.75f, false)
        insertOrder.put("C", 3);
        insertOrder.put("A", 1);
        insertOrder.put("B", 2);

        System.out.println("LinkedHashMap（accessOrder=false，插入顺序）:");
        insertOrder.forEach((k, v) -> System.out.printf("  %s → %d%n", k, v));
        // 遍历顺序：C → A → B（插入顺序）

        // 对比 HashMap：无序
        Map<String, Integer> hashMap = new HashMap<>();
        hashMap.put("C", 3);
        hashMap.put("A", 1);
        hashMap.put("B", 2);
        System.out.println("\nHashMap（无序，取决于哈希值）:");
        hashMap.forEach((k, v) -> System.out.printf("  %s → %d%n", k, v));
        System.out.println();
    }

    // ──────────────────────────────────────
    // 3. LRU 缓存：accessOrder=true + removeEldestEntry
    // ──────────────────────────────────────
    static void lruCacheDemo() {
        System.out.println("--- 3. LRU 缓存（面试高频手写题）---");

        LRUCache<String, String> cache = new LRUCache<>(3); // 最多 3 个元素

        cache.put("A", "1");
        cache.put("B", "2");
        cache.put("C", "3");
        System.out.println("put A, B, C → " + cache);

        cache.get("A"); // 访问 A，A 移到尾部（最近使用）
        System.out.println("get(A)     → " + cache);

        cache.put("D", "4"); // 插入 D，超过容量，B（最久未访问）被淘汰
        System.out.println("put D      → " + cache + "  ← B 被淘汰");
        System.out.println();
    }

    // LRU 缓存实现（基于 LinkedHashMap）
    static class LRUCache<K, V> extends LinkedHashMap<K, V> {
        private final int maxCapacity;

        LRUCache(int maxCapacity) {
            // accessOrder=true → 按访问顺序（LRU 语义）
            super(maxCapacity, 0.75f, true);
            this.maxCapacity = maxCapacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxCapacity; // 超过容量时自动移除最久未访问的 entry
        }
    }

    // ──────────────────────────────────────
    // 4. NavigableMap 完整演示
    // ──────────────────────────────────────
    static void navigableMapDemo() {
        System.out.println("--- 4. NavigableMap 完整演示 ---");

        TreeMap<Integer, String> map = new TreeMap<>();
        map.put(10, "A");
        map.put(20, "B");
        map.put(30, "C");
        map.put(40, "D");
        map.put(50, "E");

        System.out.println("原始 map: " + map);

        // SortedMap 的方法（JDK 1.2）
        System.out.println("\nSortedMap 方法:");
        System.out.println("  firstKey()           = " + map.firstKey());           // 10
        System.out.println("  lastKey()            = " + map.lastKey());            // 50
        System.out.println("  headMap(30)          = " + map.headMap(30));          // {10=A, 20=B}
        System.out.println("  tailMap(30)          = " + map.tailMap(30));          // {30=C, 40=D, 50=E}
        System.out.println("  subMap(20, 40)       = " + map.subMap(20, 40));       // {20=B, 30=C}

        // NavigableMap 的方法（JDK 6+）—— 近邻查询
        System.out.println("\nNavigableMap 新增方法:");
        System.out.println("  floorKey(25)         = " + map.floorKey(25));         // 20（≤25 的最大 key）
        System.out.println("  ceilingKey(25)       = " + map.ceilingKey(25));       // 30（≥25 的最小 key）
        System.out.println("  lowerKey(30)         = " + map.lowerKey(30));         // 20（<30 的最大 key）
        System.out.println("  higherKey(30)        = " + map.higherKey(30));        // 40（>30 的最小 key）

        // pollFirstEntry / pollLastEntry（取出并移除）
        System.out.println("\npollFirstEntry() = " + map.pollFirstEntry()); // 10=A
        System.out.println("pollLastEntry()  = " + map.pollLastEntry());   // 50=E
        System.out.println("剩余: " + map);

        // descendingMap（逆序）
        System.out.println("descendingMap() = " + map.descendingMap());

        // 带边界控制的 headMap/tailMap/subMap
        System.out.println("\n带边界控制:");
        System.out.println("  headMap(30, true)    = " + map.headMap(30, true));    // {20=B, 30=C}
        System.out.println("  headMap(30, false)   = " + map.headMap(30, false));   // {20=B}
        System.out.println("  tailMap(20, true)    = " + map.tailMap(20, true));    // {20=B, 30=C, 40=D}
        System.out.println("  tailMap(20, false)   = " + map.tailMap(20, false));   // {30=C, 40=D}
        System.out.println();
    }
}
