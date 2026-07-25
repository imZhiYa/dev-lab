package com.zhiya.aqs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AQS 演示。
 *
 * 对应层级：Level 5。
 * 演示主题：自定义共享同步器的许可证传播。
 * 验证目标：tryAcquireShared 的返回值表达获取结果和剩余许可状态。
 */
public final class AqsLevel5SharedPermitPropagationDemo {
    private AqsLevel5SharedPermitPropagationDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        System.out.println("\n===== Level 5 演示：自定义 AQS 共享传播 =====");
        int permits = 3;
        AqsSharedPermitSynchronizer semaphore = new AqsSharedPermitSynchronizer(permits);
        List<Thread> waiters = new ArrayList<>();
        List<AtomicReference<Throwable>> failures = new ArrayList<>();
        CountDownLatch attempting = new CountDownLatch(permits);
        CountDownLatch acquired = new CountDownLatch(permits);
        CountDownLatch releaseWaiters = new CountDownLatch(1);
        AtomicInteger inCriticalSection = new AtomicInteger();
        AtomicInteger maximumInCriticalSection = new AtomicInteger();

        // 先耗尽全部许可证，确保后续线程从共享获取失败路径进入 AQS 队列。
        for (int index = 0; index < permits; index++) {
            semaphore.acquire();
        }
        AqsDemoSupport.require(semaphore.availablePermits() == 0, "初始许可证未耗尽");

        for (int index = 0; index < permits; index++) {
            int workerId = index;
            AtomicReference<Throwable> failure = new AtomicReference<>();
            failures.add(failure);
            Thread waiter = AqsDemoSupport.start("shared-waiter-" + workerId, () -> {
                attempting.countDown();
                semaphore.acquire();
                try {
                    int current = inCriticalSection.incrementAndGet();
                    maximumInCriticalSection.accumulateAndGet(current, Math::max);
                    AqsDemoSupport.log(Thread.currentThread().getName(), "成功扣减 state 并进入共享临界区");
                    acquired.countDown();
                    AqsDemoSupport.await(releaseWaiters, "等待统一释放共享临界区");
                } finally {
                    inCriticalSection.decrementAndGet();
                    semaphore.release();
                }
            }, failure);
            waiters.add(waiter);
        }

        AqsDemoSupport.await(attempting, "所有共享等待者开始 acquire");
        AqsDemoSupport.awaitTrue(() -> semaphore.getQueueLength() >= permits,
                "所有共享等待者进入 AQS 同步队列");

        // 每次 release 仅产生一个许可；AQS 由共享获取结果决定是否继续推进后继，而非 unparkAll。
        for (int index = 0; index < permits; index++) {
            semaphore.release();
        }
        try {
            AqsDemoSupport.await(acquired, "全部共享等待者取得许可");
            AqsDemoSupport.require(maximumInCriticalSection.get() == permits,
                    "共享传播没有让全部可用许可证被消费");
        } finally {
            releaseWaiters.countDown();
        }

        for (int index = 0; index < waiters.size(); index++) {
            AqsDemoSupport.join(waiters.get(index), failures.get(index));
        }
        AqsDemoSupport.require(semaphore.availablePermits() == permits,
                "所有等待者 release 后许可证数量未恢复");
    }
}

