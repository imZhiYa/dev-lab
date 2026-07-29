package com.zhiya.collection.map;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Level 7.6.5：IdentityHashMap 演示
 *
 * 核心结论（来自文档）：
 * - 用 ==（引用相等）而不是 equals() 比较 key
 * - 底层：开放寻址法（数组 + 线性探测），不是链地址法
 * - 典型场景：深拷贝时检测循环引用
 * - 极度危险：违反 Map 接口的通用契约，误用会导致诡异 bug
 */
public class IdentityHashMapDemo {

    public static void main(String[] args) {
        System.out.println("=== Level 7.6.5：IdentityHashMap（== 比较）===\n");

        identityVsEquals();
        deepCopyCircularRef();
    }

    // ──────────────────────────────────────
    // 1. == vs equals 的区别
    // ──────────────────────────────────────
    static void identityVsEquals() {
        System.out.println("--- 1. == vs equals 的区别 ---");

        String a = new String("hello");
        String b = new String("hello");
        System.out.println("a == b:      " + (a == b));       // false（不同对象）
        System.out.println("a.equals(b): " + a.equals(b));    // true（内容相同）

        // HashMap 用 equals 比较
        Map<String, String> hashMap = new HashMap<>();
        hashMap.put(a, "value");
        System.out.println("\nHashMap:");
        System.out.println("  get(a) = " + hashMap.get(a));   // value
        System.out.println("  get(b) = " + hashMap.get(b));   // value ← equals 匹配

        // IdentityHashMap 用 == 比较
        Map<String, String> identityMap = new IdentityHashMap<>();
        identityMap.put(a, "value");
        System.out.println("\nIdentityHashMap:");
        System.out.println("  get(a) = " + identityMap.get(a));   // value
        System.out.println("  get(b) = " + identityMap.get(b));   // null ← == 不匹配！

        // 即使是"内容相同"的字符串，只要不是同一个对象，就查不到
        System.out.println("\n结论：IdentityHashMap 严格区分对象身份，即使 equals 返回 true");
        System.out.println();
    }

    // ──────────────────────────────────────
    // 2. 深拷贝防循环引用（典型场景）
    // ──────────────────────────────────────
    static void deepCopyCircularRef() {
        System.out.println("--- 2. 深拷贝防循环引用（典型场景）---");
        System.out.println("问题：深拷贝对象图时，如果 A→B→A 形成循环，会无限递归");
        System.out.println("解决：用 IdentityHashMap 记录「已经拷贝过的对象」（用 == 判断）");
        System.out.println();
        System.out.println("为什么用 IdentityHashMap 而不是 HashMap？");
        System.out.println("  - 两个不同的对象可能 equals 返回 true（如两个 new String(\"a\")）");
        System.out.println("  - 但它们是不同的对象，需要分别拷贝");
        System.out.println("  - IdentityHashMap 用 == 精确区分每个对象实例");
        System.out.println();

        // 简化演示
        Object obj1 = new Object();
        Object obj2 = new Object();

        Map<Object, Object> visited = new IdentityHashMap<>();
        visited.put(obj1, "copy_of_obj1");

        // obj1 已经拷贝过
        System.out.println("visited.containsKey(obj1): " + visited.containsKey(obj1)); // true
        // obj2 没有拷贝过
        System.out.println("visited.containsKey(obj2): " + visited.containsKey(obj2)); // false
        System.out.println();
    }
}

