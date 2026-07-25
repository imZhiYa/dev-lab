package com.zhiya.aqs;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
/**
 * AQS 教学同步器。
 *
 * 对应层级：Level 5。
 * 同步语义：state 表示剩余许可证数量。
 * 使用边界：仅用于演示；生产代码优先使用 java.util.concurrent.Semaphore。
 */
public final class AqsSharedPermitSynchronizer {
    private final Sync sync;

    public AqsSharedPermitSynchronizer(int permits) {
        if (permits < 0) {
            throw new IllegalArgumentException("permits must not be negative");
        }
        this.sync = new Sync(permits);
    }

    private static final class Sync extends AbstractQueuedSynchronizer {
        Sync(int permits) {
            setState(permits);
        }

        @Override
        protected int tryAcquireShared(int acquires) {
            for (;;) {
                int available = getState();
                int remaining = available - acquires;
                if (remaining < 0) {
                    // 负数表示获取失败，AQS 将当前线程纳入共享同步队列。
                    return remaining;
                }
                if (compareAndSetState(available, remaining)) {
                    // 0 表示成功但余量耗尽；正数表示成功且仍可继续共享传播。
                    return remaining;
                }
            }
        }

        @Override
        protected boolean tryReleaseShared(int releases) {
            for (;;) {
                int current = getState();
                int next = current + releases;
                if (next < current) {
                    throw new Error("maximum permit count exceeded");
                }
                if (compareAndSetState(current, next)) {
                    // true 告诉 AQS：共享资源增加，应推进合适的等待后继。
                    return true;
                }
            }
        }

        int getPermits() {
            return getState();
        }
    }

    public void acquire() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    public void release() {
        // 与 JDK Semaphore 一样，release 不校验当前线程是否曾经 acquire。
        // 业务层必须保证每一次成功 acquire 恰好配对一次 release。
        sync.releaseShared(1);
    }

    public int availablePermits() {
        return sync.getPermits();
    }

    public int getQueueLength() {
        // AQS 队列长度是并发快照估计值，只可用于诊断，不可作为业务正确性依据。
        return sync.getQueueLength();
    }
}
