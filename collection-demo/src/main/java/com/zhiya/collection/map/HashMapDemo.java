package com.zhiya.collection.map;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Level 3-4：HashMap 源码级拆解演示
 * <p>
 * 核心结论（来自文档）：
 * - 底层：数组 + 链表 + 红黑树（JDK 8+）
 * - 默认容量 16，负载因子 0.75，阈值 12
 * - 树化条件：链表长度 > 8 且容量 ≥ 64
 * - 扩容：2 倍（oldCap << 1），JDK 8 用尾插法（解决 JDK 7 死循环）
 * - 寻址：hash & (n-1)，等价于 hash % n，但更快
 */
public class HashMapDemo {

    public static void main(String[] args) {
        System.out.println("=== Level 3-4：HashMap 深度解析 ===\n");

        basicPutGet();
        hashCollisionDemo();
        initialCapacityDemo();
        keyRequirements();
    }

    // ──────────────────────────────────────
    // 1. 基本 put/get 流程
    // ──────────────────────────────────────
    static void basicPutGet() {
        System.out.println("--- 1. 基本 put/get 流程 ---");
        System.out.println("put() 完整时序：");
        System.out.println("  ① 计算 hash(key)");
        System.out.println("  ② 索引 = hash & (n-1)");
        System.out.println("  ③ 桶为空 → 直接放入");
        System.out.println("  ④ 桶非空 → 链表遍历 or 红黑树插入");
        System.out.println("  ⑤ 超过阈值 → 扩容（2倍）");
        System.out.println();

        // 默认容量 16，负载因子 0.75，阈值 = 16 × 0.75 = 12
        Map<String, Integer> map = new HashMap<>();
        System.out.println("空 map 创建（懒加载，table 还是 null）");

        map.put("apple", 1);
        System.out.println("put(\"apple\", 1) → 触发初始化，容量=16，阈值=12");

        for (int i = 0; i < 11; i++) {
            map.put("key" + i, i);
        }
        System.out.println("再 put 11 个 → 共 12 个，刚好达到阈值");

        map.put("trigger", 999);
        System.out.println("put(\"trigger\") → 第 13 个，触发扩容，容量=32，阈值=24");
        System.out.println("当前 size: " + map.size());
        System.out.println();
    }

    // ──────────────────────────────────────
    // 2. 哈希冲突演示
    // ──────────────────────────────────────
    static void hashCollisionDemo() {
        System.out.println("--- 2. 哈希冲突演示 ---");
        System.out.println("哈希冲突：不同 key → 相同桶索引");
        System.out.println("JDK 8+ 解决方案：");
        System.out.println("  - 链表长度 ≤ 8：链地址法");
        System.out.println("  - 链表长度 > 8 且容量 ≥ 64：红黑树");
        System.out.println();

        // 故意制造冲突：自定义 hashCode 返回固定值
        Map<BadHashKey, String> map = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            map.put(new BadHashKey("key" + i), "value" + i);
        }
        System.out.println("所有 key 的 hashCode 都返回 42 → 全部冲突到同一个桶");
        System.out.println("桶内结构：链表（因为容量 < 64，不会树化）");
        System.out.println("map.size() = " + map.size());
        System.out.println("map.get(new BadHashKey(\"key5\")) = " + map.get(new BadHashKey("key5")));
        System.out.println();
    }

    // 自定义 hashCode 固定返回 42，强制所有 key 冲突
    static class BadHashKey {
        private final String value;

        BadHashKey(String value) {
            this.value = value;
        }

        @Override
        public int hashCode() {
            return 42; // 固定哈希值，强制冲突
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BadHashKey that = (BadHashKey) o;
            return Objects.equals(value, that.value);
        }

        @Override
        public String toString() {
            return value;
        }
    }

    // ──────────────────────────────────────
    // 3. 初始容量预估
    // ──────────────────────────────────────
    static void initialCapacityDemo() {
        System.out.println("--- 3. 初始容量预估 ---");
        System.out.println("错误做法：new HashMap<>(1000)");
        System.out.println("  → 容量=1024（向上取 2 的幂次），阈值=768");
        System.out.println("  → 存 768 个就扩容");
        System.out.println();
        System.out.println("正确做法：new HashMap<>((int)(1000 / 0.75) + 1)");
        System.out.println("  → 初始容量 1334 → 向上取 2048，阈值=1536");
        System.out.println("  → 一次扩容都不用");
        System.out.println();

        // 错误：频繁扩容
        Map<String, String> bad = new HashMap<>(1000);
        System.out.println("new HashMap<>(1000) → 实际容量=1024, 阈值=768");

        // 正确：一次扩容都不用
        Map<String, String> good = new HashMap<>((int) (1000 / 0.75) + 1);
        System.out.println("new HashMap<>((int)(1000/0.75)+1) → 实际容量=2048, 阈值=1536");
        System.out.println();
    }

    // ──────────────────────────────────────
    // 4. Key 的要求：hashCode + equals
    // ──────────────────────────────────────
    static void keyRequirements() {
        System.out.println("--- 4. Key 的要求：hashCode + equals ---");

        // 错误：没有重写 hashCode/equals 的 User
        Map<UserBad, String> badMap = new HashMap<>();
        UserBad u1 = new UserBad("123");
        badMap.put(u1, "session1");

        UserBad u2 = new UserBad("123");
        System.out.println("没有重写 hashCode/equals：");
        System.out.println("  put(u1) 后 get(new UserBad(\"123\")) = " + badMap.get(u2)); // null！

        // 正确：重写了 hashCode/equals 的 User
        Map<UserGood, String> goodMap = new HashMap<>();
        UserGood g1 = new UserGood("123");
        goodMap.put(g1, "session1");

        UserGood g2 = new UserGood("123");
        System.out.println("重写 hashCode/equals 后：");
        System.out.println("  put(u1) 后 get(new UserGood(\"123\")) = " + goodMap.get(g2)); // session1
        System.out.println();
    }

    // 错误示例：没有重写 hashCode/equals
    static class UserBad {
        String id;

        UserBad(String id) {
            this.id = id;
        }
        // 没有 hashCode/equals，用 Object 默认的（内存地址）
    }

    // 正确示例：重写了 hashCode/equals
    static class UserGood {
        String id;

        UserGood(String id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserGood that = (UserGood) o;
            return Objects.equals(id, that.id);
        }
    }
}
