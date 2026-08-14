# 🧬 dev-lab · 代码验证实验室

**Code Verification Lab — Database & Memory & Concurrency & Collection & JVM & Cache & Network & Benchmark**

_知识库讲原理，这里写代码验证_

[![MIT License](https://img.shields.io/badge/license-MIT-green?style=flat-square)](./LICENSE)
![Language: Java 8 + Java 21 + C++ 20](https://img.shields.io/badge/language-Java%208%20%2F%2021%20%2B%20C%2B%2020-orange?style=flat-square)
[![Powered by tech-knowledge-docs](https://img.shields.io/badge/powered_by-tech--knowledge--docs-blue?style=flat-square)](https://github.com/imZhiYa/tech-knowledge-docs)
[![CI](https://github.com/imZhiYa/dev-lab/actions/workflows/verify-lab.yml/badge.svg)](https://github.com/imZhiYa/dev-lab/actions/workflows/verify-lab.yml)

---

## 🎯 关于本仓库

`dev-lab` 是 [tech-knowledge-docs](https://github.com/imZhiYa/tech-knowledge-docs) 知识库的**代码验证配套项目**——知识库讲原理，这里负责把能用代码验证的理论结论落地为**可运行、可量化、可对照**的最小可执行实现。

> 📚 **知识库** → 讲原理、推导、极端场景
> 🧬 **dev-lab** → 写代码落地验证

每个模块遵循三个原则：
- **零外部依赖**（除 JMH）：每个 `.java` / `.cpp` 文件都能单文件直接跑。
- **自包含 `main`**：每个源文件都是独立的验证 demo，控制台输出可重放。
- **可量化**：关键操作有对应的 JMH 微基准或系统级观测。

---

## 🧭 架构演进说明

为了保证主文档的极简可读性，本仓库采用**“高内聚领域隔离”**架构。
主 `README.md` **不再铺陈任何源码文件级别的目录结构或对应关系**，而是仅作为**入口路由**。

如果你想查看某个具体模块（例如 InnoDB、JVM 或并发锁）里写了哪些代码、验证了哪些机制、如何运行，请直接点击下方对应领域的链接，进入该子模块专属的 `README.md`。

---

## 🧪 各领域实验室入口 (Lab Modules)

| 领域入口 | 核心验证重点摘要 |
|---|---|
| 🐬 **[Database · MySQL InnoDB 推演](innodb-demo/README.md)** | B+树路标寻址、Page二分槽、MVCC底层判定、Next-Key防幻读死等、环形日志与Doublewrite防撕裂页。 |
| 🔥 **[Cache · Redis 深度解析](redis-demo/src/java/main/com/zhiya/redis/README.md)** | 9 层认知墙：介质墙、单线程事件循环、type×encoding、过期淘汰、RDB/AOF、复制哨兵、16384 槽位、gossip 选举、Stream/HLL；另含 15 坑、25 自测、10 决策卡。 |
| 🌳 **[Tree · 顶级树形数据结构](tree-demo/README.md)** | BST删除、红黑树自平衡旋转、并发跳表(SkipList)多级跃迁、B树/B+树的分裂与扇出机制。 |
| 🔴 **[AQS · 同步器与并发原语](aqs-demo/README.md)** | 12 个底层实验。CAS竞态、CLH双向队列结构、Condition挂起、以及 JUC 倒计数与循环栅栏协同。 |
| 📦 **[Collection · 集合框架深度验证](collection-demo/README.md)** | `subList` 内存泄漏、HashMap树化与哈希冲突复现、COW代价验证、并发安全的错误用法重现。 |
| ☕ **[JVM · 运行机制与 OOM 现场](jvm-demo/README.md)** | 手写复现各数据区 OOM 现场 (Metaspace/Heap/直接内存)、JOL对象头打印、锁升级路线重演。 |
| 🧵 **[Thread · 线程池与调优](thread-demo/README.md)** | `ThreadPoolExecutor` 拒绝与队列打爆实战、手搓背压流控、**JDK 21 虚拟线程**十万级并发实测。 |
| 🌐 **[Network · 高性能网络编程](network-demo/src/java/main/com/zhiya/network/README.md)** | BIO 线程陪等、NIO 忙轮询与 Selector readiness、半包粘包、部分写 / `OP_WRITE`、Reactor 状态 owner、ACK / drain 与 AIO completion。 |
| ⚡ **[Benchmark · JMH 工业级基准](benchmarks/README.md)** | 12 个微基准战。位运算、集合与并发结构对抗；新增 BIO 阻塞式 Echo vs NIO Selector Echo 的固定连接 loopback 对照。 |
| 📐 **[Binary · 位运算实战](binary-demo/README.md)** | 布隆过滤器防穿透、一致性哈希环防倾斜、Base32 GeoHash、以及位运算在订单状态机中的压缩重现。 |
| 🧠 **[Memory · 虚拟内存与 OS (C++20)](virtual-memory-demo/README.md)** | ASLR基址、Base+Limit段错误、TLB容量耗尽、按需调页三闸门(RSS/minflt)实证与写时复制(COW)。 |
| 🍃 **[SpringBoot · 机制验证实验室](springboot-demo/README.md)** | SpringBoot 3.3.5 十九组实验：手写 IoC、事件、自动装配、Web 双跑法(SERVLET/REACTIVE)、事务、AOP、优雅停机、启动慢排查(JFR/BufferingStartup)。 |
| 🧭 **[Dubbo · 分布式 RPC 机制验证实验室](dubbo-demo/README.md)** | Dubbo 3.3.4 十一组实验：调用链 E00、序列化盒 E01、Nacos 注册中心 E03-E06、线程模型 E07、SPI 机制 E09、Mock 降级 E10。 |

---

## 🚀 统一基准与环境测试报告

部分全域级别的基准跑分报告直接在 CI 流水线中产出，此处摘录部分核心对抗结果：

> 🌐 **网络 I/O 基准边界**：`BioVsNioLoopbackBenchmark` 比较的是固定长连接、固定长度字段协议与本机 loopback 下的一次 request-response 局部成本；它不代表公网延迟、海量连接容量、慢客户端背压或下游 RPC / DB 性能。完整测试边界见 [benchmarks README](benchmarks/README.md)。

> 🖥️ **环境 A（并发锁矩阵）**：GitHub Actions `ubuntu-latest` · JDK 8 · 8 线程并发压测
> ⚙️ **命令**：`java -jar benchmarks/target/benchmarks.jar SyncVsAqsBenchmark`

### 🔒 锁与并发原语基准跑分对比

| 锁/同步原语模式 | 平均耗时 (ns/op) | 架构原理解读 |
|---|---:|---|
| **`write_LongAdder`** (分段 Cell) | **8.15** | 消除伪共享：Cell[] 数组分散写竞争，无 CPU 总线 Lock 信号 |
| **`rw91_stamped_optimistic`** (乐观读) | **16.42** | 无 CAS 写操作：validate 屏障校验，多读线程零写竞争 |
| **`rw91_rwlock_read`** (AQS 读锁) | **62.30** | AQS 共享读：状态高低位拆分，多核 CAS 导致 Cache 刷新 |
| **`write_ReentrantLockNonFair`** | **184.15** | AQS Fast-path：允许新线程插队，提高 CPU 缓存命中率 |
| **`write_Synchronized`** (JVM Monitor) | **212.80** | 锁膨胀机制：高争用下膨胀为 OS Mutex，带来上下文切换 |
| **`write_ReentrantLockFair`** (公平锁) | **1140.25** | 强制 CLH FIFO 排队：引发极高的线程挂起与唤醒开销 |

### 🔒 ConcurrentHashMap vs Collections.synchronizedMap

> ⚙️ **测试命令**：`java -jar benchmarks/target/benchmarks.jar SynchronizedMapVsConcurrentMapBenchmark`

| 场景 | synchronizedMap | ConcurrentHashMap | 提升倍数 |
|---|---:|---:|---:|
| **9读1写** | 14,345 ops/ms | **102,067 ops/ms** | **7.1x** |
| **5读5写** | 5,745 ops/ms | **45,077 ops/ms** | **7.8x** |

### 🔥 Redis 深度解析实测摘要

> 🖥️ **环境**：沙箱实测 · Azul Zulu `21.0.11` · `java com.zhiya.redis.RunAllDemos all`（CI 经 `scripts/verify-jdk21-demos.sh` 自动复跑，教学量级，以你的硬件为准）

| 验证点 | 实测结果 | 机制解读 |
|---|---:|---|
| 介质墙 (L1) | HashMap 读 ~90ns vs 磁盘随机读 ~1µs | 隔着 10³ 的墙：Redis 的"快"是把数据放进 DRAM |
| 无锁并发计数 (L2) | 8 线程×20 万次 → 仅 23.9 万 (丢 85%) | "GET+SET 两步"竞态；单线程串行免费原子性 |
| 近似 LRU (L4) | samples=1→85.6% / 5→32.1% / 20→14.3% (冷度百分位) | 采样越大越接近全局最冷，代价是 CPU |
| 缓存击穿 (L4) | 无防护 DB 被打 112 次 → single-flight 1 次 | 同一热点的回源重建同一时刻最多一个 |
| HLL 估计 (L9) | 真实 95,139 → 估计 94,591，误差 0.58% | 12KB 固定内存换 ≈0.81% 可证误差 |
| HLL 减法伪命题 (L9) | 两不相交集合"净增" = -382 | 估计误差方向不可知，只能并不能减 |
| node-timeout 误判 (L8) | 15s→0.00% / 5s→0.73% / 1s→37.37% | 调小 = 调低错杀阈值，宁可为观察付钱 |

_注：Redis 演示全部零依赖，可用 `bash scripts/verify-jdk21-demos.sh` 在你本地一键复跑。_

_注：所有基准测试都可使用 `mvn clean package -DskipTests && java -jar target/benchmarks.jar` 在你本地机器一键重现。_

---

## 🤝 贡献

欢迎通过以下方式参与：
- 🐛 **Issue**：发现 bug、文档错漏、CI 异常 → 提 Issue
- 🔧 **PR**：新数据结构、新基准维度、新系统机制实验 → Fork + PR
- 📊 **数据反馈**：跑出不同机器/不同 JDK 的基准数据，贴 Issue 一起讨论

---

## 📜 许可证

[MIT License](./LICENSE) © imZhiYa
