package com.zhiya.aqs;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AQS 演示。
 * <p>
 * 对应层级：Level 1。
 * 演示主题：CAS 忙等与 AQS park 等待的差异，以及竞争触发的队列可观察性。
 * 验证目标：长临界区下忙等会产生大量失败 CAS；无竞争时没有等待者，竞争失败后才出现同步队列候选者。
 */
public final class AqsLevel1StateAndContentionDemo {
    private static final int WORKER_COUNT = 8;
    private static final long CRITICAL_SECTION_MILLIS = 50;

    private AqsLevel1StateAndContentionDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        System.out.printf("JDK: %s / %s%n", System.getProperty("java.version"), System.getProperty("java.vendor"));
        demonstrateReentrantState();
        compareBusySpinAndPark();
        demonstrateQueueCreationAfterContention();
    }

    private static void demonstrateReentrantState() {
        System.out.println("\n===== Level 1 演示：state 作为可重入资源账本 =====");
        ReentrantLock lock = new ReentrantLock();

        // 同一线程第二次获取不会排队，而是把内部重入计数从 1 增加到 2。
        lock.lock();
        lock.lock();
        try {
            AqsDemoSupport.require(lock.getHoldCount() == 2, "两次重入后 hold count 必须为 2");
            System.out.println("同一线程连续 lock 两次，holdCount=" + lock.getHoldCount());
        } finally {
            // 第一次 unlock 只减少重入计数，并不把锁交给等待者。
            lock.unlock();
            AqsDemoSupport.require(lock.getHoldCount() == 1, "第一次 unlock 后 hold count 必须为 1");
            lock.unlock();
        }
        AqsDemoSupport.require(!lock.isLocked(), "最后一次 unlock 后锁必须空闲");
    }

    private static void compareBusySpinAndPark() throws Exception {
        System.out.println("\n===== Level 1 演示：CAS 忙等与 ReentrantLock 等待 =====");
        AtomicBoolean spinFlag = new AtomicBoolean();
        AtomicLong failedCasCount = new AtomicLong();
        long spinElapsedMs = runSpinScenario(spinFlag, failedCasCount);
        long lockElapsedMs = runLockScenario();

        System.out.printf("CAS 忙等：总耗时=%dms，失败 CAS 次数=%d%n", spinElapsedMs, failedCasCount.get());
        System.out.printf("ReentrantLock：总耗时=%dms，失败者由 AQS park 等待%n", lockElapsedMs);
        AqsDemoSupport.require(failedCasCount.get() > 0, "未观察到竞争下的失败 CAS");
        System.out.println("结论：两者都受串行临界区时长约束；忙等额外消耗 CPU 反复确认同一份 state。");
    }

    /**
     * 朴素自旋锁没有等待队列：所有失败者持续写同一个 AtomicBoolean 所在 cache line。
     * failedCasCount 不是性能基准指标，只用于把“无效确认资源仍被占用”的行为量化展示出来。
     */
    private static long runSpinScenario(AtomicBoolean spinFlag, AtomicLong failedCasCount) throws Exception {
        return AqsDemoSupport.runConcurrently(WORKER_COUNT, "spin", () -> {
            while (!spinFlag.compareAndSet(false, true)) {
                failedCasCount.incrementAndGet();
                // JDK 8 没有 Thread.onSpinWait()；yield 只是调度提示，线程仍处于忙等状态。
                Thread.yield();
            }
            try {
                Thread.sleep(CRITICAL_SECTION_MILLIS);
            } finally {
                spinFlag.set(false);
            }
        });
    }

    /**
     * ReentrantLock 的失败者由 AQS 排队并 park；该方法仍会串行执行临界区，
     * 但等待者不需要持续执行失败 CAS。
     */
    private static long runLockScenario() throws Exception {
        ReentrantLock lock = new ReentrantLock();
        return AqsDemoSupport.runConcurrently(WORKER_COUNT, "lock", () -> {
            lock.lock();
            try {
                Thread.sleep(CRITICAL_SECTION_MILLIS);
            } finally {
                lock.unlock();
            }
        });
    }

    private static void demonstrateQueueCreationAfterContention() throws Exception {
        System.out.println("\n===== Level 1 演示：竞争失败后才出现等待队列 =====");
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch ownerHolding = new CountDownLatch(1);
        CountDownLatch releaseOwner = new CountDownLatch(1);
        AtomicReference<Throwable> ownerFailure = new AtomicReference<>();
        AtomicReference<Throwable> contenderFailure = new AtomicReference<>();

        System.out.printf("无竞争：hasQueuedThreads=%s，queueLength=%d%n",
                lock.hasQueuedThreads(), lock.getQueueLength());
        AqsDemoSupport.require(!lock.hasQueuedThreads(), "无竞争时不应有等待线程");

        Thread owner = AqsDemoSupport.start("owner", () -> {
            lock.lock();
            try {
                ownerHolding.countDown();
                AqsDemoSupport.await(releaseOwner, "等待释放 owner");
            } finally {
                lock.unlock();
            }
        }, ownerFailure);
        AqsDemoSupport.await(ownerHolding, "owner 持锁");
        Thread contender = AqsDemoSupport.start("contender", () -> {
            lock.lock();
            try {
                AqsDemoSupport.log("contender", "重新 tryAcquire 成功");
            } finally {
                lock.unlock();
            }
        }, contenderFailure);

        try {
            AqsDemoSupport.awaitTrue(() -> lock.hasQueuedThread(contender), "contender 进入同步队列");
            System.out.printf("发生竞争：hasQueuedThreads=%s，queueLength=%d%n",
                    lock.hasQueuedThreads(), lock.getQueueLength());
        } finally {
            releaseOwner.countDown();
        }
        AqsDemoSupport.join(owner, ownerFailure);
        AqsDemoSupport.join(contender, contenderFailure);
    }
}

