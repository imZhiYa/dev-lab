package com.zhiya.gc;

import java.util.ArrayList;
import java.util.List;

/**
 * JVM06 监控演示程序
 *
 * 这个程序会持续运行一段时间，用于演示如何使用 jstat、jcmd 等工具监控 JVM。
 *
 * =====================================================
 *  JVM 监控治理心得
 * =====================================================
 *
 * 核心认知：监控是 JVM 调优的眼睛
 *
 * 1. 监控三要素
 *    - 指标（Metrics）：可量化的数据
 *    - 日志（Logs）：事件记录
 *    - 追踪（Traces）：请求链路
 *
 * 2. 监控分层
 *    - 系统层：CPU、内存、磁盘、网络
 *    - JVM 层：堆、栈、GC、线程
 *    - 应用层：QPS、RT、错误率
 *    - 业务层：订单量、支付成功率
 *
 * 3. 监控工具链
 *    - 基础监控：Prometheus + Grafana
 *    - JVM 监控：JFR + JMC
 *    - 链路追踪：SkyWalking / Jaeger
 *    - 日志聚合：ELK / Loki
 *
 * =====================================================
 * 【A/B 测试】JDK 8 vs JDK 21 监控差异
 * =====================================================
 *
 * | 特性                | JDK 8                | JDK 21               | 影响                |
 * |---------------------|----------------------|----------------------|---------------------|
 * | GC 日志格式         | -XX:+PrintGCDetails  | -Xlog:gc*            | 更统一的日志格式    |
 * | JFR                 | 商业版               | 开源版               | 更广泛的应用        |
 * | 虚拟线程监控        | 无                   | 有                   | 更细粒度的线程监控  |
 * | 容器感知            | 有限                 | 完整                 | 更准确的资源监控    |
 *
 * =====================================================
 * 【实战经验】JVM 监控指标
 * =====================================================
 *
 * 1. 堆内存指标
 *    - Eden 使用率：jstat -gcutil <pid> | awk '{print $3}'
 *    - Survivor 使用率：jstat -gcutil <pid> | awk '{print $1, $2}'
 *    - Old 使用率：jstat -gcutil <pid> | awk '{print $4}'
 *    - Metaspace 使用率：jstat -gcutil <pid> | awk '{print $5}'
 *
 * 2. GC 指标
 *    - YGC 次数：jstat -gcutil <pid> | awk '{print $7}'
 *    - YGC 耗时：jstat -gcutil <pid> | awk '{print $8}'
 *    - FGC 次数：jstat -gcutil <pid> | awk '{print $9}'
 *    - FGC 耗时：jstat -gcutil <pid> | awk '{print $10}'
 *
 * 3. 线程指标
 *    - 线程数：jcmd <pid> Thread.print | grep -c "tid="
 *    - BLOCKED 线程：jcmd <pid> Thread.print | grep -c "BLOCKED"
 *    - WAITING 线程：jcmd <pid> Thread.print | grep -c "WAITING"
 *
 * 4. 类加载指标
 *    - 已加载类数：jcmd <pid> VM.classloader_stats | grep "loaded"
 *    - 已卸载类数：jcmd <pid> VM.classloader_stats | grep "unloaded"
 *
 * =====================================================
 * 【实战经验】监控告警规则
 * =====================================================
 *
 * 1. 堆内存告警
 *    - 规则：Old 使用率 > 80%
 *    - 动作：检查内存泄漏
 *    - 工具：JFR OldObjectSample
 *
 * 2. GC 告警
 *    - 规则：FGC 频率 > 1 次/分钟
 *    - 动作：检查内存泄漏 / 增加堆大小
 *    - 工具：GCViewer / GCEasy
 *
 * 3. 线程告警
 *    - 规则：BLOCKED 线程 > 10
 *    - 动作：检查锁竞争
 *    - 工具：jcmd Thread.print
 *
 * 4. Metaspace 告警
 *    - 规则：Metaspace 使用率 > 80%
 *    - 动作：检查 ClassLoader 泄漏
 *    - 工具：jcmd VM.classloader_stats
 *
 * =====================================================
 * 【实战经验】监控最佳实践
 * =====================================================
 *
 * 1. 监控粒度
 *    - 开发环境：详细日志 + 实时监控
 *    - 测试环境：关键指标 + 告警
 *    - 生产环境：最小化监控 + 告警
 *
 * 2. 监控频率
 *    - 系统指标：1 秒
 *    - JVM 指标：5 秒
 *    - 应用指标：1 分钟
 *    - 业务指标：5 分钟
 *
 * 3. 监控存储
 *    - 短期存储：内存（实时监控）
 *    - 中期存储：磁盘（日志分析）
 *    - 长期存储：对象存储（历史分析）
 *
 * =====================================================
 * 【实战经验】虚拟线程监控
 * =====================================================
 *
 * JDK 21 虚拟线程监控：
 * 1. 虚拟线程数：jcmd <pid> Thread.print | grep -c "VirtualThread"
 * 2. 载体线程数：jcmd <pid> Thread.print | grep -c "CarrierThread"
 * 3. 挂载/解绑事件：-Xlog:mount=debug
 *
 * 虚拟线程调优：
 * 1. 避免在 synchronized 内做阻塞 I/O
 * 2. 使用 ReentrantLock 替代 synchronized
 * 3. 使用 Semaphore 限制并发数
 *
 * =====================================================
 *
 * IntelliJ IDEA VM Options:
 * -Xms128m -Xmx128m -XX:NativeMemoryTracking=summary -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -Xlog:gc*,gc+heap=info,safepoint=info:file=/tmp/jvm06-gc.log:time,uptime,level,tags
 *
 * 命令行运行:
 * javac -encoding UTF-8 -d target/classes src/main/java/com/zhiya/jvm/gc/Jvm06MonitoringDemo.java
 * java -Xms128m -Xmx128m -XX:NativeMemoryTracking=summary -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -Xlog:gc*,gc+heap=info,safepoint=info:file=/tmp/jvm06-gc.log:time,uptime,level,tags -cp target/classes com.zhiya.jvm.gc.Jvm06MonitoringDemo
 *
 * 参数说明：
 * - -Xms128m -Xmx128m：堆大小 128MB（固定大小，便于观察 GC）
 * - -XX:NativeMemoryTracking=summary：Native 内存跟踪
 * - -XX:+HeapDumpOnOutOfMemoryError：OOM 时自动 dump
 * - -XX:HeapDumpPath=/tmp：dump 文件路径
 * - -Xlog:gc*,gc+heap=info：GC 事件 + 堆变化
 * - -Xlog:safepoint=info：Safepoint 信息
 *
 * 监控命令（在另一个终端执行）：
 * jstat -gcutil <pid> 1000
 * jcmd <pid> Thread.print
 * jcmd <pid> GC.class_histogram
 * jcmd <pid> VM.native_memory summary
 *
 * @author imZhiYa
 * @since JDK 21
 */
public class Jvm06MonitoringDemo {

    // 内存消费者
    private static final List<byte[]> memoryConsumer = new ArrayList<>();

    // 运行时间（秒）
    private static final int RUN_TIME_SECONDS = 30;

    public static void main(String[] args) {
        System.out.println("☕ JVM06 监控演示程序");
        System.out.println("=" .repeat(60));
        System.out.println("  📍 程序将运行 " + RUN_TIME_SECONDS + " 秒");
        System.out.println("  📍 使用 JDK 21 虚拟线程（Virtual Threads）");
        System.out.println("  📍 请在另一个终端使用以下命令监控：");
        System.out.println("     jstat -gcutil <pid> 1000");
        System.out.println("     jcmd <pid> Thread.print");
        System.out.println("     jcmd <pid> GC.class_histogram");
        System.out.println("     jcmd <pid> VM.native_memory summary");
        System.out.println("=" .repeat(60));
        System.out.println("");

        // 记录开始时间
        long startTime = System.currentTimeMillis();
        long endTime = startTime + RUN_TIME_SECONDS * 1000;

        // 循环运行
        int iteration = 0;
        while (System.currentTimeMillis() < endTime) {
            iteration++;

            // 1. 分配内存
            allocateMemory(iteration);

            // 2. 创建虚拟线程
            createVirtualThreads(iteration);

            // 3. 输出状态
            printStatus(iteration);

            // 4. 休眠
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 清理内存
        memoryConsumer.clear();
        System.gc();

        System.out.println("");
        System.out.println("=" .repeat(60));
        System.out.println("✅ 监控演示程序结束");
        System.out.println("=" .repeat(60));
    }

    /**
     * 分配内存
     *
     * 【架构师经验】内存分配是 GC 的触发器
     *
     * 分配策略：
     * 1. 小对象：TLAB 快速分配
     * 2. 大对象：直接进入老年代
     * 3. TLAB 满：退役填 filler，申新块
     *
     * 监控指标：
     * - Eden 使用率：jstat -gcutil <pid> | awk '{print $3}'
     * - YGC 次数：jstat -gcutil <pid> | awk '{print $7}'
     * - YGC 耗时：jstat -gcutil <pid> | awk '{print $8}'
     */
    private static void allocateMemory(int iteration) {
        // 每次分配 1MB
        int allocationSize = 1024 * 1024; // 1MB

        // 限制内存使用，避免 OOM
        if (memoryConsumer.size() < 50) {
            memoryConsumer.add(new byte[allocationSize]);
        }

        // 每 10 次迭代清理一次
        if (iteration % 10 == 0) {
            memoryConsumer.clear();
            System.gc();
        }
    }

    /**
     * 创建虚拟线程
     *
     * 【架构师经验】虚拟线程是 JDK 21 的核心特性
     *
     * 虚拟线程优势：
     * 1. 轻量级：可以创建数百万个虚拟线程
     * 2. 非阻塞：虚拟线程在阻塞操作时会自动释放底层平台线程
     * 3. 适合 I/O 密集型任务
     * 4. 不需要线程池管理
     *
     * 虚拟线程监控：
     * - 虚拟线程数：jcmd <pid> Thread.print | grep -c "VirtualThread"
     * - 载体线程数：jcmd <pid> Thread.print | grep -c "CarrierThread"
     * - 挂载/解绑事件：-Xlog:mount=debug
     *
     * 虚拟线程调优：
     * 1. 避免在 synchronized 内做阻塞 I/O
     * 2. 使用 ReentrantLock 替代 synchronized
     * 3. 使用 Semaphore 限制并发数
     */
    private static void createVirtualThreads(int iteration) {
        // 每 5 次迭代创建一个虚拟线程
        if (iteration % 5 == 0) {
            // 使用虚拟线程（JDK 21 特性）
            Thread.startVirtualThread(() -> {
                try {
                    // 模拟 I/O 操作
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // 也可以使用虚拟线程工厂
            // ThreadFactory factory = Thread.ofVirtual().factory();
            // Thread thread = factory.newThread(() -> { ... });
            // thread.start();
        }
    }

    /**
     * 输出状态
     *
     * 【架构师经验】状态输出是监控的基础
     *
     * 输出内容：
     * 1. 迭代次数：程序运行状态
     * 2. 内存使用：堆内存使用情况
     * 3. 线程数：虚拟线程使用情况
     *
     * 监控指标：
     * - 堆内存使用率：Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
     * - 最大堆内存：Runtime.getRuntime().maxMemory()
     * - 线程数：Thread.activeCount()
     */
    private static void printStatus(int iteration) {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();

        System.out.printf("  📍 迭代 #%d: 内存使用 %dMB / %dMB (%.1f%%)\n",
                iteration,
                usedMemory / 1024 / 1024,
                maxMemory / 1024 / 1024,
                (double) usedMemory / maxMemory * 100);

        // 每 10 次迭代输出详细信息
        if (iteration % 10 == 0) {
            System.out.println("    💡 说明：内存使用率高时，GC 会更频繁");
            System.out.println("    💡 验证方法：jstat -gcutil <pid> 1000");
        }
    }
}
