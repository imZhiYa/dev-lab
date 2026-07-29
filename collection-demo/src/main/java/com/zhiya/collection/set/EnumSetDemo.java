package com.zhiya.collection.set;

import java.util.EnumSet;

/**
 * Level 7.6.3：EnumSet 演示
 *
 * 核心结论（来自文档）：
 * - 底层：long 位向量，每个枚举值占 1 bit
 * - add/contains/remove 都是 O(1) 位运算
 * - 枚举值超过 64 个时自动切换到 long[]（JumboEnumSet）
 */
public class EnumSetDemo {

    // 定义一个枚举
    enum Permission {
        READ, WRITE, EXECUTE, DELETE, CREATE, UPDATE
    }

    public static void main(String[] args) {
        System.out.println("=== Level 7.6.3：EnumSet（位运算 Set）===\n");

        basicDemo();
        setOperations();
        bitVectorExplain();
    }

    // ──────────────────────────────────────
    // 1. 基本用法
    // ──────────────────────────────────────
    static void basicDemo() {
        System.out.println("--- 1. 基本用法 ---");

        // 创建方式
        EnumSet<Permission> readWrite = EnumSet.of(Permission.READ, Permission.WRITE);
        System.out.println("of(READ, WRITE): " + readWrite);

        EnumSet<Permission> all = EnumSet.allOf(Permission.class);
        System.out.println("allOf(): " + all);

        EnumSet<Permission> none = EnumSet.noneOf(Permission.class);
        System.out.println("noneOf(): " + none);

        EnumSet<Permission> range = EnumSet.range(Permission.READ, Permission.DELETE);
        System.out.println("range(READ, DELETE): " + range);

        EnumSet<Permission> complement = EnumSet.complementOf(readWrite);
        System.out.println("complementOf(READ, WRITE): " + complement);
        System.out.println();
    }

    // ──────────────────────────────────────
    // 2. 集合运算（位运算，极快）
    // ──────────────────────────────────────
    static void setOperations() {
        System.out.println("--- 2. 集合运算（位运算）---");

        EnumSet<Permission> userPerms = EnumSet.of(Permission.READ, Permission.WRITE);
        EnumSet<Permission> adminPerms = EnumSet.allOf(Permission.class);

        // 并集
        EnumSet<Permission> union = EnumSet.copyOf(userPerms);
        union.addAll(adminPerms);
        System.out.println("并集: " + union);

        // 交集
        EnumSet<Permission> intersection = EnumSet.copyOf(userPerms);
        intersection.retainAll(EnumSet.of(Permission.READ, Permission.DELETE));
        System.out.println("user ∩ {READ, DELETE}: " + intersection);

        // 差集
        EnumSet<Permission> diff = EnumSet.copyOf(adminPerms);
        diff.removeAll(userPerms);
        System.out.println("admin - user: " + diff);

        // 检查权限
        System.out.println("userPerms.contains(READ): " + userPerms.contains(Permission.READ));
        System.out.println("userPerms.contains(DELETE): " + userPerms.contains(Permission.DELETE));
        System.out.println();
    }

    // ──────────────────────────────────────
    // 3. 位向量原理
    // ──────────────────────────────────────
    static void bitVectorExplain() {
        System.out.println("--- 3. 位向量原理 ---");
        System.out.println("EnumSet<Permission> 存储结构（6 个枚举值）：");
        System.out.println();
        System.out.println("  ordinal:   5     4     3     2     1     0");
        System.out.println("             UPDATE CREATE DELETE EXECUTE WRITE READ");
        System.out.println();
        System.out.println("  READ + WRITE 的位向量：");
        System.out.println("  bits = 0b000011 = 3");
        System.out.println("  contains(READ)  → (3 & (1 << 0)) != 0 → true  ← O(1) 位运算");
        System.out.println("  contains(WRITE) → (3 & (1 << 1)) != 0 → true  ← O(1) 位运算");
        System.out.println("  contains(DELETE)→ (3 & (1 << 3)) != 0 → false ← O(1) 位运算");
        System.out.println();
        System.out.println("  add(DELETE):");
        System.out.println("  bits |= (1 << 3) → 0b010011 = 19");
        System.out.println();
        System.out.println("  对比 HashSet：");
        System.out.println("  - HashSet：hashCode() → 桶索引 → 可能冲突 → 链表/红黑树");
        System.out.println("  - EnumSet：ordinal → 直接位运算，零冲突，零开销");
    }
}
