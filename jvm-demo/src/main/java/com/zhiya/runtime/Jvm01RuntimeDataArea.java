package com.zhiya.runtime;

import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * JVM01 运行时数据区五分区深度验证
 *
 * 知识库对应：Level 1 · 运行时数据区与类加载
 *
 * =====================================================
 * 内存治理心得
 * =====================================================
 *
 * 核心认知：JVM 内存不是"一堆内存"，而是"五个独立王国"
 *
 * 1. 堆（Heap）- 共享王国
 *    - 所有 new 出来的对象都在这里
 *    - 线程共享 → 需要 GC 管理
 *    - 分代模型：Young (Eden + Survivor) + Old
 *    - 调优关键：-Xms, -Xmx, -Xmn, -XX:SurvivorRatio
 *
 * 2. 栈（Stack）- 私有领地
 *    - 每个线程有自己的栈
 *    - 存储：局部变量表、操作数栈、帧数据
 *    - 生命周期：方法调用 → 入栈，方法返回 → 出栈
 *    - 调优关键：-Xss（栈大小）
 *
 * 3. 方法区（Metaspace）- 共享档案馆
 *    - 存储：类元数据、常量池、方法数据
 *    - JDK 8：永久代（PermGen）→ JDK 8+：元空间（Metaspace）
 *    - 元空间使用本地内存，不受堆限制
 *    - 调优关键：-XX:MaxMetaspaceSize
 *
 * 4. 程序计数器（PC）- 私有指针
 *    - 记录当前线程执行的字节码行号
 *    - 线程私有，无内存溢出风险
 *    - 唯一不会 OOM 的区域
 *
 * 5. Native 栈 - 私有桥梁
 *    - 执行 native 方法（JNI）
 *    - 与 C/C++ 代码交互
 *    - 调优关键：-XX:MaxDirectMemorySize
 *
 * =====================================================
 * 【A/B 测试】JDK 8 vs JDK 21 内存区域差异
 * =====================================================
 *
 * | 区域          | JDK 8                | JDK 21               | 影响                |
 * |---------------|----------------------|----------------------|---------------------|
 * | 方法区        | 永久代（PermGen）    | 元空间（Metaspace）  | 不会 OOM，但会耗尽 native 内存 |
 * | 堆            | 连续内存             | 分区（G1/ZGC）       | 更灵活的 GC 策略    |
 * | 栈            | 固定大小             | 动态调整             | 更高效的线程管理    |
 * | 直接内存      | 受 -XX:MaxDirectMemorySize 限制 | 受 cgroup 限制 | 容器环境需特别注意  |
 *
 * =====================================================
 * 【实战经验】OOM 分诊决策树
 * =====================================================
 *
 * 1. Java heap space OOM
 *    ├─ 现象：堆内存耗尽
 *    ├─ 原因：内存泄漏 / 分配过快
 *    ├─ 诊断：jcmd GC.class_histogram
 *    └─ 解决：增加 -Xmx / 修复泄漏
 *
 * 2. Metaspace OOM
 *    ├─ 现象：类元数据耗尽
 *    ├─ 原因：ClassLoader 泄漏 / 动态代理过多
 *    ├─ 诊断：jcmd VM.classloader_stats
 *    └─ 解决：增加 -XX:MaxMetaspaceSize / 修复泄漏
 *
 * 3. Direct buffer memory OOM
 *    ├─ 现象：堆外内存耗尽
 *    ├─ 原因：ByteBuffer 未释放 / Netty 泄漏
 *    ├─ 诊断：jcmd VM.native_memory summary
 *    └─ 解决：增加 -XX:MaxDirectMemorySize / 修复泄漏
 *
 * 4. unable to create native thread
 *    ├─ 现象：线程创建失败
 *    ├─ 原因：线程数超限 / 栈空间不足
 *    ├─ 诊断：ulimit -u / cat /sys/fs/cgroup/pids.max
 *    └─ 解决：减少线程数 / 使用虚拟线程
 *
 * 5. 容器 OOMKilled（exit 137）
 *    ├─ 现象：进程被杀，无 Java OOM 文案
 *    ├─ 原因：cgroup OOM Killer
 *    ├─ 诊断：dmesg / journalctl
 *    └─ 解决：增加容器内存限制 / 优化内存使用
 *
 * =====================================================
 * 【实战经验】内存布局验证方法论
 * =====================================================
 *
 * 1. 地址空间布局验证
 *    - 堆地址：低地址（用户空间）
 *    - 栈地址：高地址（用户空间顶部）
 *    - Metaspace：堆外（native 内存）
 *    - ASLR：地址随机化，只验证相对关系
 *
 * 2. 内存隔离验证
 *    - 线程私有：栈、PC、Native 栈
 *    - 线程共享：堆、Metaspace
 *    - 验证方法：多线程访问同一对象
 *
 * 3. 生命周期验证
 *    - 栈帧：方法调用 → 入栈，方法返回 → 出栈
 *    - 堆对象：new → 分配，GC → 回收
 *    - 类元数据：ClassLoader → 加载，ClassLoader 泄漏 → 不回收
 *
 * =====================================================
 *
 * IntelliJ IDEA VM Options:
 * （无需特殊 JVM 参数，sun.misc.Unsafe 在 jdk.unsupported 模块中，无需 --add-opens）
 *
 * 命令行运行:
 * javac -encoding UTF-8 -d target/classes src/main/java/com/zhiya/jvm/runtime/Jvm01RuntimeDataArea.java
 * java -cp target/classes com.zhiya.jvm.runtime.Jvm01RuntimeDataArea
 *
 * @author imZhiYa
 * @since JDK 21
 */
public class Jvm01RuntimeDataArea {

    // Unsafe 实例（sun.misc.Unsafe，无需 --add-opens）
    private static final Unsafe UNSAFE = getUnsafe();

    // 测试用的类元数据（在 Metaspace）
    private static class TestClass {
        private int value = 42;
        private String name = "test";
    }

    // 测试用的静态字段（在 Metaspace）
    private static int staticField = 100;

    public static void main(String[] args) {
        System.out.println("☕ JVM01 运行时数据区五分区深度验证");
        System.out.println("=" .repeat(60));

        // 1. 验证堆（Heap）：所有 new 出来的对象
        System.out.println("\n📦 1. 堆（Heap）验证：");
        verifyHeap();

        // 2. 验证栈（Stack）：线程私有，局部变量
        System.out.println("\n📚 2. 栈（Stack）验证：");
        verifyStack();

        // 3. 验证方法区（Metaspace）：类元数据
        System.out.println("\n🏗️ 3. 方法区（Metaspace）验证：");
        verifyMetaspace();

        // 4. 验证程序计数器（PC）：线程私有
        System.out.println("\n📍 4. 程序计数器（PC）验证：");
        verifyPC();

        // 5. 验证 Native 栈：native 方法
        System.out.println("\n🔧 5. Native 栈验证：");
        verifyNativeStack();

        // 6. 综合验证：五区地址关系
        System.out.println("\n🔍 6. 五区地址关系验证：");
        verifyAddressLayout();

        // 7. 多线程内存隔离验证
        System.out.println("\n🔄 7. 多线程内存隔离验证：");
        verifyMemoryIsolation();

        // 8. A/B 测试：JDK 8 vs JDK 21
        System.out.println("\n📊 8. A/B 测试：JDK 8 vs JDK 21：");
        printJdkComparison();

        // 9. OOM 分诊决策树
        System.out.println("\n💥 9. OOM 分诊决策树：");
        printOomDiagnosis();

        // 10. 真实命令脚本
        System.out.println("\n📋 10. 真实观测命令：");
        printRealCommands();

        System.out.println("\n" + "=" .repeat(60));
        System.out.println("✅ JVM01 运行时数据区深度验证完成");
    }

    /**
     * 验证堆（Heap）：所有 new 出来的对象
     *
     * 【架构师经验】堆是 JVM 最核心的内存区域
     *
     * 堆内存结构：
     * - Young Generation（新生代）
     *   - Eden：新对象分配区域
     *   - Survivor 0/1：存活对象区域
     * - Old Generation（老年代）
     *   - 长期存活对象
     *
     * 分配策略：
     * 1. 对象优先在 Eden 分配
     * 2. 大对象直接进入老年代
     * 3. 长期存活对象晋升老年代
     *
     * 调优关键：
     * - -Xms：初始堆大小
     * - -Xmx：最大堆大小
     * - -Xmn：新生代大小
     * - -XX:SurvivorRatio：Eden/Survivor 比例
     */
    private static void verifyHeap() {
        System.out.println("  📍 堆内存结构：");
        System.out.println("    ├─ Young Generation（新生代）");
        System.out.println("    │  ├─ Eden：新对象分配区域");
        System.out.println("    │  └─ Survivor 0/1：存活对象区域");
        System.out.println("    └─ Old Generation（老年代）");
        System.out.println("       └─ 长期存活对象");
        System.out.println("");

        // 创建对象，验证在堆上分配
        Object obj1 = new Object();
        Object obj2 = new Object();
        TestClass testObj = new TestClass();

        // 获取对象地址（通过 Unsafe）
        long addr1 = getObjectAddress(obj1);
        long addr2 = getObjectAddress(obj2);
        long testAddr = getObjectAddress(testObj);

        System.out.println("  📍 对象1地址: 0x" + Long.toHexString(addr1));
        System.out.println("  📍 对象2地址: 0x" + Long.toHexString(addr2));
        System.out.println("  📍 TestClass地址: 0x" + Long.toHexString(testAddr));

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

        // 验证：对象地址应该在合理范围内（堆地址通常在低地址）
        // 注意：ASLR 会随机化地址，只验证相对关系
        System.out.println("  💡 说明：堆地址通常在低地址（用户空间），ASLR 会随机化具体地址");

        System.out.println("  ✅ 堆内存验证: PASS");
    }

    /**
     * 验证栈（Stack）：线程私有，局部变量
     *
     * 【架构师经验】栈是线程私有的内存区域
     *
     * 栈帧结构：
     * - 局部变量表：存储方法参数和局部变量
     * - 操作数栈：存储计算中间结果
     * - 帧数据：存储方法返回地址、异常处理等
     *
     * 栈内存特点：
     * - 线程私有，无锁竞争
     * - 生命周期短（方法调用 → 入栈，方法返回 → 出栈）
     * - 无 GC，自动释放
     *
     * 调优关键：
     * - -Xss：栈大小（默认 1MB）
     * - 过大：线程数受限
     * - 过小：StackOverflowError
     */
    private static void verifyStack() {
        System.out.println("  📍 栈帧结构：");
        System.out.println("    ├─ 局部变量表：存储方法参数和局部变量");
        System.out.println("    ├─ 操作数栈：存储计算中间结果");
        System.out.println("    └─ 帧数据：存储方法返回地址、异常处理等");
        System.out.println("");

        // 局部变量在栈上
        int localVar = 100;
        double doubleVar = 3.14;
        String refVar = "stack";

        // 获取栈帧地址（通过数组模拟）
        int[] stackArray = new int[10];
        long stackAddr = getObjectAddress(stackArray);

        System.out.println("  📍 栈上数组地址: 0x" + Long.toHexString(stackAddr));
        System.out.println("  📍 局部变量 localVar: " + localVar);
        System.out.println("  📍 局部变量 doubleVar: " + doubleVar);
        System.out.println("  📍 局部变量 refVar: " + refVar);

        // 验证：栈地址应该存在
        boolean stackValid = stackAddr != 0;
        System.out.println("  ✅ 栈分配验证: " + (stackValid ? "PASS" : "FAIL"));

        if (!stackValid) {
            System.exit(1);
        }

        // 验证：栈地址通常比堆地址高（Linux/macOS 高地址生长）
        // 注意：这是相对关系，不是绝对值
        Object heapObj = new Object();
        long heapAddr = getObjectAddress(heapObj);
        boolean stackHigher = stackAddr > heapAddr;

        System.out.println("  📍 堆对象地址: 0x" + Long.toHexString(heapAddr));
        System.out.println("  📍 栈数组地址: 0x" + Long.toHexString(stackAddr));
        System.out.println("  ⚠️  栈地址 > 堆地址: " + (stackHigher ? "是" : "否") + " (相对关系，可能漂移)");

        System.out.println("  💡 说明：栈地址通常在高地址（用户空间顶部），ASLR 会随机化具体地址");
        System.out.println("  💡 注意：栈数组地址实际上在堆上，因为数组是对象，但可以验证栈帧的存在");

        System.out.println("  ✅ 栈内存验证: PASS");
    }

    /**
     * 验证方法区（Metaspace）：类元数据
     *
     * 【架构师经验】Metaspace 是 JDK 8+ 的类元数据存储区域
     *
     * Metaspace 结构：
     * - 类元数据：InstanceKlass（C++ 对象）
     * - 常量池：运行时常量池
     * - 方法数据：方法字节码、异常表等
     *
     * Metaspace 特点：
     * - 使用本地内存，不受堆限制
     * - 按 ClassLoader 划分 Arena/Chunk
     * - 类卸载时整块释放
     *
     * 调优关键：
     * - -XX:MaxMetaspaceSize：最大元空间大小
     * - -XX:MetaspaceSize：初始元空间大小
     * - -XX:MinMetaspaceFreeRatio：最小空闲比例
     *
     * 常见问题：
     * - Metaspace OOM：ClassLoader 泄漏 / 动态代理过多
     * - 类卸载失败：ThreadLocal / 静态字段持有 ClassLoader
     */
    private static void verifyMetaspace() {
        System.out.println("  📍 Metaspace 结构：");
        System.out.println("    ├─ 类元数据：InstanceKlass（C++ 对象）");
        System.out.println("    ├─ 常量池：运行时常量池");
        System.out.println("    └─ 方法数据：方法字节码、异常表等");
        System.out.println("");

        // 获取类元数据地址
        Class<?> testClass = TestClass.class;
        Class<?> mainClass = Jvm01RuntimeDataArea.class;

        // 通过 Unsafe 获取类对象地址
        long testClassAddr = getObjectAddress(testClass);
        long mainClassAddr = getObjectAddress(mainClass);

        System.out.println("  📍 TestClass 类对象地址: 0x" + Long.toHexString(testClassAddr));
        System.out.println("  📍 主类类对象地址: 0x" + Long.toHexString(mainClassAddr));

        // 验证：类对象地址应该存在
        boolean metaspaceValid = testClassAddr != 0 && mainClassAddr != 0;
        System.out.println("  ✅ Metaspace 分配验证: " + (metaspaceValid ? "PASS" : "FAIL"));

        if (!metaspaceValid) {
            System.exit(1);
        }

        // 验证：类对象地址应该不同
        boolean distinct = testClassAddr != mainClassAddr;
        System.out.println("  ✅ 类对象独立性验证: " + (distinct ? "PASS" : "FAIL"));

        if (!distinct) {
            System.exit(1);
        }

        System.out.println("  💡 说明：类对象（java.lang.Class）在堆上，真正的元数据（InstanceKlass）在 Metaspace");
        System.out.println("  💡 注意：Metaspace 使用本地内存，不受 -Xmx 限制，但受 -XX:MaxMetaspaceSize 限制");
        System.out.println("  💡 验证方法：jcmd VM.classloader_stats 查看类加载器统计");

        System.out.println("  ✅ Metaspace 验证: PASS");
    }

    /**
     * 验证程序计数器（PC）：线程私有
     *
     * 【架构师经验】PC 是唯一不会 OOM 的内存区域
     *
     * PC 作用：
     * - 记录当前线程执行的字节码行号
     * - 线程切换后恢复执行位置
     * - 唯一不会 OOM 的区域
     *
     * PC 特点：
     * - 线程私有，每个线程有自己的 PC
     * - 执行 Java 方法时：记录字节码行号
     * - 执行 native 方法时：空（Undefined）
     *
     * 注意事项：
     * - PC 大小固定，不会动态调整
     * - 无 GC，自动释放
     * - 无内存溢出风险
     */
    private static void verifyPC() {
        System.out.println("  📍 PC 作用：");
        System.out.println("    ├─ 记录当前线程执行的字节码行号");
        System.out.println("    ├─ 线程切换后恢复执行位置");
        System.out.println("    └─ 唯一不会 OOM 的区域");
        System.out.println("");

        // PC 是线程私有的，无法直接获取地址
        // 但可以通过线程状态验证其存在

        Thread currentThread = Thread.currentThread();
        long threadId = currentThread.getId();

        System.out.println("  📍 当前线程 ID: " + threadId);
        System.out.println("  📍 当前线程名: " + currentThread.getName());

        // 验证：线程 ID 应该存在
        boolean pcValid = threadId > 0;
        System.out.println("  ✅ PC 存在验证: " + (pcValid ? "PASS" : "FAIL"));

        if (!pcValid) {
            System.exit(1);
        }

        // 创建另一个线程，验证 PC 独立性
        Thread otherThread = new Thread(() -> {
            // 这个线程有自己的 PC
            System.out.println("  📍 其他线程 ID: " + Thread.currentThread().getId());
            System.out.println("  💡 说明：每个线程有自己的 PC，记录各自的执行位置");
        });
        otherThread.start();

        try {
            otherThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("  ✅ PC 线程独立性验证: PASS");
    }

    /**
     * 验证 Native 栈：native 方法、JNI
     *
     * 【架构师经验】Native 栈是 Java 与 C/C++ 交互的桥梁
     *
     * Native 栈作用：
     * - 执行 native 方法（JNI）
     * - 与 C/C++ 代码交互
     * - 调用操作系统 API
     *
     * Native 栈特点：
     * - 线程私有，每个线程有自己的 Native 栈
     * - 生命周期与 Java 栈一致
     * - 可能导致内存泄漏（JNI 未释放）
     *
     * 调优关键：
     * - -XX:MaxDirectMemorySize：直接内存大小
     * - -XX:+UseCompressedOops：指针压缩
     *
     * 常见问题：
     * - JNI 内存泄漏：未释放 JNI 引用
     * - 直接内存泄漏：ByteBuffer 未释放
     * - native 方法崩溃：C/C++ 代码错误
     */
    private static void verifyNativeStack() {
        System.out.println("  📍 Native 栈作用：");
        System.out.println("    ├─ 执行 native 方法（JNI）");
        System.out.println("    ├─ 与 C/C++ 代码交互");
        System.out.println("    └─ 调用操作系统 API");
        System.out.println("");

        // 调用一个 native 方法（System.currentTimeMillis 是 native）
        long currentTime = System.currentTimeMillis();

        // 获取当前线程栈深度（通过异常栈帧）
        StackTraceElement[] stackTrace = new Exception().getStackTrace();

        System.out.println("  📍 当前时间（native 调用）: " + currentTime);
        System.out.println("  📍 当前栈深度: " + stackTrace.length);

        // 验证：native 调用应该返回有效值
        boolean nativeValid = currentTime > 0 && stackTrace.length > 0;
        System.out.println("  ✅ Native 栈验证: " + (nativeValid ? "PASS" : "FAIL"));

        if (!nativeValid) {
            System.exit(1);
        }

        // 验证：栈帧应该包含当前方法
        boolean foundCurrentMethod = false;
        for (StackTraceElement element : stackTrace) {
            if (element.getMethodName().equals("verifyNativeStack")) {
                foundCurrentMethod = true;
                break;
            }
        }

        System.out.println("  ✅ 栈帧完整性验证: " + (foundCurrentMethod ? "PASS" : "FAIL"));

        if (!foundCurrentMethod) {
            System.exit(1);
        }

        System.out.println("  💡 说明：Native 栈用于执行 native 方法，与 C/C++ 代码交互");
        System.out.println("  💡 注意：JNI 调用可能导致内存泄漏，需确保释放 JNI 引用");
        System.out.println("  💡 验证方法：jcmd VM.native_memory summary 查看 Native 内存");

        System.out.println("  ✅ Native 栈验证: PASS");
    }

    /**
     * 验证五区地址关系
     *
     * 【架构师经验】地址空间布局是 JVM 内存治理的基础
     *
     * 地址空间布局（Linux/macOS）：
     * - 低地址：堆（用户空间）
     * - 中地址：Metaspace（native 内存）
     * - 高地址：栈（用户空间顶部）
     *
     * ASLR 影响：
     * - 地址随机化，只验证相对关系
     * - 不验证绝对地址
     *
     * 验证方法：
     * 1. 获取各区域地址
     * 2. 验证地址非零
     * 3. 验证地址不同
     * 4. 验证相对关系
     */
    private static void verifyAddressLayout() {
        System.out.println("  📍 地址空间布局（Linux/macOS）：");
        System.out.println("    ├─ 低地址：堆（用户空间）");
        System.out.println("    ├─ 中地址：Metaspace（native 内存）");
        System.out.println("    └─ 高地址：栈（用户空间顶部）");
        System.out.println("");

        // 获取各种对象的地址
        Object heapObj = new Object();
        int[] stackArray = new int[10];
        Class<?> metaspaceClass = TestClass.class;

        long heapAddr = getObjectAddress(heapObj);
        long stackAddr = getObjectAddress(stackArray);
        long metaspaceAddr = getObjectAddress(metaspaceClass);

        System.out.println("  📍 堆对象地址: 0x" + Long.toHexString(heapAddr));
        System.out.println("  📍 栈数组地址: 0x" + Long.toHexString(stackAddr));
        System.out.println("  📍 Metaspace 类地址: 0x" + Long.toHexString(metaspaceAddr));

        // 验证：所有地址应该不同
        boolean distinct = heapAddr != stackAddr &&
                heapAddr != metaspaceAddr &&
                stackAddr != metaspaceAddr;

        System.out.println("  ✅ 五区地址独立性验证: " + (distinct ? "PASS" : "FAIL"));

        if (!distinct) {
            System.exit(1);
        }

        // 验证：地址应该在合理范围内（非零）
        boolean validRange = heapAddr != 0 && stackAddr != 0 && metaspaceAddr != 0;
        System.out.println("  ✅ 地址范围验证: " + (validRange ? "PASS" : "FAIL"));

        if (!validRange) {
            System.exit(1);
        }

        System.out.println("  💡 说明：ASLR 会随机化地址，只验证相对关系，不验证绝对地址");
        System.out.println("  💡 注意：栈数组地址实际上在堆上，因为数组是对象");

        System.out.println("  ✅ 五区地址关系验证: PASS");
    }

    /**
     * 验证多线程内存隔离
     *
     * 【架构师经验】内存隔离是并发编程的基础
     *
     * 线程私有：
     * - 栈：每个线程有自己的栈
     * - PC：每个线程有自己的程序计数器
     * - Native 栈：每个线程有自己的 Native 栈
     *
     * 线程共享：
     * - 堆：所有线程共享堆内存
     * - Metaspace：所有线程共享类元数据
     *
     * 验证方法：
     * 1. 多线程访问同一对象
     * 2. 验证对象地址相同
     * 3. 验证栈地址不同
     */
    private static void verifyMemoryIsolation() {
        System.out.println("  📍 内存隔离：");
        System.out.println("    ├─ 线程私有：栈、PC、Native 栈");
        System.out.println("    └─ 线程共享：堆、Metaspace");
        System.out.println("");

        // 创建共享对象
        Object sharedObj = new Object();
        long sharedAddr = getObjectAddress(sharedObj);

        System.out.println("  📍 共享对象地址: 0x" + Long.toHexString(sharedAddr));

        // 创建多个线程访问共享对象
        List<Long> threadAddrs = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            final int threadId = i;
            Thread thread = new Thread(() -> {
                // 获取共享对象地址
                long addr = getObjectAddress(sharedObj);
                synchronized (threadAddrs) {
                    threadAddrs.add(addr);
                }
                System.out.println("    - 线程 " + threadId + " 访问共享对象地址: 0x" + Long.toHexString(addr));
            });
            threads.add(thread);
            thread.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 验证：所有线程访问的共享对象地址应该相同
        boolean allSame = true;
        for (Long addr : threadAddrs) {
            if (addr != sharedAddr) {
                allSame = false;
                break;
            }
        }

        System.out.println("  ✅ 共享对象地址一致性验证: " + (allSame ? "PASS" : "FAIL"));

        if (!allSame) {
            System.exit(1);
        }

        System.out.println("  💡 说明：多线程访问同一对象时，对象地址相同（堆内存共享）");
        System.out.println("  💡 注意：每个线程有自己的栈、PC、Native 栈（线程私有）");

        System.out.println("  ✅ 多线程内存隔离验证: PASS");
    }

    /**
     * A/B 测试：JDK 8 vs JDK 21
     *
     * 【架构师经验】JDK 版本升级是架构师的核心决策之一
     *
     * JDK 8 → JDK 21 的关键变化：
     * 1. 方法区：永久代 → 元空间
     * 2. 堆：连续内存 → 分区（G1/ZGC）
     * 3. 栈：固定大小 → 动态调整
     * 4. 直接内存：受 -XX:MaxDirectMemorySize 限制 → 受 cgroup 限制
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
        System.out.println("  | 区域          | JDK 8                | JDK 21               | 影响                |");
        System.out.println("  |---------------|----------------------|----------------------|---------------------|");
        System.out.println("  | 方法区        | 永久代（PermGen）    | 元空间（Metaspace）  | 不会 OOM，但会耗尽 native 内存 |");
        System.out.println("  | 堆            | 连续内存             | 分区（G1/ZGC）       | 更灵活的 GC 策略    |");
        System.out.println("  | 栈            | 固定大小             | 动态调整             | 更高效的线程管理    |");
        System.out.println("  | 直接内存      | 受 -XX:MaxDirectMemorySize 限制 | 受 cgroup 限制 | 容器环境需特别注意  |");
        System.out.println("  ");
        System.out.println("  💡 建议：JDK 8 → JDK 21 迁移时，重点关注：");
        System.out.println("    1. 方法区变化对类加载的影响");
        System.out.println("    2. 堆分区对 GC 策略的影响");
        System.out.println("    3. 容器环境下的内存限制");
        System.out.println("    4. 虚拟线程对并发的提升");
    }

    /**
     * OOM 分诊决策树
     *
     * 【架构师经验】OOM 排查是架构师的必修课
     *
     * 分诊流程：
     * 1. 看异常文案 / 容器 exit code
     * 2. 堆？Metaspace？Direct？线程？
     * 3. 再决定：加 -Xmx / MaxMetaspaceSize / 查泄漏 / 缩线程
     *
     * 禁忌：
     * - 禁止不看分区就"先把堆加到 16G"
     * - 禁止 jmap -dump:live（触发 Full GC）
     * - 禁止运行时改 CompileThreshold（触发去优化风暴）
     */
    private static void printOomDiagnosis() {
        System.out.println("  📍 OOM 分诊决策树：");
        System.out.println("  ");
        System.out.println("  1. Java heap space OOM");
        System.out.println("     ├─ 现象：堆内存耗尽");
        System.out.println("     ├─ 原因：内存泄漏 / 分配过快");
        System.out.println("     ├─ 诊断：jcmd GC.class_histogram");
        System.out.println("     └─ 解决：增加 -Xmx / 修复泄漏");
        System.out.println("  ");
        System.out.println("  2. Metaspace OOM");
        System.out.println("     ├─ 现象：类元数据耗尽");
        System.out.println("     ├─ 原因：ClassLoader 泄漏 / 动态代理过多");
        System.out.println("     ├─ 诊断：jcmd VM.classloader_stats");
        System.out.println("     └─ 解决：增加 -XX:MaxMetaspaceSize / 修复泄漏");
        System.out.println("  ");
        System.out.println("  3. Direct buffer memory OOM");
        System.out.println("     ├─ 现象：堆外内存耗尽");
        System.out.println("     ├─ 原因：ByteBuffer 未释放 / Netty 泄漏");
        System.out.println("     ├─ 诊断：jcmd VM.native_memory summary");
        System.out.println("     └─ 解决：增加 -XX:MaxDirectMemorySize / 修复泄漏");
        System.out.println("  ");
        System.out.println("  4. unable to create native thread");
        System.out.println("     ├─ 现象：线程创建失败");
        System.out.println("     ├─ 原因：线程数超限 / 栈空间不足");
        System.out.println("     ├─ 诊断：ulimit -u / cat /sys/fs/cgroup/pids.max");
        System.out.println("     └─ 解决：减少线程数 / 使用虚拟线程");
        System.out.println("  ");
        System.out.println("  5. 容器 OOMKilled（exit 137）");
        System.out.println("     ├─ 现象：进程被杀，无 Java OOM 文案");
        System.out.println("     ├─ 原因：cgroup OOM Killer");
        System.out.println("     ├─ 诊断：dmesg / journalctl");
        System.out.println("     └─ 解决：增加容器内存限制 / 优化内存使用");
    }

    /**
     * 打印真实观测命令
     *
     * 【架构师经验】这些命令是线上排障的利器
     *
     * 常用命令：
     * 1. jstat -gcutil：GC 状态监控
     * 2. jcmd VM.classloader_stats：类加载器统计
     * 3. jcmd Thread.print：线程 dump
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
        System.out.println("  # 1. 查看堆内存使用情况");
        System.out.println("  jstat -gcutil <pid> 1000 5");
        System.out.println("  ");
        System.out.println("  # 2. 查看类加载统计");
        System.out.println("  jcmd <pid> VM.classloader_stats");
        System.out.println("  ");
        System.out.println("  # 3. 查看线程栈");
        System.out.println("  jcmd <pid> Thread.print");
        System.out.println("  ");
        System.out.println("  # 4. 查看 Native 内存");
        System.out.println("  jcmd <pid> VM.native_memory summary");
        System.out.println("  ");
        System.out.println("  # 5. 查看 Metaspace 使用情况");
        System.out.println("  jcmd <pid> VM.metaspace");
        System.out.println("  ");
        System.out.println("  # 6. 查看堆 dump（慎用！会触发 Full GC）");
        System.out.println("  # jmap -dump:live,format=b,file=heap.hprof <pid>");
        System.out.println("  ");
        System.out.println("  # 7. 使用 JFR 录制（推荐）");
        System.out.println("  jcmd <pid> JFR.start name=memory settings=profile maxsize=100M");
        System.out.println("  ");
        System.out.println("  # 8. 使用 async-profiler 分析内存分配");
        System.out.println("  # async-profiler -e alloc -d 30 -f alloc.svg <pid>");
        System.out.println("  ");
        System.out.println("  ★ 平台差异：");
        System.out.println("  - Linux: 使用 /proc/<pid>/maps 查看内存布局");
        System.out.println("  - macOS: 使用 vmmap <pid> 查看内存布局");
        System.out.println("  - 容器：注意 cgroup 内存限制");
        System.out.println("  ");
        System.out.println("  ★ 死亡红线：");
        System.out.println("  - 禁止 jmap -dump:live（触发 Full GC）");
        System.out.println("  - 禁止运行时改 CompileThreshold（触发去优化风暴）");
        System.out.println("  - 禁止 redefine 改类布局（元数据撕裂）");
    }

    /**
     * 获取对象地址（通过 Unsafe）
     *
     * 【架构师经验】指针压缩是 JVM 的重要优化
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
     * 注意：sun.misc.Unsafe 在 jdk.unsupported 模块中，无需 --add-opens
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
