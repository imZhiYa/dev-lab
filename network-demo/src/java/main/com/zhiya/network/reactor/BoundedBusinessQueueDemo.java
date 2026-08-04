package com.zhiya.network.reactor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 高性能网络编程演示。
 * <p>
 * 对应层级：Level 4（Reactor 业务边界）。
 * 演示主题：有界业务队列与明确拒绝。
 * 验证目标：业务线程池必须限制任务驻留量，不能以默认无界队列隐藏下游过载。
 */
public final class BoundedBusinessQueueDemo {
    private BoundedBusinessQueueDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        System.out.println("\n===== Level 4 演示：有界业务队列与拒绝 =====");
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicInteger rejected = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), (task, pool) -> rejected.incrementAndGet());
        executor.execute(() -> await(releaseWorker)); // 正在执行
        executor.execute(() -> {
        });                  // 队列中
        executor.execute(() -> {
        });                  // 被拒绝
        System.out.printf("active=%d，queueSize=%d，rejected=%d%n", executor.getActiveCount(), executor.getQueue().size(), rejected.get());
        require(executor.getQueue().size() == 1 && rejected.get() == 1, "有界队列没有按预期拒绝任务");
        releaseWorker.countDown();
        executor.shutdown();
        require(executor.awaitTermination(1, TimeUnit.SECONDS), "业务线程池没有结束");
        System.out.println("结论：固定线程数不等于内存有界；容量、拒绝与超时必须显式定义。");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
