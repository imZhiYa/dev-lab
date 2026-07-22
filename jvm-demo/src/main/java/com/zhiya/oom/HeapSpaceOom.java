package com.zhiya.oom;

import java.util.ArrayList;
import java.util.List;

/**
 * HeapSpaceOom 生产级复现与诊断
 * <p>
 * 知识库对应：Level 1 · 运行时数据区 / Level 6 · 生产诊断
 * <p>
 * =====================================================
 * OOM 排查心得
 * =====================================================
 * <p>
 * 1. 堆 OOM 是最常见的 OOM 类型
 * - 占所有 OOM 的 80% 以上
 * - 根因：内存泄漏 / 分配过快 / 堆太小
 * - 诊断：jcmd GC.class_histogram + JFR OldObjectSample
 * <p>
 * 2. 两种堆 OOM 子类型
 * - java.lang.OutOfMemoryError: Java heap space
 * → 堆内对象分配失败
 * - java.lang.OutOfMemoryError: GC overhead limit exceeded
 * → GC 耗时超过 98%，回收的内存不到 2%
 * → JDK 8 默认开启，JDK 21 同样支持
 * <p>
 * 3. 生产环境最常见的泄漏根因
 * - 静态集合无限增长（HashMap/List 没有上限）
 * - ThreadLocal 未 remove（线程池场景尤其致命）
 * - 缓存没有淘汰策略（纯内存缓存不设 TTL）
 * - 监听器/回调未注销（EventBus、MQ Consumer）
 * - 数据库连接/ResultSet 未关闭
 * <p>
 * =====================================================
 * 【A/B 测试】JDK 8 vs JDK 21 堆 OOM 差异
 * =====================================================
 * <p>
 * | 特性                | JDK 8              | JDK 21             |
 * |---------------------|--------------------|--------------------|
 * | 默认 GC             | Parallel           | G1                 |
 * | 堆 OOM 错误信息     | 基本               | 更详细（含分配失败原因） |
 * | GC overhead 默认    | 开启               | 开启               |
 * | OOM 时自动 dump     | 需手动配置         | 需手动配置         |
 * | 堆外内存计入 cgroup | 部分               | 完整               |
 * <p>
 * =====================================================
 * 【实战经验】堆 OOM 诊断流程
 * =====================================================
 * <p>
 * Step 1: 确认 OOM 类型
 * - 看异常文案：Java heap space / GC overhead limit exceeded
 * - 看容器 exit code：137 = cgroup OOM（不是堆 OOM）
 * <p>
 * Step 2: 查看对象分布（零风险）
 * - jcmd <pid> GC.class_histogram | head -30
 * - 找到占用最多的类 → 定位代码
 * <p>
 * Step 3: 查看引用链（推荐 JFR）
 * - jcmd <pid> JFR.start name=leak settings=profile
 * - JFR OldObjectSample → 看引用链
 * - 禁止用 jmap -dump:live（会触发 Full GC）
 * <p>
 * Step 4: 分配火焰图（推荐 async-profiler）
 * - async-profiler -e alloc -d 60 -f alloc.svg <pid>
 * - 找到分配最多的代码路径
 * <p>
 * Step 5: 修复 + 验证
 * - 修复代码 → 灰度环境验证 → 对比 Old 区增长曲线
 * <p>
 * =====================================================
 * <p>
 * IntelliJ IDEA VM Options:
 * # ========== 堆配置（故意设小，快速触发 OOM） ==========
 * -Xms64m -Xmx64m                           # 堆 64MB，快速触发 OOM
 * <p>
 * # ========== OOM 时自动 dump ==========
 * -XX:+HeapDumpOnOutOfMemoryError            # OOM 时自动生成堆 dump
 * -XX:HeapDumpPath=/tmp/oom01-heap.hprof     # dump 文件路径
 * <p>
 * # ========== GC 日志（JDK 21 推荐格式） ==========
 * -Xlog:gc*=info,gc+heap=debug,safepoint=info:file=/tmp/oom01-gc.log:time,uptime,level,tags
 * <p>
 * 命令行运行:
 * javac -encoding UTF-8 -d target/classes src/main/java/com/zhiya/jvm/gc/Oom01HeapSpace.java
 * java -Xms64m -Xmx64m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/oom01-heap.hprof -Xlog:gc*=info,gc+heap=debug:file=/tmp/oom01-gc.log:time,uptime,level,tags -cp target/classes com.zhiya.jvm.gc.Oom01HeapSpace
 *
 * @author imZhiYa
 * @since JDK 21
 */
public class HeapSpaceOom {

    public static void main(String[] args) {

        int scenario = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        System.out.println("☕ OOM01 Java Heap Space 生产级复现");
        System.out.println("=".repeat(60));
        System.out.println("  📍 场景: " + scenario);
        System.out.println("  📍 堆大小: " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + " MB");
        System.out.println("  📍 等待 OOM 触发...");
        System.out.println("=".repeat(60));
        System.out.println("");
        switch (scenario) {
            case 1:
                scenario1_staticCollectionLeak();
                break;
            case 2:
                scenario2_threadLocalLeak();
                break;
            case 3:
                scenario3_cacheNoEviction();
                break;
            default:
                System.out.println("  ❌ 无效场景: " + scenario);
        }
    }

    /**
     * 场景 1: 静态集合无限增长
     * <p>
     * 这是生产环境最常见的 OOM 根因
     * <p>
     * 典型代码:
     * private static final Map<String, Object> cache = new HashMap<>();
     * // 每次请求都 put，但从不 remove → OOM
     * <p>
     * 诊断方法:
     * 1. jcmd GC.class_histogram → 找到 java.util.HashMap$Node 数量异常
     * 2. JFR OldObjectSample → 看引用链找到 put 的代码位置
     * 3. async-profiler -e alloc → 看分配火焰图
     */
    private static void scenario1_staticCollectionLeak() {
        System.out.println("  📍 场景 1: 静态集合无限增长");
        System.out.println("  💡 典型代码: static Map 不断 put，从不 remove");
        System.out.println("  💡 诊断: jcmd GC.class_histogram → 找 HashMap$Node");
        System.out.println("");
        // 模拟：静态集合无限增长
        List<byte[]> leakList = new ArrayList<>();
        int count = 0;
        try {
            while (true) {
                // 每次分配 1MB，快速填满 64MB 堆
                leakList.add(new byte[1024 * 1024]);
                count++;

                if (count % 10 == 0) {
                    long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    long max = Runtime.getRuntime().maxMemory();
                    System.out.printf("    已分配: %d MB, 堆使用: %d/%d MB (%.1f%%)\n",
                            count, used / 1024 / 1024, max / 1024 / 1024,
                            (double) used / max * 100);
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("");
            System.out.println("  ❌ OOM 触发: " + e.getMessage());
            System.out.println("  📍 已分配: " + count + " MB");
            System.out.println("  📍 泄漏对象类型: byte[]");
            System.out.println("  💡 诊断命令: jcmd GC.class_histogram");
            System.out.println("  💡 修复方案: 设置集合上限 / 使用 LRU 缓存");
        }
    }

    /**
     * 场景 2: ThreadLocal 泄漏
     * <p>
     * 线程池场景下 ThreadLocal 是 OOM 的定时炸弹
     * <p>
     * 典型代码:
     * private static final ThreadLocal<List<byte[]>> context = new ThreadLocal<>();
     * // 线程池复用线程 → ThreadLocal 不会自动回收 → OOM
     * <p>
     * 诊断方法:
     * 1. jcmd GC.class_histogram → 找到 ThreadLocalMap 异常
     * 2. jcmd Thread.print → 看线程数和状态
     * 3. JFR OldObjectSample → 看 ThreadLocal 引用链
     */
    private static void scenario2_threadLocalLeak() {
        System.out.println("  📍 场景 2: ThreadLocal 泄漏");
        System.out.println("  💡 典型代码: ThreadLocal 不 remove，线程池复用线程");
        System.out.println("  💡 诊断: jcmd GC.class_histogram → 找 ThreadLocalMap");
        System.out.println("");

        // 模拟：ThreadLocal 泄漏
        ThreadLocal<List<byte[]>> threadLocal = new ThreadLocal<>();
        int count = 0;

        try {
            // 模拟线程池复用同一个线程
            Thread.currentThread().setName("pool-1-thread-1");

            while (true) {
                List<byte[]> list = threadLocal.get();
                if (list == null) {
                    list = new ArrayList<>();
                    threadLocal.set(list);
                }

                // 每次添加 1MB
                list.add(new byte[1024 * 1024]);
                count++;

                if (count % 10 == 0) {
                    long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    long max = Runtime.getRuntime().maxMemory();
                    System.out.printf("    已分配: %d MB, 堆使用: %d/%d MB (%.1f%%)\n",
                            count, used / 1024 / 1024, max / 1024 / 1024,
                            (double) used / max * 100);
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("");
            System.out.println("  ❌ OOM 触发: " + e.getMessage());
            System.out.println("  📍 已分配: " + count + " MB");
            System.out.println("  📍 泄漏根因: ThreadLocal 未 remove");
            System.out.println("  💡 修复方案: finally 块中 threadLocal.remove()");
        }
    }


    /**
     * 场景 3: 缓存无淘汰策略
     * <p>
     * 纯内存缓存不设 TTL/LRU 是 OOM 的温床
     * <p>
     * 典型代码:
     * private static final Map<String, byte[]> cache = new ConcurrentHashMap<>();
     * // 缓存只 put 不 evict → OOM
     * <p>
     * 诊断方法:
     * 1. jcmd GC.class_histogram → 找到 ConcurrentHashMap$Node 异常
     * 2. 查看缓存 size → 确认无限增长
     * 3. 修复: 使用 Caffeine/Guava Cache 设置 TTL + 最大条目数
     */
    private static void scenario3_cacheNoEviction() {
        System.out.println("  📍 场景 3: 缓存无淘汰策略");
        System.out.println("  💡 典型代码: ConcurrentHashMap 只 put 不 evict");
        System.out.println("  💡 诊断: jcmd GC.class_histogram → 找 ConcurrentHashMap$Node");
        System.out.println("");

        // 模拟：缓存无淘汰策略
        List<byte[]> cache = new ArrayList<>();
        int count = 0;

        try {
            while (true) {
                // 模拟缓存 key: "user:" + count
                String key = "user:" + count;
                byte[] value = new byte[512 * 1024]; // 512KB per entry

                cache.add(value);
                count++;

                if (count % 20 == 0) {
                    long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    long max = Runtime.getRuntime().maxMemory();
                    System.out.printf("    缓存条目: %d, 堆使用: %d/%d MB (%.1f%%)\n",
                            count, used / 1024 / 1024, max / 1024 / 1024,
                            (double) used / max * 100);
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("");
            System.out.println("  ❌ OOM 触发: " + e.getMessage());
            System.out.println("  📍 缓存条目数: " + count);
            System.out.println("  📍 泄漏根因: 缓存无淘汰策略");
            System.out.println("  💡 修复方案: 使用 Caffeine.maximumSize() + expireAfterWrite()");
        }
    }
}
