package com.zhiya.collection.queue;

import java.util.concurrent.*;

/**
 * Level 7.5：BlockingQueue 生产者-消费者演示
 *
 * 核心结论（来自文档）：
 * - ArrayBlockingQueue：有界数组，满时 put 阻塞，空时 take 阻塞
 * - LinkedBlockingQueue：链表（可有界），两把锁，put/take 可并发
 * - SynchronousQueue：无存储，直接交接
 * - FixedThreadPool 用无界 LinkedBlockingQueue → 可能 OOM
 */
public class BlockingQueueDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Level 7.5：BlockingQueue（生产者-消费者）===\n");

        arrayBlockingQueueDemo();
        synchronousQueueDemo();
        fixedThreadPoolWarning();
    }

    // ──────────────────────────────────────
    // 1. ArrayBlockingQueue：有界阻塞队列
    // ──────────────────────────────────────
    static void arrayBlockingQueueDemo() throws Exception {
        System.out.println("--- 1. ArrayBlockingQueue（有界）---");

        // 容量为 3 的有界队列
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(3);

        // 生产者线程
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    String item = "item-" + i;
                    System.out.println("  生产: " + item + "（队列大小: " + queue.size() + "）");
                    queue.put(item);  // 队列满时阻塞
                    System.out.println("  生产完成: " + item);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 消费者线程（延迟消费）
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    Thread.sleep(500);  // 模拟消费慢
                    String item = queue.take();  // 队列空时阻塞
                    System.out.println("  消费: " + item + "（队列大小: " + queue.size() + "）");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
        System.out.println();
    }

    // ──────────────────────────────────────
    // 2. SynchronousQueue：直接交接
    // ──────────────────────────────────────
    static void synchronousQueueDemo() throws Exception {
        System.out.println("--- 2. SynchronousQueue（直接交接）---");

        // 无存储，put 必须等待 take，take 必须等待 put
        BlockingQueue<String> queue = new SynchronousQueue<>();

        Thread producer = new Thread(() -> {
            try {
                System.out.println("  生产者: 准备交接 item...");
                queue.put("item");  // 阻塞，直到有消费者 take
                System.out.println("  生产者: 交接完成！");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread consumer = new Thread(() -> {
            try {
                Thread.sleep(1000);  // 延迟 1 秒
                System.out.println("  消费者: 准备接收...");
                String item = queue.take();  // 阻塞，直到有生产者 put
                System.out.println("  消费者: 接收到 " + item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
        System.out.println();
    }

    // ──────────────────────────────────────
    // 3. FixedThreadPool 隐患
    // ──────────────────────────────────────
    static void fixedThreadPoolWarning() {
        System.out.println("--- 3. FixedThreadPool 隐患 ---");
        System.out.println("Executors.newFixedThreadPool(10) 内部用 LinkedBlockingQueue（无界）");
        System.out.println("如果任务提交速度持续超过处理速度 → 队列无限增长 → OOM");
        System.out.println();
        System.out.println("正确做法：显式构造 ThreadPoolExecutor + 有界队列 + 拒绝策略");
        System.out.println();

        // 错误：无界队列
        // ExecutorService bad = Executors.newFixedThreadPool(10);

        // 正确：有界队列 + 拒绝策略
        ExecutorService good = new ThreadPoolExecutor(
                2,                                    // 核心线程数
                4,                                    // 最大线程数
                60L, TimeUnit.SECONDS,                // 空闲线程存活时间
                new ArrayBlockingQueue<>(100),         // 有界队列，容量 100
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者线程执行
        );

        System.out.println("ThreadPoolExecutor 构造完成：");
        System.out.println("  核心线程: 2, 最大线程: 4, 队列容量: 100");
        System.out.println("  拒绝策略: CallerRunsPolicy（队列满时由提交任务的线程执行）");

        good.shutdown();
        System.out.println();
    }
}

