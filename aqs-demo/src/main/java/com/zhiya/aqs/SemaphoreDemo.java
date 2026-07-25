package com.zhiya.aqs;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Semaphore 演示。
 * <p>
 * 对应文档《生产决策卡 2：连接池与第三方 API——为什么用 Semaphore》：
 * 共享 state 正好表达"剩余可用容量"；不用 unparkAll；超时/中断是 API 合同的一部分；
 * release() 必须与 acquire() 一一配对，否则会把 permits 撑到超过初始容量。
 * <p>
 * 覆盖点：
 * 1) 资源限流的标准写法：tryAcquire(timeout) 快速失败，而不是无限排队。
 * 2) 一次获取/释放多个许可（acquire(n)/release(n)）。
 * 3) 公平 vs 非公平的获取顺序差异。
 * 4) 反面教材：错误地多 release()，会让许可数超过初始容量，破坏下游并发边界。
 * <p>
 * 运行： java SemaphoreDemo
 */
public class SemaphoreDemo {

    // ---- 1. 典型限流用法：模拟"数据库只有 3 条连接，请求量远大于容量" ----
    static void demoResourceThrottling() throws InterruptedException {
        System.out.println("=== [1] 资源限流：Semaphore(3) 模拟只有 3 条数据库连接 ===");

        final Semaphore dbPermits = new Semaphore(3);
        int requestCount = 8;
        final CountDownLatch done = new CountDownLatch(requestCount);
        final AtomicInteger success = new AtomicInteger(0);
        final AtomicInteger rejected = new AtomicInteger(0);

        for (int i = 0; i < requestCount; i++) {
            final int id = i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 对应文档："acquire 设置业务 deadline；失败走正确的限流/排队响应"
                        boolean ok = dbPermits.tryAcquire(150, TimeUnit.MILLISECONDS);
                        if (!ok) {
                            rejected.incrementAndGet();
                            System.out.println("  [req-" + id + "] 连接池已饱和，快速拒绝（不是排队等死）");
                            return;
                        }
                        try {
                            success.incrementAndGet();
                            System.out.println("  [req-" + id + "] 拿到连接，执行查询... 剩余可用="
                                    + dbPermits.availablePermits());
                            Thread.sleep(100); // 模拟查询耗时
                        } finally {
                            dbPermits.release();
                        }
                    } catch (InterruptedException ignored) {
                    } finally {
                        done.countDown();
                    }
                }
            }, "req-" + id).start();
        }
        done.await();
        System.out.println("  结果：成功=" + success.get() + "，快速拒绝=" + rejected.get()
                + "（资源饱和时应快速拒绝，而不是所有请求一起排队变慢）\n");
    }

    // ---- 2. 一次获取/释放多个许可 ----
    static void demoAcquireMultiplePermits() throws InterruptedException {
        System.out.println("=== [2] 一次获取/释放多个许可：acquire(n) / release(n) ===");

        final Semaphore batchPermits = new Semaphore(5);
        System.out.println("  初始可用 permits = " + batchPermits.availablePermits());

        batchPermits.acquire(3); // 一次性拿走 3 个名额（比如一个批处理任务需要占 3 条并发通道）
        System.out.println("  一次性 acquire(3) 后，可用 permits = " + batchPermits.availablePermits());

        Thread other = new Thread(() -> {
            try {
                System.out.println("  [other] 尝试 acquire(3)，此时只剩 2 个，应该阻塞...");
                boolean ok = batchPermits.tryAcquire(3, 300, TimeUnit.MILLISECONDS);
                System.out.println("  [other] tryAcquire(3, 300ms) 结果 = " + ok + "（预期 false，容量不够）");
            } catch (InterruptedException ignored) {
            }
        });
        other.start();
        other.join();

        batchPermits.release(3); // 归还批量许可
        System.out.println("  release(3) 后，可用 permits 恢复 = " + batchPermits.availablePermits() + "\n");
    }

    // ---- 3. 公平 vs 非公平 ----
    static void demoFairVsUnfairOrder(boolean fair) throws InterruptedException {
        System.out.println("=== [3] 公平=" + fair + " 时多个等待者获取许可的顺序 ===");
        final Semaphore semaphore = new Semaphore(1, fair);
        semaphore.acquire(); // 占住唯一许可，逼迫后续线程排队

        int n = 5;
        final ConcurrentLinkedQueue<Integer> order = new ConcurrentLinkedQueue<Integer>();
        Thread[] waiters = new Thread[n];
        for (int i = 0; i < n; i++) {
            final int id = i;
            waiters[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(id * 15L); // 尽量让入队顺序贴近线程编号
                        semaphore.acquire();
                        order.add(Integer.valueOf(id));
                        semaphore.release();
                    } catch (InterruptedException ignored) {
                    }
                }
            });
            waiters[i].start();
        }
        Thread.sleep(150); // 等所有线程都进入排队
        semaphore.release(); // 释放，触发真正的抢占
        for (Thread w : waiters) {
            w.join();
        }

        System.out.println("  获取顺序: " + order);
        System.out.println("  " + (fair ? "公平模式：顺序应贴近入队顺序 [0,1,2,3,4]"
                : "非公平模式：顺序可能与入队顺序不同（允许插队）") + "\n");
    }

    // ---- 4. 反面教材：错误地多 release，撑爆许可上限 ----
    static void demoMisusedReleaseInflatesPermits() throws InterruptedException {
        System.out.println("=== [4] 反面教材：release() 不知道许可是否真的被 acquire 过 ===");
        Semaphore semaphore = new Semaphore(2);
        System.out.println("  初始 permits = " + semaphore.availablePermits());
        semaphore.release(); // 没有对应的 acquire，直接多 release 一次
        semaphore.release();
        System.out.println("  错误地多 release() 两次后，permits = " + semaphore.availablePermits()
                + "（已经超过初始容量 2，下游真实并发边界被破坏！）");
        System.out.println("  正确做法：每次成功 acquire 必须有且只有一次配对的 release（建议用 try/finally 或封装资源租约）\n");
    }

    public static void main(String[] args) throws InterruptedException {
        demoResourceThrottling();
        demoAcquireMultiplePermits();
        demoFairVsUnfairOrder(true);  // 公平模式
        demoFairVsUnfairOrder(false); // 非公平模式
        demoMisusedReleaseInflatesPermits();
    }
}
