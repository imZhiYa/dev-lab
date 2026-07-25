package com.zhiya.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

/**
 * 🔒 JVM synchronized 内置 Monitor 锁 vs AQS (ReentrantLock/ReadWriteLock) vs StampedLock vs 无锁 CAS 深度性能基准测试
 *
 * 知识库对应：
 *  - Level 1 · state 状态与 CAS 竞态推演 / JVM 锁升级 (偏向锁 -> 轻量级 CAS -> 重量级 Monitor)
 *  - Level 2 · AQS 同步队列与公平/非公平锁 (CLH 队列 FIFO 唤醒与 CAS 插队)
 *  - Level 5 · 线程协作与高并发原语选型 (ReentrantReadWriteLock 读写分离 / StampedLock 乐观读 / LongAdder 单元格分段)
 *
 * 【测试维度与矩阵】
 * 1. 锁机制全覆盖：
 *    - synchronized (JVM 内置 Monitor)
 *    - ReentrantLock (AQS 独占锁 · 非公平插队模式)
 *    - ReentrantLock (AQS 独占锁 · 公平 CLH 排队模式)
 *    - ReentrantReadWriteLock (AQS 共享读 / 独占写)
 *    - StampedLock (JDK 8+ 无 CAS 开销乐观读 / 印记写)
 *    - LongAdder (JDK 8+ 热点分散分段 Cell 数组 CAS)
 *
 * 2. 真实业务场景模拟：
 *    - 纯写高竞争场景 (100% Write Contention)
 *    - 读多写少混合场景 (利用 JMH Group 模拟 8 读 2 写真实读写锁争用)
 *    - 临界区业务耗时参数化 (@Param("10", "100") 通过 Blackhole.consumeCPU 模拟真实临界区逻辑粒度)
 *
 * 验证人：imZhiYa
 * 运行方式：
 *   cd benchmarks
 *   mvn clean package -DskipTests
 *   java -jar target/benchmarks.jar SyncVsAqsBenchmark
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@Threads(8) // 默认 8 线程高并发争用
public class SyncVsAqsBenchmark {

    // =========================================================================
    // 参数矩阵：模拟不同临界区业务计算开销 (10 指令 vs 100 指令)
    // =========================================================================
    @Param({"10", "100"})
    private int workloadTokens;

    // =========================================================================
    // 共享状态与同步原语定义
    // =========================================================================
    private long sharedCounter = 0;

    private final ReentrantLock nonFairLock = new ReentrantLock(false);
    private final ReentrantLock fairLock = new ReentrantLock(true);
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(false);
    private final StampedLock stampedLock = new StampedLock();
    private final LongAdder longAdder = new LongAdder();

    // =========================================================================
    // 维度一：纯写高竞争场景 (100% Write Contention)
    // =========================================================================

    /**
     * 1.1 JVM synchronized 内置 Monitor 锁 (纯写)
     */
    @Benchmark
    public long write_Synchronized(Blackhole bh) {
        synchronized (this) {
            Blackhole.consumeCPU(workloadTokens);
            return ++sharedCounter;
        }
    }

    /**
     * 1.2 AQS ReentrantLock 非公平锁 (纯写 · CAS 非公平插队)
     */
    @Benchmark
    public long write_ReentrantLockNonFair(Blackhole bh) {
        nonFairLock.lock();
        try {
            Blackhole.consumeCPU(workloadTokens);
            return ++sharedCounter;
        } finally {
            nonFairLock.unlock();
        }
    }

    /**
     * 1.3 AQS ReentrantLock 公平锁 (纯写 · CLH 队列严格 FIFO 排队)
     */
    @Benchmark
    public long write_ReentrantLockFair(Blackhole bh) {
        fairLock.lock();
        try {
            Blackhole.consumeCPU(workloadTokens);
            return ++sharedCounter;
        } finally {
            fairLock.unlock();
        }
    }

    /**
     * 1.4 StampedLock 独占写锁 (纯写)
     */
    @Benchmark
    public long write_StampedLock(Blackhole bh) {
        long stamp = stampedLock.writeLock();
        try {
            Blackhole.consumeCPU(workloadTokens);
            return ++sharedCounter;
        } finally {
            stampedLock.unlockWrite(stamp);
        }
    }

    /**
     * 1.5 LongAdder 分段 Cell 数组 CAS (纯写 · 消除伪共享与 CAS 自旋热点)
     */
    @Benchmark
    public void write_LongAdder(Blackhole bh) {
        Blackhole.consumeCPU(workloadTokens);
        longAdder.increment();
    }

    // =========================================================================
    // 维度二：读多写少混合并发场景 (利用 JMH Group 模拟 8 读线程 + 2 写线程真实争用)
    // =========================================================================

    // --- 1. synchronized 读写并发组 ---
    @Benchmark
    @Group("group_synchronized")
    @GroupThreads(8)
    public long group_sync_read(Blackhole bh) {
        synchronized (this) {
            Blackhole.consumeCPU(workloadTokens);
            return sharedCounter;
        }
    }

    @Benchmark
    @Group("group_synchronized")
    @GroupThreads(2)
    public long group_sync_write(Blackhole bh) {
        synchronized (this) {
            Blackhole.consumeCPU(workloadTokens);
            return ++sharedCounter;
        }
    }

    // --- 2. ReentrantReadWriteLock (AQS 读写分离) 组 ---
    @Benchmark
    @Group("group_rwlock")
    @GroupThreads(8)
    public long group_rwlock_read(Blackhole bh) {
        rwLock.readLock().lock();
        try {
            Blackhole.consumeCPU(workloadTokens);
            return sharedCounter;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Benchmark
    @Group("group_rwlock")
    @GroupThreads(2)
    public long group_rwlock_write(Blackhole bh) {
        rwLock.writeLock().lock();
        try {
            Blackhole.consumeCPU(workloadTokens);
            return ++sharedCounter;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // --- 3. StampedLock 乐观读 / 悲观写 组 (生产高并发只读最佳实践) ---
    @Benchmark
    @Group("group_stamped")
    @GroupThreads(8)
    public long group_stamped_optimistic_read(Blackhole bh) {
        // Step 1: 尝试无锁乐观读 (无 CAS、无内存屏障开销)
        long stamp = stampedLock.tryOptimisticRead();
        long currentVal = sharedCounter;

        // Step 2: 校验在读取期间是否有写锁被抢占
        if (!stampedLock.validate(stamp)) {
            // Step 3: 校验失败，降级退化为悲观读锁 (Read Lock)
            stamp = stampedLock.readLock();
            try {
                currentVal = sharedCounter;
            } finally {
                stampedLock.unlockRead(stamp);
            }
        }

        Blackhole.consumeCPU(workloadTokens);
        return currentVal;
    }

    @Benchmark
    @Group("group_stamped")
    @GroupThreads(2)
    public long group_stamped_write(Blackhole bh) {
        long stamp = stampedLock.writeLock();
        try {
            Blackhole.consumeCPU(workloadTokens);
            return ++sharedCounter;
        } finally {
            stampedLock.unlockWrite(stamp);
        }
    }
}
