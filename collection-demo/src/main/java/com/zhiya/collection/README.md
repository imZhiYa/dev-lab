# 📦 Collection 集合框架深度验证 (collection-demo)

本模块配合知识库文档 `https://github.com/imZhiYa/tech-knowledge-docs/tree/main/docs/04-collections`，对 Java 原生集合框架底层的各种 "坑点"、扩容机制与并发替代方案进行了地毯式的验证推演。

## 🧪 实验模块与能力矩阵

| 源代码文件 | 知识深度 | 解决的核心痛点与验证结果 |
| :--- | :--- | :--- |
| `ListDemo.java` | Level 2 | ArrayList vs LinkedList。深度验证随机访问耗时差距，复现 `subList` 导致的内存泄漏与强耦合陷阱。 |
| `HashMapDemo.java` | Level 3-4 | 手工构建哈希冲突（Hash Collision），验证红黑树树化条件（链表长度>8且数组容量>64），复原 `hashCode`/`equals` 约定。 |
| `TreeMapLinkedHashMapDemo.java` | Level 5+5.5 | `TreeMap` 提供排序特性实验；`LinkedHashMap` 演练按访问顺序排队机制（瞬间手搓 LRU 缓存架构）。 |
| `ConcurrentMapDemo.java` | Level 6 | `ConcurrentHashMap`：验证为何不能放 `null`，重现经典复合操作（Check-Then-Act）不是线程安全，演示 `CounterCell` 是如何提升性能的。 |
| `CopyOnWriteDemo.java` | Level 7.6.1 | 写时复制（COW）机制：展示 Fail-Fast（抛异常）与 Fail-Safe（无锁迭代器）的底层区别，以及写代价剧增现象。 |
| `ArrayDequeDemo.java` | Level 7.5 | 彻底废弃旧版 `Stack`，用 `ArrayDeque` 实现高效无锁栈、队列与双端队列。 |
| `BlockingQueueDemo.java` | Level 7.5 | `ArrayBlockingQueue`、`SynchronousQueue` 在生产者消费者模型中的表现，以及 `Executors.newFixedThreadPool` 中的隐患。 |
| `EnumSetDemo.java` | Level 7.6.3 | 高级位向量集合。展示它是如何在底层变成极其高效的 `O(1)` 位运算的。 |
| `EnumMapDemo.java` | Level 7.6.4 | 通过 `ordinal` 索引直接映射内存数组，性能碾压 `HashMap` 的实战。 |
| `IdentityHashMapDemo.java` | Level 7.6.5 | 验证其打破常规，使用 `==` 代替 `equals` 比较对象的极端机制，用于深拷贝检测循环引用。 |
| `ConcurrentSkipListMapDemo.java` | Level 7.6.6 | 并发跳表实战，观察它是如何在并发高争用场景下替代 `TreeMap`（红黑树）的。 |

## 🚀 运行方式

由于代码完全基于 JDK 标准库 API，没有任何第三方依赖：

```bash
cd src/main/java
javac com/zhiya/collection/*.java
java com.zhiya.collection.HashMapDemo # 替换为你想验证的具体 Demo 类
```
