package com.zhiya.aqs;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AQS 演示。
 *
 * 对应层级：Level 4。
 * 演示主题：Condition 的 await、signal 和重新获取。
 * 验证目标：await 完整释放重入状态，重新获取后恢复保存的重入次数。
 */
public final class AqsLevel4ConditionReacquisitionDemo {
    private AqsLevel4ConditionReacquisitionDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        System.out.println("\n===== Level 4 演示：Condition 的侧队列到同步队列 =====");
        ReentrantLock lock = new ReentrantLock();
        Condition notEmpty = lock.newCondition();
        AtomicReference<String> item = new AtomicReference<>();
        AtomicInteger restoredHoldCount = new AtomicInteger();
        CountDownLatch consumerReadyToAwait = new CountDownLatch(1);
        AtomicReference<Throwable> consumerFailure = new AtomicReference<>();

        Thread consumer = AqsDemoSupport.start("consumer", () -> {
            lock.lock();
            lock.lock();
            try {
                consumerReadyToAwait.countDown();
                while (item.get() == null) {
                    AqsDemoSupport.log("consumer", "条件不成立，await 完整释放两层重入锁");
                    notEmpty.await();
                }
                restoredHoldCount.set(lock.getHoldCount());
                AqsDemoSupport.log("consumer", "重新获得锁并复检 predicate 成功");
            } finally {
                lock.unlock();
                lock.unlock();
            }
        }, consumerFailure);

        AqsDemoSupport.await(consumerReadyToAwait, "consumer 准备 await");
        lock.lock();
        try {
            // 能获得该锁说明 consumer 的 await 已完整释放了两层重入状态。
            item.set("payload");
            AqsDemoSupport.log("producer", "在同一把锁内更新 predicate 并 signal");
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
        AqsDemoSupport.join(consumer, consumerFailure);
        AqsDemoSupport.require(restoredHoldCount.get() == 2, "await 返回后必须恢复保存的重入次数");
    }
}

