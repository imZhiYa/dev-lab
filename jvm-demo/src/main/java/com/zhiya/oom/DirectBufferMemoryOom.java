package com.zhiya.oom;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
/**
 * DirectBufferMemoryOom  生产级复现与诊断
 * <p>
 * 知识库对应：Level 1 · 运行时数据区 / Level 6 · 生产诊断
 * <p>
 * =====================================================
 *  Direct Buffer OOM 排查心得
 * =====================================================
 * <p>
 * 1. Direct Buffer 是什么？
 * - Java NIO 的 ByteBuffer.allocateDirect() 分配的堆外内存
 * - 不在 Java 堆内，不受 -Xmx 限制
 * - 受 -XX:MaxDirectMemorySize 限制（默认约等于 -Xmx）
 * - 容器环境下受 cgroup memory.limit_in_bytes 限制
 * <p>
 * 2. 为什么生产环境容易 OOM？
 * - Netty 默认使用 Direct Buffer → 高并发下快速增长
 * - MappedByteBuffer 映射文件 → 大文件映射消耗大量堆外内存
 * - 未正确 release → Direct Buffer 泄漏
 * - 容器内存限制 → 堆 + Direct Buffer + Metaspace + 线程栈 > cgroup 限制
 * <p>
 * 3. "堆很健康却 OOMKilled" 的根因
 * - Java 堆使用率 50% → 看起来很健康
 * - 但 Direct Buffer + Metaspace + 线程栈 > cgroup 剩余内存
 * - 触发 cgroup OOM Killer → exit 137
 * - 这是容器环境最常见的 OOM 陷阱
 * <p>
 * 4. Direct Buffer 与 Native Memory 的区别
 * - Direct Buffer：NIO ByteBuffer.allocateDirect()
 * - Native Memory：JNI malloc、FFM Arena、Unsafe.allocateMemory()
 * - 两者都不在 Java 堆内，但 Direct Buffer 有 Cleaner 机制
 * - Cleaner 依赖 GC 触发 → 释放时机不确定
 * <p>
 * =====================================================
 * 【A/B 测试】JDK 8 vs JDK 21 Direct Buffer 差异
 * =====================================================
 * <p>
 * | 特性                | JDK 8              | JDK 21             |
 * |---------------------|--------------------|--------------------|
 * | Direct Buffer       | sun.misc.Cleaner   | java.lang.ref.Cleaner |
 * | 释放机制            | GC 触发            | GC 触发（更可靠）  |
 * | FFM API             | 无                 | 有（JEP 424）      |
 * | 内存跟踪            | NMT 部分支持       | NMT 更完善         |
 * | 默认 MaxDirect      | 等于 -Xmx          | 等于 -Xmx          |
 * | 容器感知            | 有限               | 完整               |
 * <p>
 * =====================================================
 * 【实战经验】Direct Buffer OOM 诊断流程
 * =====================================================
 * <p>
 * Step 1: 确认 OOM 类型
 * - 异常信息: java.lang.OutOfMemoryError: Direct buffer memory
 * - 不是 Java heap space → 不要加 -Xmx
 * - 不是 Metaspace → 不要加 -XX:MaxMetaspaceSize
 * <p>
 * Step 2: 查看 Native 内存（零风险）
 * - jcmd <pid> VM.native_memory summary
 * - 看 Internal (Direct ByteBuffer) 部分
 * <p>
 * Step 3: 查看 Direct Buffer 使用
 * - 代码中跟踪 ByteBuffer.allocateDirect() 调用
 * - 检查是否有对应的 Cleaner.clean() / release()
 * <p>
 * Step 4: 查看容器内存分布
 * - cat /sys/fs/cgroup/memory.current
 * - 对比: 堆 + Direct + Metaspace + 线程栈 + CodeCache
 * <p>
 * Step 5: 修复 + 验证
 * - 修复代码 → 灰度验证 → 监控 Native Memory 趋势
 * <p>
 * =====================================================
 * 【实战经验】Direct Buffer 释放机制
 * =====================================================
 * <p>
 * JDK 21 的 Direct Buffer 释放链路:
 * 1. ByteBuffer.allocateDirect() → 分配 native 内存
 * 2. 注册 java.lang.ref.Cleaner（JDK 9+ 替代 sun.misc.Cleaner）
 * 3. ByteBuffer 对象不可达 → GC 触发 → Cleaner 回调 → free native memory
 * <p>
 * 关键问题：释放时机由 GC 控制，不是立即释放！
 * - ByteBuffer 不可达后，可能要等多次 GC 才会释放
 * - 高并发下分配速率 > 释放速率 → Direct Buffer 持续增长
 * <p>
 * 最佳实践：
 * 1. 使用 try-with-resources（如果实现了 AutoCloseable）
 * 2. Netty: 使用 PooledByteBufAllocator + ReferenceCountUtil.release()
 * 3. FFM API（JDK 21）: 使用 Arena.ofAuto() 或 Arena.ofConfined()
 * <p>
 * =====================================================
 * <p>
 * IntelliJ IDEA VM Options:
 * # ========== Direct Buffer 配置（故意设小，快速触发 OOM） ==========
 * -Xms32m -Xmx32m                           # 堆 32MB（故意小，让 Direct Buffer 成为瓶颈）
 * -XX:MaxDirectMemorySize=16m                # Direct Buffer 上限 16MB
 * <p>
 * # ========== OOM 时自动 dump ==========
 * -XX:+HeapDumpOnOutOfMemoryError
 * -XX:HeapDumpPath=/tmp/oom03-direct.hprof
 * <p>
 * # ========== GC 日志（JDK 21 推荐格式） ==========
 * -Xlog:gc*=info:file=/tmp/oom03-gc.log:time,uptime,level,tags
 * <p>
 * 命令行运行:
 * javac -encoding UTF-8 -d target/classes src/main/java/com/zhiya/jvm/gc/Oom03DirectBufferMemory.java
 * java -Xms32m -Xmx32m -XX:MaxDirectMemorySize=16m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/oom03-direct.hprof -Xlog:gc*=info:file=/tmp/oom03-gc.log:time,uptime,level,tags -cp target/classes com.zhiya.jvm.gc.Oom03DirectBufferMemory
 *
 * @author imZhiYa
 * @since JDK 21
 */
public class DirectBufferMemoryOom {

    /**
     * 模拟场景选择
     * <p>
     * 场景 1: ByteBuffer.allocateDirect() 未释放（最常见）
     * 场景 2: Netty 风格的 Direct Buffer 泄漏
     * 场景 3: 堆很健康但容器 OOMKilled（堆外内存总和超限）
     * <p>
     * 使用方法:
     * java ... com.zhiya.jvm.gc.Oom03DirectBufferMemory 1   # 场景 1
     * java ... com.zhiya.jvm.gc.Oom03DirectBufferMemory 2   # 场景 2
     * java ... com.zhiya.jvm.gc.Oom03DirectBufferMemory 3   # 场景 3
     */
    public static void main(String[] args) {

        int scenario = args.length > 0 ? Integer.parseInt(args[0]) : 1;

        System.out.println("☕ OOM03 Direct Buffer Memory 生产级复现");
        System.out.println("=".repeat(60));
        System.out.println("  📍 场景: " + scenario);
        System.out.println("  📍 堆大小: " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + " MB");
        System.out.println("  📍 MaxDirectMemorySize: 可通过 -XX:MaxDirectMemorySize=16m 设置");
        System.out.println("  📍 等待 Direct Buffer OOM 触发...");
        System.out.println("=".repeat(60));
        System.out.println("");

        switch (scenario) {
            case 1:
                scenario1_directBufferLeak();
                break;
            case 2:
                scenario2_nettyStyleLeak();
                break;
            case 3:
                scenario3_heapHealthyButContainerOom();
                break;
            default:
                System.out.println("未知场景: " + scenario);
                System.exit(1);
        }
    }

        /**
         * 场景 1: ByteBuffer.allocateDirect() 未释放
         *
         * 这是 Direct Buffer OOM 最常见的根因
         *
         * 典型代码:
         * while (true) {
         *     ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024);
         *     // 使用 buffer...
         *     // 忘记释放 → native 内存泄漏
         *     // buffer = null; // 这不会立即释放！要等 GC
         * }
         *
         * 诊断方法:
         * 1. jcmd <pid> VM.native_memory summary → 看 Internal (Direct ByteBuffer)
         * 2. 代码搜索 ByteBuffer.allocateDirect() → 检查是否有对应 release
         * 3. JFR: 看 DirectByteBuffer 分配事件
         */
        private static void scenario1_directBufferLeak () {
            System.out.println("  📍 场景 1: ByteBuffer.allocateDirect() 未释放");
            System.out.println("  💡 典型代码: allocateDirect 后不 release，依赖 GC 清理");
            System.out.println("  💡 诊断: jcmd VM.native_memory summary → 看 Direct ByteBuffer");
            System.out.println("");

            List<ByteBuffer> buffers = new ArrayList<>();
            int count = 0;

            try {
                while (true) {
                    // 每次分配 1MB Direct Buffer
                    ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024);
                    buffers.add(buffer);
                    count++;

                    if (count % 4 == 0) {
                        System.out.println("    已分配 Direct Buffer: " + count + " MB");
                    }
                }
            } catch (OutOfMemoryError e) {
                System.out.println("");
                System.out.println("  ❌ OOM 触发: " + e.getMessage());
                System.out.println("  📍 已分配 Direct Buffer: " + count + " MB");
                System.out.println("  📍 泄漏根因: ByteBuffer.allocateDirect() 未释放");
                System.out.println("  💡 修复方案:");
                System.out.println("    1. 使用 try-with-resources（JDK 21 FFM Arena）");
                System.out.println("    2. 手动调用 Cleaner: ((DirectBuffer)buffer).cleaner().clean()");
                System.out.println("    3. 使用 PooledByteBufAllocator（Netty）");
            }
        }

        /**
         * 场景 2: Netty 风格的 Direct Buffer 泄漏
         *
         * Netty 默认使用 Direct Buffer 作为 ByteBuf
         * 如果忘记 release() → native 内存泄漏
         *
         * 典型代码:
         * ByteBuf buf = allocator.directBuffer(1024 * 1024);
         * // 使用 buf...
         * // 忘记 buf.release() → 泄漏
         *
         * Netty 的引用计数机制:
         * - retain(): 引用计数 +1
         * - release(): 引用计数 -1，到 0 时释放 native 内存
         * - 忘记 release() → 引用计数永远 > 0 → 内存泄漏
         *
         * 诊断方法:
         * 1. Netty ResourceLeakDetector.setLevel(Level.PARANOID)
         * 2. jcmd <pid> VM.native_memory summary
         */
        private static void scenario2_nettyStyleLeak () {
            System.out.println("  📍 场景 2: Netty 风格的 Direct Buffer 泄漏");
            System.out.println("  💡 典型代码: ByteBuf.directBuffer() 后不 release()");
            System.out.println("  💡 诊断: ResourceLeakDetector.setLevel(PARANOID)");
            System.out.println("");

            List<ByteBuffer> buffers = new ArrayList<>();
            int count = 0;

            try {
                while (true) {
                    // 模拟 Netty 的 directBuffer（使用 ByteBuffer.allocateDirect）
                    ByteBuffer buffer = ByteBuffer.allocateDirect(512 * 1024);

                    // 模拟使用 buffer（写入一些数据）
                    buffer.put(new byte[128]);
                    buffer.flip();

                    // 模拟忘记 release → 泄漏
                    buffers.add(buffer);
                    count++;

                    if (count % 8 == 0) {
                        System.out.println("    已分配 Direct Buffer: " + (count / 2) + " MB");
                    }
                }
            } catch (OutOfMemoryError e) {
                System.out.println("");
                System.out.println("  ❌ OOM 触发: " + e.getMessage());
                System.out.println("  📍 已分配 Direct Buffer: " + (count / 2) + " MB");
                System.out.println("  📍 泄漏根因: ByteBuf 未 release（引用计数泄漏）");
                System.out.println("  💡 修复方案:");
                System.out.println("    1. finally 块中 buf.release()");
                System.out.println("    2. 开启 Netty ResourceLeakDetector");
                System.out.println("    3. 使用 try-with-resources（如果 ByteBuf 实现了 AutoCloseable）");
            }
        }

        /**
         * 场景 3: 堆很健康但容器 OOMKilled
         *
         * 这是容器环境最常见的 OOM 陷阱
         *
         * 根因分析:
         * - Java 堆使用率 50% → 看起来很健康
         * - 但 Direct Buffer + Metaspace + 线程栈 > cgroup 剩余内存
         * - 触发 cgroup OOM Killer → exit 137
         *
         * 内存分布公式:
         * Total = Heap(-Xmx) + Metaspace + DirectBuffer + 线程栈(-Xss * 线程数) + CodeCache + ...
         *
         * 如果 Total > cgroup memory.limit_in_bytes → OOMKilled
         *
         * 诊断方法:
         * 1. cat /sys/fs/cgroup/memory.current → 看总内存
         * 2. jcmd <pid> VM.native_memory summary → 看各部分
         * 3. 对比: 堆 + Direct + Metaspace + 线程栈 + CodeCache
         *
         * 修复方案:
         * 1. 增加容器内存限制
         * 2. 减小 -Xmx（给堆外内存留空间）
         * 3. 减少线程数（虚拟线程）
         * 4. 修复 Direct Buffer 泄漏
         */
        private static void scenario3_heapHealthyButContainerOom () {
            System.out.println("  📍 场景 3: 堆很健康但容器 OOMKilled");
            System.out.println("  💡 根因: 堆外内存总和 > cgroup 剩余内存");
            System.out.println("  💡 诊断: jcmd VM.native_memory summary → 看各部分总和");
            System.out.println("");

            // 展示内存分布计算
            Runtime runtime = Runtime.getRuntime();
            long heapMax = runtime.maxMemory();
            long heapUsed = runtime.totalMemory() - runtime.freeMemory();

            System.out.println("  📍 当前内存分布:");
            System.out.println("    - 堆最大: " + heapMax / 1024 / 1024 + " MB");
            System.out.println("    - 堆已用: " + heapUsed / 1024 / 1024 + " MB");
            System.out.println("    - 堆使用率: " + (heapUsed * 100 / heapMax) + "%");
            System.out.println("");
            System.out.println("  💡 堆外内存（需要通过 jcmd VM.native_memory 查看）:");
            System.out.println("    - Metaspace: 通常 20~100 MB");
            System.out.println("    - Direct Buffer: 可能几百 MB（Netty 高并发）");
            System.out.println("    - 线程栈: -Xss * 线程数（默认 1MB * N）");
            System.out.println("    - CodeCache: 通常 240 MB");
            System.out.println("");
            System.out.println("  💡 容器内存计算公式:");
            System.out.println("    Total = Heap + Metaspace + DirectBuffer + 线程栈 + CodeCache + ...");
            System.out.println("    如果 Total > cgroup memory.limit_in_bytes → OOMKilled (exit 137)");
            System.out.println("");

            // 模拟：堆使用率很低，但不断分配 Direct Buffer
            List<ByteBuffer> buffers = new ArrayList<>();
            int count = 0;

            try {
                while (true) {
                    ByteBuffer buffer = ByteBuffer.allocateDirect(2 * 1024 * 1024);
                    buffers.add(buffer);
                    count++;

                    // 堆使用率很低（Direct Buffer 不占堆）
                    long currentHeapUsed = runtime.totalMemory() - runtime.freeMemory();
                    long currentHeapMax = runtime.maxMemory();

                    if (count % 4 == 0) {
                        System.out.printf("    Direct: %d MB, 堆使用率: %d%% (看起来很健康!)\n",
                                count * 2, currentHeapUsed * 100 / currentHeapMax);
                    }
                }
            } catch (OutOfMemoryError e) {
                System.out.println("");
                System.out.println("  ❌ OOM 触发: " + e.getMessage());
                System.out.println("  📍 Direct Buffer 已分配: " + count * 2 + " MB");
                System.out.println("  📍 但堆使用率仍然很低！→ 这就是'堆很健康却 OOMKilled'的根因");
                System.out.println("  💡 修复方案:");
                System.out.println("    1. 增加容器内存限制");
                System.out.println("    2. 减小 -Xmx（给堆外内存留空间）");
                System.out.println("    3. 设置 -XX:MaxDirectMemorySize 限制 Direct Buffer");
                System.out.println("    4. 减少线程数（使用虚拟线程）");
                System.out.println("    5. 修复 Direct Buffer 泄漏");
            }
        }
    }

