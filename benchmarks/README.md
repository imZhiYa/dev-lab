# ⚡ JMH 微基准测试套件 (benchmarks)

微服务时代不能光凭“直觉”和“我看博客上说”来评判底层 API 的快慢。本模块引入了工业级基准测试框架 **JMH (Java Microbenchmark Harness)**，用最严谨的数据回答各种技术争论。

这里的测试涵盖了位运算、并发锁降级机制、同步容器 vs 并发容器等极具争议性的话题。

## 🧪 实验模块与能力矩阵

| 测试套件文件 | 测试对抗组 | 核心验证重点 |
| :--- | :--- | :--- |
| `BitwiseBenchmark.java` | **位运算 vs 算术** | 在乘除法、取模等场景下，编译器是否会自动优化算术指令？位运算到底能拉开多大差距？ |
| `TreeBenchmark.java` | **4 种树形结构** | 测定 `BST`, `MaxHeap`, `MinHeap`, `RedBlackTree` 在 1K / 10K / 100K 规模下的插入耗时雪崩曲线。 |
| `TreeBenchmarkDiagnostic.java` | **BST 单档深入** | 专门针对 `BST` 在命中与未命中场景中的 CPU 缓存预读与分支预测开销。 |
| `SyncVsAqsBenchmark.java` | **并发全类型锁对抗** | `synchronized` vs `ReentrantLock` vs `StampedLock` vs `LongAdder`。验证在强竞争下“伪共享消除”与“无锁乐观读”的霸主地位。 |
| `ArrayListVsLinkedListBenchmark.java` | **双雄之争** | 用数据粉碎面试八股文——为什么 `LinkedList` 在现代 CPU 缓存行机制下几乎全方位落败？ |
| `MapBenchmark.java` | **三大 Map 对比** | `HashMap` / `TreeMap` / `LinkedHashMap` 操作全维度压测。 |
| `ConcurrentMapBenchmark.java` | **并发 Map** | 9读1写 vs 5读5写。比拼 `ConcurrentHashMap` 与 `Collections.synchronizedMap`。 |
| `DequeBenchmark.java` | **栈队列选择** | 验证 `ArrayDeque` 作为栈和队列如何将 `LinkedList` 按在地上摩擦。 |
| `SetAndEnumMapBenchmark.java` | **特定结构降维打击** | `EnumSet` 的 `O(1)` 极速与普通 `HashSet` 的差距。 |
| `CopyOnWriteAndSkipListBenchmark.java` | **高级并发结构** | `CopyOnWrite` 变态的写时复制惩罚曲线，以及无锁跳表 `ConcurrentSkipListMap` 的优异读写平衡。 |
| `SynchronizedMapVsConcurrentMapBenchmark.java` | **终极综合比对** | 验证全局锁导致的总线风暴问题。 |

## 🚀 编译与执行

JMH 必须打成胖包 (Fat Jar) 以防止类加载和 JIT 编译器的干扰，推荐使用环境：**JDK 8**。

```bash
mvn clean package -DskipTests

# 运行全维度锁对抗基准（例如开 8 线程并发，预热 1 轮，迭代 3 轮）
java -jar target/benchmarks.jar "SyncVsAqsBenchmark.*" -p n=1000 -t 8 -wi 1 -i 3 -f 1
```
