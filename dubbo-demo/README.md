# 🧭 Dubbo · 全链路机制验证实验室

**Apache Dubbo 3.3.4 机制验证 — demo00~demo08 + 4 个故障注入脚本，配套 tech-knowledge-docs/docs/08-dubbo 九篇**

_从 API 进入，穿透源码与运行时；文档讲因果，这里负责跑出证据（E00~E10 实测）_

> 📚 **知识库** → [docs/08-dubbo](https://github.com/imZhiYa/tech-knowledge-docs/tree/main/docs/08-dubbo) 九篇
> 🧬 **本模块** → 8 个 demo 模块 + 1 个共享 api 模块，覆盖一次 RPC 调用的完整决策链与全部故障场景

---

## 📦 环境要求

| 依赖 | 版本 |
|---|---|
| JDK | 21（编译 target 21） |
| Maven | 3.8+（多模块聚合 + exec 插件） |
| Nacos | 2.4.3 standalone（docker：`nacos/nacos-server:v2.4.3`，鉴权关闭，127.0.0.1:8848） |
| ZooKeeper | 3.9（docker：`zookeeper:3.9`，127.0.0.1:2181，仅 demo05 需要） |

> ⚠️ **多网卡环境必须固定注册地址**：装有代理软件（存在 TUN 虚拟网卡）时，Dubbo 的本机地址探测可能选中虚拟网卡 IP，导致消费端回连被劫持。运行注册中心相关 demo 前：
>
> ```bash
> export DUBBO_IP_TO_BIND=<物理网卡IP>
> export DUBBO_IP_TO_REGISTRY=<物理网卡IP>
> ```
>
> 直连类 demo（demo00/01/07）已通过 `dubbo.network.interface.preferred=lo0` 规避，无需此设置。

---

## 🗺️ demo 清单

| 模块 | 实验 | 验证内容 | 入口 |
|---|---|---|---|
| dubbo-api | — | 共享契约：GreetingService / OrderService + 请求 POJO | 被各模块依赖 |
| demo00-callchain | E00 | 一次 RPC 全链路：Proxy 代理类名、Filter 链节点、线程命名 | `ProviderApp` / `ConsumerApp` |
| demo01-protocol | E01/E02 | 信封 × 盒子四象限压测、Kryo 自实现 SPI（Microkernel 契约）、SerializeDump 字节产物 | `ConsumerBench` / `ProviderApp` / `SerializeDump` |
| demo02-registry | E03 | 注册三态（interface/instance/all）：条目格式、字段数、字节体积 | `ProviderApp` / `ConsumerApp` + `scripts/run-e03.sh` |
| demo04-registry-protocol | E04/E05/E06 | 杀 Nacos 派单窗口、恢复期推空、failover 重试边界、泛化调用 | `ConsumerLoopApp` / `GenericCallApp` + `scripts/run-e04.sh` / `run-e05.sh` |
| demo05-zk-lock | E04b | ZK 分布式锁互斥、session 语义（kill -9 释放窗口）、ZK vs Nacos 剔除对比 | `LockDemo` / `CrashHolder` / `WaitingContender` + `scripts/run-e04b.sh` |
| demo07-threadpool | E07/E08 | 线程池默认参数、250 并发饱和拒绝、dispatcher 边界、优雅停机 | `ProviderApp` / `ConsumerApp` |
| demo08-spi | E09/E10 | @SPI/@Adaptive/@Activate/Wrapper、自定义 LoadBalance 挂真实链路、mock 降级边界 | `SpiBasicsApp` / `AdaptiveApp` / `ActivateApp` / `WrapperApp` / `MockApp` |

---

## 🚀 快速开始

```bash
# 编译全部模块
mvn compile

# E00 直连全链路（先起 provider，再跑 consumer）
mvn exec:java -pl demo00-callchain -Dexec.mainClass=com.zhiya.dubbo.demo.callchain.ProviderApp
mvn exec:java -pl demo00-callchain -Dexec.mainClass=com.zhiya.dubbo.demo.callchain.ConsumerApp

# E01 压测（dubbo 协议 + hessian2）
mvn exec:java -pl demo01-protocol -Dexec.mainClass=com.zhiya.dubbo.demo.protocol.ConsumerBench \
  -Dproto=dubbo -Dserialization=hessian2 -Dhost=127.0.0.1

# E03 注册三态（需 Nacos；先固定注册地址，见上）
demo02-registry/scripts/run-e03.sh all

# E04 故障窗口（杀 Nacos，验证本地缓存派单）
demo04-registry-protocol/scripts/run-e04.sh

# E07 线程池饱和（250 并发 × 3s 业务）
mvn exec:java -pl demo07-threadpool -Dexec.mainClass=com.zhiya.dubbo.demo.threadpool.ProviderApp -Ddemo.sleep.ms=3000
mvn exec:java -pl demo07-threadpool -Dexec.mainClass=com.zhiya.dubbo.demo.threadpool.ConsumerApp -Ddemo.concurrent=250 -Ddemo.calls=250
```

---

## 🔬 验证边界（诚实声明）

- 所有压测数字均为**本机单机串行方向性参考**，不可外推跨机/并发结论
- 注册中心实验依赖 docker（Nacos/ZK）；控制面故障注入脚本会 `kill -9` 本地进程，请勿在生产环境运行
- OOM 类机制（泛化调用 ThreadLocal 泄漏史）以 issue 锚点论证，本仓库不做 OOM 复现
- 每个实验的具体观测点与输出对照见知识库对应篇的"坑与细节"与实验记录（experiment-xxx，随知识库发布）
