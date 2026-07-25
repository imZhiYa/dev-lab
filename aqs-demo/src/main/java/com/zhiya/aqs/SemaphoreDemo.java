package com.zhiya.aqs;

import java.util.concurrent.Semaphore;

/**
 * Semaphore 并发限流演示。
 *
 * 演示主题：许可证计数信号量，限制同一时刻访问临界资源的线程数。
 * 演示目标：
 * - Semaphore 初始化许可证数（PERMITS）
 * - 线程调用 acquire() 获取许可证，许可不足则阻塞等待
 * - 线程使用完资源后调用 release() 释放许可证，唤醒等待的线程
 * - 支持获取/释放多个许可证，常用于限制资源池大小
 * - 与 CountDownLatch 不同，Semaphore 支持多次获取和释放
 */
public class SemaphoreDemo {

    public static void main(String[] args) {
        final int PERMITS = 2;
        Semaphore semaphore = new Semaphore(PERMITS);
        for (int i = 1; i <= 6; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    System.out.println("Thread " + id + " trying to acquire a permit.");
                    semaphore.acquire();
                    System.out.println("Thread " + id + " acquired a permit. Available permits: " + semaphore.availablePermits());
                    // 模拟使用资源
                    Thread.sleep(500 + (long) (Math.random() * 800));
                    System.out.println("Thread " + id + " releasing permit.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    semaphore.release();
                }
            }, "sema-worker-" + i).start();
        }
    }
}
