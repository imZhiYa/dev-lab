# 🧬 dev-lab · 代码验证实验室

**Code Verification Lab — Binary & Tree & AQS & Memory & Benchmark**

_知识库讲原理，这里写代码验证_

[![MIT License](https://img.shields.io/badge/license-MIT-green?style=flat-square)](./LICENSE)
![Language: Java 8 + Java 21 + C++ 20](https://img.shields.io/badge/language-Java%208%20%2F%2021%20%2B%20C%2B%2B%2020-orange?style=flat-square)
[![Powered by tech-knowledge-docs](https://img.shields.io/badge/powered_by-tech--knowledge--docs-blue?style=flat-square)](https://github.com/imZhiYa/tech-knowledge-docs)
[![CI](https://github.com/imZhiYa/dev-lab/actions/workflows/verify-lab.yml/badge.svg)](https://github.com/imZhiYa/dev-lab/actions/workflows/verify-lab.yml)

---

## 🎯 关于本仓库

`dev-lab` 是 [tech-knowledge-docs](https://github.com/imZhiYa/tech-knowledge-docs) 知识库的**代码验证配套项目**——知识库讲原理，这里负责把能用代码验证的理论结论落地为**可运行、可量化、可对照**的最小可执行实现。

> 📚 **知识库** → 讲原理、推导、极端场景  
> 🧬 **dev-lab** → 写代码落地验证

每个模块遵循三个原则：

- **零外部依赖**（除 JMH）：每个 `.java` / `.cpp` 文件都能 `javac && java` / `c++` 单文件直接跑
- **自包含 `main`**：每个类、每个源文件都是独立的验证 demo，控制台输出可重放
- **可量化**：关键操作有对应的 JMH 微基准（纳秒级）或 `/proc`、`libproc` 系统观测（字节级）

---

## 📐 目录结构

```text
dev-lab/
│
├── aqs-demo/                          # 🔴 AQS 抽象队列同步器 & 并发协作原语
│   └── src/main/java/com/zhiya/aqs/
│       ├── AqsLevel1StateAndContentionDemo.java      # Level 1: state 状态与 CAS 竞态推演
│       ├── AqsLevel2FairQueueDemo.java               # Level 2/3: CLH 双向队列与公平/非公平锁
│       ├── AqsLevel4BoundedBufferDemo.java           # Level 4: Condition 条件队列与有界缓冲区
│       ├── AqsLevel4ConditionReacquisitionDemo.java  # Level 4: Condition 唤醒后重新获取锁
│       ├── AqsLevel4ConditionSignalStrategyDemo.java # Level 4: Condition signal/signalAll 唤醒策略
│       ├── AqsLevel5CancellationAndInterruptionDemo.java # Level 5: 节点取消与中断响应机制
│       ├── AqsLevel5SharedPermitPropagationDemo.java # Level 5: 共享模式 Permit 传播与唤醒
│       ├── AqsLevel5CoordinationPrimitiveSelectionDemo.java # Level 5: 线程协作原语选型比对
│       ├── CountDownLatchDemo.java                   # CountDownLatch 倒计数器实战
│       ├── CyclicBarrierDemo.java                    # CyclicBarrier 循环栅栏实战
│       ├── PhaserDemo.java                           # Phaser 多阶段栅栏实战
│       └── SemaphoreDemo.java                        # Semaphore 信号量实战
│
├── binary-demo/                       # 📐 二进制 & 位运算
│   └── src/main/java/com/zhiya/binary/
│       ├── BinaryUtils.java                          # 进制转换、补码、位运算工具集
│       ├── BloomFilterBitMapGuard.java              # 布隆过滤器 & 位图防缓存穿透
│       ├── ConsistentHashBinaryRing.java             # 一致性哈希环（虚拟节点防倾斜）
│       ├── DynamicStateGuard.java                    # 位运算状态机（订单状态流转）
│       ├── GeohashBitwiseSpatialIndex.java          # 经纬度 64 位编码、Base32 GeoHash
│       ├── HyperLogLogBitwiseEstimator.java         # 基数估计（亿级 UV 去重）
│       ├── LeetCodeBitwiseClassics.java             # LeetCode 位运算经典题解
│       └── SnowflakeBitwiseGenerator.java           # 雪花算法 ID 合成
│
├── tree-demo/                         # 🌳 树形数据结构
│   └── src/main/java/com/zhiya/tree/
│       ├── BST.java                                  # 二叉搜索树
│       ├── BTree.java                                # B 树（阶=3）
│       ├── BPlusTree.java                            # B+ 树
│       ├── MaxHeap.java                              # 最大堆（数组实现）
│       ├── MinHeap.java                              # 最小堆（数组实现）
│       ├── NonRecursiveTraversal.java                # 非递归遍历 5 种
│       ├── RedBlackTree.java                         # 红黑树（泛型版）
│       ├── SkipList.java                             # 无锁并发跳表
│       └── Trie.java                                 # 字典树
│
├── benchmarks/                        # ⚡ JMH 微基准测试套件
│   └── src/main/java/com/zhiya/benchmark/
│       ├── BitwiseBenchmark.java                     # 位运算性能基准
│       ├── TreeBenchmark.java                        # 树形数据结构基准（参数化）
│       ├── TreeBenchmarkDiagnostic.java              # 诊断版（排查异常差异）
│       └── SyncVsAqsBenchmark.java                   # 锁基准：synchronized vs AQS vs StampedLock vs LongAdder
│
├── virtual-memory-demo/               # 🧠 虚拟内存 & OS 内存机制（C++20 · 8 个公审实验）
│   ├── include/vm_probe.h                            # 跨平台观测层（Linux /proc + macOS libproc）
│   ├── src/vm01_*.cpp ~ vm08_*.cpp                   # 单文件自包含，命名即知识库 Level 序号
│   ├── CMakeLists.txt / Makefile                     # 标准道 + 零依赖快道
│   └── README.md                                     # 指路牌
│
├── jvm-demo/                          # ☕ JVM 配置 & OOM 演示（JDK 21 + GC 矩阵）
│   └── src/main/java/com/zhiya/
│       ├── runtime/                                  # Jvm01 Runtime Data Area
│       ├── classloading/                             # Jvm02 Class Loading（含 System.exit 校验）
│       ├── object/                                   # Jvm03 Object Layout & TLAB
│       ├── sync/                                     # Jvm04 锁升级
│       ├── gc/                                       # Jvm05/Jvm06 GC 日志 & 监控
│       └── oom/                                      # OOM 复现与诊断
│           ├── HeapSpaceOom.java
│           ├── MetaspaceOom.java                     # ★ 核心：JDK 动态代理类无限生成（带 ClassLoader 强引用）
│           ├── DirectBufferMemoryOom.java
│           └── GcOverheadLimitOom.java
│
├── scripts/                           # 运维 & CI 自动化公审脚本引擎
│   ├── verify-jdk8-demos.sh                          # JDK 8 专区智能增量构建与公审脚本
│   ├── verify-jdk21-demos.sh                         # JDK 21 专区 JVM 诊断与 OOM 公审脚本
│   └── verify-cpp20-demos.sh                         # C++20 专区虚拟内存实验公审脚本
│
├── .github/workflows/
│   └── verify-lab.yml                 # 🔄 Polyglot Matrix CI: 路径检测 + 多技术栈 Job 矩阵并行公审
│
├── .gitignore
├── LICENSE                            # 📜 MIT
└── README.md                          # 📖 你正在看
```

---

## 🧪 已实现能力矩阵

### 🔴 AQS · 抽象队列同步器与并发原语（12 个 Demo）

| 文件 | 知识点 Level | 验证内容 |
|---|---|---|
| `AqsLevel1StateAndContentionDemo.java` | Level 1 | `state` 状态管理、CAS 竞态与 `ReentrantLock` 基础 |
| `AqsLevel2FairQueueDemo.java` | Level 2/3 | CLH 双向队列结构、公平锁（Strict FIFO）与非公平锁（插队）比对 |
| `AqsLevel4BoundedBufferDemo.java` | Level 4 | `Condition` 条件队列与无锁/阻塞有界缓冲区实现 |
| `AqsLevel4ConditionReacquisitionDemo.java` | Level 4 | `Condition.await()` 唤醒后重新获取锁的响应机制 |
| `AqsLevel4ConditionSignalStrategyDemo.java` | Level 4 | `signal()` 与 `signalAll()` 唤醒策略与谓词校验 |
| `AqsLevel5CancellationAndInterruptionDemo.java` | Level 5 | CLH 节点 `CANCELLED` 状态、`lock()` 与 `lockInterruptibly()` 中断响应 |
| `AqsLevel5SharedPermitPropagationDemo.java` | Level 5 | AQS 共享模式 `PROPAGATE` 广播机制与 `AqsSharedPermitSynchronizer` |
| `AqsLevel5CoordinationPrimitiveSelectionDemo.java` | Level 5 | 线程协作原语选型比对（`CountDownLatch` vs `CyclicBarrier` vs `Phaser`） |
| `CountDownLatchDemo.java` | JUC 协作原语 | 闭锁倒计数器、不可重置机制、多 waiter 响应 |
| `CyclicBarrierDemo.java` | JUC 协作原语 | 循环栅栏、屏障破损（BrokenBarrierException）、超时响应与重置 |
| `PhaserDemo.java` | JUC 协作原语 | 动态注册/注销 Phase 栅栏、多阶段同步推进 |
| `SemaphoreDemo.java` | JUC 协作原语 | 共享信号量 Permit 争用、批量 `acquire(n)`/`release(n)`、公平性机制 |

### 🟢 Binary · 位运算实战（8 个文件）

| 文件 | 知识点 | 验证内容 |
|---|---|---|
| `BinaryUtils.java` | 二进制底层思维 | 进制转换、补码运算、位操作工具集 |
| `BloomFilterBitMapGuard.java` | 位图 + 哈希 | 布隆过滤器、缓存穿透防护 |
| `ConsistentHashBinaryRing.java` | 分布式哈希 | 一致性哈希环、虚拟节点防数据倾斜 |
| `DynamicStateGuard.java` | 状态压缩 | 订单状态流转的位运算状态机 |
| `GeohashBitwiseSpatialIndex.java` | 空间索引 | 经纬度 64 位编码、Base32 GeoHash |
| `HyperLogLogBitwiseEstimator.java` | 基数估计 | 概率性 UV 去重，标准误差 ~0.81% |
| `LeetCodeBitwiseClassics.java` | 面试算法 | LeetCode 位运算经典题解 |
| `SnowflakeBitwiseGenerator.java` | 分布式 ID | 雪花算法位运算版 |

### 🟢 Tree · 树形数据结构（9 个文件）

| 文件 | 知识点 | 验证内容 |
|---|---|---|
| `BST.java` | 二叉搜索树 | 增删查、三种删除情况、合法性验证 |
| `BTree.java` | 多路搜索树 | B 树（阶=3）的插入分裂、查找 |
| `BPlusTree.java` | B 树变体 | B+ 树节点分裂、范围查询友好 |
| `MaxHeap.java` | 完全二叉堆 | 上浮/下沉、堆排序 |
| `MinHeap.java` | 完全二叉堆 | 上浮/下沉、堆排序 |
| `NonRecursiveTraversal.java` | 遍历技巧 | 前/中/后序（单栈+双栈）+ BFS |
| `RedBlackTree.java` | 自平衡 BST | 5 条不变式、旋转、泛型 K/V |
| `SkipList.java` | 概率平衡 | 无锁 CAS 插入、层级跳跃 |
| `Trie.java` | 前缀树 | 插入/查找/前缀匹配/删除 |

### ⚡ Benchmark · JMH 微基准（4 个测试套件）

| 文件 | 测试对象 | 用途 |
|---|---|---|
| `BitwiseBenchmark.java` | 位运算 vs 算术 | 算术替代、掩码聚合、HashMap 容量对齐 |
| `TreeBenchmark.java` | BST / MaxHeap / MinHeap / RedBlackTree | 1K / 10K / 100K 三档规模基准 |
| `TreeBenchmarkDiagnostic.java` | BST（单档 n=10000） | 排查 hit/miss 路径异常差异的对照实验 |
| `SyncVsAqsBenchmark.java` | `synchronized` vs AQS vs `StampedLock` vs `LongAdder` | 全维度并发锁、读写偏置与无锁 CAS 压测 |

### 🟢 Memory · OS 内存机制（8 个实验 · C++20 · 断言自校验）

| 文件 | 知识点 | 验证内容 |
|---|---|---|
| `vm01_address_space_layout.cpp` | 进程地址空间 | Text/Data/BSS/Heap/Stack 真实排序，ASLR 只动基址不动次序 |
| `vm02_base_limit_translation.cpp` | Base+Limit 翻译 | 翻译语义 + 越界拒绝（段错误由来）+ 双进程隔离实证 |
| `vm03_page_table_walk.cpp` | PTE 与四级页表 | 文档推演值逐一钉死：48 位单级 512GB 灾难 vs 四级 40KB 救赎 |
| `vm04_demand_paging.cpp` | 按需调页 | vsize/RSS/minflt 三闸门实证「先答应后真调」；含 THP 校正器 |
| `vm05_tlb_sensitivity.cpp` | TLB 容量边界 | 工作集阶梯扫描：16 页 → 16384 页，访存单价逐级跳档 |
| `vm06_copy_on_write.cpp` | fork + COW | 誊抄计数一页不差 + RSS 双账本（Linux VmRSS / macOS 独占页） |
| `vm07_page_replacement.cpp` | 页面置换 | FIFO 的 Bélády 异常 vs LRU 的栈性质，CLOCK 近似收益 |
| `vm08_ept_nested_walk.cpp` | EPT/NPT 虚拟化 | 双层翻译最坏 20~24 次、整箱打包压缩、TLB 救赎 |

### ☕ JVM 演示（JDK 21 + GC 矩阵 + OOM 复现）

| 模块 | 重点 | 说明 |
|---|---|---|
| `jvm-demo/` | JDK 21 + GC 矩阵诊断 | 10 个 demo（6 正常 + 4 OOM），支持 G1 / ZGC |
| `MetaspaceOom.java` | **★ 绝对核心演示** | JDK 动态代理类无限生成（带 ClassLoader 强引用，精准复现元空间 OOM） |
| `verify-jdk21-demos.sh` | 执行入口 | 自动编译并调用 `run-jvm-demos.sh` 生成 `summary-*.txt` 核心分析报告 |

---

## 📊 基准测试成绩

> 🖥️ **环境 A（并发锁矩阵）**：GitHub Actions `ubuntu-latest` · JDK 8 (Temurin) · **8 线程并发压测**  
> ⚙️ **命令**：`java -jar benchmarks/target/benchmarks.jar SyncVsAqsBenchmark`

### 🔒 锁与并发原语基准跑分对比 (SyncVsAqsBenchmark · JDK 8 · 8 线程并发)

| 锁/同步原语模式 | 临界区耗时 (tokens) | 平均耗时 (ns/op) | 架构原理解读 |
|---|---:|---:|---|
| **`write_LongAdder`** (分段 Cell CAS) | 10 | **8.15** | 消除伪共享：Cell[] 数组分散写竞争，无 CPU 总线 Lock 信号 |
| **`rw91_stamped_optimistic_read`** (乐观读) | 10 | **16.42** | 无 CAS 写操作：validate 屏障校验，多读线程零写竞争 |
| **`rw91_rwlock_read`** (AQS 读锁) | 10 | **62.30** | AQS 共享读：状态高低位拆分，多核 CAS 修改 state 导致 Cache 刷新 |
| **`write_ReentrantLockNonFair`** (非公平锁) | 10 | **184.15** | AQS Fast-path CAS 抢占：允许新线程插队，提高 CPU 缓存命中率 |
| **`write_Synchronized`** (JVM Monitor) | 10 | **212.80** | 锁膨胀机制：高争用下膨胀为 OS Mutex，带来上下文切换 |
| **`write_ReentrantLockFair`** (公平锁) | 10 | **1140.25** | 强制 CLH FIFO 排队：检查 precursor 并强制挂起/唤醒线程 |

---

> 🖥️ **环境 B（单线程数据结构）**：GitHub Actions `ubuntu-latest` · JDK 8 (Temurin) · **1 线程**  
> ⚙️ **命令**：`java -jar benchmarks/target/benchmarks.jar "TreeBenchmark.*" -p n=10000 -t 1`  
> ⏱️ **测量**：`@Warmup(3, 1s) @Measurement(5, 1s) @Fork(1)`

### 🌳 BST（二叉搜索树 · n=10K）

| 操作 | 耗时 | 单位 |
|---|---:|---|
| `bstSearchHit`（命中） | 0.093 | μs/op |
| `bstSearchMiss`（未命中） | 0.016 | μs/op |
| `bstBulkInsert`（批量建树） | 867.851 | μs/op |

### ⛰️ 堆（数组实现 · n=10K）

| 操作 | 耗时 | 单位 |
|---|---:|---|
| `maxHeapPeek` | 0.003 | μs/op |
| `maxHeapExtract` | 74.976 | μs/op |
| `maxHeapBulkInsert`（无参构造） | 108.776 | μs/op |
| `maxHeapBulkInsertPrealloc`（预分配） | 84.912 | μs/op |
| `minHeapPeek` | 0.003 | μs/op |
| `minHeapExtract` | 109.689 | μs/op |
| `minHeapBulkInsert` | 111.805 | μs/op |

### 🔴 红黑树（泛型 · n=10K）

| 操作 | 耗时 | 单位 |
|---|---:|---|
| `rbtBulkPut`（新插入） | 1484.214 | μs/op |
| `rbtPutUpdate`（覆盖已存在 key） | 2908.153 | μs/op |

> 📌 **数据会随 CI 浮动**。想看最新数据：在 Actions 页面跑一次 workflow，或本地 `mvn clean package -DskipTests` 后跑 jar。  
> 📌 **更多档位（1K / 100K）**：TreeBenchmark 内部用 `@Param` 展开，去掉 `-p n=10000` 即可跑全档。  
> 📌 **Memory 实验的参考形态**：断言只看形态不看绝对值 —— Linux 4KB 页下，vm04 分配后 RSS +0.00MB → 触碰后 +128MB、缺页 ≈32768 次；vm07 FIFO 9→10 次缺页（Bélády 异常成立）。

---

## 🚀 如何使用

### 0. 克隆代码

```bash
git clone https://github.com/imZhiYa/dev-lab.git
cd dev-lab
```

### 1. 跑 AQS 框架 & 协作原语类（12 个 Demo）

```bash
cd aqs-demo
mkdir -p target/classes
find src/main/java -name "*.java" | xargs javac -encoding UTF-8 -d target/classes

# 跑 AQS 锁与 CLH 队列推演
java -cp target/classes com.zhiya.aqs.AqsLevel1StateAndContentionDemo
java -cp target/classes com.zhiya.aqs.AqsLevel2FairQueueDemo

# 跑 JUC 协作原语
java -cp target/classes com.zhiya.aqs.CountDownLatchDemo
java -cp target/classes com.zhiya.aqs.SemaphoreDemo
# ... 其他 Demo 同理
```

### 2. 跑 Binary 位运算类（8 个 Demo）

```bash
cd binary-demo
mkdir -p target/classes
find src/main/java -name "*.java" | xargs javac -d target/classes

java -cp target/classes com.zhiya.binary.BinaryUtils
java -cp target/classes com.zhiya.binary.HyperLogLogBitwiseEstimator
```

### 3. 跑 Tree 树形结构类（9 个 Demo）

```bash
cd tree-demo
mkdir -p target/classes
find src/main/java -name "*.java" | xargs javac -d target/classes

java -cp target/classes com.zhiya.tree.BST
java -cp target/classes com.zhiya.tree.RedBlackTree
```

### 4. 跑 JMH 微基准测试

```bash
cd benchmarks
mvn clean package -DskipTests

# 跑全维度 synchronized vs AQS vs StampedLock vs LongAdder 锁基准测试 (8 线程)
java -jar target/benchmarks.jar SyncVsAqsBenchmark

# 跑位运算基准
java -jar target/benchmarks.jar BitwiseBenchmark

# 跑树形数据结构基准
java -jar target/benchmarks.jar TreeBenchmark
```

### 5. 跑 Memory 内存实验（8 个 Demo · C++20）

```bash
# 零依赖快道：编译 8 个实验并公审
make -C virtual-memory-demo run
```

### 6. 跑 JVM 诊断与 OOM 演示（JDK 21 + G1/ZGC 矩阵）

```bash
# 通过统一入口脚本运行
chmod +x scripts/verify-jdk21-demos.sh
./scripts/verify-jdk21-demos.sh

# 查看核心日志与报告
cat /tmp/jvm-demo-logs/summary-G1.txt
```

---

## 🧩 与 tech-knowledge-docs 的对应

每个验证文件背后都对应知识库（[tech-knowledge-docs](https://github.com/imZhiYa/tech-knowledge-docs)）的一篇原理推导：

| 知识库文档 (tech-knowledge-docs) | 代码验证文件 / 模块 (dev-lab) |
|---|---|
| `docs/03-concurrency/🔐 AQS 核心机制深度解析.md` | `aqs-demo/` 全量 12 个 AQS 机制与 JUC 协作原语 Demo |
| `binary/01-二进制底层思维与位运算.md` | `BinaryUtils.java` + `LeetCodeBitwiseClassics.java` |
| `binary/02-位图与布隆过滤器.md` | `BloomFilterBitMapGuard.java` |
| `binary/03-一致性哈希环.md` | `ConsistentHashBinaryRing.java` |
| `binary/04-位运算状态机.md` | `DynamicStateGuard.java` |
| `binary/05-GeoHash 空间索引.md` | `GeohashBitwiseSpatialIndex.java` |
| `binary/06-HyperLogLog 基数估计.md` | `HyperLogLogBitwiseEstimator.java` |
| `binary/07-雪花算法.md` | `SnowflakeBitwiseGenerator.java` |
| `data-structures/🌳 树形数据结构.md` | `tree-demo/` 全部 9 个文件 |
| `benchmark/JMH 微基准方法论.md` | `SyncVsAqsBenchmark.java` + `BitwiseBenchmark.java` + `TreeBenchmark.java` |
| `os-memory/🧠 虚拟内存.md` | `virtual-memory-demo/` 全部 8 个实验 |

---

## 🤝 贡献

欢迎通过以下方式参与：

- 🐛 **Issue**：发现 bug、文档错漏、CI 异常 → 提 Issue
- 🔧 **PR**：新数据结构、新基准维度、新位运算技巧、新系统机制实验 → Fork + PR
- 📊 **数据反馈**：跑出不同机器/不同 JDK 的基准数据，贴 Issue 一起讨论

---

## 📜 许可证

[MIT License](./LICENSE) © imZhiYa

**[⬆ 回到顶部](#-dev-lab--代码验证实验室)**

Made with 🧬 by [imZhiYa](https://github.com/imZhiYa)
