# ☕ JVM 运行时机制演示 (jvm-demo)

本模块对应知识库文档 `docs/02-jvm/☕ JVM 运行时机制深度解析.md`，主要提供针对 **JDK 21** 和现代 GC（如 G1、ZGC）的深入代码验证。

在这个实验场中，我们将亲手构造 OOM，并利用诊断参数打印日志，看看底层的内存分配与垃圾回收到底发生了什么。

## 🧪 实验模块与能力矩阵

| 目录/文件 | 核心关注点 | 实验说明与验证目标 |
| :--- | :--- | :--- |
| `runtime/Jvm01RuntimeDataArea.java` | **运行时数据区** | 程序计数器、虚拟机栈、堆、方法区的实战分配。 |
| `classloading/Jvm02ClassLoading.java` | **类加载机制** | 验证类加载的生命周期（加载、链接、初始化），包含 `System.exit` 跳过卸载等机制校验。 |
| `object/Jvm03ObjectLayoutTlab.java` | **对象布局 & TLAB** | 打印 Java 对象头（Mark Word），验证内存逃逸分析（Escape Analysis）和 TLAB（线程本地分配缓存）。 |
| `sync/Jvm04SyncLockUpgrade.java` | **锁升级机制** | 配合 JOL 工具，打印对象头，观察无锁 -> 偏向锁 -> 轻量级锁 -> 重量级锁的具体转换过程。 |
| `gc/Jvm05/Jvm06` | **GC 日志与监控** | 支持 G1 和 ZGC 的日志对比分析实验。 |
| `MetaspaceOom.java` | **★ OOM 核心重现** | 利用 JDK 动态代理和 ClassLoader 的强引用关系，精准击穿元空间限制，制造 Metaspace OOM 灾难现场。 |

## 🚀 运行方式

环境要求：**JDK 21** 及其以上版本（推荐使用 Azul Zulu 或 Temurin 等支持完全现代化诊断参数的发行版）。

```bash
# 自动编译并调用所有预设场景，生成 summary 诊断分析报告
cd ../
./scripts/verify-jdk21-demos.sh
```
