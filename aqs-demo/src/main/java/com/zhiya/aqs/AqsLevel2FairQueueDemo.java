package com.zhiya.aqs;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AQS 演示。
 *
 * 对应层级：Level 2。
 * 演示主题：同步队列候选顺序与公平、非公平获取。
 * 验证目标：公平锁优先推进已排队前驱；非公平锁不对队列顺序作获取保证。
 */
public final class AqsLevel2FairQueueDemo {
    private static final int WAITER_COUNT = 6;

    private AqsLevel2FairQueueDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        System.out.println("\n===== Level 2/3 演示：公平候选顺序与非公平获取 =====");
        List<Integer> fairOrder = runQueueRound(true);
        List<Integer> nonFairOrder = runQueueRound(false);
        List<Integer> expectedOrder = Arrays.asList(0, 1, 2, 3, 4, 5);

        AqsDemoSupport.require(fairOrder.equals(expectedOrder), "公平锁没有按已确认的入队顺序推进候选者");
        System.out.println("公平锁获取顺序: " + fairOrder);
        System.out.println("非公平锁获取顺序: " + nonFairOrder);
        System.out.println("说明：非公平 lock() 不检查队列，释放窗口允许新到线程 CAS 插队；");
        System.out.println("是否恰好插队受调度影响，不能将某一轮必然乱序写成自动化断言。");
    }

    private static List<Integer> runQueueRound(boolean fair) throws Exception {
        ReentrantLock lock = new ReentrantLock(fair);
        ConcurrentLinkedQueue<Integer> acquireOrder = new ConcurrentLinkedQueue<>();
        List<Thread> waiters = new ArrayList<>(WAITER_COUNT);
        List<AtomicReference<Throwable>> failures = new ArrayList<>(WAITER_COUNT);

        lock.lock();
        try {
            for (int id = 0; id < WAITER_COUNT; id++) {
                int waiterId = id;
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Thread waiter = AqsDemoSupport.start("waiter-" + waiterId, () -> {
                    lock.lock();
                    try {
                        acquireOrder.add(waiterId);
                    } finally {
                        lock.unlock();
                    }
                }, failure);
                waiters.add(waiter);
                failures.add(failure);
                // 每次确认当前 waiter 已入队后才创建下一位，使入队顺序可验证而不是依赖 sleep。
                AqsDemoSupport.awaitTrue(() -> lock.hasQueuedThread(waiter),
                        "waiter-" + waiterId + " 进入同步队列");
            }
            System.out.printf("公平=%s，释放前 queueLength=%d%n", fair, lock.getQueueLength());
        } finally {
            lock.unlock();
        }

        for (int index = 0; index < waiters.size(); index++) {
            AqsDemoSupport.join(waiters.get(index), failures.get(index));
        }
        return new ArrayList<Integer>(acquireOrder);
    }
}

