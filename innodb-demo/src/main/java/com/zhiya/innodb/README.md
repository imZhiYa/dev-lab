# 🐬 InnoDB 底层机制实验 (innodb-demo)

本模块配合知识库文档 `docs/05-database/🐬MySQL-InnoDB-深度解析.md`，使用纯 Java（无任何第三方依赖）将 MySQL InnoDB 引擎最核心的 7 个底层架构机制进行了抽象与还原。

你可以直接在本地或云端运行这些文件，亲眼观察这些在面试中极难讲清楚的组件，在代码层面是如何精准运转的。

## 📐 目录结构与已实现能力矩阵

| 源代码文件 | 对应的核心知识点 | 解决的核心痛点与验证结果 |
| :--- | :--- | :--- |
| `BufferPoolLRU.java` | **改良版 LRU (分代缓存)** | 验证了 New/Old 分区如何防止诸如 `SELECT COUNT(*)` 产生的全表扫描把热点数据踢出。 |
| `BPlusTreeRoutingDemo.java` | **B+ Tree 路由寻址** | 验证了 B+树非叶子节点被彻底净化后，如何作为纯粹的 "Page No" 路标，使得 O(logN) 能够轻松支持千万级数据的寻址。 |
| `MVCCDemo.java` | **MVCC 多版本并发控制** | 验证了通过一条 Undo Log 链条和 `ReadView` 里的四条核心比对规则，如何在不加锁的情况下实现 RR 级别（甚至模拟 RC 级别）。 |
| `NextKeyLockDemo.java` | **Next-Key Lock (临键锁)** | 验证了 "空气间隙" 被加上 Gap Lock 后，如何彻底把别的事务并发 `INSERT` 拦截在外，从而在当前读（`FOR UPDATE`）下防御幻读。 |
| `PageDirectoryDemo.java` | **Page Directory 槽二分查找** | 验证了在拿到了一个拥有上千条记录的 16KB 页后，如何通过分组抽取 "最大值" 形成槽数组，通过 二分查找 + 4次局部的链表扫描 完成极致压缩耗时的页内检索。 |
| `RedoLogRingBufferDemo.java` | **WAL 机制与环形缓冲区** | 验证了为什么 TPS 极高时 MySQL 会突然卡死排队——由于写满触发了套圈 (`write_pos == checkpoint`) 被迫等待落盘。 |
| `DoublewriteBufferDemo.java` | **Doublewrite Buffer** | 验证了底层文件系统断电产生“撕裂的半个页”时，没有 DW 兜底则数据库永久损坏，有 DW 兜底则完美覆盖恢复的过程。 |

## 🚀 运行方式

环境要求：**JDK 8+** (推荐 JDK 21)。
无任何 Maven/Gradle 依赖。

```bash
# 1. 编译
cd src/main/java
javac -encoding UTF-8 com/imzhiya/devlab/innodb/*.java

# 2. 运行任意一个实验 (以 MVCC 为例)
java -Dfile.encoding=UTF-8 com.imzhiya.devlab.innodb.MVCCDemo
```
