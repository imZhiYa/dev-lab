package com.zhiya;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualThreadDemo {

    public static void main(String[] args) throws Exception {
        demo1();
        System.out.println("\n" + "=".repeat(60) + "\n");
        demo2();
    }

    // ============================================================
    // 实验 1：虚拟线程没有背压 —— 直接无限提交
    // ============================================================
    private static void demo1() throws Exception {
        System.out.println("【实验 1】虚拟线程没有背压：直接提交 20 个任务\n");

        ExecutorService vtPool = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger counter = new AtomicInteger();

        long start = System.nanoTime();
        for (int i = 1; i <= 20; i++) {
            int id = i;
            vtPool.submit(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                counter.incrementAndGet();
            });
        }
        vtPool.shutdown();
        vtPool.awaitTermination(3, TimeUnit.SECONDS);

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("   20 个任务提交耗时=%dms（几乎不阻塞）%n", elapsedMs);
        System.out.printf("   最终完成任务数=%d%n", counter.get());
        System.out.println("   ★ 虚拟线程没有背压，直接创建 20 个线程并发执行");
    }

    // ============================================================
    // 实验 2：虚拟线程隔离阻塞 I/O 的代价 —— 不需要隔离
    // ============================================================
    private static void demo2() throws Exception {
        System.out.println("【实验 2】虚拟线程混入阻塞 I/O：不会拖慢 CPU 任务\n");

        ExecutorService vtPool = Executors.newVirtualThreadPerTaskExecutor();

        int orders = 10;
        CompletableFuture<?>[] ioTasks = new CompletableFuture[orders];
        for (int i = 0; i < orders; i++) {
            int id = i;
            ioTasks[i] = CompletableFuture.supplyAsync(() -> blockingRpc(id), vtPool);
        }

        long start = System.nanoTime();
        CompletableFuture<?>[] cpuTasks = new CompletableFuture[4];
        for (int i = 0; i < 4; i++) {
            cpuTasks[i] = CompletableFuture.supplyAsync(() -> fibonacci(30), vtPool);
        }

        CompletableFuture.allOf(cpuTasks).join();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("   纯 CPU 任务完成耗时=%dms%n", elapsedMs);

        CompletableFuture.allOf(ioTasks).join();
        System.out.printf("   阻塞 I/O 任务完成数=%d%n", orders);

        vtPool.shutdown();
    }

    private static Integer blockingRpc(int orderId) {
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        return orderId;
    }

    private static long fibonacci(int n) {
        return n <= 1 ? n : fibonacci(n - 1) + fibonacci(n - 2);
    }
}
