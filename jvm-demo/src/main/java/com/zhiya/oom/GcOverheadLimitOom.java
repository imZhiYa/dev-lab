package com.zhiya.oom;

import java.util.ArrayList;
import java.util.List;

/**
 * OOM05 GC Overhead Limit Exceeded 生产级复现与诊断
 * <p>
 * 知识库对应：Level 3 · GC / Level 6 · 生产诊断
 * <p>
 * =====================================================
 * GC Overhead OOM 排查心得
 * =====================================================
 * <p>
 * 1. GC Overhead Limit Exceeded 是什么？
 * - JVM 检测到 GC 耗时超过 98%，且回收的堆内存不到 2%
 * - 本质上是"GC 忙了半天但没回收多少" → OOM
 * - JDK 6 Update 24 引入，JDK 8/21 默认开启
 * <p>
 * 2. 与 Java heap space OOM 的区别
 * - Java heap space: 堆直接满了，分配失败
 * - GC overhead limit: 堆还没满，但 GC 已经忙不过来了
 * - GC overhead 是 Java heap space 的"前兆"
 * - 如果禁用 GC overhead → 最终会触发 Java heap space
 * <p>
 * 3. 什么场景下会触发？
 * - 堆内存接近满（90%+），但还有少量对象不断分配
 * - GC 频繁触发，但大部分对象都是存活的（老年代泄漏）
 * - Survivor 太小 → 对象过早晋升 → Old 区快速增长
 * <p>
 * 4. 如何禁用（不推荐）
 * - -XX:-UseGCOverheadLimit → 禁用检测
 * - 禁用后最终会触发 Java heap space OOM
 * - 只在特殊场景下使用（如已知 OOM 但需要继续运行）
 * <p>
 * =====================================================
 * 【A/B 测试】JDK 8 vs JDK 21 GC Overhead 差异
 * =====================================================
 * <p>
 * | 特性                | JDK 8              | JDK 21             |
 * |---------------------|--------------------|--------------------|
 * | 默认开启            | 是                 | 是                 |
 * | 阈值                | 98% GC 时间 + 2% 回收 | 同左             |
 * | 禁用参数            | -XX:-UseGCOverheadLimit | 同左          |
 * | 默认 GC             | Parallel           | G1                 |
 * | G1 行为             | N/A                | G1 不会触发此 OOM  |
 * <p>
 * ★ 重要：G1 GC 不会触发 GC overhead limit exceeded！
 * G1 有自己的 OOM 机制（Evacuation Failure / To-space exhausted）
 * 只有 Parallel / Serial / CMS 才会触发 GC overhead
 * <p>
 * =====================================================
 * 【实战经验】GC Overhead 诊断流程
 * =====================================================
 * <p>
 * Step 1: 确认 OOM 类型
 * - 异常信息: GC overhead limit exceeded
 * - 不是 Java heap space → 说明 GC 忙不过来了
 * <p>
 * Step 2: 查看 GC 日志
 * - 看 GC 频率和耗时
 * - 看老年代使用率趋势
 * <p>
 * Step 3: 查看对象分布
 * - jcmd <pid> GC.class_histogram
 * - 找到占用最多的对象类型
 * <p>
 * Step 4: 修复 + 验证
 * - 增加堆大小
 * - 修复内存泄漏
 * - 优化 GC 参数
 * <p>
 * =====================================================
 * <p>
 * IntelliJ IDEA VM Options:
 * # ========== 堆配置（故意设小，快速触发 GC Overhead） ==========
 * -Xms32m -Xmx32m                           # 堆 32MB，快速触发 GC overhead
 * <p>
 * # ========== 使用 Parallel GC（G1 不会触发 GC overhead） ==========
 * -XX:+UseParallelGC                         # 必须使用 Parallel GC
 * <p>
 * # ========== GC 日志（JDK 21 推荐格式） ==========
 * -Xlog:gc*=info,gc+heap=debug:file=/tmp/oom05-gc.log:time,uptime,level,tags
 * <p>
 * 命令行运行:
 * javac -encoding UTF-8 -d target/classes src/main/java/com/zhiya/jvm/gc/Oom05GcOverheadLimit.java
 * java -Xms32m -Xmx32m -XX:+UseParallelGC -Xlog:gc*=info,gc+heap=debug:file=/tmp/oom05-gc.log:time,uptime,level,tags -cp target/classes com.zhiya.jvm.gc.Oom05GcOverheadLimit
 *
 * @author imZhiYa
 * @since JDK 21
 */
public class GcOverheadLimitOom {

    /**
     * 模拟场景选择
     * <p>
     * 场景 1: 老年代泄漏导致 GC Overhead（最常见）
     * 场景 2: Survivor 太小导致过早晋升
     * 场景 3: 禁用 GC Overhead 后的 Java heap space OOM
     * <p>
     * 使用方法:
     * java -XX:+UseParallelGC ... com.zhiya.jvm.gc.Oom05GcOverheadLimit 1   # 场景 1
     * java -XX:+UseParallelGC ... com.zhiya.jvm.gc.Oom05GcOverheadLimit 2   # 场景 2
     * java -XX:+UseParallelGC -XX:-UseGCOverheadLimit ... com.zhiya.jvm.gc.Oom05GcOverheadLimit 3   # 场景 3
     */
    public static void main(String[] args) {
        int scenario = args.length > 0 ? Integer.parseInt(args[0]) : 1;

        System.out.println("☕ OOM05 GC Overhead Limit Exceeded 生产级复现");
        System.out.println("=".repeat(60));
        System.out.println("  📍 场景: " + scenario);
        System.out.println("  📍 堆大小: " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + " MB");
        System.out.println("  ⚠️  注意: G1 GC 不会触发此 OOM，必须使用 Parallel GC");
        System.out.println("  📍 等待 GC Overhead OOM 触发...");
        System.out.println("=".repeat(60));
        System.out.println("");

        switch (scenario) {
            case 1:
                scenario1_oldGenLeak();
                break;
            case 2:
                scenario2_prematurePromotion();
                break;
            case 3:
                scenario3_disabledOverhead();
                break;
            default:
                System.out.println("未知场景: " + scenario);
                System.exit(1);
        }
    }

    /**
     * 场景 1: 老年代泄漏导致 GC Overhead
     * <p>
     * 这是 GC Overhead 最常见的根因
     * <p>
     * 根因分析:
     * - 老年代对象持续增长（泄漏）
     * - GC 频繁触发，但大部分对象都是存活的
     * - GC 耗时超过 98%，回收的内存不到 2%
     * - JVM 判定"GC 忙不过来了" → OOM
     * <p>
     * 典型代码:
     * private static final List<Object> leak = new ArrayList<>();
     * while (true) {
     * leak.add(new Object());  // 老年代泄漏
     * }
     * <p>
     * 诊断方法:
     * 1. GC 日志 → 看 GC 频率和耗时
     * 2. jcmd GC.class_histogram → 看老年代对象分布
     * 3. 修复: 找到泄漏根因
     */
    private static void scenario1_oldGenLeak() {
        System.out.println("  📍 场景 1: 老年代泄漏导致 GC Overhead");
        System.out.println("  💡 典型代码: static List 不断 add，从不 clear");
        System.out.println("  💡 诊断: GC 日志 → 看 GC 频率和耗时");
        System.out.println("");

        // 老年代泄漏：对象不断积累，GC 无法回收
        List<byte[]> leakList = new ArrayList<>();
        int count = 0;

        try {
            while (true) {
                // 每次分配小对象（不触发 OOM，但让 GC 忙起来）
                leakList.add(new byte[1024]);
                count++;

                if (count % 10000 == 0) {
                    long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    long max = Runtime.getRuntime().maxMemory();
                    System.out.printf("    已分配: %d 条, 堆使用: %d/%d MB (%.1f%%)\n",
                            count, used / 1024 / 1024, max / 1024 / 1024,
                            (double) used / max * 100);
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("");
            System.out.println("  ❌ OOM 触发: " + e.getMessage());
            System.out.println("  📍 已分配: " + count + " 条");
            System.out.println("  📍 OOM 类型: " + e.getClass().getSimpleName());

            if (e.getMessage().contains("GC overhead")) {
                System.out.println("  📍 根因: GC 耗时超过 98%，回收的内存不到 2%");
                System.out.println("  💡 修复方案:");
                System.out.println("    1. 增加堆大小: -Xmx256m");
                System.out.println("    2. 修复老年代泄漏");
                System.out.println("    3. 优化 Survivor 大小");
            } else {
                System.out.println("  📍 根因: Java heap space（堆直接满）");
                System.out.println("  💡 修复方案: 增加堆大小 / 修复泄漏");
            }
        }
    }

    /**
     * 场景 2: Survivor 太小导致过早晋升
     * <p>
     * Survivor 太小 → 对象过早晋升 → 老年代快速增长
     * <p>
     * 根因分析:
     * - Survivor 区太小，容纳不下存活对象
     * - 存活对象直接晋升到老年代
     * - 老年代快速增长 → GC 频繁 → GC Overhead
     * <p>
     * 诊断方法:
     * 1. jstat -gcutil → 看 Survivor 使用率
     * 2. GC 日志 → 看晋升速率
     * 3. 修复: 增大 Survivor / 调整晋升阈值
     */
    private static void scenario2_prematurePromotion() {
        System.out.println("  📍 场景 2: Survivor 太小导致过早晋升");
        System.out.println("  💡 根因: Survivor 太小 → 对象过早晋升 → 老年代快速增长");
        System.out.println("  💡 诊断: jstat -gcutil → 看 Survivor 使用率");
        System.out.println("");

        // 创建大量短生命周期对象 + 少量长生命周期对象
        // 短生命周期对象会快速晋升到老年代
        List<byte[]> longLived = new ArrayList<>();
        int count = 0;

        try {
            while (true) {
                // 创建长生命周期对象（存活多个 GC 周期）
                longLived.add(new byte[4096]);

                // 创建大量短生命周期对象（快速晋升）
                for (int i = 0; i < 100; i++) {
                    byte[] temp = new byte[1024];
                    temp[0] = (byte) count; // 使用一下，避免被优化掉
                }

                count++;

                if (count % 1000 == 0) {
                    long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    long max = Runtime.getRuntime().maxMemory();
                    System.out.printf("    长生命周期对象: %d, 堆使用: %d/%d MB (%.1f%%)\n",
                            longLived.size(), used / 1024 / 1024, max / 1024 / 1024,
                            (double) used / max * 100);
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("");
            System.out.println("  ❌ OOM 触发: " + e.getMessage());
            System.out.println("  📍 长生命周期对象数: " + longLived.size());
            System.out.println("  💡 修复方案:");
            System.out.println("    1. 增大 Survivor: -XX:SurvivorRatio=4");
            System.out.println("    2. 增大堆: -Xmx128m");
            System.out.println("    3. 调整晋升阈值: -XX:MaxTenuringThreshold=15");
        }
    }

    /**
     * 场景 3: 禁用 GC Overhead 后的 Java heap space OOM
     * <p>
     * 禁用 GC Overhead 不会解决问题，只是推迟了 OOM
     * <p>
     * 根因分析:
     * - 使用 -XX:-UseGCOverheadLimit 禁用 GC Overhead 检测
     * - GC 继续忙碌，但最终堆还是会满
     * - 触发 Java heap space OOM
     * <p>
     * 使用场景:
     * - 已知会 OOM，但需要继续运行（如数据迁移）
     * - 临时解决方案，不能长期使用
     * <p>
     * 诊断方法:
     * - 与场景 1 相同
     * - 修复: 增加堆大小 / 修复泄漏
     */
    private static void scenario3_disabledOverhead() {
        System.out.println("  📍 场景 3: 禁用 GC Overhead 后的 Java heap space OOM");
        System.out.println("  💡 使用 -XX:-UseGCOverheadLimit 禁用检测");
        System.out.println("  💡 不会解决问题，只是推迟了 OOM");
        System.out.println("");

        List<byte[]> leakList = new ArrayList<>();
        int count = 0;

        try {
            while (true) {
                leakList.add(new byte[1024]);
                count++;

                if (count % 10000 == 0) {
                    long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    long max = Runtime.getRuntime().maxMemory();
                    System.out.printf("    已分配: %d 条, 堆使用: %d/%d MB (%.1f%%)\n",
                            count, used / 1024 / 1024, max / 1024 / 1024,
                            (double) used / max * 100);
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("");
            System.out.println("  ❌ OOM 触发: " + e.getMessage());
            System.out.println("  📍 已分配: " + count + " 条");
            System.out.println("  📍 OOM 类型: " + e.getClass().getSimpleName());

            if (e.getMessage().contains("GC overhead")) {
                System.out.println("  📍 未禁用 GC Overhead → 触发 GC overhead OOM");
            } else {
                System.out.println("  📍 已禁用 GC Overhead → 触发 Java heap space OOM");
                System.out.println("  💡 说明: 禁用 GC Overhead 只是推迟了 OOM，不会解决问题");
            }

            System.out.println("  💡 修复方案:");
            System.out.println("    1. 不要禁用 GC Overhead（治标不治本）");
            System.out.println("    2. 增加堆大小 / 修复泄漏");
            System.out.println("    3. 优化 GC 参数");
        }
    }


}

