package com.zhiya.oom;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * MetaspaceOom 生产级复现与诊断
 *
 * 知识库对应：Level 1 · 运行时数据区 / Level 6 · 生产诊断
 *
 * =====================================================
 * 20 年一线大厂的 Metaspace OOM 排查心得
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
 * 命令行运行:
 * javac -encoding UTF-8 -d target/classes src/main/java/com/zhiya/jvm/gc/Oom02Metaspace.java
 * java -XX:MaxMetaspaceSize=32m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/oom02-metaspace.hprof -Xlog:gc*=info,class+unload=info:file=/tmp/oom02-gc.log:time,uptime,level,tags -cp target/classes com.zhiya.jvm.gc.Oom02Metaspace
 *
 * @author imZhiYa
 * @since JDK 21
 */
public class MetaspaceOom {

    /**
     * 模拟场景选择
     *
     * 场景 1: JDK 动态代理类无限生成（最常见）
     * 场景 2: CGLIB 风格的类无限生成（模拟 Spring AOP）
     * 场景 3: 大量匿名类生成（Lambda/内部类场景）
     *
     * 使用方法:
     * java ... com.zhiya.jvm.gc.Oom02Metaspace 1   # 场景 1
     * java ... com.zhiya.jvm.gc.Oom02Metaspace 2   # 场景 2
     * java ... com.zhiya.jvm.gc.Oom02Metaspace 3   # 场景 3
     */
    public static void main(String[] args) {
        // 打印 JVM 实际收到的启动参数
        System.out.println("JVM Args: " +
                java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
        int scenario = args.length > 0 ? Integer.parseInt(args[0]) : 1;

        System.out.println("☕ OOM02 Metaspace 生产级复现");
        System.out.println("=" .repeat(60));
        System.out.println("  📍 场景: " + scenario);
        System.out.println("  📍 MaxMetaspaceSize: 可通过 -XX:MaxMetaspaceSize=32m 设置");
        System.out.println("  📍 等待 Metaspace OOM 触发...");
        System.out.println("=" .repeat(60));
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
     * 场景 1: JDK 动态代理类无限生成
     *
     * JDK 动态代理每次调用 Proxy.newProxyInstance
     * 都会生成一个新的代理类 → Metaspace 持续增长
     *
     * 典型代码:
     * // 在循环中反复创建代理类（而非复用）
     * for (Object target : targets) {
     *     Proxy.newProxyInstance(...);  // 每次生成新类
     * }
     *
     * 诊断方法:
     * 1. jcmd <pid> VM.classloader_stats → 看 $Proxy 类数量
     * 2. jcmd <pid> GC.class_histogram → 看 $Proxy 数量异常
     * 3. 修复: 缓存代理类，不要在循环中创建
     */
    private static void scenario1_jdkProxyLeak() {
        System.out.println("  📍 场景 1: JDK 动态代理类无限生成");
        System.out.println("  💡 典型代码: 循环中反复 Proxy.newProxyInstance");
        System.out.println("  💡 诊断: jcmd VM.classloader_stats → 看 $Proxy 数量");
        System.out.println("");
        int count = 0;
        try {
            while (true) {
                // 每次创建新的 InvocationHandler 实例
                InvocationHandler handler = new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        return null;
                    }
                };
                // 每次调用都会生成一个新的代理类（类名不同）
                // 关键：每次 new 一个 handler → Proxy 无法复用类 → Metaspace 增长
                Object proxy = Proxy.newProxyInstance(
                        MetaspaceOom.class.getClassLoader(),
                        new Class[]{Runnable.class},
                        handler
                );
                count++;
                if (count % 1000 == 0) {
                    System.out.println("    已生成代理类: " + count);
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("");
            System.out.println("  ❌ OOM 触发: " + e.getMessage());
            System.out.println("  📍 已生成代理类: " + count);
            System.out.println("  📍 泄漏根因: JDK 动态代理类无限生成");
            System.out.println("  💡 修复方案: 缓存代理类 / 使用 CGLIB 的 Enhancer 缓存");
        }
    }

    /**
     * 场景 2: 类无限生成（模拟 CGLIB/Spring AOP）
     *
     * Spring AOP 默认使用 CGLIB 代理
     * 如果配置错误，每次调用都可能生成新的代理类
     *
     * 典型代码:
     * // Spring 配置错误：每次注入都生成新代理
     * @Scope("prototype") + @Autowired → 每次生成新类
     *
     * 诊断方法:
     * 1. jcmd <pid> VM.classloader_stats → 看类数量趋势
     * 2. jcmd <pid> GC.class_histogram → 看 Class 实例数
     */
    private static void scenario2_classGenerationLeak() {
        System.out.println("  📍 场景 2: 类无限生成（模拟 CGLIB/Spring AOP）");
        System.out.println("  💡 典型代码: @Scope('prototype') + CGLIB 代理");
        System.out.println("  💡 诊断: jcmd GC.class_histogram → 看 Class 实例数");
        System.out.println("");

        int count = 0;

        try {
            while (true) {
                // 使用 JDK 动态代理模拟 CGLIB 行为
                // 每次创建不同的接口组合 → 生成不同的代理类
                final int index = count;

                // 创建一个匿名实现类（每次循环都会生成新的类）
                Runnable runnable = new Runnable() {
                    private int id = index;

                    @Override
                    public void run() {
                        System.out.println(id);
                    }
                };

                count++;

                if (count % 1000 == 0) {
                    System.out.println("    已生成匿名类: " + count);
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("");
            System.out.println("  ❌ OOM 触发: " + e.getMessage());
            System.out.println("  📍 已生成匿名类: " + count);
            System.out.println("  📍 泄漏根因: 匿名类/内部类无限生成");
            System.out.println("  💡 修复方案: 提取为命名类 / 缓存实例");
        }
    }

    /**
     * 场景 3: Lambda 类生成
     *
     * JDK 8+ 的 Lambda 使用 MethodHandle + invokedynamic
     * 每个 Lambda 捕获点可能生成一个内部类
     * 但 Lambda 通常会复用，只有在特定条件下才会泄漏
     *
     * 这个场景展示的是：大量不同的 Lambda 表达式生成大量类
     */
    private static void scenario3_lambdaLeak() {
        System.out.println("  📍 场景 3: Lambda 类生成");
        System.out.println("  💡 典型代码: 循环中创建大量不同的 Lambda 表达式");
        System.out.println("  💡 注意: Lambda 通常会复用，只有特定条件下才泄漏");
        System.out.println("");
        int count = 0;
        try {
            while (true) {
                // 创建一个捕获局部变量的 Lambda
                // 每次循环 i 不同 → Lambda 的捕获状态不同 → 可能生成新类
                final int i = count;
                // 使用 Map 存储 Lambda（模拟缓存场景）
                Map<String, Runnable> lambdaCache = new HashMap<>();
                lambdaCache.put("key:" + count, () -> System.out.println(i));
                count++;
                if (count % 1000 == 0) {
                    System.out.println("    已生成 Lambda: " + count);
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("");
            System.out.println("  ❌ OOM 触发: " + e.getMessage());
            System.out.println("  📍 已生成 Lambda: " + count);
            System.out.println("  📍 泄漏根因: Lambda 捕获不同状态 → 可能生成新类");
            System.out.println("  💡 修复方案: 提取 Lambda 为方法引用 / 减少捕获变量");
        }
    }
}
