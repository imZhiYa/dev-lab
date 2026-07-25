package com.zhiya.aqs;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
/**
 * AQS 演示。
 *
 * 对应层级：Level 4。
 * 演示主题：有界缓冲区的 notEmpty 与 notFull ConditionQueue。
 * 验证目标：等待条件使用 while 复检，signal 只转移候选者而不转移锁所有权。
 */
public final class AqsLevel4BoundedBufferDemo {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();
    private final Deque<Integer> queue = new ArrayDeque<>();
    private final int capacity;

    public AqsLevel4BoundedBufferDemo(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public void put(int item) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            // await 返回不代表队列一定有空间：可能虚假唤醒，也可能被其他生产者先占用空间。
            while (queue.size() == capacity) {
                log("队列已满，生产者进入 notFull ConditionQueue 等待");
                notFull.await();
            }
            queue.addLast(item);
            log("生产 -> " + item + "，当前队列=" + queue);
            // signal 仅将一个候选者移回同步队列，锁的所有权仍由当前线程持有。
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public int take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            // await 返回后必须在锁保护下复检 predicate，不能将 signal 当作资源所有权。
            while (queue.isEmpty()) {
                log("队列为空，消费者进入 notEmpty ConditionQueue 等待");
                notEmpty.await();
            }
            int item = queue.removeFirst();
            log("消费 <- " + item + "，当前队列=" + queue);
            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }

    private boolean hasNotEmptyWaiter() {
        lock.lock();
        try {
            return lock.hasWaiters(notEmpty);
        } finally {
            lock.unlock();
        }
    }

    private boolean hasNotFullWaiter() {
        lock.lock();
        try {
            return lock.hasWaiters(notFull);
        } finally {
            lock.unlock();
        }
    }

    private void log(String message) {
        System.out.printf("[%s] %s%n", Thread.currentThread().getName(), message);
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        System.out.println("\n===== Level 4 演示：Condition 两本登记册（有界缓冲区）=====");
        AqsLevel4BoundedBufferDemo buffer = new AqsLevel4BoundedBufferDemo(2);
        AtomicReference<Throwable> consumerFailure = new AtomicReference<>();
        AtomicReference<Throwable> producerFailure = new AtomicReference<>();
        AtomicReference<Integer> consumedFirst = new AtomicReference<>();

        // 先让消费者因“队列为空”进入 notEmpty 侧队列。
        Thread consumer = AqsDemoSupport.start("consumer", () -> consumedFirst.set(buffer.take()), consumerFailure);
        AqsDemoSupport.awaitTrue(buffer::hasNotEmptyWaiter, "消费者进入 notEmpty ConditionQueue");
        buffer.put(1);
        AqsDemoSupport.join(consumer, consumerFailure);
        AqsDemoSupport.require(consumedFirst.get() == 1, "消费者未获得生产者放入的元素");

        // 再填满队列，让生产者因“队列已满”进入 notFull 侧队列。
        buffer.put(2);
        buffer.put(3);
        Thread producer = AqsDemoSupport.start("producer", () -> buffer.put(4), producerFailure);
        AqsDemoSupport.awaitTrue(buffer::hasNotFullWaiter, "生产者进入 notFull ConditionQueue");
        int releasedSlotItem = buffer.take();
        AqsDemoSupport.join(producer, producerFailure);

        AqsDemoSupport.require(releasedSlotItem == 2, "FIFO 缓冲区顺序错误");
        AqsDemoSupport.require(buffer.take() == 3, "缓冲区内容错误");
        AqsDemoSupport.require(buffer.take() == 4, "signal 后生产者没有重新获取锁并完成 put");
    }
}

