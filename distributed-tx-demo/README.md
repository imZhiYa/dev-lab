# ⚖️ 分布式事务 · 机制验证实验室

**EX-01~06 机制验证 — 配套分布式事务知识库系列（distributed-tx-00 ~ 03）**

_知识库讲原理与因果链，这里负责让"机制真的发生"：本地事务边界、XA 悬挂、TCC 三难题、Saga 编排补偿、Outbox 原子性_

> 📚 **知识库** → 分布式事务系列文章（distributed-tx-00 方案谱系 ~ distributed-tx-03 生产演练）
> 🧬 **本模块** → 6 组实验 + 纯逻辑冒烟，输出可重放、可对照

---

## 🎯 设计哲学：机制验证级，不是 benchmark 级

每个实验只回答一个问题：**机制是否真实发生、量级是否可感**。
- FAST 档：数据量只保证信号 ≫ 噪声，一次会话全部 6 实验 ≤ 3 分钟
- 观测穿透：不靠框架日志，直接查三库的 `resource` / `freeze` / `tcc_fence` / `saga` 表——状态机每一跳都落库可见
- 数据可重放：`down -v` 即销毁，零残留；每个 EX 开头自复位（可重复执行）
- 与知识库实验差异化：本模块全部是 **MySQL 8.0.36 三库单实例 + 裸 JDBC** 的机制级验证（不接 Seata/框架，聚焦协议与模式本身）

---

## 📦 环境要求

| 依赖 | 版本 |
|---|---|
| JDK | 21（Azul/Temurin 均可） |
| Maven | 3.8+ |
| Docker | colima（4C/8G 推荐） |
| 中间件 | MySQL 8.0.36 × 1 容器（单容器 3 database：order_db / inventory_db / payment_db，即「下单请求 B 的三库」基线） |

> ⚠️ 首次会话需拉镜像（MySQL 8.0.36 ~600MB）与首次 Maven 构建（3~5 分钟），此后会话 ≤ 3 分钟。
> ⚠️ root/root 口令仅为本地 lab 沙箱使用（端口 3308 仅绑定本机），非任何真实环境凭据。

---

## 🚀 快速开始

```bash
cd distributed-tx-demo

# 一把跑完 6 实验 + 冒烟（起容器 → 实验串行 → 全部断言通过）
docker compose -f scripts/compose-mysql.yml up -d --wait
bash scripts/run-all.sh

# 只跑某一个
./run.sh com.zhiya.dtx.experiment.Ex02TccThreeProblems

# 纯逻辑冒烟（零中间件，几秒，CI 同款）
./run.sh com.zhiya.dtx.core.DtxSmokeApp
```

---

## 🧪 实验索引（EX → 验证点 → 对应文章）

| 实验 | 验证机制 | 对应文章 |
| --- | --- | --- |
| `SmokeApp` | TCC 控制表状态机判定 / Saga 状态机迁移 / 谱系选型矩阵（纯逻辑，CI） | distributed-tx-02 L2/L4 · 00 L5 |
| `EX-01 XA 悬挂` | XA PREPARE 后三库 in-doubt、XA RECOVER 排查、prepare 持锁（悬挂）、人工裁决一致 | distributed-tx-00 L2 / DC-01 |
| `EX-02 TCC 三难题` | 空回滚（Cancel 先到写 SUSPENDED）/ 悬挂（迟到 Try 拦截）/ 幂等（重复 Confirm/Cancel）/ 可用量不足回滚 | distributed-tx-02 L2 / DC-03 |
| `EX-03 悬挂检测` | FROZEN 超时扫描、防误伤（正常长事务不误判）、只告警不清理、阈值权衡 | distributed-tx-02 L3 |
| `EX-04 Saga 补偿` | 持久化状态机 + 倒序补偿 + 补偿对偶（净效果归零） | distributed-tx-02 L4 / DC-04 |
| `EX-05 Saga 超时` | 结果未知不能直接补偿：查询确认成功推进 / 失败补偿 / 未知冻结（防双花） | distributed-tx-02 L4 |
| `EX-06 Outbox 原子性` | 状态与事件同本地事务（双写之缝消除）、发布幂等 | distributed-tx-00 L4 / DC-05 |

> 系列谱系里的 **Seata AT**（undo_log / 全局锁 / 脏写检测）为框架接入 + 写放大压测（知识库 EX-07），依赖 Seata 与本模块「不接框架」的定位不同，作为边界保留；Outbox/半消息机制细节回链 `knowledge/ddd-05` 与 `knowledge/mq-07`（dev-lab `ddd-demo` / `mq-demo` 已有闭环）。

---

## 📊 实验结果

> 🖥️ 环境：macOS arm64 宿主 + colima 4C/8G 跨 VM · JDK 21 · MySQL 8.0.36（单容器三库）· FAST 档 · 2026-08-19
> 结果口径：**教学量级（机制验证级）**，绝对数不外推；所有断言是「正确性」断言，非性能数字

| 验证点 | 实测结果 | 机制解读 |
|---|---|---|
| XA prepare 后悬挂 | 3 个分支（b-order/b-inv/b-pay）被 `XA RECOVER` 列出；prepare 后 SKU1 行被锁（新连接 UPDATE 锁等待超时）；XA COMMIT 后三库一致、in-doubt 清空 | prepare 后参与者「持锁 + in-doubt」是悬挂的两面；RECOVER 是协调者崩溃后的唯一排查入口 |
| TCC 空回滚/悬挂/幂等 | 空回滚写 `SUSPENDED` → 迟到 Try 被拦截（`TRY_REJECTED`）；重复 Confirm/Cancel 幂等（不二次扣减/不二次释放）；可用量不足 Try 整体回滚（控制表插入一并回滚） | 幂等 = 唯一约束 + 状态机终态判定；防悬挂靠「写」控制表而非「查」 |
| TCC 悬挂检测 | 阈值 60s 只检出 2h 前的 `FROZEN`，不误伤刚创建的 FROZEN；扫描后仍 FROZEN（只告警不清理）；阈值 3h 连真悬挂也漏报 | 悬挂是二阶段结果丢失的兜底；阈值 = 发现延迟 vs 误报率的权衡，无最优值 |
| Saga 倒序补偿 | T1 扣库存成功、T2 扣款失败（余额不足）→ 倒序补偿 C1 加回库存；库存回 100、资金未扣（净效果零） | 编排器 = 持久化状态机 + 补偿表；补偿对偶（T/C 净效果零） |
| Saga 超时结果未知 | 查询确认 SUCCESS→DONE / FAILURE→补偿 / UNKNOWN→冻结（库存 95 未补偿、余额 9000 未退款） | 结果未知不能直接补偿，否则「扣款成功+退款」双花；先查询确认再决定 |
| Outbox 原子性 | 下单+登记事件同一事务；登记失败→下单也回滚；已 PUBLISHED 不重复投递 | 状态与事件同事务，双写之缝被消除 |

> 未跑实验保持「待实测」标注（AGENTS.md 硬规则），跑完后把实测回填到知识库 `knowledge/distributed-tx/experiments/README.md` 的执行状态表。

---

## 🌐 测试边界声明

**本模块数据不能断言**（也请勿这样引用）：
- 不能据此宣称 XA / TCC / Saga 的生产性能（本机单实例三库 ≠ 生产多机多实例）
- 不覆盖跨机网络分区、多副本 MySQL、协调者多节点高可用
- 结论形态是「机制发生了 + 状态机判定正确」，性能与阈值绝对值需生产复核

---

## 🤖 CI 集成

`./run.sh com.zhiya.dtx.core.DtxSmokeApp`（JDK 21）：纯逻辑冒烟（TCC 控制表状态机 / Saga 状态机 / 谱系选型），几秒完成。
EX-01~06 依赖 docker 容器，CI 跳过，本地跑法见上方「快速开始」。
