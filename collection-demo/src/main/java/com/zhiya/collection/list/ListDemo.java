package com.zhiya.collection.list;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Level 2：ArrayList vs LinkedList 真实差异演示
 *
 * 核心结论（来自文档）：
 * - ArrayList：连续内存，CPU 缓存友好，随机访问 O(1)
 * - LinkedList：离散内存，每个节点额外 24 字节指针开销，随机访问 O(n)
 * - 99% 的场景 ArrayList 更快
 */
public class ListDemo {

    public static void main(String[] args) {
        System.out.println("=== Level 2：ArrayList vs LinkedList ===\n");

        basicOperations();
        randomAccessDemo();
        memoryLayoutDemo();
        subListViewTrap();
    }

    // ──────────────────────────────────────
    // 1. 基本操作对比
    // ──────────────────────────────────────
    static void basicOperations() {
        System.out.println("--- 1. 基本操作 ---");

        // ArrayList：动态数组，默认容量 10，1.5 倍扩容
        List<String> arrayList = new ArrayList<>();
        arrayList.add("A");  // 均摊 O(1)
        arrayList.add("B");
        arrayList.add("C");
        System.out.println("ArrayList: " + arrayList);

        // LinkedList：双向链表，每个节点 (prev + next + item) × 8 字节
        List<String> linkedList = new LinkedList<>();
        linkedList.add("A");  // O(1)，直接挂节点
        linkedList.add("B");
        linkedList.add("C");
        System.out.println("LinkedList: " + linkedList);

        System.out.println();
    }

    // ──────────────────────────────────────
    // 2. 随机访问性能差异
    // ──────────────────────────────────────
    static void randomAccessDemo() {
        System.out.println("--- 2. 随机访问 get(index) ---");
        System.out.println("ArrayList get(i)：直接算地址 base + i × 4，O(1)");
        System.out.println("LinkedList get(i)：从头遍历到第 i 个节点，O(n)\n");

        int size = 100_000;
        List<Integer> arrayList = new ArrayList<>();
        List<Integer> linkedList = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            arrayList.add(i);
            linkedList.add(i);
        }

        // ArrayList 随机访问
        long start = System.nanoTime();
        for (int i = 0; i < size; i++) {
            arrayList.get(i);
        }
        long arrayListTime = System.nanoTime() - start;

        // LinkedList 随机访问（极慢！只取前 1000 个）
        start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            linkedList.get(i);
        }
        long linkedListTimePartial = System.nanoTime() - start;

        System.out.printf("ArrayList get × %d 次: %d ms%n", size, arrayListTime / 1_000_000);
        System.out.printf("LinkedList get × 1000 次: %d ms（仅 1000 次就比 ArrayList %d 次慢）%n%n",
                linkedListTimePartial / 1_000_000, size);
    }

    // ──────────────────────────────────────
    // 3. 内存布局差异
    // ──────────────────────────────────────
    static void memoryLayoutDemo() {
        System.out.println("--- 3. 内存布局 ---");
        System.out.println("ArrayList 内存布局（连续）：");
        System.out.println("  +---+---+---+---+---+---+---+---+");
        System.out.println("  | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 |  ← 索引访问：O(1)");
        System.out.println("  +---+---+---+---+---+---+---+---+");
        System.out.println("    ^                               ^");
        System.out.println("    首地址                          末地址");
        System.out.println("    CPU 缓存命中率：高（预取机制）");
        System.out.println();
        System.out.println("LinkedList 内存布局（离散）：");
        System.out.println("  [Node0] → [Node1] → [Node2] → [Node3] → ...");
        System.out.println("    ^           ^           ^           ^");
        System.out.println("   0x1000     0x3456     0x789A     0xBCDE   ← 内存地址不连续");
        System.out.println("    prev       prev       prev       prev     ← 每个节点额外 24 字节");
        System.out.println("    next       next       next       next     ← (prev + next + item) × 8");
        System.out.println();
    }

    // ──────────────────────────────────────
    // 4. subList 陷阱
    // ──────────────────────────────────────
    static void subListViewTrap() {
        System.out.println("--- 4. subList 陷阱 ---");

        List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C", "D", "E"));
        System.out.println("原 list: " + list);

        // subList 返回的是视图，不是副本！
        List<String> sub = list.subList(1, 3);
        System.out.println("subList(1,3): " + sub);

        // 修改 sub 会影响原 list
        sub.clear();
        System.out.println("sub.clear() 后原 list: " + list);  // [A, D, E] ← 数据丢失！

        // 正确做法：创建副本
        List<String> list2 = new ArrayList<>(Arrays.asList("A", "B", "C", "D", "E"));
        List<String> subCopy = new ArrayList<>(list2.subList(1, 3));
        subCopy.clear();
        System.out.println("创建副本后 clear，原 list 不受影响: " + list2);
        System.out.println();
    }
}
