# 🧬 dev-lab · 代码验证实验室

<p align="center">
  <strong>Code Verification Lab — Binary & Tree & More</strong>
  <br>
  <em>知识库讲原理，这里写代码验证</em>
</p>

<p align="center">
  <a href="https://github.com/imZhiYa/tech-knowledge-docs">
    <img src="https://img.shields.io/badge/powered_by-tech--knowledge--docs-blue?style=flat-square" alt="Powered by tech-knowledge-docs">
  </a>
  <a href="./LICENSE">
    <img src="https://img.shields.io/badge/license-MIT-green?style=flat-square" alt="MIT License">
  </a>
  <img src="https://img.shields.io/badge/language-Java-orange?style=flat-square" alt="Language: Java">
</p>

---

## 关于

这个仓库是 [tech-knowledge-docs](https://github.com/imZhiYa/tech-knowledge-docs) 的**代码验证配套项目**。

知识库讲原理，这里写代码验证。所有能用代码验证的理论结论，都会在这里落地为可运行的实现。

---

## 目录结构

```
dev-lab/
│
├── binary-demo/                          # 📐 二进制 & 位运算
│   └── src/main/java/com/zhiya/binary/
│       ├── BinaryUtils.java              # 二进制工具类（进制转换/补码/位运算）
│       ├── BloomFilterBitMapGuard.java   # 布隆过滤器&位图防缓存穿透
│       ├── ConsistentHashBinaryRing.java # 一致性哈希环（虚拟节点防倾斜）
│       ├── DynamicStateGuard.java        # 位运算状态机（订单状态流转）
│       ├── GeohashBitwiseSpatialIndex.java # Geohash 空间索引（经纬度编码）
│       ├── HyperLogLogBitwiseEstimator.java # HyperLogLog 基数估计
│       ├── LeetCodeBitwiseClassics.java  # LeetCode 位运算经典题解
│       └── SnowflakeBitwiseGenerator.java # 雪花算法 ID 生成器
│
├── benchmarks/                           # ⚡ JMH 微基准测试
│   └── src/main/java/com/zhiya/benchmark/
│       └── BitwiseBenchmark.java         # 位运算性能基准
│
├── tree-demo/                            # 🌳 树形数据结构
│   └── src/main/java/com/zhiya/tree/
│       ├── BST.java                      # 二叉搜索树
│       ├── AVLTree.java                  # AVL 平衡树
│       ├── RedBlackTree.java             # 红黑树
│       ├── BinaryTreeTraversal.java      # 二叉树遍历
│       ├── MaxHeap.java                  # 最大堆
│       ├── MinHeap.java                  # 最小堆
│       ├── Trie.java                     # 字典树
│       ├── BTree.java                    # B 树
│       └── BPlusTree.java               # B+ 树
│
└── ... 更多模块按需补充（JVM / 并发 / 网络…）
```

---

## 🟢 已完成 · Binary（8 个 Java 文件）

| 文件 | 对应知识点 | 验证内容 |
|------|-----------|---------|
| `BinaryUtils.java` | 二进制底层思维 | 进制转换、补码运算、位操作工具集 |
| `BloomFilterBitMapGuard.java` | 位图实战 | 布隆过滤器实现、缓存穿透防护 |
| `ConsistentHashBinaryRing.java` | 分布式哈希 | 一致性哈希环、虚拟节点、数据倾斜 |
| `DynamicStateGuard.java` | 状态压缩 | 订单状态流转的位运算状态机 |
| `GeohashBitwiseSpatialIndex.java` | 空间索引 | 经纬度 64 位编码、Base32 GeoHash |
| `HyperLogLogBitwiseEstimator.java` | 基数估计 | HyperLogLog 位运算实现 |
| `LeetCodeBitwiseClassics.java` | 面试算法 | LeetCode 位运算经典题目 |
| `SnowflakeBitwiseGenerator.java` | 分布式 ID | 雪花算法位运算实现 |

## 🟢 已完成 · Tree（9 个 Java 文件）

| 文件 | 对应知识点 |
|------|-----------|
| `BST.java` | 二叉搜索树 |
| `AVLTree.java` | AVL 平衡树 |
| `RedBlackTree.java` | 红黑树 |
| `BinaryTreeTraversal.java` | 二叉树遍历 |
| `MaxHeap.java` | 最大堆 |
| `MinHeap.java` | 最小堆 |
| `Trie.java` | 字典树 |
| `BTree.java` | B 树 |
| `BPlusTree.java` | B+ 树 |

---

## 🧩 与 tech-knowledge-docs 的关系

```
📚 tech-knowledge-docs       → 讲原理、推导、极端场景
🧬 dev-lab                   → 写代码落地验证
```

例如：
- `binary/01-二进制底层思维与位运算.md` 👉 `BinaryUtils.java` + `LeetCodeBitwiseClassics.java`
- `data-structures/🌳 树形数据结构.md` 👉 `tree-demo/`（9 个树结构实现）

---

## 如何使用

```bash
git clone https://github.com/imZhiYa/dev-lab.git
cd dev-lab

# 编译运行 binary-demo
cd binary-demo
mvn compile exec:java

# 运行 JMH 基准测试
cd benchmarks
mvn clean install
java -jar target/benchmarks.jar
```

---

## 关联项目

| 项目 | 说明 |
|------|------|
| [📚 tech-knowledge-docs](https://github.com/imZhiYa/tech-knowledge-docs) | 架构师硬核知识库（原理篇） |
| [🧪 cs-visual-tools](https://github.com/imZhiYa/cs-visual-tools) | CS 可视化交互工具 |

---

## 许可证

[MIT License](./LICENSE)
