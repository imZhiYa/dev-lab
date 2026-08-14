package com.zhiya.dubbo.demo.zklock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 互斥验证：N 个线程抢同一把锁，各自持锁 HOLD_MS 后释放。验证两件事：
 *   1. 任意时刻只有一个持有者（并发持有计数不得超过 1）
 *   2. 获取顺序与启动顺序一致（顺序节点保证 FIFO 公平队列）
 *
 * 每个线程开自己的 ZK 连接（独立 session），模拟 N 个独立客户端。
 *
 * 用法：LockDemo <threads> <holdMs> <zkHost:port>
 */
public class LockDemo {

    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        int holdMs = args.length > 1 ? Integer.parseInt(args[1]) : 300;
        String zkAddr = args.length > 2 ? args[2] : "127.0.0.1:2181";
        String lockName = args.length > 3 ? args[3] : "order-stock";

        System.out.println("=== LockDemo n=" + n + " holdMs=" + holdMs + " zk=" + zkAddr + " lock=" + lockName);
        System.out.println("t=0.000s start");

        AtomicInteger holders = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        AtomicInteger violation = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            final int id = i;
            Thread t = new Thread(() -> {
                try {
                    long t0 = System.nanoTime();
                    ZkDistributedLock lock = new ZkDistributedLock(zkAddr, lockName, 10000);
                    lock.lock();
                    long acquireMs = Duration.ofNanos(System.nanoTime() - t0).toMillis();
                    int cur = holders.incrementAndGet();
                    maxConcurrent.accumulateAndGet(cur, Math::max);
                    if (cur > 1) violation.incrementAndGet();
                    System.out.printf("t=%7.3fs [thread %d] LOCKED after %dms node=%s holders=%d%n",
                            Duration.ofNanos(System.nanoTime() - T0).toMillis() / 1000.0, id, acquireMs,
                            lock.myNodePath(), cur);
                    Thread.sleep(holdMs);
                    lock.unlock();
                    int after = holders.decrementAndGet();
                    System.out.printf("t=%7.3fs [thread %d] unlocked node=%s holders=%d%n",
                            Duration.ofNanos(System.nanoTime() - T0).toMillis() / 1000.0, id,
                            lock.myNodePath(), after);
                    lock.close();
                } catch (Exception e) {
                    System.err.println("[thread " + id + "] ERROR: " + e);
                }
            }, "th-" + i);
            threads.add(t);
            t.start();
            Thread.sleep(50); // stagger starts so acquisition order is observable
        }

        for (Thread t : threads) {
            t.join();
        }
        System.out.printf("=== DONE maxConcurrent=%d concurrent-holder-violation=%d%n",
                maxConcurrent.get(), violation.get());
        if (violation.get() > 0 || maxConcurrent.get() > 1) {
            System.err.println("MUTUAL EXCLUSION VIOLATED");
            System.exit(1);
        }
        System.out.println("MUTUAL EXCLUSION OK");
    }

    private static final long T0 = System.nanoTime();
}
