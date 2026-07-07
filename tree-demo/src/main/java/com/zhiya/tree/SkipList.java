package com.zhiya.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 无锁并发跳表（SkipList）· 原理演示版
 *
 * 核心思想：局部化修改 + CAS 无锁并发
 *
 * 为什么高并发场景（Redis ZSET、ConcurrentSkipListMap）选择跳表而不是红黑树？
 * 答：跳表插入仅修改局部相邻节点的指针，可直接用 CAS 实现无锁扩展；
 *     红黑树需要全局旋转/变色，无法局部化 CAS。
 *
 * ⚠️ 原理演示代码，非生产级实现。
 */
public class SkipList<E extends Comparable<E>> {

    public static final int MAX_LEVEL = 16;

    static class Node<E> {
        final E val;
        final AtomicReference<Node<E>>[] next;

        @SuppressWarnings("unchecked")
        Node(E val, int level) {
            this.val = val;
            this.next = new AtomicReference[level + 1];
            for (int i = 0; i <= level; i++) {
                this.next[i] = new AtomicReference<>(null);
            }
        }
    }

    private final Node<E> head = new Node<>(null, MAX_LEVEL);

    // =====================================
    // 随机层高：抛硬币，每层概率 1/2
    // =====================================
    private int randomLevel() {
        int level = 0;
        while (level < MAX_LEVEL && ThreadLocalRandom.current().nextBoolean()) {
            level++;
        }
        return level;
    }

    // =====================================
    // 插入
    // =====================================
    public void insert(E val) {
        int topLevel = randomLevel();
        Node<E> newNode = new Node<>(val, topLevel);
        Node<E>[] predecessors = findPredecessors(val);

        for (int level = 0; level <= topLevel; level++) {
            Node<E> pred = (level < predecessors.length) ? predecessors[level] : head;
            while (true) {
                Node<E> successor = pred.next[level].get();
                newNode.next[level].set(successor);
                if (pred.next[level].compareAndSet(successor, newNode)) {
                    break; // CAS 成功，本层插入完成
                }
                // CAS 失败 → 其他线程并发修改了，重试
            }
        }
    }

    // =====================================
    // 查找所有层的前驱节点
    // =====================================
    @SuppressWarnings("unchecked")
    private Node<E>[] findPredecessors(E val) {
        Node<E>[] result = new Node[MAX_LEVEL + 1];
        Node<E> cur = head;
        for (int level = MAX_LEVEL; level >= 0; level--) {
            while (cur.next[level].get() != null
                    && cur.next[level].get().val.compareTo(val) < 0) {
                cur = cur.next[level].get();
            }
            result[level] = cur;
        }
        return result;
    }

    // =====================================
    // 查找
    // =====================================
    public boolean search(E val) {
        Node<E> cur = head;
        for (int level = MAX_LEVEL; level >= 0; level--) {
            while (cur.next[level].get() != null
                    && cur.next[level].get().val.compareTo(val) < 0) {
                cur = cur.next[level].get();
            }
        }
        cur = cur.next[0].get();
        return cur != null && cur.val.compareTo(val) == 0;
    }

    // =====================================
    // Level 0 遍历（底层 = 全量有序数据）
    // =====================================
    public List<E> dumpLevel0() {
        List<E> result = new ArrayList<>();
        Node<E> cur = head.next[0].get();
        while (cur != null) {
            result.add(cur.val);
            cur = cur.next[0].get();
        }
        return result;
    }

    // =====================================
    // 可视化打印
    // =====================================
    public void printSkipList() {
        System.out.println("🌳 跳表结构（Level 0 ~ " + MAX_LEVEL + "）:");
        for (int level = MAX_LEVEL; level >= 0; level--) {
            System.out.print("  Lv" + level + ": ");
            Node<E> cur = head.next[level].get();
            if (cur == null) {
                System.out.println("(空)");
                continue;
            }
            while (cur != null) {
                System.out.print(cur.val + " ");
                cur = cur.next[level].get();
            }
            System.out.println();
        }
        System.out.println("  >> Level 0 = 完整有序数据，上层 = 捷径索引");
    }

    private static String repeat(String s, int count) {
        if (count <= 0) return "";
        char[] chars = new char[s.length() * count];
        for (int k = 0; k < count; k++)
            for (int j = 0; j < s.length(); j++)
                chars[k * s.length() + j] = s.charAt(j);
        return new String(chars);
    }


    // =====================================
    // main
    // =====================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println(repeat("=", 60));
        System.out.println("🌳 跳表（无锁 CAS）· 代码验证");
        System.out.println(repeat("=", 60));

        // 1. 单线程基本验证
        System.out.println("\n📥 单线程插入: 10, 50, 80, 30, 65");
        SkipList<Integer> skip = new SkipList<>();
        int[] data = {10, 50, 80, 30, 65};
        for (int v : data) skip.insert(v);

        java.util.List<Integer> level0 = skip.dumpLevel0();
        System.out.println("  Level 0: " + level0);

        boolean sorted = true;
        for (int i = 1; i < level0.size(); i++) {
            if (level0.get(i) < level0.get(i - 1)) sorted = false;
        }
        System.out.println("  严格有序: " + (sorted ? "✅" : "❌"));

        // 打印跳表
        skip.printSkipList();

        // 查找验证
        System.out.println("\n🔍 查找验证:");
        System.out.println("  查找 30: " + (skip.search(30) ? "✅" : "❌"));
        System.out.println("  查找 100: " + (skip.search(100) ? "❌ 误判" : "✅ 无"));

        // 2. 多线程并发压测
        System.out.println("\n🚀 并发压测: 8 线程各插入 100 个元素...");
        SkipList<Integer> concurrentSkip = new SkipList<>();
        int threadCount = 8, perThread = 100;
        Thread[] threads = new Thread[threadCount];

        for (int t = 0; t < threadCount; t++) {
            final int base = t * 1000;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < perThread; i++) concurrentSkip.insert(base + i);
            });
        }
        for (Thread th : threads) th.start();
        for (Thread th : threads) th.join();

        List<Integer> concurrentResult = concurrentSkip.dumpLevel0();
        boolean concurrentSorted = true;
        for (int i = 1; i < concurrentResult.size(); i++) {
            if (concurrentResult.get(i) < concurrentResult.get(i - 1)) concurrentSorted = false;
        }

        System.out.println("  插入总数: " + concurrentResult.size()
                + "（预期 " + (threadCount * perThread) + "）");
        System.out.println("  严格有序: " + (concurrentSorted ? "✅" : "❌"));

        boolean allPass = sorted && concurrentSorted
                && (concurrentResult.size() == threadCount * perThread);
        System.out.println("\n📊 结论: " + (allPass ? "✅ 全部通过" : "❌ 有异常"));
    }
}
