package com.zhiya.aqs;

import java.util.concurrent.Semaphore;

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
