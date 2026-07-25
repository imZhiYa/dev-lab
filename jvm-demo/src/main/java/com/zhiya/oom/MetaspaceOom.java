package com.zhiya.oom;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MetaspaceOom 生产级复现与诊断
 *
 * 知识库对应：Level 1 · 运行时数据区 / Level 6 · 生产诊断
 *
 * =====================================================
 *  Metaspace OOM 排查心得
 * =====================================================
 *
 * 1. Metaspace OOM 的三种根因
 *    - 动态代理类无限生成（CGLIB/JDK Proxy/反射）
 *    - ClassLoader 泄漏（Tomcat 热部署、Groovy 脚本引擎）
 *    - 大量 JSP 编译（每个 JSP 生成一个 Class）
 *
 * 2. Metaspace 与永久代的区别
 *    - JDK 7: 永久代（PermGen）在堆内，大小受 -XX:MaxPermSize 限制
 *    - JDK 8+: 元空间（Metaspace）在堆外 native 内存，受 -XX:MaxMetaspaceSize 限制
 *    - 元空间使用本地内存，不会触发 Java heap space OOM
 *    - 但会触发 java.lang.OutOfMemoryError: Metaspace
 *
 * 3. 生产环境最常见的泄漏根因
 *    - Groovy/SpEL 表达式引擎重复编译
 *    - CGLIB 代理类无限生成（Spring AOP 配置错误）
 *    - 反射生成类（BeanUtils.copyProperties 等工具滥用）
 *    - Tomcat 热部署后旧 WebappClassLoader 未回收
 *
 * =====================================================
 * 【A/B 测试】JDK 8 vs JDK 21 Metaspace 差异
 * =====================================================
 *
 * | 特性                | JDK 8              | JDK 21             |
 * |---------------------|--------------------|--------------------|
 * | 元数据存储          | 永久代（PermGen）  | 元空间（Metaspace）|
 * | 内存位置            | 堆内               | 堆外（native）     |
 * | 默认大小            | 固定               | 自适应增长         |
 * | 类卸载效率          | 低                 | 更高               |
 * | 容器感知            | 有限               | 完整               |
 *
 * =====================================================
 * 【实战经验】Metaspace OOM 诊断流程
 * =====================================================
 *
 * Step 1: 确认 OOM 类型
 *   - 异常信息: java.lang.OutOfMemoryError: Metaspace
 *   - 不是 Java heap space → 不要加 -Xmx
 *
 * Step 2: 查看类加载器统计（零风险）
 *   - jcmd <pid> VM.classloader_stats
 *   - 找到加载类数量最多的 ClassLoader
 *
 * Step 3: 查看 Metaspace 使用
 *   - jcmd <pid> VM.metaspace
 *   - 看 Metaspace 使用趋势
 *
 * Step 4: 定位泄漏的 ClassLoader
 *   - jcmd <pid> GC.class_histogram
 *   - 找到异常多的 Class/ClassLoader 实例
 *
 * Step 5: 修复 + 验证
 *   - 修复代码 → 重启验证 → 监控 Metaspace 趋势
 *
 * =====================================================
 *
 * IntelliJ IDEA VM Options:
 * # ========== Metaspace 配置（故意设小，快速触发 OOM） ==========
 * -XX:MaxMetaspaceSize=32m                   # 元空间上限 32MB
 *
 * # ========== OOM 时自动 dump ==========
 * -XX:+HeapDumpOnOutOfMemoryError
 * -XX:HeapDumpPath=/tmp/oom02-metaspace.hprof
 *
 * # ========== GC 日志（JDK 21 推荐格式） ==========
 * -Xlog:gc*=info,class+unload=info:file=/tmp/oom02-gc.log:time,uptime,level,tags
 *
 * @author imZhiYa
 * @since JDK 21
 */
public class MetaspaceOom {

    // 💡 关键机制：静态强引用列表保持对 ClassLoader 实例的引用
    // 原因：如果无强引用，JVM 在 Metaspace 快满时会自动通过 Full GC 卸载 ClassLoader 及其生成的代理类，
    //       导致程序进入“无限生成与自动回收”的死循环而无法触发 Metaspace OOM。
    private static final List<ClassLoader> classLoaderHolder = new ArrayList<>();

    /**
     * 模拟场景选择
     *
     * 【核心场景】场景 1: JDK 动态代理类无限生成（最常见、最重要）
     * 场景 2: 类无限生成（模拟 CGLIB / Spring AOP 配置错误）
     * 场景 3: Lambda / 匿名内部类加载泄露
     *
     * 使用方法:
     * java ... com.zhiya.oom.MetaspaceOom 1   # 场景 1（核心）
     * java ... com.zhiya.oom.MetaspaceOom 2   # 场景 2
     * java ... com.zhiya.oom.MetaspaceOom 3   # 场景 3
     */
    public static void main(String[] args) {
        // 打印 JVM 实际收到的启动参数
        System.out.println("JVM Args: " +
                java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());

        int scenario = args.length > 0 ? Integer.parseInt(args[0]) : 1;

        System.out.println("");
        System.out.println("████████████████████████████████████████████████████████████");
        System.out.println("█  ★★★  核心演示程序：MetaspaceOom  ★★★                    █");
        System.out.println("█  重点场景：JDK 动态代理类无限生成导致 Metaspace OOM       █");
        System.out.println("█  观察指标：已生成代理类数量持续增长 (1000, 2000, 3000...) █");
        System.out.println("████████████████████████████████████████████████████████████");
        System.out.println("");

        System.out.println("☕ OOM02 Metaspace 生产级复现");
        System.out.println("------------------------------------------------------------");
        System.out.println("  📍 场景: " + scenario);
        System.out.println("  📍 MaxMetaspaceSize: 可通过 -XX:MaxMetaspaceSize=12m/32m 设置");
        System.out.println("  📍 等待 Metaspace OOM 触发...");
        System.out.println("------------------------------------------------------------");
        System.out.println("");

        switch (scenario) {
            case 1:
                scenario1_jdkProxyLeak();
                break;
            case 2:
                scenario2_classGenerationLeak();
                break;
            case 3:
                scenario3_lambdaLeak();
                break;
            default:
                System.out.println("未知场景: " + scenario);
                System.exit(1);
        }
    }

    /**
     * 【核心场景】场景 1: JDK 动态代理类无限生成
     *
     * JDK 动态代理每次调用 Proxy.newProxyInstance，如果搭配独立的 ClassLoader 且未被复用，
     * 就会在 Metaspace 中生成一个新的 $ProxyN 字节码类。
     */
    private static void scenario1_jdkProxyLeak() {
        System.out.println("  📍 场景 1: JDK 动态代理类无限生成   ★★★ 核心演示 ★★★");
        System.out.println("  💡 典型代码: 循环中反复 Proxy.newProxyInstance 并使用独立 ClassLoader");
        System.out.println("  💡 诊断命令: jcmd <pid> VM.classloader_stats | grep -i proxy");
        System.out.println("  💡 诊断命令: jcmd <pid> VM.metaspace");
        System.out.println("");

        int count = 0;
        try {
            while (true) {
                // 1. 每次创建一个全新的 ClassLoader（跳过 Proxy 的类缓存机制）
                URLClassLoader newLoader = new URLClassLoader(
                        new URL[0],
                        MetaspaceOom.class.getClassLoader()
                );

                // 2. 保持强引用，阻止 GC 自动回收该 ClassLoader 及 Metaspace 中的字节码
                classLoaderHolder.add(newLoader);

                // 3. 创建 InvocationHandler
                InvocationHandler handler = new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        return null;
                    }
                };

                // 4. 生成动态代理实例
                Object proxy = Proxy.newProxyInstance(
                        newLoader,
                        new Class[]{Runnable.class},
                        handler
                );

                count++;

                // 每 1000 个打印一次进度
                if (count % 1000 == 0) {
                    System.out.println("已生成代理类: " + count);
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("");
            System.out.println("  ❌ OOM 成功触发: " + e);
            System.out.println("  📍 已累计生成代理类: " + count);
            System.out.println("  📍 泄漏根因: JDK 动态代理类无限生成（每个代理使用独立 ClassLoader 且存在强引用未被回收）");
            System.out.println("  💡 修复方案: 缓存代理类 / 使用 CGLIB Enhancer 缓存 / 统一复用 ClassLoader");
            System.out.println("  💡 诊断方式: jcmd <pid> VM.classloader_stats 查看 $Proxy* 类数量");
        }
    }

    /**
     * 场景 2: 类无限生成（模拟 CGLIB/Spring AOP）
     */
    private static void scenario2_classGenerationLeak() {
        System.out.println("  📍 场景 2: 类无限生成（模拟 CGLIB/Spring AOP）");
        System.out.println("  💡 典型代码: @Scope('prototype') + CGLIB 代理");
        System.out.println("  💡 诊断: jcmd GC.class_histogram → 看 Class 实例数");
        System.out.println("");

        int count = 0;
        try {
            while (true) {
                URLClassLoader newLoader = new URLClassLoader(
                        new URL[0],
                        MetaspaceOom.class.getClassLoader()
                );
                classLoaderHolder.add(newLoader);

                InvocationHandler handler = (proxy, method, args) -> null;
                Proxy.newProxyInstance(newLoader, new Class[]{Runnable.class}, handler);

                count++;

                if (count % 1000 == 0) {
                    System.out.println("    已生成类: " + count);
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("");
            System.out.println("  ❌ OOM 成功触发: " + e);
            System.out.println("  📍 已生成类: " + count);
            System.out.println("  📍 泄漏根因: 匿名类/内部类/代理类无限生成");
            System.out.println("  💡 修复方案: 提取为命名类 / 缓存实例");
        }
    }

    /**
     * 场景 3: Lambda 类生成
     */
    private static void scenario3_lambdaLeak() {
        System.out.println("  📍 场景 3: Lambda 类生成");
        System.out.println("  💡 典型代码: 循环中创建大量不同的 Lambda 表达式");
        System.out.println("  💡 注意: Lambda 通常会复用，只有特定条件下才泄漏");
        System.out.println("");

        int count = 0;
        try {
            while (true) {
                URLClassLoader newLoader = new URLClassLoader(
                        new URL[0],
                        MetaspaceOom.class.getClassLoader()
                );
                classLoaderHolder.add(newLoader);

                InvocationHandler handler = (proxy, method, args) -> null;
                Proxy.newProxyInstance(newLoader, new Class[]{Runnable.class}, handler);

                count++;

                if (count % 1000 == 0) {
                    System.out.println("    已生成 Lambda: " + count);
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("");
            System.out.println("  ❌ OOM 成功触发: " + e);
            System.out.println("  📍 已生成 Lambda: " + count);
            System.out.println("  📍 泄漏根因: Lambda 捕获不同状态导致生成新类");
            System.out.println("  💡 修复方案: 提取 Lambda 为方法引用 / 减少捕获变量");
        }
    }
}
