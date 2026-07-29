package com.zhiya.collection.concurrent;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Level 7.6.1：CopyOnWriteArrayList 演示
 *
 * 核心结论（来自文档）：
 * - 写时复制：每次 add/set/remove 都复制整个数组
 * - 遍历拿到的是快照，不会抛 ConcurrentModificationException
 * - 适合读多写少（读:写 ≥ 100:1）
 */
public class CopyOnWriteDemo {

    public static void main(String[] args) {
        System.out.println("=== Level 7.6.1：CopyOnWriteArrayList ===\n");

        snapshotDemo();
        failFastVsFailSafe();
        writeCostDemo();
    }

    // ──────────────────────────────────────
    // 1. 快照机制演示
    // ──────────────────────────────────────
    static void snapshotDemo() {
        System.out.println("--- 1. 快照机制 ---");

        CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
        list.add("A");
        list.add("B");
        list.add("C");

        // 获取迭代器（拿到当前数组快照）
        Iterator<String> it = list.iterator();

        // 在迭代期间添加元素
        list.add("D");
        list.add("E");

        System.out.println("当前 list: " + list);          // [A, B, C, D, E]
        System.out.print("迭代器快照: ");
        while (it.hasNext()) {
            System.out.print(it.next() + " ");              // [A, B, C] ← 快照，看不到 D, E
        }
        System.out.println("\n");
    }

    // ──────────────────────────────────────
    // 2. Fail-Fast vs Fail-Safe 对比
    // ──────────────────────────────────────
    static void failFastVsFailSafe() {
        System.out.println("--- 2. Fail-Fast vs Fail-Safe ---");

        // Fail-Fast：ArrayList 遍历时删除 → ConcurrentModificationException
        System.out.println("Fail-Fast（ArrayList）:");
        try {
            java.util.ArrayList<String> arrayList = new java.util.ArrayList<>();
            arrayList.add("A");
            arrayList.add("B");
            arrayList.add("C");
            for (String s : arrayList) {
                if ("B".equals(s)) {
                    arrayList.remove(s); // ❌ CME
                }
            }
        } catch (java.util.ConcurrentModificationException e) {
            System.out.println("  ❌ ConcurrentModificationException（Fail-Fast）");
        }

        // Fail-Safe：CopyOnWriteArrayList 遍历时删除 → 不抛异常
        System.out.println("Fail-Safe（CopyOnWriteArrayList）:");
        CopyOnWriteArrayList<String> cowList = new CopyOnWriteArrayList<>();
        cowList.add("A");
        cowList.add("B");
        cowList.add("C");

        for (String s : cowList) {
            if ("B".equals(s)) {
                cowList.remove(s); // ✅ 不会抛异常
            }
        }
        System.out.println("  ✅ 不抛异常，最终结果: " + cowList);
        System.out.println();
    }

    // ──────────────────────────────────────
    // 3. 写操作代价演示
    // ──────────────────────────────────────
    static void writeCostDemo() {
        System.out.println("--- 3. 写操作代价（O(n) 复制整个数组）---");

        int size = 10_000;
        CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<>();

        // 写操作：每次都要复制整个数组
        long start = System.nanoTime();
        for (int i = 0; i < size; i++) {
            list.add(i);
        }
        long writeTime = System.nanoTime() - start;

        // 读操作：直接读数组，O(1)
        start = System.nanoTime();
        for (int i = 0; i < size; i++) {
            list.get(i);
        }
        long readTime = System.nanoTime() - start;

        System.out.printf("写 %d 次: %d ms%n", size, writeTime / 1_000_000);
        System.out.printf("读 %d 次: %d ms%n", size, readTime / 1_000_000);
        System.out.println("结论：写操作代价高，只适合读多写少场景");
        System.out.println();
    }
}
