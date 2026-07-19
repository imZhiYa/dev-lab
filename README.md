# 🧬 dev-lab · 代码验证实验室

**Code Verification Lab — Binary & Tree & Memory & Benchmark**

_知识库讲原理，这里写代码验证_

[![MIT License](https://img.shields.io/badge/license-MIT-green?style=flat-square)](./LICENSE)
![Language: Java 8 + C++ 20](https://img.shields.io/badge/language-Java%208%20%2B%20C%2B%2B%2020-orange?style=flat-square)
[![Powered by tech-knowledge-docs](https://img.shields.io/badge/powered_by-tech--knowledge--docs-blue?style=flat-square)](https://github.com/imZhiYa/tech-knowledge-docs)
[![CI](https://github.com/imZhiYa/dev-lab/actions/workflows/verify-lab.yml/badge.svg)](https://github.com/imZhiYa/dev-lab/actions/workflows/verify-lab.yml)
[![memory CI](https://github.com/imZhiYa/dev-lab/actions/workflows/virtual-memory-demo-ci.yml/badge.svg)](https://github.com/imZhiYa/dev-lab/actions/workflows/virtual-memory-demo-ci.yml)

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

```
dev-lab/
│
├── binary-demo/ # 📐 二进制 & 位运算
│ └── src/main/java/com/zhiya/binary/
│     ├── BinaryUtils.java # 进制转换、补码、位运算工具集
│     ├── BloomFilterBitMapGuard.java # 布隆过滤器 & 位图防缓存穿透
│     ├── ConsistentHashBinaryRing.java # 一致性哈希环（虚拟节点防倾斜）
│     ├── DynamicStateGuard.java # 位运算状态机（订单状态流转）
│     ├── GeohashBitwiseSpatialIndex.java # 经纬度 64 位编码、Base32 GeoHash
│     ├── HyperLogLogBitwiseEstimator.java # 基数估计（亿级 UV 去重）
│     ├── LeetCodeBitwiseClassics.java # LeetCode 位运算经典题解
│     └── SnowflakeBitwiseGenerator.java # 雪花算法 ID 合成
│
├── tree-demo/ # 🌳 树形数据结构
│ └── src/main/java/com/zhiya/tree/
│     ├── BST.java # 二叉搜索树
│     ├── BTree.java # B 树（阶=3）
│     ├── BPlusTree.java # B+ 树
│     ├── MaxHeap.java # 最大堆（数组实现）
│     ├── MinHeap.java # 最小堆（数组实现）
│     ├── NonRecursiveTraversal.java # 非递归遍历 5 种
│     ├── RedBlackTree.java # 红黑树（泛型版）
│     ├── SkipList.java # 无锁并发跳表
│     └── Trie.java # 字典树
│
├── benchmarks/ # ⚡ JMH 微基准测试
│ └── src/main/java/com/zhiya/benchmark/
│     ├── BitwiseBenchmark.java # 位运算性能基准
│     ├── TreeBenchmark.java # 树形数据结构基准（参数化）
│     └── TreeBenchmarkDiagnostic.java # 诊断版（排查异常差异）
│
├── virtual-memory-demo/ # 🧠 虚拟内存 & OS 内存机制（C++20 · 8 个公审实验）
│   ├── include/vm_probe.h # 跨平台观测层（Linux /proc + macOS libproc）
│   ├── src/vm01_*.cpp ~ vm08_*.cpp # 单文件自包含，命名即知识库 Level 序号
│   ├── CMakeLists.txt / Makefile # 标准道 + 零依赖快道
│   └── README.md # 指路牌（详见下方能力矩阵与使用说明）
│
├── .github/workflows/
│   ├── verify-lab.yml # 🔄 CI: 编译 + 公审 + 跑分 + 诊断
│   └── virtual-memory-demo-ci.yml # 🧠 内存实验室 CI: make + ctest 双通道
│
├── .gitignore
├── LICENSE # 📜 MIT
└── README.md # 📖 你正在看
```

---

## 🧪 已实现能力矩阵

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

### 🟢 Benchmark · JMH 微基准（3 个文件）

| 文件 | 测试对象 | 用途 |
|---|---|---|
| `BitwiseBenchmark.java` | 位运算 vs 算术 | 算术替代、掩码聚合、HashMap 容量对齐 |
| `TreeBenchmark.java` | BST / MaxHeap / MinHeap / RedBlackTree | 1K / 10K / 100K 三档规模基准 |
| `TreeBenchmarkDiagnostic.java` | BST（单档 n=10000） | 排查 hit/miss 路径异常差异的对照实验 |

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

---

## 📊 基准测试成绩（最近一次 CI #44 · 2026-07-07）

> 🖥️ **环境**：GitHub Actions `ubuntu-latest` · JDK 8 (Temurin) · 1 线程
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

### 0. 克隆

```bash
git clone https://github.com/imZhiYa/dev-lab.git
cd dev-lab
```

### 1. 跑 Binary 位运算类（8 个 demo）

```bash
cd binary-demo
mkdir -p target/classes
find src/main/java -name "*.java" | xargs javac -d target/classes

# 任选一个跑（每个类都自带 main() 验证 demo）
java -cp target/classes com.zhiya.binary.BinaryUtils
java -cp target/classes com.zhiya.binary.HyperLogLogBitwiseEstimator
# ... 其他 6 个类同理
```

### 2. 跑 Tree 树形结构类（9 个 demo）

```bash
cd tree-demo
mkdir -p target/classes
find src/main/java -name "*.java" | xargs javac -d target/classes

# 任选一个跑
java -cp target/classes com.zhiya.tree.BST
java -cp target/classes com.zhiya.tree.RedBlackTree
# ... 其他 7 个类同理
```

### 3. 跑 JMH 微基准测试

```bash
cd benchmarks
mvn clean package -DskipTests
# 产物: target/benchmarks.jar (fat jar,含 JMH 依赖)
```

**列出所有 benchmark：**
```bash
java -jar target/benchmarks.jar -l
```

**跑位运算基准：**
```bash
java -jar target/benchmarks.jar BitwiseBenchmark -wi 2 -i 3 -f 1 -t 1
```

**跑树基准（全档 1K / 10K / 100K）：**
```bash
java -jar target/benchmarks.jar TreeBenchmark -wi 2 -i 3 -f 1 -t 1
```

**只跑某一档规模：**
```bash
# 只看 10K 节点
java -jar target/benchmarks.jar "TreeBenchmark.*" -p n=10000 -t 1

# 只看 BST 相关
java -jar target/benchmarks.jar "TreeBenchmark.bst.*" -t 1
```

**跑诊断版（排查特定异常差异）：**
```bash
java -jar target/benchmarks.jar TreeBenchmarkDiagnostic -p n=10000 -t 1
```

**导出 JSON 给二次分析：**
```bash
java -jar target/benchmarks.jar TreeBenchmark -rf json -rff result.json
```

### 4. 跑 Memory 内存实验（8 个 demo · C++20）

```bash
# 🔧 零依赖快道：编译 8 个实验 + 按序公审（任一严格断言 FAIL 即红）
make -C virtual-memory-demo run

# 📐 标准道：CMake + CTest
cmake -S virtual-memory-demo -B build && cmake --build build
ctest --test-dir build --output-on-failure   # 100% tests passed out of 8

# 单文件速玩（任挑一个实验）
c++ -O2 -std=c++20 -Ivirtual-memory-demo/include \
    virtual-memory-demo/src/vm04_demand_paging.cpp -o vm04 && ./vm04
```

> ⚠️ **平台差异须知**（细节在 `virtual-memory-demo/src/` 各实验源码 ★ 注脚里）：
> ① Apple Silicon 用户态页是 **16KB** 不是 4KB，页数类数字天然除以 4；
> ② CI/云宿主机普遍开 **THP 透明大页**，缺页计数会被压成 1/512 —— `vm04` 已内置 `MADV_NOHUGEPAGE` 校正器；
> ③ macOS 上 free 后 RSS 不回落是 **MADV_FREE 惰性回收**设计，不是泄漏（`vm06` 同理：其 RSS 通道是独占页账本）。

---

## 🧩 与 tech-knowledge-docs 的对应

每个验证文件背后都对应知识库的一篇原理推导：

| 知识库文档 | 代码验证文件 |
|---|---|
| `binary/01-二进制底层思维与位运算.md` | `BinaryUtils.java` + `LeetCodeBitwiseClassics.java` |
| `binary/02-位图与布隆过滤器.md` | `BloomFilterBitMapGuard.java` |
| `binary/03-一致性哈希环.md` | `ConsistentHashBinaryRing.java` |
| `binary/04-位运算状态机.md` | `DynamicStateGuard.java` |
| `binary/05-GeoHash 空间索引.md` | `GeohashBitwiseSpatialIndex.java` |
| `binary/06-HyperLogLog 基数估计.md` | `HyperLogLogBitwiseEstimator.java` |
| `binary/07-雪花算法.md` | `SnowflakeBitwiseGenerator.java` |
| `data-structures/🌳 树形数据结构.md` | `tree-demo/` 全部 9 个文件 |
| `benchmark/JMH 微基准方法论.md` | `BitwiseBenchmark.java` + `TreeBenchmark.java` + `TreeBenchmarkDiagnostic.java` |
| `os-memory/🧠 虚拟内存.md` | `virtual-memory-demo/` 全部 8 个实验 |

---

## 🤝 贡献

欢迎通过以下方式参与：

- 🐛 **Issue**：发现 bug、文档错漏、CI 异常 → 提 Issue
- 🔧 **PR**：新数据结构、新基准维度、新位运算技巧、新系统机制实验 → Fork + PR
- 📊 **数据反馈**：跑出不同机器/不同 JDK 的基准数据，贴 Issue 一起讨论

**新增文件的规范**：
- 每个 `.java` 必须有 `public static void main(String[])` 自包含 demo；每个 `.cpp` 必须有 `int main()` 自校验断言（PASS/FAIL 退出码说话）
- 包名遵循 `com.zhiya.<模块>.<子类>`；C++ 模块遵循 `include/` 放头文件、`src/` 放源文件、构建脚本显式列源文件
- 优先无外部依赖；如必须，加到 `pom.xml`（Java）/ 在 `CMakeLists.txt` 注释里说明原因（C++）
- 中文注释 + 英文变量名（与现有风格保持一致）

---

## 📜 许可证

[MIT License](./LICENSE) © imZhiYa

**[⬆ 回到顶部](#-dev-lab--代码验证实验室)**

Made with 🧬 by [imZhiYa](https://github.com/imZhiYa)
