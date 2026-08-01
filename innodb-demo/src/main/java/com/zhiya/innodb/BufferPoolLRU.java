package com.zhiya.innodb;

import java.util.HashMap;
import java.util.Map;

/**
 * 演示 InnoDB 核心机制：改良版 LRU (New/Old 分区)
 * 解决问题：防止全表扫描导致 Buffer Pool 热点数据被"污染"（踢出）。
 *
 * 核心逻辑：
 * 1. 内存页分为 New 区（默认 63%）和 Old 区（默认 37%）。
 * 2. 新加载的页放入 Old 区头部，而不是直接放入 New 区。
 * 3. 只有在 Old 区存活时间超过 innodb_old_blocks_time (默认 1000ms)，
 *    且再次被访问的页，才能"毕业"进入 New 区头部。
 *
 * Author: L (资深架构师)
 */
public class BufferPoolLRU<K, V> {

    // Page 节点结构
    static class Node<K, V> {
        K key; // 对应 Page No
        V value; // 对应 16KB Page 数据
        long firstAccessTime; // 首次加载到 Old 区的时间
        Node<K, V> prev, next;
        boolean inNewSublist; // 标记是否已经毕业进入 New 区

        Node(K key, V value) {
            this.key = key;
            this.value = value;
            this.firstAccessTime = System.currentTimeMillis();
            this.inNewSublist = false;
        }
    }

    private final int capacity;
    private final int oldCapacity; // Old区最大容量 (37%)
    private final int newCapacity; // New区最大容量 (63%)
    private final long oldBlocksTimeMs; // 毕业门槛时间

    private int oldSize = 0;
    private int newSize = 0;

    // 统一的 Hash 表用于 O(1) 定位 (对应 InnoDB 的 Buffer Pool Hash Table)
    private final Map<K, Node<K, V>> pageMap;

    // 两个虚拟头尾节点，维护整个链表 (New 和 Old 在物理上可能是一个链表，这里为了演示逻辑清晰，使用双链表概念)
    // New 区链表
    private final Node<K, V> newHead, newTail;
    // Old 区链表
    private final Node<K, V> oldHead, oldTail;

    public BufferPoolLRU(int capacity, double oldPct, long oldBlocksTimeMs) {
        this.capacity = capacity;
        this.oldCapacity = (int) (capacity * oldPct);
        this.newCapacity = capacity - this.oldCapacity;
        this.oldBlocksTimeMs = oldBlocksTimeMs;
        this.pageMap = new HashMap<>();

        newHead = new Node<>(null, null);
        newTail = new Node<>(null, null);
        newHead.next = newTail;
        newTail.prev = newHead;

        oldHead = new Node<>(null, null);
        oldTail = new Node<>(null, null);
        oldHead.next = oldTail;
        oldTail.prev = oldHead;
    }

    public V get(K key) {
        Node<K, V> node = pageMap.get(key);
        if (node == null) {
            return null; // Hash Table 未命中，需从磁盘读取 (模拟)
        }

        long currentTime = System.currentTimeMillis();

        // 查找：如果在 New 区，直接移到 New 区头部 (活跃)
        if (node.inNewSublist) {
            moveToHead(node, newHead);
        } else {
            // 如果在 Old 区，检查是否满足"毕业"条件
            if (currentTime - node.firstAccessTime >= oldBlocksTimeMs) {
                // 毕业！移出 Old 区，进入 New 区头部
                removeNode(node);
                oldSize--;

                node.inNewSublist = true;
                addToHead(node, newHead);
                newSize++;

                // 如果 New 区溢出，淘汰 New 区尾部，将其降级到 Old 区头部
                if (newSize > newCapacity) {
                    Node<K, V> demoted = removeTail(newTail);
                    newSize--;
                    demoted.inNewSublist = false;
                    demoted.firstAccessTime = System.currentTimeMillis(); // 重置时间
                    addToHead(demoted, oldHead);
                    oldSize++;
                }
            } else {
                // 不满毕业时间，只是普通访问（防止全表扫描污染）
                // 真实 InnoDB：移到 Old 区头部，维持组内 LRU 顺序（但绝不毕业）
                moveToHead(node, oldHead);
            }
        }

        // 淘汰清理：如果 Old 区溢出，物理淘汰 Old 区尾部的"一次性页"
        evictOldIfNecessary();
        return node.value;
    }

    public void put(K key, V value) {
        if (pageMap.containsKey(key)) {
            Node<K, V> node = pageMap.get(key);
            node.value = value;
            get(key); // 复用 get 逻辑更新位置
            return;
        }

        // 步骤 ② 加载：新页直接放入 Old 区头部！
        Node<K, V> newNode = new Node<>(key, value);
        pageMap.put(key, newNode);
        addToHead(newNode, oldHead);
        oldSize++;

        evictOldIfNecessary();
    }

    private void evictOldIfNecessary() {
        // InnoDB 淘汰规则：永远优先淘汰 Old 区尾部（一次性页）
        while (oldSize > oldCapacity && oldSize > 0) {
            Node<K, V> evicted = removeTail(oldTail);
            pageMap.remove(evicted.key);
            oldSize--;
            System.out.println("[LRU 淘汰] 淘汰 Old 区尾部页: " + evicted.key);
        }
        // 兜底：总容量已满 且 Old 区已空时，才轮到淘汰 New 区尾部（冷热点耗尽）
        while (oldSize + newSize > capacity) {
            if (oldSize > 0) {
                Node<K, V> evicted = removeTail(oldTail);
                pageMap.remove(evicted.key);
                oldSize--;
                System.out.println("[LRU 淘汰] 淘汰 Old 区尾部页: " + evicted.key);
            } else {
                Node<K, V> evicted = removeTail(newTail);
                pageMap.remove(evicted.key);
                newSize--;
                System.out.println("[LRU 淘汰] Old 区已空，被迫淘汰 New 区尾部页: " + evicted.key);
            }
        }
    }

    // --- 链表基础操作 ---
    private void addToHead(Node<K, V> node, Node<K, V> head) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToHead(Node<K, V> node, Node<K, V> head) {
        removeNode(node);
        addToHead(node, head);
    }

    private Node<K, V> removeTail(Node<K, V> tail) {
        Node<K, V> res = tail.prev;
        removeNode(res);
        return res;
    }

    // 简单测试
    public static void main(String[] args) throws InterruptedException {
        // 总容量 10 页，Old 区 37% (3页)，New 区 63% (7页)
        // 毕业窗口时间：1000ms
        BufferPoolLRU<String, String> pool = new BufferPoolLRU<>(10, 0.37, 1000);

        System.out.println("--- 1. 模拟常规热点数据加载 ---");
        pool.put("Page1", "UserData"); // 进 Old
        Thread.sleep(1100);
        pool.get("Page1"); // 超过 1s 再次访问 -> 毕业进 New!
        System.out.println("Page1 是否在 New 区: " + pool.pageMap.get("Page1").inNewSublist);
        System.out.println("  ✓ 结论：存活超 1s 后再次访问 → 晋升 New 区（真热点）\n");

        System.out.println("--- 1.5 反直觉对照：1s 内再次访问 → 不晋升 ---");
        // Page9 加载后立刻在 900ms 内再次访问（全表扫描的一次性访问特征）
        pool.put("Page9", "ScanData"); // 进 Old
        Thread.sleep(900);            // 存活仅 900ms < 1000ms
        pool.get("Page9");            // 再次访问
        boolean graduatedTooEarly = pool.pageMap.get("Page9").inNewSublist;
        System.out.println("Page9 存活 900ms 后再次访问，是否被晋升到 New 区: " + graduatedTooEarly);
        System.out.println("  " + (graduatedTooEarly ? "✗ 错误：1s 内不应晋升" : "✓ 正确：存活 <1s 的连续访问 = 全表扫描特征，不晋升"));
        Thread.sleep(200);            // 补足到 >1s
        pool.get("Page9");            // 待满 1s 后再访问 → 才晋升
        System.out.println("Page9 存活超 1s 后再访问，是否晋升到 New 区: " + pool.pageMap.get("Page9").inNewSublist);
        System.out.println("  ✓ 结论：只有【待满 1s + 再次访问】两个条件同时满足才毕业\n");

        System.out.println("\n--- 2. 模拟全表扫描 (空洞制造者) ---");
        // 全表扫描瞬间加载大量数据，但只访问一次
        for (int i = 2; i <= 6; i++) {
            pool.put("Page" + i, "ScanData");
        }
        // 此时 Old 区容量(3)溢出，Page2, Page3 已经被淘汰，但不会影响 New 区的 Page1 !
        System.out.println("全表扫描后，热点 Page1 依然存活在 New 区: " + pool.pageMap.containsKey("Page1"));
        System.out.println("最早扫描的 Page2 是否被淘汰: " + !pool.pageMap.containsKey("Page2"));
        System.out.println("  ✓ 结论：全表扫描的一次性页在 Old 区自然淘汰，不污染 New 区热点");
    }
}
