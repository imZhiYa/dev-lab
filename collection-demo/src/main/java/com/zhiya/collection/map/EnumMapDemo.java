package com.zhiya.collection.map;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Level 7.6.4：EnumMap 演示
 * <p>
 * 核心结论（来自文档）：
 * - Key 必须是枚举类型
 * - 底层：ordinal 做数组索引，零哈希零冲突，O(1) 直接寻址
 * - 不允许 null key（枚举值不可能是 null），允许 null value
 * - 遍历顺序 = 枚举声明顺序（ordinal 顺序）
 */
public class EnumMapDemo {

    enum Status {
        PENDING, PROCESSING, SUCCESS, FAILED
    }

    enum Day {
        MON, TUE, WED, THU, FRI, SAT, SUN
    }

    public static void main(String[] args) {
        System.out.println("=== Level 7.6.4：EnumMap（枚举专用 Map）===\n");

        basicDemo();
        compareWithHashMap();
        ordinalIndexExplain();
    }

    // ──────────────────────────────────────
    // 1. 基本用法
    // ──────────────────────────────────────
    static void basicDemo() {
        System.out.println("--- 1. 基本用法 ---");

        // 按状态统计订单数
        Map<Status, Integer> orderCount = new EnumMap<>(Status.class);
        orderCount.put(Status.PENDING, 10);
        orderCount.put(Status.PROCESSING, 5);
        orderCount.put(Status.SUCCESS, 95);
        orderCount.put(Status.FAILED, 2);

        // 遍历顺序 = 枚举声明顺序（ordinal 0 → 3）
        System.out.println("订单状态统计（按 ordinal 顺序）:");
        orderCount.forEach((k, v) -> System.out.printf("  %s: %d%n", k, v));

        // 按星期统计访问量
        Map<Day, Integer> weeklyVisits = new EnumMap<>(Day.class);
        weeklyVisits.put(Day.MON, 1200);
        weeklyVisits.put(Day.TUE, 1100);
        weeklyVisits.put(Day.WED, 1300);
        weeklyVisits.put(Day.THU, 1250);
        weeklyVisits.put(Day.FRI, 1400);
        weeklyVisits.put(Day.SAT, 800);
        weeklyVisits.put(Day.SUN, 700);

        System.out.println("\n每周访问量:");
        weeklyVisits.forEach((k, v) -> System.out.printf("  %s: %d%n", k, v));

        // 不允许 null key
        try {
            orderCount.put(null, 0);
        } catch (NullPointerException e) {
            System.out.println("\nEnumMap.put(null, ...) → NPE（枚举值不可能是 null）");
        }
        System.out.println();
    }

    // ──────────────────────────────────────
    // 2. 与 HashMap 对比
    // ──────────────────────────────────────
    static void compareWithHashMap() {
        System.out.println("--- 2. 与 HashMap 对比 ---");

        // HashMap：需要哈希计算，可能冲突
        Map<Status, String> hashMap = new HashMap<>();
        hashMap.put(Status.PENDING, "等待中");
        hashMap.put(Status.SUCCESS, "成功");

        // EnumMap：ordinal 直接做索引，零哈希零冲突
        Map<Status, String> enumMap = new EnumMap<>(Status.class);
        enumMap.put(Status.PENDING, "等待中");
        enumMap.put(Status.SUCCESS, "成功");

        System.out.println("HashMap 遍历（无序，取决于哈希值）:");
        hashMap.forEach((k, v) -> System.out.printf("  %s → %s%n", k, v));

        System.out.println("EnumMap 遍历（ordinal 顺序）:");
        enumMap.forEach((k, v) -> System.out.printf("  %s → %s%n", k, v));
        System.out.println();
    }

    // ──────────────────────────────────────
    // 3. ordinal 索引原理
    // ──────────────────────────────────────
    static void ordinalIndexExplain() {
        System.out.println("--- 3. ordinal 索引原理 ---");
        System.out.println("EnumMap 底层结构：");
        System.out.println("  EnumMap<Status, Integer>");
        System.out.println("    keyUniverse = Status.class.getEnumConstants()");
        System.out.println("    vals = Object[4]  ← 数组长度 = 枚举值个数");
        System.out.println();
        System.out.println("  get(Status.SUCCESS)  → vals[2]           ← ordinal=2，直接数组索引，O(1)");
        System.out.println("  put(Status.FAILED, 1) → vals[3] = 1      ← ordinal=3，直接数组索引，O(1)");
        System.out.println();
        System.out.println("  对比 HashMap：");
        System.out.println("  - HashMap：key.hashCode() → 扰动函数 → & (n-1) → 桶索引 → 可能冲突 → 链表/红黑树");
        System.out.println("  - EnumMap：key.ordinal() → 直接数组索引，零冲突，零开销");
        System.out.println();
        System.out.println("  Status 各值的 ordinal：");
        System.out.println("    PENDING(0)    → vals[0]");
        System.out.println("    PROCESSING(1) → vals[1]");
        System.out.println("    SUCCESS(2)    → vals[2]");
        System.out.println("    FAILED(3)     → vals[3]");
    }
}
