# 🔴 AQS 抽象队列同步器 & 并发协作原语 (aqs-demo)

本模块对应知识库深水区文档，硬核拆解 JUC 包下最核心的地基——**AbstractQueuedSynchronizer (AQS)** 及其衍生的各类锁与并发同步器。

这里是整个 Java 并发编程的心脏地带，通过这 12 个分级代码实验，我们将层层剥开 AQS 的黑盒。

## 🧪 实验模块与能力矩阵

| 源代码文件 | 知识深度 | 核心探究机制与验证 |
| :--- | :--- | :--- |
| `AqsLevel1StateAndContentionDemo.java` | Level 1 | 演示 `state` 状态字段的 CAS 并发争用，这是锁机制（如 `ReentrantLock`）最初的起点。 |
| `AqsLevel2FairQueueDemo.java` | Level 2/3 | 重点解析 CLH 双向队列！对比严格排队的“公平锁”与允许插队偷锁的“非公平锁”。 |
| `AqsLevel4BoundedBufferDemo.java` | Level 4 | `Condition` 的条件等待队列。演示无锁或阻塞式“有界缓冲区”如何优雅地做到挂起与唤醒。 |
| `AqsLevel4ConditionReacquisitionDemo.java` | Level 4 | 线程在 `Condition.await()` 被唤醒后，并不是直接执行，而是要经历重新去竞争锁的过程。 |
| `AqsLevel4ConditionSignalStrategyDemo.java` | Level 4 | 精准验证单线程唤醒 `signal()` 与大范围广播 `signalAll()` 的业务选型策略。 |
| `AqsLevel5CancellationAndInterruptionDemo.java` | Level 5 | 模拟线程中断或超时，观察节点状态被置为 `CANCELLED` 后，AQS 是如何进行链表清洗的。 |
| `AqsLevel5SharedPermitPropagationDemo.java` | Level 5 | 揭秘 AQS 独有的 `PROPAGATE` 广播传播机制，它是信号量和闭锁的基础。 |
| `AqsLevel5CoordinationPrimitiveSelectionDemo.java` | Level 5 | 并发协作原语在不同业务场景下的架构选型 PK 综合比对。 |
| `CountDownLatchDemo.java` | JUC 原语 | 一次性闭锁：多线程并发压测起跑线和收尾关卡的最佳实践。 |
| `CyclicBarrierDemo.java` | JUC 原语 | 循环栅栏：如何让一批线程互相等待，并且具备异常破损（Broken）的连锁反应机制。 |
| `PhaserDemo.java` | JUC 原语 | 超级动态栅栏：展示多阶段（Phase）递进、线程自由中途加入和退出的高阶协同。 |
| `SemaphoreDemo.java` | JUC 原语 | 控制并发度的令牌桶：演示批量抢占 (`acquire(n)`)、公平性以及并发限流策略。 |

## 🚀 运行方式

由于代码完全基于 `java.util.concurrent` 标准库，无需引入任何包：

```bash
cd src/main/java
javac com/zhiya/aqs/*.java
java com.zhiya.aqs.AqsLevel2FairQueueDemo
```
