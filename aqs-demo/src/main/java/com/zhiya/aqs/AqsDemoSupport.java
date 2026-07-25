package com.zhiya.aqs;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * AQS 演示基础设施。
 *
 * 统一职责：线程启动、并发统一放行、超时等待、失败传播和断言。
 * 使用边界：仅供本包内演示类复用，不承载任何 AQS 业务语义。
 */
final class AqsDemoSupport {
    static final Duration TIMEOUT = Duration.ofSeconds(5);

    private AqsDemoSupport() {
    }

    /**
     * 启动一个平台线程，并把线程内部异常保存到调用方可见的位置。
     * 不直接在线程内部抛出异常，是因为未捕获异常只会打印到 stderr，测试主线程无法可靠失败。
     */
    static Thread start(String name, ThrowingRunnable action, AtomicReference<Throwable> failure) {
        Thread thread = new Thread(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }, name);
        thread.start();
        return thread;
    }

    /**
     * 等待一次确定的协调事件。超时将失败而不是无限挂起，避免错误的并发协议拖住整个演示。
     */
    static void await(CountDownLatch latch, String description) throws InterruptedException {
        if (!latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new AssertionError("Timed out: " + description);
        }
    }

    /**
     * 将一组平台线程先全部创建并停在 start 闸门，再统一放行。
     * 这样测量的是同一时刻争用资源的结果，而不是线程创建先后造成的串行假象。
     * 返回值从统一放行到全部完成的墙钟耗时，适合比较忙等和阻塞等待的整体成本。
     */
    static long runConcurrently(int workerCount, String threadNamePrefix, ThrowingRunnable action) throws Exception {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workerCount);
        Thread[] workers = new Thread[workerCount];
        @SuppressWarnings("unchecked")
        AtomicReference<Throwable>[] failures = new AtomicReference[workerCount];

        for (int index = 0; index < workerCount; index++) {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            failures[index] = failure;
            workers[index] = start(threadNamePrefix + "-" + index, () -> {
                ready.countDown();
                try {
                    await(start, "统一放行 " + threadNamePrefix + " 线程");
                    action.run();
                } finally {
                    done.countDown();
                }
            }, failure);
        }

        await(ready, threadNamePrefix + " 线程就绪");
        long started = System.nanoTime();
        start.countDown();
        await(done, threadNamePrefix + " 线程完成");
        for (int index = 0; index < workerCount; index++) {
            join(workers[index], failures[index]);
        }
        return (System.nanoTime() - started) / 1_000_000;
    }

    /**
     * 等待可观察状态成立，例如指定线程确实进入同步队列或 ConditionQueue。
     * 此轮询只用于测试观察；业务代码不能把队列观测快照当作正确性或限流依据。
     */
    static void awaitTrue(BooleanSupplier condition, String description) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out: " + description);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }

    /**
     * 有界等待线程结束，并把 start() 捕获的失败重新抛给主测试线程。
     */
    static void join(Thread thread, AtomicReference<Throwable> failure) throws Exception {
        thread.join(TIMEOUT.toMillis());
        if (thread.isAlive()) {
            throw new AssertionError("Thread did not terminate: " + thread.getName());
        }
        Throwable throwable = failure.get();
        if (throwable != null) {
            if (throwable instanceof Exception) {
                throw (Exception) throwable;
            }
            if (throwable instanceof Error) {
                throw (Error) throwable;
            }
            throw new RuntimeException(throwable);
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void log(String who, String message) {
        System.out.printf("[%s] %s%n", who, message);
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}

