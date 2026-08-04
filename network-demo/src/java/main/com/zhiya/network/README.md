# 🌐 高性能网络编程原理实验室（network-demo）

本目录是《高性能网络编程原理：从 I/O、BIO、NIO、AIO 到 Reactor 的状态所有权》的可运行实验集。

这里不把 BIO、NIO、AIO、Selector、Reactor 当作“性能等级”或框架名对比，而是跟踪一次请求在网络生命周期中的两个问题：

```text
R 停住时，谁承担等待？
等待被转移后，谁拥有 R 的状态？
```

每个 Java 文件均是独立 demo：不依赖 Netty、Maven、JUnit 或其他第三方库；服务端、受控客户端、断言、统计和清理逻辑均在同一文件中。

---

## 📐 实验约束

| 原则 | 具体要求 |
| :-- | :-- |
| 零外部依赖 | 只使用 JDK 标准库；不引入 Netty、Lombok、JUnit 或日志框架。 |
| 自包含 main | 每个 demo 都提供 `main() → run()`；单文件内含 server、client、协议与断言。 |
| 受控可重放 | 使用 loopback、临时端口、`CountDownLatch` 等同步点；不依赖任意 `sleep` 推断关键状态。 |
| 可量化 | 输出连接数、读取次数、队列长度、状态流转或耗时，并以 `require(...)` 收口。 |
| 状态明确 | 半包、decoder、outbound buffer、ACK、drain 等可变状态必须有清晰 owner 和终态。 |

> 编译产物不是源码。不要打开 `.class` 文件；它是 JVM 二进制字节码，文本预览会显示 `NUL` 和乱码。

---

## 🗂️ 实验地图

| 包 | 文件 | 对应主题 | 验证重点 |
| :-- | :-- | :-- | :-- |
| `bio` | `BioThreadPerConnectionDemo.java` | BIO / 线程陪等 | 空闲连接不发字节时，每条连接仍占用一个阻塞在 `read()` 的 worker。 |
| `nio` | `NonBlockingBusyPollingDemo.java` | 非阻塞忙轮询 | `read()==0` 不代表无等待；扫描全部连接会把等待错误转化为 CPU 空转。 |
| `nio` | `SelectorReadinessDemo.java` | readiness 语义 | READ-ready 代表值得尝试读取，不代表完整应用请求已经抵达。 |
| `nio` | `HalfPacketAndStickyPacketDemo.java` | 半包 / 粘包 | TCP 交付字节流；长度字段 decoder 负责累计与完整帧产出。 |
| `nio` | `PartialWriteAndOpWriteDemo.java` | 部分写 / OP_WRITE | 一次 write 不保证写完；仅在 outbound 有残留时订阅 `OP_WRITE`。 |
| `reactor` | `SingleReactorOwnershipDemo.java` | Reactor 状态 owner | 同一连接状态只能由所属 Event Loop 串行修改。 |
| `reactor` | `BoundedBusinessQueueDemo.java` | 有界业务边界 | 固定 worker 数不等于内存有界；队列容量与拒绝策略必须显式定义。 |
| `lifecycle` | `ApplicationAckStateMachineDemo.java` | 应用层 ACK | `LOCAL_WRITE_DONE` 不等于 `ACKED`；业务完成由协议确认定义。 |
| `lifecycle` | `GracefulDrainDemo.java` | 优雅停机 | 半包、业务中、待写与待 ACK 请求必须按不同 drain 策略收口。 |
| `aio` | `AsynchronousSocketChannelDemo.java` | completion 风格 AIO | completion 只交付一段 I/O 结果；framing、状态与部分写仍由应用维护。 |

---

## 🧭 学习顺序

```text
1. BIO：线程为什么会陪等
   BioThreadPerConnectionDemo

2. NIO：非阻塞为何仍会空转
   NonBlockingBusyPollingDemo

3. Selector：就绪不等于完整请求
   SelectorReadinessDemo

4. Framing：字节边界不是消息边界
   HalfPacketAndStickyPacketDemo

5. 写路径：部分写与 OP_WRITE
   PartialWriteAndOpWriteDemo

6. Reactor：连接状态属于谁
   SingleReactorOwnershipDemo
   BoundedBusinessQueueDemo

7. 生命周期：ACK 与 drain 如何收口
   ApplicationAckStateMachineDemo
   GracefulDrainDemo

8. AIO：completion 改变什么，不改变什么
   AsynchronousSocketChannelDemo
```

---

## 🚀 编译与运行

项目 CI 以 JDK 21 编译本目录全部源码。单个 demo 也可独立运行。

从仓库根目录执行：

```bash
OUT_DIR=$(mktemp -d)

javac -encoding UTF-8 \
  -d "$OUT_DIR" \
  network-demo/src/java/main/com/zhiya/network/nio/HalfPacketAndStickyPacketDemo.java

java -Dfile.encoding=UTF-8 \
  -cp "$OUT_DIR" \
  com.zhiya.network.nio.HalfPacketAndStickyPacketDemo

rm -rf "$OUT_DIR"
```

运行 BIO 示例：

```bash
OUT_DIR=$(mktemp -d)

javac -encoding UTF-8 \
  -d "$OUT_DIR" \
  network-demo/src/java/main/com/zhiya/network/bio/BioThreadPerConnectionDemo.java

java -Dfile.encoding=UTF-8 \
  -cp "$OUT_DIR" \
  com.zhiya.network.bio.BioThreadPerConnectionDemo

rm -rf "$OUT_DIR"
```

### 全量编译与运行

```bash
OUT_DIR=$(mktemp -d)

find network-demo/src/java/main -name "*.java" \
  | xargs javac -encoding UTF-8 -d "$OUT_DIR"

for java_file in $(find network-demo/src/java/main -name "*Demo.java" | sort); do
  package_name=$(sed -n 's/^package \(.*\);/\1/p' "$java_file")
  class_name=$(basename "$java_file" .java)
  java -Dfile.encoding=UTF-8 -cp "$OUT_DIR" "${package_name}.${class_name}"
done

rm -rf "$OUT_DIR"
```

---

## 🔍 读输出时应关注什么

| 现象 | 应得出的结论 |
| :-- | :-- |
| 多个空闲 BIO 连接对应多个阻塞 worker | 问题不在“阻塞 API”，而在昂贵线程无界陪等。 |
| 多轮扫描得到大量 `read()==0` | non-blocking 不等于无等待；需要多路复用避免应用扫描全部连接。 |
| Selector 报 READ-ready，但只读到 `HEL` | readiness 不是完整协议帧。 |
| 半包后 decoded frames 为 0，补齐后才为 1 | TCP 没有应用消息边界，decoder 必须保存累计状态。 |
| `write()==0` 或 buffer 仍有 remaining | 必须保存剩余字节，并仅在有待写数据时关注 `OP_WRITE`。 |
| `LOCAL_WRITE_DONE → WAITING_ACK → ACKED` | 本地 write 不等于客户端业务确认。 |
| drain 对不同状态输出不同处理动作 | 优雅关闭是状态审计，不是立即 `close()`。 |
| AIO read completion 收到一段字节 | completion 不替代 framing、请求关联和关闭收口。 |

---

## ⚠️ 边界与生产提醒

这些 demo 用于验证语义，不是生产服务器，也不应据此直接得到固定吞吐或连接数结论。

```text
Selector / epoll readiness ≠ 完整帧 ≠ 本地 write 完成 ≠ 对端收到 ≠ 业务 ACK。
TCP 字节流没有消息边界。
队列不是免费缓冲；慢客户端必须有 outbound 字节预算与背压策略。
Reactor 不会自动解决业务阻塞、下游并发、超时、幂等或 drain。
AIO completion 不会自动保存应用协议与业务状态。
```

性能局部成本请参见：

```text
benchmarks/BioVsNioLoopbackBenchmark.java
```

该 JMH 基准只比较固定连接、本机 loopback 下的 BIO Echo 与 NIO Selector Echo request-response 成本；多连接、慢客户端和背压问题仍应由本目录的系统级实验与生产压测判断。
