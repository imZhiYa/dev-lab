# 🧵 Thread 线程池与并发实验 (thread-demo)

随着摩尔定律的失效和多核时代的到来，如何用好操作系统的底层调度和并发模型成为了高级程序员的核心分水岭。
本模块主要针对传统操作系统级线程池的深度验证，以及 **JDK 21** 带来的颠覆性革命——**Virtual Threads（虚拟线程 / 协程）**。

## 🧪 实验模块与能力矩阵

| 源代码文件 | 核心知识点 | 解决的核心痛点与验证结果 |
| :--- | :--- | :--- |
| `ThreadPoolLabs.java` | **线程池机制** | 彻底手剥 `ThreadPoolExecutor` 的核心参数。演示“核心线程->队列->最大线程->拒绝策略”的执行流水线，重现常见的生产故障（如错误配置导致拒绝、队列打爆导致 OOM）。 |
| `ReactiveNoDepsDemo.java` | **响应式编程与背压** | 不依赖 `RxJava` 或 `Project Reactor`，用原生 API 实现背压（Backpressure）机制。验证如何防止慢速消费者被快速生产者的数据洪水撑爆内存。 |
| `VirtualThreadDemo.java` | **虚拟线程 (JDK 21+)** | 颠覆传统的昂贵 OS 线程模型！验证如何在一个普通的 JVM 里，只消耗几十 MB 内存就能轻松开启 10 万+ 个并发阻塞任务。揭示 Continuation 挂起和恢复底层的高效调度魔法。 |

## 🚀 运行方式

环境要求：**必须使用 JDK 21 或更高版本**（因为用到了真正的虚拟线程 API）。

```bash
# 确保你当前使用的是 JDK 21
java -version

# 编译与运行
cd src/main/java
javac -encoding UTF-8 com/zhiya/VirtualThreadDemo.java
java -Dfile.encoding=UTF-8 com.zhiya.VirtualThreadDemo
```
