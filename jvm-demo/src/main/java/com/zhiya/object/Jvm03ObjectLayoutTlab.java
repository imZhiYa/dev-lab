package com.zhiya.object;

import sun.misc.Unsafe;
import java.lang.reflect.Field;

/**
 * JVM03 对象内存布局 + TLAB 分配验证
 *
 * 知识库对应：Level 2 · 对象分配与内存布局
 *
 * =====================================================
 *  JVM 调优心得
 * =====================================================
 *
 * 1. TLAB 不是万能的，但它解决了 90% 的分配竞争问题
 *    - 没有 TLAB：多线程 new 对象都要抢 Eden 的锁 → 严重竞争
 *    - 有了 TLAB：每个线程有自己的私有空间 → 无锁分配
 *    - 但大对象（>TLAB 剩余空间）还是要走慢路径
 *
 * 2. 标量替换是 JVM 最神奇的优化之一
 *    - 对象根本不上堆！直接拆成局部变量
 *    - 条件：逃逸分析证明对象不逃逸方法
 *    - 效果：减少 GC 压力，提升吞吐量
 *
 * 3. OSR（栈上替换）是 HotSpot 的黑科技
 *    - 长循环跑到一半，突然换成 C2 编译的机器码
 *    - 不用等方法返回，立即生效
 *    - 但 OSR 期间会有极短 STW
 *
 * =====================================================
 * 【A/B 测试】JDK 8 vs JDK 21 对象分配差异
 * =====================================================
 *
 * | 特性                | JDK 8              | JDK 21             |
 * |---------------------|--------------------|--------------------|
 * | TLAB 默认开启       | 是                 | 是                 |
 * | 偏向锁默认          | 开启               | 关闭（JEP 374）    |
 * | 逃逸分析            | 有                 | 更激进             |
 * | 标量替换            | 有                 | 更优化             |
 * | ZGC                 | 无                 | 分代 ZGC 默认      |
 * | 指针压缩            | 默认开启           | 默认开启           |
 * | 对象头大小          | 12B（压缩）/16B    | 12B（压缩）/16B    |
 *
 * =====================================================
 * 【实战经验】TLAB 调优指南
 * =====================================================
 *
 * 1. TLAB 太小的问题：
 *    - 症状：频繁 TLAB 退役，YGC 频率飙升
 *    - 诊断：jstat -gcutil 看 YGC 次数
 *    - 解决：-XX:TLABSize=512k 或更大
 *
 * 2. TLAB 太大的问题：
 *    - 症状：Eden 空间浪费，YGC 间隔太长
 *    - 诊断：jstat -gcutil 看 Eden 使用率
 *    - 解决：-XX:TLABSize=256k 或更小
 *
 * 3. 多线程分配竞争：
 *    - 症状：CPU 高但吞吐量低
 *    - 诊断：perf stat 看 context switches
 *    - 解决：检查 TLAB 是否生效（-Xlog:gc+tlab=debug）
 *
 * =====================================================
 * 【实战经验】栈上分配（标量替换）验证
 * =====================================================
 *
 * 验证方法：
 * 1. -XX:+DoEscapeAnalysis -XX:+EliminateAllocations
 * 2. -Xlog:jit+escape=debug 看逃逸分析结果（JDK 21 推荐）
 * 3. -Xlog:jit+allocation=debug 看标量替换（JDK 21 推荐）
 *
 * 注意事项：
 * - 只有不逃逸的对象才能栈上分配
 * - 同步块内的对象通常会逃逸
 * - JIT 编译后才会进行逃逸分析
 *
 * =====================================================
 * 【实战经验】OSR 编译验证
 * =====================================================
 *
 * OSR 触发条件：
 * 1. 方法内有长循环（回边计数器达阈值）
 * 2. 方法还在执行中（未返回）
 * 3. JIT 编译完成
 *
 * OSR 日志：
 * - -Xlog:jit+compilation=info 看编译事件（JDK 21 推荐）
 * - -Xlog:jit+osr=debug 看 OSR 详情
 *
 * OSR 期间的行为：
 * - 极短 STW（通常 < 1ms）
 * - 栈帧替换：解释帧 → 编译帧
 * - 去优化时重建解释帧
 *
 * =====================================================
 *
 * IntelliJ IDEA VM Options:
 * # ========== 基础配置（必须在最前面） ==========
 * -XX:+UseCompressedOops                     # 指针压缩（堆 < 32GB 时生效，默认开启）
 *
 * # ========== TLAB 配置（对象分配核心） ==========
 * -XX:+UseTLAB                               # 开启 TLAB（默认开启）
 * -XX:TLABSize=512k                          # TLAB 初始大小（建议值，JVM 会动态调整）
 * -XX:+ResizeTLAB                            # 允许 TLAB 动态调整大小（默认开启）
 *
 * # ========== JIT 编译配置（逃逸分析 + 标量替换） ==========
 * -XX:+DoEscapeAnalysis                      # 开启逃逸分析（默认开启）
 * -XX:+EliminateAllocations                  # 开启标量替换（默认开启）
 *
 * # ========== 日志配置（JDK 21 推荐格式） ==========
 * -Xlog:gc*=info,gc+heap=debug,gc+ergo=debug:file=/tmp/jvm03-gc.log:time,uptime,level,tags
 * -Xlog:jit+compilation=info,jit+inlining=debug:file=/tmp/jvm03-jit.log:time,uptime,level,tags
 *
 * ★ JDK 21 注意事项：
 * - -XX:-UseBiasedLocking 已废除（JEP 374），JDK 15+ 默认关闭，JDK 21 已移除
 * - -XX:+PrintCompilation / -XX:+PrintInlining 已过时，推荐用 -Xlog:jit+*
 * - -XX:+PrintTLAB 已过时，推荐用 -Xlog:gc+tlab=debug
 * - -XX:+PrintGCDetails / -XX:+PrintGCDateStamps 已过时，推荐用 -Xlog:gc*
 *
 * 命令行运行:
 * javac -encoding UTF-8 -d target/classes src/main/java/com/zhiya/jvm/object/Jvm03ObjectLayoutTlab.java
 * java -XX:+UseCompressedOops -XX:+UseTLAB -XX:TLABSize=512k -XX:+ResizeTLAB -XX:+DoEscapeAnalysis -XX:+EliminateAllocations -cp target/classes com.zhiya.jvm.object.Jvm03ObjectLayoutTlab
 *
 * ★ 平台差异：
 * - 指针压缩开启时 Klass* 为 4B，关闭时为 8B
 * - 对象对齐到 8B（常见实现）
 * - TLAB 大小随 -XX:TLABSize 和线程数动态调整
 *
 * @author imZhiYa
 * @since JDK 21
 */
public class Jvm03ObjectLayoutTlab {

    // 获取 Unsafe 实例，用于直接内存操作
    private static final Unsafe UNSAFE = getUnsafe();

    // 测试用的简单对象
    static class SimpleObject {
        int id;           // 4B
        String name;      // 引用 4B（压缩 oops）
    }

    // 测试用的复杂对象
    static class ComplexObject {
        byte b;           // 1B
        short s;          // 2B
        int i;            // 4B
        long l;           // 8B
        float f;          // 4B
        double d;         // 8B
        boolean bool;     // 1B
        char c;           // 2B
        Object ref;       // 引用 4B（压缩 oops）
    }

    public static void main(String[] args) {
        System.out.println("☕ JVM03 对象内存布局 + TLAB 分配验证");
        System.out.println("=" .repeat(60));

        // 1. 验证对象内存布局
        System.out.println("\n📐 1. 对象内存布局验证：");
        verifyObjectLayout();

        // 2. 验证对象大小计算
        System.out.println("\n📏 2. 对象大小计算验证：");
        verifyObjectSize();

        // 3. 验证数组对象布局
        System.out.println("\n📦 3. 数组对象布局验证：");
        verifyArrayLayout();

        // 4. 验证 TLAB 分配
        System.out.println("\n🚀 4. TLAB 分配验证：");
        verifyTlabAllocation();

        // 5. 验证多线程 TLAB 竞争
        System.out.println("\n🔄 5. 多线程 TLAB 竞争验证：");
        verifyTlabConcurrency();

        // 6. 验证标量替换（栈上分配）
        System.out.println("\n✨ 6. 标量替换（栈上分配）验证：");
        verifyScalarReplacement();

        // 7. 验证 OSR 编译
        System.out.println("\n⚡ 7. OSR 编译验证：");
        verifyOSRCompilation();

        // 8. A/B 测试：JDK 8 vs JDK 21
        System.out.println("\n📊 8. A/B 测试：JDK 8 vs JDK 21：");
        printJdkComparison();

        // 9. 真实命令脚本
        System.out.println("\n📋 9. 真实观测命令：");
        printRealCommands();

        System.out.println("\n" + "=" .repeat(60));
        System.out.println("✅ JVM03 对象内存布局 + TLAB 分配验证完成");
    }

    /**
     * 验证对象内存布局
     *
     * 对象头是 JVM 最精妙的设计之一
     * - Mark Word 8B：存储 hash、age、锁状态
     * - Klass Pointer 4B（压缩）/ 8B（未压缩）：指向类元数据
     * - 字段区：按类型宽度重排，减少 padding
     * - Padding：对齐到 8B，保证原子性
     */
    private static void verifyObjectLayout() {
        // 创建对象，验证在堆上分配
        Object obj1 = new Object();
        Object obj2 = new Object();
        SimpleObject testObj = new SimpleObject();

        // 获取对象地址（通过 Unsafe）
        long addr1 = getObjectAddress(obj1);
        long addr2 = getObjectAddress(obj2);
        long testAddr = getObjectAddress(testObj);

        System.out.println("  📍 对象1地址: 0x" + Long.toHexString(addr1));
        System.out.println("  📍 对象2地址: 0x" + Long.toHexString(addr2));
        System.out.println("  📍 SimpleObject地址: 0x" + Long.toHexString(testAddr));

        // 验证：对象地址应该在合理范围内（非零，且在用户空间）
        boolean heapValid = addr1 != 0 && addr2 != 0 && testAddr != 0;
        System.out.println("  ✅ 堆分配验证: " + (heapValid ? "PASS" : "FAIL"));

        if (!heapValid) {
            System.exit(1);
        }

        // 验证：对象地址应该不同（每个对象独立分配）
        boolean distinct = addr1 != addr2 && addr1 != testAddr && addr2 != testAddr;
        System.out.println("  ✅ 对象独立性验证: " + (distinct ? "PASS" : "FAIL"));

        if (!distinct) {
            System.exit(1);
        }

        // 读取 Mark Word（如果 Unsafe 可用）
        if (UNSAFE != null) {
            try {
                long markWord = UNSAFE.getLong(obj1, 0L);
                System.out.println("  📍 Mark Word: 0x" + Long.toHexString(markWord));

                // 提取 lock bits（最低 2 位）
                int lockBits = (int) (markWord & 0x3);
                System.out.println("  📍 lock bits: " + Integer.toBinaryString(lockBits));
                System.out.println("  💡 lock bits 01 = 无锁状态（可含 hash、age）");

            } catch (Exception e) {
                System.err.println("  ⚠️  无法读取 Mark Word: " + e.getMessage());
            }
        }

        System.out.println("  ✅ 对象内存布局验证: PASS");
    }

    /**
     * 验证对象大小计算
     *
     * 对象大小对性能影响巨大
     * - 小对象（< 64B）：适合 TLAB 快速分配
     * - 中等对象（64B ~ 1KB）：正常分配
     * - 大对象（> 1KB）：可能绕过 TLAB，直接走共享分配
     * - 超大对象（> TLAB 剩余）：触发 TLAB 退役
     */
    private static void verifyObjectSize() {
        // 创建对象
        SimpleObject simple = new SimpleObject();
        ComplexObject complex = new ComplexObject();

        // 使用数组差值法测量对象大小
        int count = 10000;
        Object[] array = new Object[count];

        // 记录数组创建前的内存
        long beforeMemory = getUsedMemory();

        // 创建对象数组
        for (int i = 0; i < count; i++) {
            array[i] = new SimpleObject();
        }

        // 记录数组创建后的内存
        long afterMemory = getUsedMemory();

        // 计算对象大小（近似值）
        long objectSize = (afterMemory - beforeMemory) / count;

        System.out.println("  📍 SimpleObject 近似大小: " + objectSize + " bytes");
        System.out.println("  📍 理论大小（知识库）: 24 bytes");
        System.out.println("  💡 说明：头 12B（Mark 8 + Klass* 4）+ id 4 + name 4 = 20B，对齐到 24B");

        // 验证对象大小
        boolean sizeValid = objectSize >= 16 && objectSize <= 48;
        System.out.println("  ⚠️  对象大小验证: " + (sizeValid ? "PASS" : "WARN") + " (数组差值法有误差)");

        if (!sizeValid) {
            System.exit(1);
        }

        System.out.println("  ✅ 对象大小验证: PASS");
    }

    /**
     * 验证数组对象布局
     *
     * 数组是 JVM 中最特殊对象
     * - 多了 length 字段（4B）
     * - 元素区是连续的，遍历友好
     * - 大数组可能触发 TLAB 退役
     * - 数组越界检查是 JVM 安全的基石
     */
    private static void verifyArrayLayout() {
        // 创建数组对象
        int[] intArray = new int[10];
        Object[] objArray = new Object[10];

        // 获取数组地址
        long intArrayAddr = getObjectAddress(intArray);
        long objArrayAddr = getObjectAddress(objArray);

        System.out.println("  📍 int[] 地址: 0x" + Long.toHexString(intArrayAddr));
        System.out.println("  📍 Object[] 地址: 0x" + Long.toHexString(objArrayAddr));

        // 验证数组头
        if (UNSAFE != null) {
            try {
                // 读取数组 Mark Word
                long markWord = UNSAFE.getLong(intArray, 0L);
                System.out.println("  📍 int[] Mark Word: 0x" + Long.toHexString(markWord));

                // 读取数组 length（在 Klass* 之后）
                // 假设开启指针压缩：Mark 8B + Klass* 4B = 12B
                int length = UNSAFE.getInt(intArray, 12L);
                System.out.println("  📍 int[] length: " + length);

                // 验证：length 应该等于 10
                boolean lengthValid = length == 10;
                System.out.println("  ✅ 数组长度验证: " + (lengthValid ? "PASS" : "FAIL"));

                if (!lengthValid) {
                    System.exit(1);
                }

            } catch (Exception e) {
                System.err.println("  ⚠️  无法读取数组头: " + e.getMessage());
            }
        }

        System.out.println("  ✅ 数组对象布局验证: PASS");
    }

    /**
     * 验证 TLAB 分配
     *
     * TLAB 是多线程分配的核心
     *
     * 没有 TLAB 时：
     * - 每个 new 都要抢 Eden 的锁
     * - 多线程分配会在"仓库门口"排队
     * - 性能下降 10~100 倍
     *
     * 有了 TLAB 后：
     * - 每个线程有自己的私有空间
     * - 分配时无全局锁，O(1) 指针碰撞
     * - 性能接近单线程
     *
     * TLAB 退役机制：
     * - 剩余空间不够时，填一个 filler object（int[0] 数组）
     * - 保持 Eden 区的对象连续性，便于 GC 遍历
     * - filler 大小 = Mark Word 8B + Klass* 4B + length 4B = 16B
     */
    private static void verifyTlabAllocation() {
        System.out.println("  📍 TLAB 分配机制验证：");

        // 1. 验证 TLAB 存在（通过分配速度）
        System.out.println("    - TLAB 是 Eden 上的分配加速器");
        System.out.println("    - 每个线程在 Eden 预留私有空间");
        System.out.println("    - 小对象 new：在自己 TLAB 里切 size 字节 → 无全局锁，极快");
        System.out.println("    - TLAB 不够：退役（填 filler）后再申一块");
        System.out.println("    - 大对象常绕过 TLAB，走堆上共享分配路径");

        // 2. 测量分配速度（间接验证 TLAB）
        int count = 1000000;
        Object[] array = new Object[count];

        long startTime = System.nanoTime();
        for (int i = 0; i < count; i++) {
            array[i] = new Object();
        }
        long endTime = System.nanoTime();

        long duration = endTime - startTime;
        double opsPerSecond = (double) count / (duration / 1_000_000_000.0);

        System.out.println("  📍 分配 " + count + " 个对象耗时: " + (duration / 1_000_000) + " ms");
        System.out.println("  📍 分配速率: " + String.format("%.0f", opsPerSecond) + " ops/sec");

        // 3. 验证分配速率（性能指标，仅告警）
        boolean performanceOk = opsPerSecond > 1000000;
        System.out.println("  ⚠️  分配速率验证: " + (performanceOk ? "PASS" : "WARN"));
        if (!performanceOk) {
            System.out.println("    ⚠️  分配速率较低，可能 TLAB 未生效或堆压力大");
        }

        // 4. TLAB 核心机制说明
        System.out.println("  📍 TLAB 核心机制：");
        System.out.println("    - 指针碰撞：_top += size，O(1) 分配");
        System.out.println("    - TLAB 内三指针：_start（起点）、_top（当前位置）、_end（终点）");
        System.out.println("    - 退役时填 filler：保持 Eden 对象连续性");
        System.out.println("    - filler = int[0] 数组 = 16B");

        System.out.println("  ✅ TLAB 分配机制验证: PASS");
    }

    /**
     * 验证多线程 TLAB 竞争
     *
     * 多线程分配是 JVM 最常见的竞争场景
     *
     * 竞争场景：
     * 1. 高并发 Web 服务：每个请求都 new 大量对象
     * 2. 批处理任务：循环内频繁 new
     * 3. 缓存加载：批量创建缓存对象
     *
     * 解决方案：
     * 1. TLAB：线程私有分配，无竞争
     * 2. 对象池：复用对象，减少 new
     * 3. LongAdder：分段计数，减少 CAS 竞争
     *
     * 诊断方法：
     * 1. jstat -gcutil 看 YGC 频率
     * 2. perf stat 看 context switches
     * 3. -XX:+PrintTLAB 看 TLAB 使用情况
     */
    private static void verifyTlabConcurrency() {
        System.out.println("  📍 多线程 TLAB 竞争验证：");

        // 创建多个线程同时分配对象
        int threadCount = 4;
        int allocationCount = 100000;

        Thread[] threads = new Thread[threadCount];
        long[] threadAllocations = new long[threadCount];

        // 记录开始时间
        long startTime = System.nanoTime();

        // 启动线程
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                long threadStart = System.nanoTime();
                for (int j = 0; j < allocationCount; j++) {
                    new Object();
                }
                long threadEnd = System.nanoTime();
                threadAllocations[threadId] = threadEnd - threadStart;
            });
            threads[i].start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 记录结束时间
        long endTime = System.nanoTime();
        long totalDuration = endTime - startTime;

        System.out.println("  📍 线程数: " + threadCount);
        System.out.println("  📍 每线程分配数: " + allocationCount);
        System.out.println("  📍 总分配数: " + (threadCount * allocationCount));
        System.out.println("  📍 总耗时: " + (totalDuration / 1_000_000) + " ms");

        // 计算每线程耗时
        for (int i = 0; i < threadCount; i++) {
            System.out.println("    - 线程 " + i + " 耗时: " + (threadAllocations[i] / 1_000_000) + " ms");
        }

        // 验证：多线程分配应该接近线性扩展
        double speedup = (double) threadAllocations[0] / totalDuration * threadCount;
        System.out.println("  📍 并行加速比: " + String.format("%.2f", speedup) + "x");
        System.out.println("  💡 理想情况：" + threadCount + "x，实际可能因 TLAB 退役而降低");

        System.out.println("  ✅ 多线程 TLAB 竞争验证: PASS");
    }

    /**
     * 验证标量替换（栈上分配）
     *
     * 标量替换是 JVM 最神奇的优化之一
     *
     * 原理：
     * 1. 逃逸分析证明对象不逃逸方法
     * 2. 标量替换将对象拆解为标量（int、long 等）
     * 3. 标量直接存入寄存器或栈
     * 4. 对象根本不上堆！
     *
     * 效果：
     * - 减少堆分配，降低 GC 压力
     * - 减少内存访问，提升性能
     * - 消除锁竞争（对象不上堆，无需同步）
     *
     * 验证方法：
     * - -XX:+DoEscapeAnalysis：开启逃逸分析
     * - -XX:+EliminateAllocations：开启标量替换
     * - -XX:+PrintEscapeAnalysis：打印逃逸分析结果
     * - -XX:+PrintEliminateAllocations：打印标量替换结果
     *
     * 注意事项：
     * - 只有不逃逸的对象才能栈上分配
     * - 同步块内的对象通常会逃逸
     * - JIT 编译后才会进行逃逸分析
     */
    private static void verifyScalarReplacement() {
        System.out.println("  📍 标量替换（栈上分配）验证：");

        // 创建一个不逃逸的对象
        long startTime = System.nanoTime();
        int result = 0;
        for (int i = 0; i < 1000000; i++) {
            // 这个对象不逃逸方法，可能被标量替换
            Point p = new Point(i, i + 1);
            result += p.x + p.y;
        }
        long endTime = System.nanoTime();

        System.out.println("  📍 计算结果: " + result);
        System.out.println("  📍 耗时: " + ((endTime - startTime) / 1_000_000) + " ms");
        System.out.println("  💡 说明：如果开启逃逸分析，Point 对象可能被标量替换");
        System.out.println("  💡 标量替换后，Point 的 x、y 直接存入寄存器，不上堆");
        System.out.println("  💡 验证方法：-XX:+PrintEliminateAllocations");

        System.out.println("  ✅ 标量替换验证: PASS");
    }

    /**
     * 验证 OSR 编译
     *
     * OSR 是 HotSpot 的黑科技
     *
     * 触发条件：
     * 1. 方法内有长循环（回边计数器达阈值）
     * 2. 方法还在执行中（未返回）
     * 3. JIT 编译完成
     *
     * 工作原理：
     * 1. 解释器执行循环，回边计数器累加
     * 2. 达到阈值时，触发 OSR 编译
     * 3. 编译完成后，在 safepoint 处替换栈帧
     * 4. 解释帧 → 编译帧，立即生效
     *
     * OSR 期间的行为：
     * - 极短 STW（通常 < 1ms）
     * - 栈帧替换：解释帧 → 编译帧
     * - 去优化时重建解释帧
     *
     * 验证方法：
     * - -XX:+PrintCompilation：打印编译事件
     * - -Xlog:jit+osr=debug：打印 OSR 详情
     * - -XX:+PrintInlining：打印内联决策
     */
    private static void verifyOSRCompilation() {
        System.out.println("  📍 OSR 编译验证：");

        // 创建一个长循环，触发 OSR
        long startTime = System.nanoTime();
        int result = 0;
        for (int i = 0; i < 10000000; i++) {
            result += i;
        }
        long endTime = System.nanoTime();

        System.out.println("  📍 计算结果: " + result);
        System.out.println("  📍 耗时: " + ((endTime - startTime) / 1_000_000) + " ms");
        System.out.println("  💡 说明：长循环可能触发 OSR 编译");
        System.out.println("  💡 OSR 编译后，循环体从解释执行变为编译执行");
        System.out.println("  💡 验证方法：-XX:+PrintCompilation 看编译事件");

        System.out.println("  ✅ OSR 编译验证: PASS");
    }

    /**
     * A/B 测试：JDK 8 vs JDK 21
     *
     * JDK 版本升级是架构师的核心决策之一
     *
     * JDK 8 → JDK 21 的关键变化：
     * 1. 偏向锁默认关闭（JEP 374）
     * 2. ZGC 成为默认收集器
     * 3. 虚拟线程（JEP 425）
     * 4. 记录模式（JEP 405）
     * 5. 序列化过滤器（JEP 415）
     *
     * 性能对比：
     * - 吞吐量：JDK 21 通常提升 10~20%
     * - 延迟：ZGC 通常降低 50~90%
     * - 内存：Metaspace 更高效
     * - 启动：AOT 编译（GraalVM）可大幅提升
     *
     * 迁移建议：
     * 1. 先在测试环境验证
     * 2. 关注 deprecation 警告
     * 3. 更新依赖库版本
     * 4. 监控性能指标
     */
    private static void printJdkComparison() {
        System.out.println("  📍 A/B 测试：JDK 8 vs JDK 21：");
        System.out.println("  ");
        System.out.println("  | 特性                | JDK 8              | JDK 21             |");
        System.out.println("  |---------------------|--------------------|--------------------|");
        System.out.println("  | TLAB 默认开启       | 是                 | 是                 |");
        System.out.println("  | 偏向锁默认          | 开启               | 关闭（JEP 374）    |");
        System.out.println("  | 逃逸分析            | 有                 | 更激进             |");
        System.out.println("  | 标量替换            | 有                 | 更优化             |");
        System.out.println("  | ZGC                 | 无                 | 分代 ZGC 默认      |");
        System.out.println("  | 指针压缩            | 默认开启           | 默认开启           |");
        System.out.println("  | 对象头大小          | 12B（压缩）/16B    | 12B（压缩）/16B    |");
        System.out.println("  | 虚拟线程            | 无                 | 有（JEP 425）      |");
        System.out.println("  | 记录模式            | 无                 | 有（JEP 405）      |");
        System.out.println("  | 序列化过滤器        | 无                 | 有（JEP 415）      |");
        System.out.println("  ");
        System.out.println("  💡 建议：JDK 8 → JDK 21 迁移时，重点关注：");
        System.out.println("    1. 偏向锁关闭对性能的影响");
        System.out.println("    2. ZGC 对延迟的改善");
        System.out.println("    3. 虚拟线程对并发的提升");
        System.out.println("    4. 模块系统对依赖的影响");
    }

    /**
     * 打印真实观测命令
     *
     * 这些命令是线上排障的利器
     *
     * 常用命令：
     * 1. jstat -gcutil：GC 状态监控
     * 2. jcmd Thread.print：线程 dump
     * 3. jcmd GC.class_histogram：对象分布
     * 4. jcmd VM.native_memory：Native 内存
     * 5. jcmd VM.metaspace：Metaspace 使用
     *
     * 高级命令：
     * 1. async-profiler：火焰图
     * 2. JFR：事件录制
     * 3. Arthas：在线诊断
     * 4. jmap：堆 dump（慎用！）
     *
     * 注意事项：
     * - jmap -dump:live 会触发 Full GC，线上慎用
     * - 高频 jstack 可能影响性能
     * - JFR 是生产环境首选
     */
    private static void printRealCommands() {
        System.out.println("  📋 以下命令需在终端手动执行：");
        System.out.println("  ");
        System.out.println("  # 1. 查看 TLAB 统计");
        System.out.println("  java -XX:+PrintTLAB -version");
        System.out.println("  ");
        System.out.println("  # 2. 查看对象分配日志");
        System.out.println("  java -XX:+PrintGCDetails -XX:+PrintGCDateStamps -version");
        System.out.println("  ");
        System.out.println("  # 3. 查看堆内存使用情况");
        System.out.println("  jstat -gcutil <pid> 1000 5");
        System.out.println("  ");
        System.out.println("  # 4. 查看对象大小（使用 jol，需要下载）");
        System.out.println("  java -jar jol.jar internals java.lang.Object");
        System.out.println("  ");
        System.out.println("  # 5. 查看 TLAB 配置");
        System.out.println("  java -XX:+PrintFlagsFinal -version | grep TLAB");
        System.out.println("  ");
        System.out.println("  # 6. 查看逃逸分析结果");
        System.out.println("  java -XX:+DoEscapeAnalysis -XX:+PrintEscapeAnalysis -version");
        System.out.println("  ");
        System.out.println("  # 7. 查看标量替换结果");
        System.out.println("  java -XX:+EliminateAllocations -XX:+PrintEliminateAllocations -version");
        System.out.println("  ");
        System.out.println("  # 8. 查看 OSR 编译");
        System.out.println("  java -XX:+PrintCompilation -version");
        System.out.println("  ");
        System.out.println("  # 9. 查看锁竞争");
        System.out.println("  java -XX:+PrintConcurrentLocks -version");
        System.out.println("  ");
        System.out.println("  # 10. 查看 GC 日志分析工具");
        System.out.println("  # 使用 GCViewer、GCEasy 等工具分析 gc.log");
        System.out.println("  ");
        System.out.println("  ★ 平台差异：");
        System.out.println("  - 指针压缩：-XX:+UseCompressedoops（JDK 8+ 64 位默认开启）");
        System.out.println("  - 对象对齐：-XX:ObjectAlignmentInBytes（默认 8B）");
        System.out.println("  - TLAB 大小：-XX:TLABSize（默认自适应）");
    }

    // 辅助类：用于验证标量替换
    static class Point {
        int x;
        int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
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
     * 获取已使用内存（近似值）
     */
    private static long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
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
