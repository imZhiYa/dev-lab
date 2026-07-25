package com.zhiya.aqs;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CountDownLatch 演示。
 * <p>
 * 核心语义（对应 AQS 文档里 state 的业务解释表）：
 * state = 剩余未完成数；countDown() 使 state-1；state 归零后，
 * 所有在 await() 上等待的线程被"共享传播"式放行；一次性，不能 reset。
 * <p>
 * 场景：主线程要等 N 个初始化任务全部完成后才能"开始营业"。
 * 同时演示：
 * 1) 多个 await() 线程会一起被放行（不是像 Semaphore 那样按需接力）。
 * 2) 一次性：countDown 到 0 后即使再调用 countDown() 也不会有任何效果，
 * getCount() 恒为 0，不能像 CyclicBarrier 那样重置复用。
 * 3) await(timeout) 的超时合同。
 * <p>
 */
public class CountDownLatchDemo {

    static void demoBasicFanOutFanIn() throws InterruptedException {
        System.out.println("=== [1] 基本用法：N 个初始化任务完成后，多个等待者一起被放行 ===");
        final int workerCount = 5;
        final CountDownLatch readyLatch = new CountDownLatch(workerCount);// state = 5

        // 3 个"观察者"线程都在等同一个 latch 归零
        int waiterCount = 3;
        final CountDownLatch waitersDone = new CountDownLatch(waiterCount);
        for (int i = 0; i < waiterCount; i++) {
            final int id = i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        System.out.println("  [waiter-" + id + "] 开始等待所有初始化任务完成...");
                        readyLatch.await();
                        System.out.println("  [waiter-" + id + "] 被放行！所有前置任务已完成，getCount()="
                                + readyLatch.getCount());
                    } catch (InterruptedException ignored) {
                    } finally {
                        waitersDone.countDown();
                    }
                }
            }, "waiter-" + id).start();
        }

        Thread.sleep(50); // 确保 waiter 们先进入 await

        for (int i = 0; i < workerCount; i++) {
            final int id = i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(30L * (id + 1)); // 模拟不同耗时的初始化任务
                    } catch (InterruptedException ignored) {
                    }
                    System.out.println("  [worker-" + id + "] 完成初始化，countDown()，剩余="
                            + (readyLatch.getCount() - 1));
                    readyLatch.countDown();
                }
            }, "worker-" + id).start();
        }

        waitersDone.await();
        System.out.println("  结果：3 个 waiter 几乎同时被放行（一次 countDown 归零触发批量放行），"
                + "而不是像 Semaphore 那样一个一个接力唤醒。\n");
    }

    static void demoOneShotNotReusable() throws InterruptedException {
        System.out.println("=== [2] 一次性：归零后不能 reset，重复 countDown 无效果 ===");
        CountDownLatch latch = new CountDownLatch(2);
        latch.countDown();
        latch.countDown();
        System.out.println("  归零后 getCount() = " + latch.getCount());
        latch.countDown(); // 再调用不会抛异常，也不会变负数
        System.out.println("  再次 countDown() 后 getCount() 仍 = " + latch.getCount()
                + "（不会变负，也无法恢复计数——这是它与 CyclicBarrier/Phaser 最大的区别）");
        latch.await(); // 已经是 0，立即返回，不阻塞
        System.out.println("  await() 在已归零的 latch 上立即返回，不阻塞。\n");
    }

    static void demoAwaitTimeout() throws InterruptedException {
        System.out.println("=== [3] await(timeout)：超时返回 false，不会无限等待 ===");
        CountDownLatch latch = new CountDownLatch(1); // 永远不会被 countDown
        long t0 = System.nanoTime();
        boolean released = latch.await(300, TimeUnit.MILLISECONDS);
        long costMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("  await(300ms) 返回 " + released + "，实际耗时约 " + costMs + "ms"
                + "（预期 false，因为没有人 countDown）\n");
    }

    public static void main(String[] args) throws InterruptedException {
        demoBasicFanOutFanIn();
        demoOneShotNotReusable();
        demoAwaitTimeout();
    }
}
