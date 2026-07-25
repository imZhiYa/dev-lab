package com.zhiya.aqs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AQS 演示。
 *
 * 对应层级：Level 4。
 * 演示主题：Condition.signal() 与 Condition.signalAll() 的唤醒策略。
 * 验证目标：signal 只为一个候选者制造重新竞争机会；当新增资源可同时满足多个等待者时才使用 signalAll。
 */
public final class AqsLevel4ConditionSignalStrategyDemo {
    private static final int WAITER_COUNT = 2;

    private AqsLevel4ConditionSignalStrategyDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        demonstrateSignalForOnePermit();
        demonstrateSignalAllForMultiplePermits();
    }

    private static void demonstrateSignalForOnePermit() throws Exception {
        System.out.println("\n===== Level 4 演示：一个 permit 使用 signal() =====");
        PermitGate gate = new PermitGate();
        WaiterGroup waiters = WaiterGroup.start(gate, "signal-waiter-");

        gate.awaitConditionWaiters(WAITER_COUNT);
        // 只新增一个 permit，因此只应转移一个候选者回同步队列。
        gate.addPermits(1, false);
        AqsDemoSupport.await(waiters.firstAcquired, "一个等待者重新获取锁并消费 permit");
        AqsDemoSupport.require(waiters.completed.getCount() == 1,
                "只有一个 permit 时不应有两个等待者同时通过 predicate");

        // 第二个 permit 到来时，再唤醒一个候选者；避免无意义地唤醒全部线程。
        gate.addPermits(1, false);
        waiters.awaitCompletion();
        System.out.println("结论：一次资源变化只满足一个等待者时，signal() 避免额外的同步队列竞争。");
    }

    private static void demonstrateSignalAllForMultiplePermits() throws Exception {
        System.out.println("\n===== Level 4 演示：多个 permit 使用 signalAll() =====");
        PermitGate gate = new PermitGate();
        WaiterGroup waiters = WaiterGroup.start(gate, "signal-all-waiter-");

        gate.awaitConditionWaiters(WAITER_COUNT);
        // 两个 permit 同时到来，两个等待者都可能通过 predicate，signalAll 才与资源语义匹配。
        gate.addPermits(WAITER_COUNT, true);
        waiters.awaitCompletion();
        System.out.println("结论：signalAll() 不是防丢通知的默认按钮；它应对应“多个等待者现在都可能继续”的 predicate。");
    }

    /**
     * 一个最小的“可消费 permit”状态机。
     * ConditionQueue 等待的是 permits > 0，不是“锁是否空闲”；锁只保护 predicate 的检查和修改。
     */
    private static final class PermitGate {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition permitsAvailable = lock.newCondition();
        private int permits;

        void acquire() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                // signal 可能早于实际运行；也允许虚假唤醒，因此 await 必须位于 while 中。
                while (permits == 0) {
                    permitsAvailable.await();
                }
                permits--;
            } finally {
                lock.unlock();
            }
        }

        void addPermits(int addedPermits, boolean wakeAll) {
            if (addedPermits <= 0) {
                throw new IllegalArgumentException("addedPermits must be positive");
            }
            lock.lock();
            try {
                // 修改 predicate 与通知必须在同一把锁保护下，防止检查—等待之间遗漏状态变化。
                permits += addedPermits;
                if (wakeAll) {
                    permitsAvailable.signalAll();
                } else {
                    permitsAvailable.signal();
                }
            } finally {
                lock.unlock();
            }
        }

        void awaitConditionWaiters(int expectedWaiters) {
            // getWaitQueueLength 是诊断快照；这里仅用于让演示在两个线程都登记后再发放 permit。
            AqsDemoSupport.awaitTrue(() -> {
                lock.lock();
                try {
                    return lock.getWaitQueueLength(permitsAvailable) >= expectedWaiters;
                } finally {
                    lock.unlock();
                }
            }, expectedWaiters + " 个线程进入 ConditionQueue");
        }
    }

    /**
     * 将重复的等待者创建、失败传播和完成校验收敛在一起，避免两个策略场景复制线程脚手架。
     */
    private static final class WaiterGroup {
        private final List<Thread> threads;
        private final List<AtomicReference<Throwable>> failures;
        private final CountDownLatch firstAcquired;
        private final CountDownLatch completed;
        private final AtomicInteger acquiredCount;

        private WaiterGroup(List<Thread> threads, List<AtomicReference<Throwable>> failures,
                            CountDownLatch firstAcquired, CountDownLatch completed, AtomicInteger acquiredCount) {
            this.threads = threads;
            this.failures = failures;
            this.firstAcquired = firstAcquired;
            this.completed = completed;
            this.acquiredCount = acquiredCount;
        }

        static WaiterGroup start(PermitGate gate, String threadPrefix) {
            List<Thread> threads = new ArrayList<>(WAITER_COUNT);
            List<AtomicReference<Throwable>> failures = new ArrayList<>(WAITER_COUNT);
            CountDownLatch firstAcquired = new CountDownLatch(1);
            CountDownLatch completed = new CountDownLatch(WAITER_COUNT);
            AtomicInteger acquiredCount = new AtomicInteger();

            for (int index = 0; index < WAITER_COUNT; index++) {
                int waiterId = index;
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Thread thread = AqsDemoSupport.start(threadPrefix + waiterId, () -> {
                    boolean completedNormally = false;
                    try {
                        gate.acquire();
                        int sequence = acquiredCount.incrementAndGet();
                        AqsDemoSupport.log(Thread.currentThread().getName(), "重新获得锁并消费 permit，顺序=" + sequence);
                        // 先发布完成计数，再通知主线程检查，避免主线程看到旧的 completed 快照。
                        completed.countDown();
                        firstAcquired.countDown();
                        completedNormally = true;
                    } finally {
                        // 即使 acquire 因异常失败，也不能让演示主线程无限等待。
                        if (!completedNormally) {
                            completed.countDown();
                        }
                    }
                }, failure);
                threads.add(thread);
                failures.add(failure);
            }
            return new WaiterGroup(threads, failures, firstAcquired, completed, acquiredCount);
        }

        void awaitCompletion() throws Exception {
            AqsDemoSupport.await(completed, "全部等待者完成");
            for (int index = 0; index < threads.size(); index++) {
                AqsDemoSupport.join(threads.get(index), failures.get(index));
            }
            AqsDemoSupport.require(acquiredCount.get() == WAITER_COUNT, "存在未完成的 permit 消费者");
        }
    }
}
