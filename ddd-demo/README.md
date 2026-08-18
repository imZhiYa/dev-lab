# 🏛️ DDD · 领域建模与架构决策机制验证实验室

> 与知识库 [knowledge/ddd 系列文章](../../../openCodeProjects/knowledge/ddd/)（ddd-00 ~ ddd-06）配套的代码实验。
> 业务背景：电商首页「千人千面」推荐 —— 策略上下文 + 在线决策上下文 + 画像上下文。

## 本实验室验证什么（Why）

DDD 文章里的机制，只有落成可运行、可断言、可复现的代码才算"知道"。每个 EX 对应文章里的一条因果链：

| 实验 | 验证的机制 | 对应文章 | 断言点 |
|---|---|---|---|
| EX-01 | 聚合不变量：值对象自守卫、发布前置校验、状态机、集合封装 | ddd-03 | 13 条断言 |
| EX-02 | 防腐层（ACL）契约翻译：字段搬移、脏数据丢弃计数、本地模型隔离 | ddd-02 | 9 条 |
| EX-03 | 架构边界守护：ArchUnit 把"领域不依赖框架/上下文隔离"编译成测试 | ddd-02/03/04 | 5 条规则 |
| EX-04 | 读路径全链路：特征→策略视图→召回→排序→领域策略→曝光，含 4 种降级 | ddd-04 | 9 条 |
| EX-05 | Outbox 与最终一致性：同事务登记、投递失败重试、eventId 幂等、业务键幂等 | ddd-05 | 14 条 |
| EX-06 | 契约演进：schemaVersion 与快照版本分离、增字段+默认值、语义不变 | ddd-02/06 | 4 条 |
| EX-07 | DDD 选型决策矩阵：六维度打分 → 推荐方向，含"禁止为规范上 DDD" | ddd-06 | 4 条 |

## 分层与依赖方向

```
com.zhiya.ddd
├── contracts/       发布语言（Published Language）：跨上下文的稳定契约，只依赖 JDK
├── domain/          strategy（策略聚合：写路径）与 recommendation（在线决策：读路径）两个上下文
├── ports/           领域需要的端口：FeatureProvider / Ranker / OutboxStore / TransactionRunner ...
├── application/     用例编排 + ACL 翻译：只依赖 domain/ports/contracts，不依赖任何适配器
├── adapters/memory/ 内存假实现：验证机制用，不上生产
└── demo/            EX-01~07 入口 + Checks 断言助手
```

依赖方向是单向的（由 EX-03 的 ArchUnit 规则守护）：

```
domain.strategy ──► domain.recommendation
       ▲                    ▲
       │                    │
application ◄──── ports ◄── adapters
       ▲                    │
       └──── contracts ─────┘   （contracts 只依赖 JDK）
```

## 运行方式

```bash
# 单实验
./run.sh com.zhiya.ddd.demo.Ex01StrategyAggregateDemo     # 或 scripts/run-ex.sh 01
# 全部 7 个
bash scripts/run-all.sh
# CI 同款公审
bash ../scripts/verify-ddd-demos.sh
```

环境：JDK 21（run.sh 自动经 `/usr/libexec/java_home` 探测）、Maven。

## 模拟边界（诚实声明，禁止外推）

- **内存事务**：`InMemoryTransactionRunner` 直接执行 action，等价于"成功提交"。真实 Outbox 依赖
  "业务写入与 Outbox 登记同一 DB 事务"，这里的近似只验证机制，不验证 SQL 事务行为（待核证清单见
  knowledge/ddd/research/ddd-research.md）。
- **内存发送通道**：`RecordingSender` 只模拟投递失败/重试的判定逻辑，不模拟 Kafka 语义。
- **假排序算法**：`FakeRanker` 的排序规则只是为了产生可断言的输出，不代表真实算法。
- **ArchUnit 是构建期检查**：不是运行时守卫，违规代码编译期即失败。

## 生产落地提示（本实验室没有的东西）

- 真实适配器：Redis 特征缓存、MySQL Outbox 表、Kafka 发布器 —— 逻辑与这里一致，但要按
  springboot-demo / mq-demo 的基线（Spring Boot 3.3.5、Kafka 3.5.2）补基础设施实验。
- 并发正确性：内存仓储没有真实数据库隔离级别，乐观锁字段只在单线程演示。
