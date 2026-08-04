# ⚡ JMH 微基准测试套件 (benchmarks)

微服务时代不能光凭"直觉"和"我看博客上说"来评判底层 API 的快慢。本模块引入了工业级基准测试框架 **JMH (Java Microbenchmark Harness)**，用最严谨的数据回答各种技术争论。

这里的测试涵盖了位运算、并发锁降级机制、同步容器 vs 并发容器、网络 IO 模型等极具争议性的话题。

> 本项目是学习实验，不是权威 benchmark 报告。任何结论都有场景边界（见下方"测试边界"），请勿把局部数据外推为普适真理。

## 🧪 实验模块与能力矩阵

| 测试套件文件 | 测试对抗组 | 核心验证重点 |
| :--- | :--- | :--- |
| `BitwiseBenchmark.java` | **位运算 vs 算术** | 在乘除法、取模等场景下，编译器是否会自动优化算术指令？位运算到底能拉开多大差距？ |
| `TreeBenchmark.java` | **4 种树形结构** | 测定 `BST`, `MaxHeap`, `MinHeap`, `RedBlackTree` 在 1K / 10K / 100K 规模下的插入耗时雪崩曲线。 |
| `TreeBenchmarkDiagnostic.java` | **BST 单档深入** | 专门针对 `BST` 在命中与未命中场景中的 CPU 缓存预读与分支预测开销。 |
| `SyncVsAqsBenchmark.java` | **并发全类型锁对抗** | `synchronized` vs `ReentrantLock` vs `StampedLock` vs `LongAdder`。验证在强竞争下"伪共享消除"与"无锁乐观读"的霸主地位。 |
| `ArrayListVsLinkedListBenchmark.java` | **双雄之争** | 用数据粉碎面试八股文——为什么 `LinkedList` 在现代 CPU 缓存行机制下几乎全方位落败？ |
| `MapBenchmark.java` | **三大 Map 对比** | `HashMap` / `TreeMap` / `LinkedHashMap` 操作全维度压测。 |
| `ConcurrentMapBenchmark.java` | **并发 Map** | 9读1写 vs 5读5写。比拼 `ConcurrentHashMap` 与 `Collections.synchronizedMap`。 |
| `DequeBenchmark.java` | **栈队列选择** | 验证 `ArrayDeque` 作为栈和队列如何将 `LinkedList` 按在地上摩擦。 |
| `SetAndEnumMapBenchmark.java` | **特定结构降维打击** | `EnumSet` 的 `O(1)` 极速与普通 `HashSet` 的差距。 |
| `CopyOnWriteAndSkipListBenchmark.java` | **高级并发结构** | `CopyOnWrite` 变态的写时复制惩罚曲线，以及无锁跳表 `ConcurrentSkipListMap` 的优异读写平衡。 |
| `SynchronizedMapVsConcurrentMapBenchmark.java` | **终极综合比对** | 验证全局锁导致的总线风暴问题。 |
| `BioVsNioLoopbackBenchmark.java` | **BIO vs NIO 本机回环** | 单连接、单线程 loopback 下"完整请求写出 + 完整响应读回"的往返成本：阻塞 read/write 的线程陪等 vs Selector readiness 驱动。 |

## 🌐 BioVsNioLoopbackBenchmark 设计边界

该套件对比的是**本机 127.0.0.1、固定长连接、单连接低并发**场景下的局部 request-response 成本：

- BIO：工作线程在阻塞 `read`/`write` 中承担等待（连接 worker 陪等模型）
- NIO：`Selector` 集中领取 `OP_ACCEPT` / `OP_READ` / 按需 `OP_WRITE`（readiness 驱动模型）
- 协议：4 字节长度字段 + 定长 payload，一次 benchmark 操作 = 一轮完整请求写出 + 完整响应读回
- 连接创建、线程启动、Selector 初始化全部在 `@Setup` 中完成，**不计入** benchmark 耗时

**不能由本基准断言**（也请勿这样引用）：
- 不能据此宣称 NIO 在所有场景下都比 BIO 快
- 不覆盖公网延迟、海量空闲连接容量、慢客户端背压、下游 DB/RPC 场景
- 服务端线程模型、连接数、并发度变化时结论可能反转

## 🚀 编译与执行

JMH 必须打成胖包 (Fat Jar) 以防止类加载和 JIT 编译器的干扰，推荐使用环境：**JDK 8**。

```bash
mvn clean package -DskipTests
```

> 注意：`pom.xml` 通过 `build-helper-maven-plugin` 把 `../tree-demo/src/main/java`（BST / MaxHeap / MinHeap / RedBlackTree / BPlusTree / SkipList / Trie 等）作为额外源码根一并编译打包，所以 `mvn package` 一条命令即可产出含全部 benchmark 的 `target/benchmarks.jar`。

```bash
# 运行全维度锁对抗基准（例如开 8 线程并发，预热 1 轮，迭代 3 轮）
java -jar target/benchmarks.jar "SyncVsAqsBenchmark.*" -p n=1000 -t 8 -wi 1 -i 3 -f 1

# 运行树形结构基准
java -jar target/benchmarks.jar "TreeBenchmark.*" -p n=10000 -t 1 -wi 1 -i 3 -f 1

# 运行 BIO vs NIO 本机回环基准（3 档 payload，单线程）
java -jar target/benchmarks.jar "com.zhiya.benchmark.BioVsNioLoopbackBenchmark.*" \
  -p payloadBytes=16,256,4096 -t 1 -wi 1 -i 3 -f 1 -foe true
```

## 🤖 CI 集成

仓库的 `verify-lab.yml` 流水线（JDK 8 专区）会自动编译本模块并执行三组跑分：`TreeBenchmark`、`SynchronizedMapVsConcurrentMapBenchmark`、`BioVsNioLoopbackBenchmark`，完整日志分别落盘为 `tree-benchmark.log`、`sync-vs-concurrent.log`、`bio-vs-nio-loopback.log`，作为每次 main 分支提交的"性能回归看门狗"。
