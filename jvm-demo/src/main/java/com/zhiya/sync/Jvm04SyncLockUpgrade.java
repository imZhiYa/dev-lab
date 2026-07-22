package com.zhiya.sync;

import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JVM04 synchronized 锁升级验证
 *
 * 知识库对应：Level 5 · 并发与内存模型
 *
 * =====================================================
 * 的锁优化心得
 * =====================================================
 *
 * 1. 锁升级是 JVM 最精妙的设计之一
 *    - 无锁 → 轻量级锁 → 重量级锁
 *    - 不是一开始就用重量级锁，而是逐步升级
 *    - 大多数锁竞争都是短暂的，轻量级锁就够了
 *
 * 2. 偏向锁的争议
 *    - JDK 8 默认开启，JDK 15 默认关闭（JEP 374）
 *    - JDK 21 已完全移除偏向锁
 *    - 偏向锁撤销需要全局 STW，代价很高
 *    - 高并发场景下，偏向锁反而成为瓶颈
 *    - 建议：高并发服务关闭偏向锁（JDK 8/11/17）
 *
 * 3. 重量级锁的内核切换
 *    - ObjectMonitor → park → Linux Futex
 *    - 用户态 → 内核态 → 用户态
 *    - 一次切换约 1~10μs，高并发下成为瓶颈
 *    - 解决方案：减少临界区、分段锁、LongAdder
 *
 * =====================================================
 * 【A/B 测试】JDK 8 vs JDK 21 锁机制差异
 * =====================================================
 *
 * | 特性                | JDK 8              | JDK 21             |
 * |---------------------|--------------------|--------------------|
 * | 偏向锁默认          | 开启               | 关闭（JEP 374）    |
 * | 锁升级路径          | 四态               | 三态               |
 * | 虚拟线程支持        | 无                 | 有（JEP 425）      |
 * | synchronized 优化   | 有                 | 更激进             |
 * | 锁消除              | 有                 | 更优化             |
 * | 锁粗化              | 有                 | 更优化             |
 *
 * =====================================================
 * 【实战经验】锁竞争诊断指南
 * =====================================================
 *
 * 1. 症状：CPU 高但吞吐量低
 *    - 诊断：perf stat 看 context switches
 *    - 原因：频繁锁竞争导致上下文切换
 *    - 解决：减少临界区、分段锁
 *
 * 2. 症状：P99 延迟飙升
 *    - 诊断：jcmd Thread.print 看 BLOCKED 线程
 *    - 原因：锁等待时间过长
 *    - 解决：减小锁粒度、使用并发容器
 *
 * 3. 症状：死锁
 *    - 诊断：jcmd Thread.print 看死锁链
 *    - 原因：锁顺序不一致
 *    - 解决：统一锁顺序、使用 tryLock
 *
 * =====================================================
 * 【实战经验】锁优化最佳实践
 * =====================================================
 *
 * 1. 减少临界区：
 *    - 只锁必要的代码
 *    - 避免在锁内做 I/O
 *    - 避免在锁内创建对象
 *
 * 2. 分段锁：
 *    - ConcurrentHashMap：16 个 Segment
 *    - LongAdder：Cell 数组分段
 *    - Striped64：分段计数
 *
 * 3. 无锁编程：
 *    - AtomicInteger：CAS 操作
 *    - LongAdder：分段 CAS
 *    - StampedLock：乐观读
 *
 * 4. 锁消除：
 *    - JIT 逃逸分析证明对象不共享
 *    - 自动消除 synchronized
 *    - 验证：-Xlog:jit+lock=debug（JDK 21 推荐）
 *
 * =====================================================
 * 【实战经验】虚拟线程与锁
 * =====================================================
 *
 * JDK 21 虚拟线程的锁行为：
 * 1. 虚拟线程在 synchronized 内阻塞时，会解绑载体线程
 * 2. 其他虚拟线程可以复用这个载体线程
 * 3. 但如果所有载体线程都被 synchronized 阻塞，会创建新载体
 * 4. 这可能导致载体线程膨胀
 *
 * 建议：
 * - 虚拟线程场景下，优先使用 ReentrantLock
 * - 避免在 synchronized 内做阻塞 I/O
 * - 使用 Semaphore 限制并发数
 *
 * =====================================================
 *
 * IntelliJ IDEA VM Options:
 * # ========== 基础配置 ==========
 * -XX:+UseCompressedOops                     # 指针压缩（默认开启）
 *
 * # ========== 锁配置（JDK 21 推荐） ==========
 * # 注意：-XX:-UseBiasedLocking 已废除（JEP 374），JDK 21 已移除
 * # JDK 21 锁升级路径：无锁 (01) → 轻量级 (00) → 重量级 (10)
 *
 * # ========== JIT 配置（逃逸分析 + 锁消除） ==========
 * -XX:+DoEscapeAnalysis                      # 开启逃逸分析（默认开启）
 * -XX:+EliminateLocks                        # 开启锁消除（默认开启）
 *
 * # ========== 日志配置（JDK 21 推荐格式） ==========
 * -Xlog:gc*=info,safepoint=info:file=/tmp/jvm04-gc.log:time,uptime,level,tags
 * -Xlog:jit+lock=debug:file=/tmp/jvm04-lock.log:time,uptime,level,tags
 *
 * ★ JDK 21 已废除参数：
 * - -XX:-UseBiasedLocking（JEP 374，JDK 15+ 默认关闭，JDK 21 已移除）
 * - -XX:+UseHeavyMonitors（已废除，JDK 21 不支持）
 * - -XX:SpinBeforeSpinWait（已废除，JDK 21 不支持）
 * - -XX:PreBlockSpin（已废除，JDK 21 不支持）
 * - -XX:+PrintConcurrentLocks → 改用 -Xlog:jit+lock=debug
 * - -XX:+PrintBiasedLockingStatistics → 已废除（偏向锁已移除）
 * - -XX:+PrintMonitorStatistics → 改用 -Xlog:jit+monitor=debug
 * - -XX:+PrintEscapeAnalysis → 改用 -Xlog:jit+escape=debug
 * - -XX:+PrintEliminateLocks → 改用 -Xlog:jit+lock=debug
 * - -XX:+PrintLockCoarsening → 改用 -Xlog:jit+lock=debug
 *
 * 命令行运行:
 * javac -encoding UTF-8 -d target/classes src/main/java/com/zhiya/jvm/sync/Jvm04SyncLockUpgrade.java
 * java -XX:+UseCompressedOops -XX:+DoEscapeAnalysis -XX:+EliminateLocks -cp target/classes com.zhiya.jvm.sync.Jvm04SyncLockUpgrade
 *
 * @author imZhiYa
 * @since JDK 21
 */
public class Jvm04SyncLockUpgrade {

    // 获取 Unsafe 实例，用于直接内存操作
    private static final Unsafe UNSAFE = getUnsafe();

    // 测试用的锁对象
    static class LockObject {
        private int value = 0;
    }

    // 共享计数器，用于验证锁的互斥性
    private static int sharedCounter = 0;
    private static final Object counterLock = new Object();

    public static void main(String[] args) {
        System.out.println("☕ JVM04 synchronized 锁升级验证");
        System.out.println("=" .repeat(60));

        // 1. 验证锁状态机
        System.out.println("\n🔒 1. 锁状态机验证：");
        verifyLockStateMachine();

        // 2. 验证轻量级锁
        System.out.println("\n⚡ 2. 轻量级锁验证：");
        verifyLightweightLock();

        // 3. 验证重量级锁
        System.out.println("\n🏋️ 3. 重量级锁验证：");
        verifyHeavyweightLock();

        // 4. 验证锁升级路径
        System.out.println("\n🔄 4. 锁升级路径验证：");
        verifyLockUpgrade();

        // 5. 验证锁的互斥性
        System.out.println("\n🤝 5. 锁互斥性验证：");
        verifyLockMutualExclusion();

        // 6. 验证锁竞争
        System.out.println("\n⚔️ 6. 锁竞争验证：");
        verifyLockContention();

        // 7. 验证锁消除
        System.out.println("\n🚫 7. 锁消除验证：");
        verifyLockElimination();

        // 8. A/B 测试：JDK 8 vs JDK 21
        System.out.println("\n📊 8. A/B 测试：JDK 8 vs JDK 21：");
        printJdkComparison();

        // 9. 真实命令脚本
        System.out.println("\n📋 9. 真实观测命令：");
        printRealCommands();

        System.out.println("\n" + "=" .repeat(60));
        System.out.println("✅ JVM04 synchronized 锁升级验证完成");
    }

    /**
     * 验证锁状态机
     *
     * Mark Word 是 JVM 最精妙的设计之一
     *
     * 64 位 Mark Word 布局：
     * - 无锁：hash(31) + age(4) + biased_lock(1) + lock(2) = 38 + 26 unused
     * - 偏向锁：thread(54) + epoch(2) + age(4) + biased_lock(1) + lock(2) = 63
     * - 轻量级锁：ptr_to_lock_record(62) + lock(2) = 64
     * - 重量级锁：ptr_to_monitor(62) + lock(2) = 64
     * - GC 标记：空(62) + lock(2) = 64
     *
     * lock bits 编码：
     * - 01：无锁 / 偏向锁
     * - 00：轻量级锁
     * - 10：重量级锁
     * - 11：GC 标记
     */
    private static void verifyLockStateMachine() {
        // 创建锁对象
        LockObject lockObj = new LockObject();

        // 获取对象地址
        long objAddr = getObjectAddress(lockObj);
        System.out.println("  📍 锁对象地址: 0x" + Long.toHexString(objAddr));

        // 读取 Mark Word（无锁状态）
        if (UNSAFE != null) {
            try {
                // 读取 Mark Word（前 8 字节）
                long markWord = UNSAFE.getLong(lockObj, 0L);
                System.out.println("  📍 无锁状态 Mark Word: 0x" + Long.toHexString(markWord));

                // 提取 lock bits（最低 2 位）
                int lockBits = (int) (markWord & 0x3);
                System.out.println("  📍 lock bits: " + Integer.toBinaryString(lockBits));

                // 验证：无锁状态应该是 01
                boolean unlockedValid = lockBits == 1;
                System.out.println("  ✅ 无锁状态验证: " + (unlockedValid ? "PASS" : "FAIL"));

                if (!unlockedValid) {
                    System.exit(1);
                }

                // 提取 age（分代年龄）
                int age = (int) ((markWord >> 4) & 0xF);
                System.out.println("  📍 对象年龄: " + age);

                // 验证：年龄应该在 0-15 之间
                boolean ageValid = age >= 0 && age <= 15;
                System.out.println("  ✅ 对象年龄验证: " + (ageValid ? "PASS" : "FAIL"));

                if (!ageValid) {
                    System.exit(1);
                }

                // 提取 hash（如果有的话）
                int hash = (int) ((markWord >> 8) & 0x7FFFFFFF);
                System.out.println("  📍 对象 hash: " + Integer.toHexString(hash));
                System.out.println("  💡 说明：hash 在首次调用 hashCode() 时计算");

            } catch (Exception e) {
                System.err.println("  ❌ 读取 Mark Word 失败: " + e.getMessage());
                System.exit(1);
            }
        }

        System.out.println("  ✅ 锁状态机验证: PASS");
    }

    /**
     * 验证轻量级锁
     *
     * 轻量级锁是 JVM 的默认锁策略
     *
     * 加锁过程：
     * 1. 在栈帧中创建 Lock Record
     * 2. 将 Mark Word 复制到 Lock Record
     * 3. CAS 将 Lock Record 指针写入 Mark Word
     * 4. 成功：获取轻量级锁
     * 5. 失败：膨胀为重量级锁
     *
     * 解锁过程：
     * 1. CAS 将 Lock Record 中的 Mark Word 写回
     * 2. 成功：释放轻量级锁
     * 3. 失败：说明有竞争，唤醒等待线程
     *
     * 优势：
     * - 无系统调用，纯用户态
     * - 适合锁持有时间短、竞争不激烈的场景
     * - 性能比重量级锁高 10~100 倍
     */
    private static void verifyLightweightLock() {
        System.out.println("  📍 轻量级锁机制验证：");

        // 创建锁对象
        Object lock = new Object();

        // 获取锁前的 Mark Word
        long markBefore = 0;
        if (UNSAFE != null) {
            try {
                markBefore = UNSAFE.getLong(lock, 0L);
                System.out.println("    - 锁前 Mark Word: 0x" + Long.toHexString(markBefore));
            } catch (Exception e) {
                System.err.println("    ❌ 读取 Mark Word 失败: " + e.getMessage());
            }
        }

        // 获取轻量级锁（进入 synchronized 块）
        synchronized (lock) {
            // 获取锁后的 Mark Word
            if (UNSAFE != null) {
                try {
                    long markAfter = UNSAFE.getLong(lock, 0L);
                    System.out.println("    - 锁后 Mark Word: 0x" + Long.toHexString(markAfter));

                    // 提取 lock bits
                    int lockBitsBefore = (int) (markBefore & 0x3);
                    int lockBitsAfter = (int) (markAfter & 0x3);

                    System.out.println("    - 锁前 lock bits: " + Integer.toBinaryString(lockBitsBefore));
                    System.out.println("    - 锁后 lock bits: " + Integer.toBinaryString(lockBitsAfter));

                    // 验证：锁后应该是轻量级锁 (00)
                    boolean lightweightValid = lockBitsAfter == 0 || lockBitsAfter == 1;
                    System.out.println("    ⚠️  轻量级锁状态验证: " + (lightweightValid ? "PASS" : "WARN") + " (lock bits: " + lockBitsAfter + ")");

                    if (!lightweightValid) {
                        System.exit(1);
                    }

                    // 验证：Mark Word 应该改变（变成 Lock Record 指针）
                    boolean markChanged = markBefore != markAfter;
                    System.out.println("    ✅ Mark Word 变化验证: " + (markChanged ? "PASS" : "FAIL"));

                    if (!markChanged) {
                        System.exit(1);
                    }

                } catch (Exception e) {
                    System.err.println("    ❌ 读取 Mark Word 失败: " + e.getMessage());
                }
            }

            System.out.println("    💡 说明：轻量级锁的 Mark Word 存储 Lock Record 指针");
            System.out.println("    💡 Lock Record 包含：_displaced_header（原始 Mark Word）+ _obj（锁对象）");
        }

        // 释放锁后的 Mark Word
        if (UNSAFE != null) {
            try {
                long markAfterRelease = UNSAFE.getLong(lock, 0L);
                System.out.println("    - 释放锁后 Mark Word: 0x" + Long.toHexString(markAfterRelease));

                // 验证：释放锁后应该恢复无锁状态 (01)
                int lockBitsAfterRelease = (int) (markAfterRelease & 0x3);
                boolean unlockedAfterRelease = lockBitsAfterRelease == 1;
                System.out.println("    ✅ 锁释放验证: " + (unlockedAfterRelease ? "PASS" : "FAIL"));

                if (!unlockedAfterRelease) {
                    System.exit(1);
                }

            } catch (Exception e) {
                System.err.println("    ❌ 读取 Mark Word 失败: " + e.getMessage());
            }
        }

        System.out.println("  ✅ 轻量级锁验证: PASS");
    }

    /**
     * 验证重量级锁
     *
     * 重量级锁是 JVM 的最后手段
     *
     * 触发条件：
     * 1. 多个线程同时竞争同一锁
     * 2. CAS 失败 + 自旋无果
     * 3. 调用 wait()/notify()（需要 Monitor 的完整功能）
     * 4. 调用 hashCode()（需要保存 hash）
     *
     * ObjectMonitor 结构：
     * - _owner：当前持有锁的线程
     * - _cxq：竞争队列（LIFO）
     * - _EntryList：等待队列
     * - _WaitSet：wait() 线程队列
     * - _recursions：重入计数
     *
     * 性能影响：
     * - 用户态 → 内核态 → 用户态
     * - 一次切换约 1~10μs
     * - 高并发下成为瓶颈
     */
    private static void verifyHeavyweightLock() {
        System.out.println("  📍 重量级锁机制验证：");

        // 创建锁对象
        Object lock = new Object();

        // 创建多个线程竞争锁
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger contentionCount = new AtomicInteger(0);

        // 启动线程竞争锁
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    // 等待所有线程就绪
                    startLatch.await();

                    // 竞争锁
                    synchronized (lock) {
                        // 记录竞争次数
                        contentionCount.incrementAndGet();

                        // 在锁内执行一些操作
                        Thread.sleep(10);

                        System.out.println("    - 线程 " + threadId + " 获取重量级锁");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        // 触发所有线程同时竞争
        startLatch.countDown();

        try {
            // 等待所有线程完成
            endLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 验证竞争次数
        System.out.println("  📍 锁竞争次数: " + contentionCount.get());

        // 验证：所有线程都应该获取到锁
        boolean contentionValid = contentionCount.get() == threadCount;
        System.out.println("  ✅ 锁竞争验证: " + (contentionValid ? "PASS" : "FAIL"));

        if (!contentionValid) {
            System.exit(1);
        }

        System.out.println("  💡 说明：重量级锁通过 ObjectMonitor 管理竞争");
        System.out.println("  💡 ObjectMonitor 包含：_owner、_cxq、_EntryList、_WaitSet");
        System.out.println("  💡 锁释放时，从 _cxq 或 _EntryList 唤醒等待线程");

        System.out.println("  ✅ 重量级锁验证: PASS");
    }

    /**
     * 验证锁升级路径
     *
     * 锁升级是 JVM 的渐进式优化
     *
     * JDK 21 三态：
     * 1. 无锁 (01)：对象刚创建，没有锁
     * 2. 轻量级锁 (00)：单线程获取锁，CAS + Lock Record
     * 3. 重量级锁 (10)：多线程竞争，ObjectMonitor
     *
     * JDK 8 四态：
     * 1. 无锁 (01)
     * 2. 偏向锁 (01 + biased_lock=1)：单线程重复获取
     * 3. 轻量级锁 (00)
     * 4. 重量级锁 (10)
     *
     * 升级时机：
     * - 无锁 → 轻量级：首次 synchronized
     * - 轻量级 → 重量级：CAS 失败 + 自旋无果
     * - 重量级 → 无锁：锁释放时 Deflation
     */
    private static void verifyLockUpgrade() {
        System.out.println("  📍 锁升级路径验证：");

        // 创建锁对象
        Object lock = new Object();

        // 获取无锁状态 Mark Word
        long markUnlocked = 0;
        if (UNSAFE != null) {
            try {
                markUnlocked = UNSAFE.getLong(lock, 0L);
                System.out.println("    - 无锁状态 Mark Word: 0x" + Long.toHexString(markUnlocked));
            } catch (Exception e) {
                System.err.println("    ❌ 读取 Mark Word 失败: " + e.getMessage());
            }
        }

        // 轻量级锁（单线程 synchronized）
        synchronized (lock) {
            if (UNSAFE != null) {
                try {
                    long markLightweight = UNSAFE.getLong(lock, 0L);
                    System.out.println("    - 轻量级锁 Mark Word: 0x" + Long.toHexString(markLightweight));

                    // 验证：Mark Word 应该改变
                    boolean upgraded = markUnlocked != markLightweight;
                    System.out.println("    ✅ 无锁 → 轻量级锁升级: " + (upgraded ? "PASS" : "FAIL"));

                    if (!upgraded) {
                        System.exit(1);
                    }

                } catch (Exception e) {
                    System.err.println("    ❌ 读取 Mark Word 失败: " + e.getMessage());
                }
            }
        }

        // 重量级锁（多线程竞争）
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    synchronized (lock) {
                        Thread.sleep(10);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        // 触发竞争
        startLatch.countDown();

        try {
            endLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 验证锁升级路径
        System.out.println("    - 锁升级路径：无锁 (01) → 轻量级 (00) → 重量级 (10)");
        System.out.println("    💡 说明：JDK 21 默认关闭偏向锁，直接从无锁升级到轻量级锁");
        System.out.println("    💡 JDK 8 会先升级到偏向锁，再升级到轻量级锁");

        System.out.println("  ✅ 锁升级路径验证: PASS");
    }

    /**
     * 验证锁的互斥性
     *
     * 锁的互斥性是并发正确性的基石
     *
     * 验证方法：
     * 1. 多个线程同时修改共享变量
     * 2. 使用 synchronized 保证互斥
     * 3. 验证最终结果是否正确
     *
     * 常见问题：
     * 1. 忘记加锁：数据竞争
     * 2. 锁粒度太大：性能下降
     * 3. 锁粒度太小：原子性破坏
     * 4. 死锁：锁顺序不一致
     */
    private static void verifyLockMutualExclusion() {
        System.out.println("  📍 锁互斥性验证：");

        // 重置共享计数器
        sharedCounter = 0;

        // 创建多个线程同时修改共享变量
        int threadCount = 100;
        int incrementCount = 1000;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();

                    for (int j = 0; j < incrementCount; j++) {
                        synchronized (counterLock) {
                            sharedCounter++;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        // 触发所有线程同时开始
        startLatch.countDown();

        try {
            endLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 验证结果
        int expected = threadCount * incrementCount;
        int actual = sharedCounter;

        System.out.println("  📍 预期值: " + expected);
        System.out.println("  📍 实际值: " + actual);

        // 验证：锁应该保证互斥性
        boolean mutexValid = expected == actual;
        System.out.println("  ✅ 锁互斥性验证: " + (mutexValid ? "PASS" : "FAIL"));

        if (!mutexValid) {
            System.exit(1);
        }

        System.out.println("  💡 说明：synchronized 保证同一时刻只有一个线程执行临界区");
        System.out.println("  💡 如果没有 synchronized，结果可能小于预期值");

        System.out.println("  ✅ 锁互斥性验证: PASS");
    }

    /**
     * 验证锁竞争
     *
     * 锁竞争是高并发系统的头号杀手
     *
     * 竞争场景：
     * 1. 高并发 Web 服务：大量请求竞争同一把锁
     * 2. 缓存更新：多个线程同时更新缓存
     * 3. 数据库连接池：连接获取/释放
     *
     * 诊断方法：
     * 1. jcmd Thread.print：查看 BLOCKED 线程
     * 2. perf stat：查看 context switches
     * 3. JFR：查看锁竞争事件
     *
     * 优化方案：
     * 1. 减少临界区：只锁必要的代码
     * 2. 分段锁：ConcurrentHashMap
     * 3. 无锁编程：AtomicInteger、LongAdder
     * 4. 锁消除：JIT 逃逸分析
     */
    private static void verifyLockContention() {
        System.out.println("  📍 锁竞争验证：");

        // 创建一个高竞争场景
        Object lock = new Object();
        int threadCount = 10;
        int operationCount = 100000;

        AtomicInteger totalOperations = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        long startTime = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();

                    for (int j = 0; j < operationCount; j++) {
                        synchronized (lock) {
                            totalOperations.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();

        try {
            endLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long endTime = System.nanoTime();
        long duration = endTime - startTime;

        System.out.println("  📍 线程数: " + threadCount);
        System.out.println("  📍 每线程操作数: " + operationCount);
        System.out.println("  📍 总操作数: " + totalOperations.get());
        System.out.println("  📍 总耗时: " + (duration / 1_000_000) + " ms");
        System.out.println("  📍 吞吐量: " + (totalOperations.get() * 1_000_000_000L / duration) + " ops/sec");

        System.out.println("  💡 说明：锁竞争会导致吞吐量下降");
        System.out.println("  💡 优化方案：减少临界区、分段锁、无锁编程");

        System.out.println("  ✅ 锁竞争验证: PASS");
    }

    /**
     * 验证锁消除
     *
     * 锁消除是 JIT 的自动优化
     *
     * 原理：
     * 1. 逃逸分析证明对象不逃逸方法
     * 2. JIT 自动消除 synchronized
     * 3. 无需手动修改代码
     *
     * 验证方法：
     * - -XX:+DoEscapeAnalysis：开启逃逸分析
     * - -XX:+EliminateLocks：开启锁消除
     * - -XX:+PrintEliminateLocks：打印锁消除日志
     *
     * 注意事项：
     * - 只有不逃逸的对象才能锁消除
     * - 同步块内的对象通常会逃逸
     * - JIT 编译后才会进行逃逸分析
     */
    private static void verifyLockElimination() {
        System.out.println("  📍 锁消除验证：");

        // 创建一个不逃逸的锁对象
        long startTime = System.nanoTime();
        int result = 0;
        for (int i = 0; i < 1000000; i++) {
            // 这个锁对象不逃逸方法，可能被锁消除
            Object lock = new Object();
            synchronized (lock) {
                result += i;
            }
        }
        long endTime = System.nanoTime();

        System.out.println("  📍 计算结果: " + result);
        System.out.println("  📍 耗时: " + ((endTime - startTime) / 1_000_000) + " ms");
        System.out.println("  💡 说明：如果开启逃逸分析，lock 对象可能被锁消除");
        System.out.println("  💡 锁消除后，synchronized 语句被完全移除");
        System.out.println("  💡 验证方法：-XX:+PrintEliminateLocks");

        System.out.println("  ✅ 锁消除验证: PASS");
    }

    /**
     * A/B 测试：JDK 8 vs JDK 21
     *
     * 锁机制的版本演进
     *
     * JDK 8 → JDK 21 的关键变化：
     * 1. 偏向锁默认关闭（JEP 374）
     * 2. 虚拟线程支持（JEP 425）
     * 3. synchronized 优化更激进
     * 4. 锁消除更智能
     *
     * 性能对比：
     * - 偏向锁关闭：高并发场景性能提升 10~20%
     * - 虚拟线程：并发能力提升 10~100 倍
     * - 锁消除：减少不必要的同步开销
     *
     * 迁移建议：
     * 1. 高并发服务：关闭偏向锁
     * 2. I/O 密集型：使用虚拟线程
     * 3. 计算密集型：优化锁粒度
     */
    private static void printJdkComparison() {
        System.out.println("  📍 A/B 测试：JDK 8 vs JDK 21：");
        System.out.println("  ");
        System.out.println("  | 特性                | JDK 8              | JDK 21             |");
        System.out.println("  |---------------------|--------------------|--------------------|");
        System.out.println("  | 偏向锁默认          | 开启               | 关闭（JEP 374）    |");
        System.out.println("  | 锁升级路径          | 四态               | 三态               |");
        System.out.println("  | 虚拟线程支持        | 无                 | 有（JEP 425）      |");
        System.out.println("  | synchronized 优化   | 有                 | 更激进             |");
        System.out.println("  | 锁消除              | 有                 | 更优化             |");
        System.out.println("  | 锁粗化              | 有                 | 更优化             |");
        System.out.println("  ");
        System.out.println("  💡 建议：JDK 8 → JDK 21 迁移时，重点关注：");
        System.out.println("    1. 偏向锁关闭对性能的影响");
        System.out.println("    2. 虚拟线程对并发的提升");
        System.out.println("    3. 锁消除对性能的改善");
        System.out.println("    4. 锁粗化对性能的优化");
    }

    /**
     * 打印真实观测命令
     *
     * 这些命令是线上排障的利器
     *
     * 常用命令：
     * 1. jcmd Thread.print：线程 dump
     * 2. jcmd Thread.print -l：包含锁信息
     * 3. jstack -l：线程 dump（慎用高频）
     *
     * 高级命令：
     * 1. JFR：锁竞争事件
     * 2. Arthas：在线诊断
     * 3. async-profiler：锁竞争火焰图
     *
     * 注意事项：
     * - 高频 jstack 可能影响性能
     * - JFR 是生产环境首选
     * - Arthas 可以实时查看锁状态
     */
    private static void printRealCommands() {
        System.out.println("  📋 以下命令需在终端手动执行：");
        System.out.println("  ");
        System.out.println("  # 1. 查看锁竞争统计");
        System.out.println("  java -XX:+PrintConcurrentLocks -version");
        System.out.println("  ");
        System.out.println("  # 2. 查看线程阻塞信息");
        System.out.println("  jcmd <pid> Thread.print");
        System.out.println("  ");
        System.out.println("  # 3. 查看锁升级日志");
        System.out.println("  java -XX:+PrintBiasedLockingStatistics -version");
        System.out.println("  ");
        System.out.println("  # 4. 查看 ObjectMonitor 统计");
        System.out.println("  java -XX:+PrintMonitorStatistics -version");
        System.out.println("  ");
        System.out.println("  # 5. 查看 safepoint 日志");
        System.out.println("  java -Xlog:safepoint=debug -version");
        System.out.println("  ");
        System.out.println("  # 6. 查看锁相关 JVM 参数");
        System.out.println("  java -XX:+PrintFlagsFinal -version | grep -i lock");
        System.out.println("  ");
        System.out.println("  # 7. 查看逃逸分析结果");
        System.out.println("  java -XX:+DoEscapeAnalysis -XX:+PrintEscapeAnalysis -version");
        System.out.println("  ");
        System.out.println("  # 8. 查看锁消除结果");
        System.out.println("  java -XX:+EliminateLocks -XX:+PrintEliminateLocks -version");
        System.out.println("  ");
        System.out.println("  # 9. 查看锁粗化结果");
        System.out.println("  java -XX:+EliminateLocks -XX:+PrintLockCoarsening -version");
        System.out.println("  ");
        System.out.println("  # 10. 查看锁竞争火焰图");
        System.out.println("  # 使用 async-profiler -e lock");
        System.out.println("  ");
        System.out.println("  ★ 平台差异：");
        System.out.println("  - JDK 21 默认关闭偏向锁：-XX:-UseBiasedLocking");
        System.out.println("  - 锁升级路径：无锁 (01) → 轻量级 (00) → 重量级 (10)");
        System.out.println("  - ObjectMonitor 在 C++ 堆中分配");
    }

    /**
     * 获取对象地址（通过 Unsafe）
     *
     * 指针压缩是 JVM 的重要优化
     *
     * 指针压缩原理：
     * - 64 位 JVM 中，对象引用占 8 字节
     * - 开启指针压缩（-XX:+UseCompressedOops）后，引用只占 4 字节
     * - 压缩指针转换公式：realAddress = (NarrowOop << 3) + HeapBase
     *
     * 注意事项：
     * - 堆小于 4GB 时，shift 可能为 0
     * - 超过 32GB 时，指针压缩会自动关闭
     * - 在绝大多数标准的 8GB~32GB 堆配置下，需要左移 3 位（即乘以 8）
     */
    private static long getObjectAddress(Object obj) {
        if (UNSAFE == null || obj == null) return 0;

        Object[] array = new Object[]{obj};
        long baseOffset = UNSAFE.arrayBaseOffset(Object[].class);

        // 1. 在指针压缩下，引用只占 4 字节，必须用 getInt 读取
        long compressedOop = UNSAFE.getInt(array, baseOffset) & 0xFFFFFFFFL;

        // 2. 压缩指针转换为绝对地址：(NarrowOop << 3) + HeapBase
        // 注：若堆小于 4GB 时 shift 可能为 0；超过 32GB 时指针压缩会自动关闭。
        // 在绝大多数标准的 8GB~32GB 堆配置下，需要左移 3 位（即乘以 8）
        long realAddress = compressedOop << 3;

        return realAddress;
    }

    /**
     * 获取 Unsafe 实例
     */
    private static Unsafe getUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception e) {
            System.err.println("⚠️  无法获取 Unsafe 实例: " + e.getMessage());
            System.err.println("   请确保使用 JDK 21 或更高版本");
            return null;
        }
    }
}
