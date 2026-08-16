# 📨 MQ · 消息中间件机制验证实验室

**EX-01~06 机制验证 — 配套 MQ 知识库系列（mq-00 ~ mq-10）**

_知识库讲原理与因果链，这里负责让"机制真的发生"：吞吐代价、被踢边界、幂等泄漏、积压公式、乱序补偿、队列存储形态_

> 📚 **知识库** → MQ 系列文章（mq-00 为什么存在 ~ mq-10 生产问题分析）
> 🧬 **本模块** → 6 组实验，每次会话 ~10 分钟，输出可重放、可对照

---

## 🎯 设计哲学：机制验证级，不是 benchmark 级

每个实验只回答一个问题：**机制是否真实发生、量级是否可感**。
- FAST 档：时间尺度缩到"机制可见的最小值"（如 interval 5s 而非 30s），一次会话全部 6 实验 ≤10 分钟
- 数据最小化：灌库量只保证信号 ≫ 噪声（如 3~5 万条），测试数据全在容器 overlay，`down -v` 即销毁，零残留
- 参数唯一真源：每个实验的参数固化在对应 Java 类常量与 `scripts/run-exXX.sh`，README 不抄命令
- `SCALE=LONG` 钩子：将来要参数曲线时加长窗口/加档重跑，同一脚本

---

## 📦 环境要求

| 依赖 | 版本 |
|---|---|
| JDK | 21（`/usr/libexec/java_home -v 21` 自动探测，Azul/Temurin 均可） |
| Maven | 3.8+ |
| Docker | colima（4C/8G 推荐；2C/4G 可跑但吞吐量级受限） |
| 中间件 | Kafka 3.5.2 / MySQL 8.4 / Redis 7 / RabbitMQ 3.13（compose 编排，固定文章基线） |

> ⚠️ 首次会话需拉镜像（Kafka/MySQL/RabbitMQ ~1.2GB）与首次 Maven 构建（3~5 分钟），此后会话 ~10 分钟。
> ⚠️ 连接口令（guest/guest、mqlab）仅为本地 lab 沙箱使用，非任何真实环境凭据。

---

## 🚀 快速开始

```bash
cd mq-demo

# 一把跑完 6 实验（起容器 → 实验串行 → 汇总 → down -v 销毁全部测试数据）
bash scripts/run-all.sh

# 只跑某一个
bash scripts/run-ex06.sh

# 纯逻辑冒烟（零中间件，几秒，CI 同款）
bash run.sh com.zhiya.mq.core.CoreSmokeApp
```

---

## 🧪 实验索引（EX → 验证点 → 对应文章）

| 实验 | 验证机制 | 对应文章 |
| --- | --- | --- |
| `EX-01 单分区吞吐` | acks=1 vs acks=all 的吞吐代价（确认级别 → 复制同步） | mq-02 存储引擎 / mq-08 副本与高可用 |
| `EX-02 批量消费与踢边界` | max.poll.records 对吞吐的影响；批处理时长 > max.poll.interval.ms 被踢 → rebalance | mq-05 消费模型 |
| `EX-03 幂等三方案` | DB 唯一索引 vs Redis SETNX vs 状态机+唯一索引的漏幂等率；Redis 淘汰注入后泄漏 | mq-10 L3 / mq-04 |
| `EX-04 积压恢复校准` | backlog = 缺口 × 时长线性公式；清空时间 ≈ Lag /（消费速率−生产速率） | mq-10 L2 |
| `EX-05 CQv1 vs CQv2` | 经典队列全驻内存 vs quorum 队列落盘形态 | mq-02 存储引擎 |
| `EX-06 乱序与补偿注入` | 状态机拒绝乱序 → 延迟重试恢复；无上限重试 = 活锁；上限耗尽 → DLQ | mq-10 L3/L4 |

---

## 📊 实验结果

> 🖥️ **环境**：macOS arm64 宿主 + colima 4C/8G 跨 VM · Zulu 21.0.11 · Kafka 3.5.2（CP 7.5.2 单 broker 单副本）/ RabbitMQ 3.13 / Redis 7 / MySQL 8.4 · FAST 档 · 2026-08
> 结果口径：**教学量级（机制验证级）**，跨 VM 只产相对结论，绝对数不外推

| 验证点 | 实测结果 | 机制解读 |
|---|---|---|
| acks=1 vs all 确认代价 | 同步确认 123 vs 131 msg/s（P50 8.0/7.1ms），差异 6.1% ≈ 0 | 单副本 ISR=1 时 acks=all 没有可等的副本，退化为 acks=1；代价随副本数增长（mq-08 容错预算） |
| 批量大小对拉取影响 | 拉 5 万条：records=500 需 395ms（100 次往返）vs 5000 需 318ms（10 次） | poll 往返是纯开销；单条处理有成本时大批次还能换下游批量写 |
| interval 超时被踢 | interval=5s + 批 5000×2ms=10s → 确定性 rebalance，且连续触发循环 | 批处理时长 > max.poll.interval.ms → 心跳断 → 判死 → 消息重消费（重复/乱序放大器） |
| 三方案漏幂等率（正常态） | A(DB 唯一索引)/B(Redis SETNX)/C(状态机+DB) 各 10/10 处理、漏 0% | 三方案在键未丢失时都能拦截重复 |
| Redis 淘汰后漏幂等率 | maxmemory 16MB LRU 灌满 → 幂等键存活 10/10→0/10 → 重放后 B 漏 100%（10/10 业务事件重复处理），A/C 仍 0 | 正确性必须落 DB 事务；Redis 丢键 = 幂等失效，只能做性能层前置过滤 |
| backlog 公式偏差 | 生产 80,000 / 消费 43,863 → Lag 35,637 vs 推算 40,000（偏差 10.9%）；清空 80,000 条 942ms | Lag = 缺口 × 时长线性成立；偏差来自消费速率波动与窗口对齐 |
| 乱序恢复 / 活锁 / DLQ | PAID 先到 → 状态机拒绝 → 第 2 次延迟重试恢复（幂等键不变）；无上限重试 20s 内 26 次持续增长 | 乱序可以等（秒级），但每个"等"必须有上限，否则活锁；上限耗尽转 DLQ |
| CQv1 vs CQv2 形态 | v1：30,000 条、bytes 29.3MB 可见、进程内存 0.1MB；v2：30,000 条、bytes 不暴露（RA 内部）、进程内存 1.7MB、观测有 ~秒级可见性延迟 | v1 消息体挂消息存储（bytes 可观测）；v2 Raft 日志式（bytes 由 RA 管理） |

### 实验过程中发现的真实机制（比表格更有价值）

- **单副本拓扑测不到 acks 代价**：EX-01 原本想测 acks=all 的吞吐损失，结果两档几乎无差——因为 ISR=1 时没有"其他副本"可等。这恰是机制正确性的证据，多副本代价见 mq-08 容错预算公式。
- **异步缓冲会掩盖确认延迟**：第一版 EX-01 用异步 send 压满，吞吐由 accumulator 缓冲决定而非 broker 确认，acks 差异被完全吸收；改为 in-flight=1 同步确认后延迟才成为吞吐分母。
- **Kafka Admin API deleteTopics 对不存在的 topic 抛 UnknownTopicOrPartitionException**（不是幂等的），CLI 有 ensureTopicExists 前置检查正是为此。
- **amqp-client 5.21 addConfirmListener 与 waitForConfirms 组合会导致 publish 不落库**（确认帧消费冲突）；纯 waitForConfirms 稳定。
- **management API 对 quorum 队列不返回深度指标**（messages/memory 缺失），rabbitmqctl 的 message_bytes 对 quorum 也为空列；观测 quorum 只能拿 messages + memory。
- **quorum 队列消息可见性有秒级延迟**（灌完 2s 内 rabbitmqctl 显示 0，8s 后 30,000），且同名重建存在 RA 异步删除竞态——实验用时间戳队列名规避。

---

## 🌐 测试边界声明

**本模块数据不能断言**（也请勿这样引用）：
- 不能据此宣称某 MQ 的绝对吞吐上限（跨 VM 网络与单节点拓扑 ≠ 生产）
- 不能外推集群规模下的复制延迟、rebalance 抖动、积压恢复时间
- 结论形态是"机制发生了 + 相对差异方向"，参数敏感度的绝对值需 LONG 档与生产复核

---

## 🤖 CI 集成

`scripts/verify-mq-demos.sh`（JDK 21 专区）：编译 + `CoreSmokeApp` 纯逻辑冒烟（状态机乱序拒绝 / 幂等键确定性 / 重试退避序列），几秒完成。
EX-01~06 依赖 docker 容器，CI 跳过，本地跑法见上方"快速开始"。
