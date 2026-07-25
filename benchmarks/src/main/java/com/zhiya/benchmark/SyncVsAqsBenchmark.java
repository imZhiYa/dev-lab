package com.zhiya.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

/**
 * 🔒 全维度 JVM synchronized 内置锁 vs AQS 框架 (ReentrantLock / ReadWriteLock / Semaphore) vs StampedLock vs 无锁 CAS 纳秒级并发基准测试套件
 *
 * 知识库对应：
 *  - Level 1 · state 状态与 CAS 竞态推演 / JVM 锁升级 (偏向锁 -> 轻量级自旋 -> 重量级 OS Mutex 膨胀)
 *  - Level 2 · AQS 同步队列与公平/非公平锁 (CLH 队列 FIFO 排队与 CAS 非公平插队)
 *  - Level 4 · Condition 条件队列与锁重入深度开销
 *  - Level 5 · 线程协作原语选型 (ReentrantReadWriteLock 读写分离 / StampedLock 乐观读 / LongAdder 单元格分段 / AtomicLong CAS 旋锁)
 *
 * 【测试维度与全量原语矩阵】
 * 1. 独占锁与内置锁 (Exclusive Locks):
 *    - write_Synchronized            : JVM 内置 Monitor 锁 (偏向/轻量/重量锁膨胀)
 *    - write_ReentrantLockNonFair    : AQS 独占锁 · 非公平 CAS 插队模式
 *    - write_ReentrantLockFair       : AQS 独占锁 · 公平 CLH FIFO 队列排队模式
 *    - write_ReentrantLockReentrant  : AQS 独占锁 · 10 层深度锁重入 (Reentrancy Cost)
 *
 * 2. 读写分离与乐观锁 (Read/Write & Optimistic Locks):
 *    - rw_ReentrantReadWriteLock     : AQS 共享读锁 / 独占写锁 (State 高低位拆分)
 *    - rw_StampedLockPessimistic     : StampedLock 悲观读锁 / 悲观写锁
 *    - rw_StampedLockOptimistic      : StampedLock 无锁乐观读 (validate 屏障) -> 降级悲观锁
 *    - rw_StampedLockLockConversion  : StampedLock 读锁动态升级转写锁 (tryConvertToWriteLock)
 *
 * 3. 共享信号量与 CAS 原子组件 (Shared Semaphores & Atomic Components):
 *    - shared_SemaphoreNonFair       : AQS 共享模式 Permit 信号量 (非公平)
 *    - shared_SemaphoreFair          : AQS 共享模式 Permit 信号量 (公平)
 *    - cas_AtomicLong                : 单变量 CAS 自旋 (Unsafe.compareAndSwapLong)
 *    - cas_AtomicLongFieldUpdater    : 内存偏置 Updater 零装箱 CAS
 *    - cas_LongAdder                 : JDK 8+ 热点分散分段 Cell 数组 CAS (消除伪共享/缓存一致性风暴)
 *
 * 4. 真实并发场景与粒度参数 (Real-world Scenarios & Granularity):
 *    - 临界区业务粒度 (@Param({"10", "100", "1000"}) 指令数)
 *    - 读多写少组 (JMH Group: 9 读 1 写)
 *    - 读写均衡组 (JMH Group: 5 读 5 写)
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
@Threads(8) // 默认 8 线程高并发争用压测
public class SyncVsAqsBenchmark {

    // =========================================================================
    // 参数矩阵：模拟不同业务临界区计算耗时 (微观 10 指令 / 中观 100 指令 / 宏观 1000 指令)
    // =========================================================================
    @Param({"10", "100", "1000"})
    private int workloadTokens;

    // =========================================================================
    // 共享状态与同步原语全量定义
    // =========================================================================
    private long sharedCounter = 0;
    private volatile long volatileCounter = 0;

    // 1. AQS 独占锁 (公平 vs 非公平)
    private final ReentrantLock nonFairLock = new ReentrantLock(false);
    private final ReentrantLock fairLock = new ReentrantLock(true);

    // 2. AQS 读写锁与 StampedLock
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(false);
    private final StampedLock stampedLock = new StampedLock();

    // 3. AQS 共享模式信号量
    private final Semaphore nonFairSemaphore = new Semaphore(1, false);
    private final Semaphore fairSemaphore = new Semaphore(1, true);

    // 4. 无锁 CAS 与分段 Cell 组件
    private final AtomicLong atomicLong = new AtomicLong(0);
    private final LongAdder longAdder = new LongAdder();

    private static final AtomicLongFieldUpdater<SyncVsAqsBenchmark> UPDATER =
            AtomicLongFieldUpdater.newUpdater(SyncVsAqsBenchmark.class, "volatileCounter");

    // =========================================================================
    // 模块一：纯写高争用场景 (100% Write Contention)
    // =========================================================================

    /**
     * 1.1 JVM synchronized 内置 Monitor 锁 (偏向 -> 轻量 -> 重量膨胀)
     */
    @Benchmark
    public long write_Synchronized(Blackhole bh) {
        synchronized (this) {
            Blackhole.consumeCPU(workloadTokens);
            return ++sharedCounter;
        }
    }

    /**
     * 1.2 AQS ReentrantLock 非公平锁 (CAS 抢占优先)
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
     * 1.3 AQS ReentrantLock 公平锁 (CLH 双向队列 FIFO 严格排队)
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
     * 1.4 AQS 独占锁 10 层深度锁重入开销 (Reentrancy Depth Cost)
     */
    @Benchmark
    public long write_ReentrantLock_10LevelsReentrant(Blackhole bh) {
        return recursiveLock(nonFairLock, 10, bh);
    }

    private long recursiveLock(ReentrantLock lock, int depth, Blackhole bh) {
        lock.lock();
        try {
            if (depth > 1) {
                return recursiveLock(lock, depth - 1, bh);
            } else {
                Blackhole.consumeCPU(workloadTokens);
                return ++sharedCounter;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 1.5 StampedLock 独占写锁模式
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

    // =========================================================================
    // 模块二：AQS 共享信号量与 CAS 无锁化原语 (Shared Permit & CAS Primitives)
    // =========================================================================

    /**
     * 2.1 AQS 共享模式 Semaphore (非公平信号量)
     */
    @Benchmark
    public long shared_SemaphoreNonFair(Blackhole bh) throws InterruptedException {
        nonFairSemaphore.acquire();
        try {
            Blackhole.consumeCPU(workloadTokens);
            return ++sharedCounter;
        } finally {
            nonFairSemaphore.release();
        }
    }

    /**
     * 2.2 AQS 共享模式 Semaphore (公平信号量)
     */
    @Benchmark
    public long shared_SemaphoreFair(Blackhole bh) throws InterruptedException {
        fairSemaphore.acquire();
        try {
            Blackhole.consumeCPU(workloadTokens);
            return ++sharedCounter;
        } finally {
            fairSemaphore.release();
        }
    }

    /**
     * 2.3 AtomicLong 单变量 CAS 自旋 (高并发下会导致总线 Lock 信号与缓存一致性流量风暴)
     */
    @Benchmark
    public long cas_AtomicLong(Blackhole bh) {
        Blackhole.consumeCPU(workloadTokens);
        return atomicLong.incrementAndGet();
    }

    /**
     * 2.4 AtomicLongFieldUpdater 零装箱字段原子更新
     */
    @Benchmark
    public long cas_AtomicLongFieldUpdater(Blackhole bh) {
        Blackhole.consumeCPU(workloadTokens);
        return UPDATER.incrementAndGet(this);
    }

    /**
     * 2.5 LongAdder 分段 Cell 数组 CAS (JDK 8+ 消除伪共享/分散写热点)
     */
    @Benchmark
    public void cas_LongAdder(Blackhole bh) {
        Blackhole.consumeCPU(workloadTokens);
        longAdder.increment();
    }

    // =========================================================================
    // 模块三：读多写少并发组 (9 读 1 写 · 模拟本地缓存 / 状态中心高并发场景)
    // =========================================================================

    // --- 3.1 synchronized 读写偏置组 ---
    @Benchmark
    @Group("rw_heavy_sync")
    @GroupThreads(9)
    public long rw91_sync_read(Blackhole bh) {
        synchronized (this) {
            Blackhole.consumeCPU(workloadTokens);
            return sharedCounter;
        }
    }

    @Benchmark
    @Group("rw_heavy_sync")
    @GroupThreads(1)
    public long rw91_sync_write(Blackhole bh) {
        synchronized (this) {
            Blackhole.consumeCPU(workloadTokens);
            return ++sharedCounter;
        }
    }

    // --- 3.2 ReentrantReadWriteLock (AQS 读写分离) 偏置组 ---
    @Benchmark
    @Group("rw_heavy_rwlock")
    @GroupThreads(9)
    public long rw91_rwlock_read(Blackhole bh) {
        rwLock.readLock().lock();
        try {
            Blackhole.consumeCPU(workloadTokens);
            return sharedCounter;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Benchmark
    @Group("rw_heavy_rwlock")
    @GroupThreads(1)
    public long rw91_rwlock_write(Blackhole bh) {
        rwLock.writeLock().lock();
        try {
            Blackhole.consumeCPU(workloadTokens);
            return ++sharedCounter;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // --- 3.3 StampedLock 乐观读 / 降级悲观读 偏置组 (无 CAS 内存屏障高并发只读) ---
    @Benchmark
    @Group("rw_heavy_stamped")
    @GroupThreads(9)
    public long rw91_stamped_optimistic_read(Blackhole bh) {
        // Step 1: 尝试无锁乐观读 (无任何 CAS 写操作，零 CPU 缓存一致性开销)
        long stamp = stampedLock.tryOptimisticRead();
        long currentVal = sharedCounter;

        // Step 2: 内存屏障校验——判断读取期间是否有并发写锁侵入
        if (!stampedLock.validate(stamp)) {
            // Step 3: 校验失败，退化降级为悲观读锁 (Read Lock)
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
    @Group("rw_heavy_stamped")
    @GroupThreads(1)
    public long rw91_stamped_write(Blackhole bh) {
        long stamp = stampedLock.writeLock();
        try {
            Blackhole.consumeCPU(workloadTokens);
            return ++sharedCounter;
        } finally {
            stampedLock.unlockWrite(stamp);
        }
    }

    // =========================================================================
    // 模块四：读写均衡混合并发组 (5 读 5 写 · 模拟高频双向读写状态通道)
    // =========================================================================

    // --- 4.1 ReentrantReadWriteLock 读写均衡 ---
    @Benchmark
    @Group("rw_balanced_rwlock")
    @GroupThreads(5)
    public long rw55_rwlock_read(Blackhole bh) {
        rwLock.readLock().lock();
        try {
            Blackhole.consumeCPU(workloadTokens);
            return sharedCounter;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Benchmark
    @Group("rw_balanced_rwlock")
    @GroupThreads(5)
    public long rw55_rwlock_write(Blackhole bh) {
        rwLock.writeLock().lock();
        try {
            Blackhole.consumeCPU(workloadTokens);
            return ++sharedCounter;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // --- 4.2 StampedLock 动态锁升级 (tryConvertToWriteLock 读锁转写锁) ---
    @Benchmark
    @Group("rw_balanced_stamped_conversion")
    @GroupThreads(5)
    public long rw55_stamped_conversion_read_write(Blackhole bh) {
        long stamp = stampedLock.readLock();
        try {
            while (sharedCounter < 0) {
                // 尝试将悲观读锁无缝升级为写锁
                long ws = stampedLock.tryConvertToWriteLock(stamp);
                if (ws != 0L) {
                    stamp = ws;
                    sharedCounter = Math.abs(sharedCounter);
                    break;
                } else {
                    // 升级失败，显式释放读锁并重新抢占写锁
                    stampedLock.unlockRead(stamp);
                    stamp = stampedLock.writeLock();
                }
            }
            Blackhole.consumeCPU(workloadTokens);
            return sharedCounter;
        } finally {
            stampedLock.unlock(stamp);
        }
    }
}
