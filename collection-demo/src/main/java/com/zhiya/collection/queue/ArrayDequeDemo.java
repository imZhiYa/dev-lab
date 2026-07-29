package com.zhiya.collection.queue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Stack;

/**
 * Level 7.5：ArrayDeque 演示（替代 Stack 和 LinkedList）
 *
 * 核心结论（来自文档）：
 * - 官方推荐用 ArrayDeque 替代 Stack 和 LinkedList 做栈/队列
 * - 底层：循环数组，CPU 缓存友好
 * - Stack 是遗留类，synchronized 浪费
 */
public class ArrayDequeDemo {

    public static void main(String[] args) {
        System.out.println("=== Level 7.5：ArrayDeque（替代 Stack）===\n");

        stackDemo();
        queueDemo();
        dequeDemo();
        compareWithStack();
    }

    // ──────────────────────────────────────
    // 1. 当栈用（LIFO）
    // ──────────────────────────────────────
    static void stackDemo() {
        System.out.println("--- 1. 当栈用（LIFO）---");

        Deque<String> stack = new ArrayDeque<>();
        stack.push("A");  // 等价于 addFirst
        stack.push("B");
        stack.push("C");

        System.out.println("栈: " + stack);
        System.out.println("pop: " + stack.pop());    // C（后进先出）
        System.out.println("peek: " + stack.peek());  // B（查看栈顶）
        System.out.println("pop: " + stack.pop());    // B
        System.out.println("剩余: " + stack);
        System.out.println();
    }

    // ──────────────────────────────────────
    // 2. 当队列用（FIFO）
    // ──────────────────────────────────────
    static void queueDemo() {
        System.out.println("--- 2. 当队列用（FIFO）---");

        Deque<String> queue = new ArrayDeque<>();
        queue.offer("A");  // 等价于 addLast
        queue.offer("B");
        queue.offer("C");

        System.out.println("队列: " + queue);
        System.out.println("poll: " + queue.poll());   // A（先进先出）
        System.out.println("peek: " + queue.peek());   // B（查看队首）
        System.out.println("poll: " + queue.poll());   // B
        System.out.println("剩余: " + queue);
        System.out.println();
    }

    // ──────────────────────────────────────
    // 3. 双端队列（两端都能操作）
    // ──────────────────────────────────────
    static void dequeDemo() {
        System.out.println("--- 3. 双端队列（两端都能操作）---");

        Deque<String> deque = new ArrayDeque<>();
        deque.offerFirst("B");  // 头部入队
        deque.offerFirst("A");
        deque.offerLast("C");   // 尾部入队
        deque.offerLast("D");

        System.out.println("双端队列: " + deque);
        System.out.println("peekFirst: " + deque.peekFirst()); // A
        System.out.println("peekLast: " + deque.peekLast());   // D
        System.out.println("pollFirst: " + deque.pollFirst()); // A
        System.out.println("pollLast: " + deque.pollLast());   // D
        System.out.println("剩余: " + deque);
        System.out.println();
    }

    // ──────────────────────────────────────
    // 4. 与 Stack 对比
    // ──────────────────────────────────────
    static void compareWithStack() {
        System.out.println("--- 4. 与 Stack 对比 ---");

        // Stack：遗留类，extends Vector，synchronized 浪费
        Stack<String> stack = new Stack<>();
        stack.push("A");
        stack.push("B");
        System.out.println("Stack: " + stack);

        // ArrayDeque：推荐替代
        ArrayDeque<String> deque = new ArrayDeque<>();
        deque.push("A");
        deque.push("B");
        System.out.println("ArrayDeque: " + deque);

        System.out.println();
        System.out.println("为什么不用 Stack？");
        System.out.println("  - Stack extends Vector，继承了 synchronized 方法");
        System.out.println("  - 单线程下 synchronized 是纯开销");
        System.out.println("  - ArrayDeque 循环数组，CPU 缓存友好，性能更好");
        System.out.println();
        System.out.println("为什么不用 LinkedList 做队列？");
        System.out.println("  - LinkedList 内存不连续，CPU 缓存不友好");
        System.out.println("  - ArrayDeque 循环数组，缓存命中率高");
    }
}

