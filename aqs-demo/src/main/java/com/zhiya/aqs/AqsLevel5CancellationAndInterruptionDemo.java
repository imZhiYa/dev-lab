package com.zhiya.aqs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AQS 演示。
 *
 * 对应层级：Level 5。
 * 演示主题：超时取消、中断取消和不可中断获取。
 * 验证目标：取消节点不阻塞后继，并区分 lock 与 lockInterruptibly 的中断合同。
 */
public final class AqsLevel5CancellationAndInterruptionDemo {
    private AqsLevel5CancellationAndInterruptionDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        System.out.println("\n===== Level 5 演示：取消 / 超时 / 中断合同 =====");
        demoTimeoutThenSelfHeal();
        demoLockInterruptibly();
        demoLockIsUninterruptibleButRestoresInterruptStatus();
    }

    private static void demoTimeoutThenSelfHeal() throws Exception {
        System.out.println("\n--- 多个超时节点离场后，存活后继仍能前进 ---");
        ReentrantLock lock = new ReentrantLock(true);
        int workerCount = 5;
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch shortTimeoutFinished = new CountDownLatch(3);
        CountDownLatch done = new CountDownLatch(workerCount);
        AtomicInteger timedOut = new AtomicInteger();
        AtomicInteger acquired = new AtomicInteger();
        List<Thread> workers = new ArrayList<>(workerCount);
        List<AtomicReference<Throwable>> failures = new ArrayList<>(workerCount);

        lock.lock();
        try {
            for (int id = 0; id < workerCount; id++) {
                int workerId = id;
                long timeoutMs = workerId % 2 == 0 ? 500 : 3_000;
                AtomicReference<Throwable> failure = new AtomicReference<>();
                failures.add(failure);
                Thread worker = AqsDemoSupport.start("worker-" + workerId, () -> {
                    ready.countDown();
                    try {
                        AqsDemoSupport.await(start, "统一放行 timed acquire 线程");
                        boolean locked = lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
                        if (locked) {
                            try {
                                acquired.incrementAndGet();
                                AqsDemoSupport.log("worker-" + workerId, "获得锁");
                            } finally {
                                lock.unlock();
                            }
                        } else {
                            timedOut.incrementAndGet();
                            AqsDemoSupport.log("worker-" + workerId, "超时离场");
                        }
                    } finally {
                        if (workerId % 2 == 0) {
                            shortTimeoutFinished.countDown();
                        }
                        done.countDown();
                    }
                }, failure);
                workers.add(worker);
            }

            AqsDemoSupport.await(ready, "timed acquire 线程就绪");
            start.countDown();
            for (Thread worker : workers) {
                AqsDemoSupport.awaitTrue(() -> lock.hasQueuedThread(worker),
                        worker.getName() + " 进入同步队列");
            }
            AqsDemoSupport.await(shortTimeoutFinished, "短超时节点取消等待");
            AqsDemoSupport.require(timedOut.get() == 3, "短超时节点未全部离场");
            System.out.printf("短超时节点离场后 queueLength=%d%n", lock.getQueueLength());
        } finally {
            lock.unlock();
        }

        AqsDemoSupport.await(done, "存活等待者完成获取");
        for (int index = 0; index < workers.size(); index++) {
            AqsDemoSupport.join(workers.get(index), failures.get(index));
        }
        AqsDemoSupport.require(timedOut.get() == 3, "已取消节点不应重新获得锁");
        AqsDemoSupport.require(acquired.get() == 2, "存活后继被取消节点永久阻塞");
    }

    private static void demoLockInterruptibly() throws Exception {
        System.out.println("\n--- lockInterruptibly() 被中断后立刻放弃排队 ---");
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch ownerHolding = new CountDownLatch(1);
        CountDownLatch releaseOwner = new CountDownLatch(1);
        CountDownLatch bAttempting = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicReference<Throwable> aFailure = new AtomicReference<>();
        AtomicReference<Throwable> bFailure = new AtomicReference<>();

        Thread a = startOwner(lock, ownerHolding, releaseOwner, aFailure);
        AqsDemoSupport.await(ownerHolding, "A 持锁");
        Thread b = AqsDemoSupport.start("B", () -> {
            bAttempting.countDown();
            try {
                lock.lockInterruptibly();
                try {
                    throw new AssertionError("B 不应在中断后获得锁");
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException expected) {
                interrupted.set(true);
                AqsDemoSupport.log("B", "收到 InterruptedException，取消同步队列等待");
            }
        }, bFailure);

        try {
            AqsDemoSupport.await(bAttempting, "B 开始 interruptible acquire");
            AqsDemoSupport.awaitTrue(() -> lock.hasQueuedThread(b), "B 进入同步队列");
            b.interrupt();
        } finally {
            releaseOwner.countDown();
        }
        AqsDemoSupport.join(a, aFailure);
        AqsDemoSupport.join(b, bFailure);
        AqsDemoSupport.require(interrupted.get(), "lockInterruptibly 没有响应中断");
    }

    private static void demoLockIsUninterruptibleButRestoresInterruptStatus() throws Exception {
        System.out.println("\n--- lock() 不取消排队，成功后恢复 interrupt 标记 ---");
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch ownerHolding = new CountDownLatch(1);
        CountDownLatch releaseOwner = new CountDownLatch(1);
        CountDownLatch bAttempting = new CountDownLatch(1);
        AtomicBoolean interruptRestored = new AtomicBoolean();
        AtomicReference<Throwable> aFailure = new AtomicReference<>();
        AtomicReference<Throwable> bFailure = new AtomicReference<>();

        Thread a = startOwner(lock, ownerHolding, releaseOwner, aFailure);
        AqsDemoSupport.await(ownerHolding, "A 持锁");
        Thread b = AqsDemoSupport.start("B", () -> {
            bAttempting.countDown();
            lock.lock();
            try {
                interruptRestored.set(Thread.currentThread().isInterrupted());
                AqsDemoSupport.log("B", "获得锁后 interrupt 标记 = " + interruptRestored.get());
            } finally {
                lock.unlock();
            }
        }, bFailure);

        try {
            AqsDemoSupport.await(bAttempting, "B 开始普通 acquire");
            AqsDemoSupport.awaitTrue(() -> lock.hasQueuedThread(b), "B 进入同步队列");
            b.interrupt();
        } finally {
            releaseOwner.countDown();
        }
        AqsDemoSupport.join(a, aFailure);
        AqsDemoSupport.join(b, bFailure);
        AqsDemoSupport.require(interruptRestored.get(), "lock() 获取成功后没有恢复 interrupt 标记");
    }

    private static Thread startOwner(ReentrantLock lock, CountDownLatch ownerHolding,
                                     CountDownLatch releaseOwner, AtomicReference<Throwable> failure) {
        return AqsDemoSupport.start("A", () -> {
            lock.lock();
            try {
                ownerHolding.countDown();
                AqsDemoSupport.await(releaseOwner, "A 等待释放");
            } finally {
                lock.unlock();
            }
        }, failure);
    }
}
