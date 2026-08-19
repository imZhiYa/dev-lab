# 🔀 Sharding · ShardingSphere 机制验证实验室

**EX-01~07 机制验证 — 配套分库分表知识库系列（shard-00 ~ shard-05）**

_知识库讲原理与因果链，这里负责让"机制真的发生"：分片键分布、路由与广播、改写、翻页代价、跨片 JOIN、扩容搬迁、连接预算_

> 📚 **知识库** → 分库分表系列文章（shard-00 两堵墙与拆分谱系 ~ shard-05 选型与边界）
> 🧬 **本模块** → 7 组实验 + 纯逻辑冒烟，输出可重放、可对照

---

## 🎯 设计哲学：机制验证级，不是 benchmark 级

每个实验只回答一个问题：**机制是否真实发生、量级是否可感**。
- FAST 档：数据量只保证信号 ≫ 噪声（2.5 万行订单），一次会话全部 7 实验 ≤ 5 分钟
- 观测穿透：不用中间件自带日志，用 **MySQL general_log（log_output=TABLE）+ 每库专属 MySQL 用户**——中间件替应用发的每条物理 SQL 全部实录（路由目标库、改写形态、LIMIT 下推）
- 数据可重放：`run-all.sh` 从头灌数（EX-01 幂等），`down -v` 即销毁，零残留
- 与知识库实验差异化：本模块全部是 **ShardingSphere-JDBC 5.4.1 客户端视角**的物理 SQL 级观测

---

## 📦 环境要求

| 依赖 | 版本 |
|---|---|
| JDK | 21 |
| Maven | 3.8+ |
| Docker | colima（4C/8G 推荐） |
| 中间件 | MySQL 8.0.36 × 1 容器（单容器 4 database ds0~ds3，轻量；连接预算按物理库算，与实例数无关） |
| 分片中间件 | ShardingSphere-JDBC 5.4.1（锁知识库实验基线，行为按此版本实测） |

> ⚠️ 首次会话需拉镜像（MySQL 8.0.36 ~600MB）与首次 Maven 构建（3~5 分钟），此后会话 ~5 分钟。

---

## 🚀 快速开始

```bash
cd sharding-demo

# 一把跑完 7 实验 + 冒烟（起容器 → 实验串行 → 全部断言通过）
docker compose -f scripts/compose-mysql.yml up -d
bash scripts/run-all.sh

# 只跑某一个
bash scripts/run-ex03.sh   # 见 scripts/run-all.sh 内命令，或直接
./run.sh com.zhiya.sharding.experiment.Ex03Rewrite

# 纯逻辑冒烟（零中间件，几秒，CI 同款）
./run.sh com.zhiya.sharding.core.ShardingSmokeApp
```

---

## 🧪 实验索引（EX → 验证点 → 对应文章）

| 实验 | 验证机制 | 对应文章 |
| --- | --- | --- |
| `SmokeApp` | gcd 搬迁公式 / 同余判据 / 路由片数 / 翻页下推形态（纯逻辑，CI） | shard-04 L2 / shard-03 L3 |
| `EX-01 分片键分布` | 连续递增键取模 8 片均匀 vs 雪花低速率键（每 ms 序列 0/1）集中 2 片 | shard-01 L3 |
| `EX-02 路由与广播` | 带分片键 → 1 条物理 SQL 路由单片；无分片键 → 广播 8 片（同库合并 UNION ALL 下推） | shard-02 L3 |
| `EX-03 改写引擎` | AVG→SUM+COUNT 下推；同片 IN 合并 1 条、跨库 IN 拆分 2 条 | shard-02 L4/L5 |
| `EX-04 翻页代价` | offset=9000 每片下推 LIMIT 0, 9020（线性放大）；keyset 每片 LIMIT 20（恒定） | shard-03 L3 |
| `EX-05 跨片 JOIN` | 无绑定 JOIN 不报错但**静默丢数据**（实测 1000 行期望只回 260 行） | shard-03 L4 |
| `EX-06 扩容搬迁比例` | SQL 实测 8→16 迁移 0.5000，与 gcd 公式一致 | shard-04 L2 |
| `EX-07 连接预算` | 广播 8 片后仅 4 连接（按库算）；上限 = 4 数据源 × 池大小 4 = 16 | shard-02 L6 |

---

## 📊 实验结果

> 🖥️ **环境**：macOS arm64 宿主 + colima 4C/8G · Zulu 21 · MySQL 8.0.36（单容器 4 database）· ShardingSphere-JDBC 5.4.1 · 2026-08
> 结果口径：**教学量级（机制验证级）**，绝对数不外推

| 验证点 | 实测结果 | 机制解读 |
|---|---|---|
| EX-01 连续键均匀 | 1 万连续键 → 8 片各 1250（max/min=1.000） | 取模 = 低 3 位，连续键遍历 0~7 余数类 |
| EX-01 低速率雪花键集中 | 1.5 万雪花键（每 ms 1~2 单）→ 片 0/1 两片（66.7% 在片 0） | 雪花低 12 位是毫秒内序列，低速率下序列恒为 0/1 → 低 3 位锁死 0/1 |
| EX-02 带键路由 | `WHERE order_id=100` → 仅 1 条物理 SQL，目标 ds2（片 4 → 库 2/表 0） | 路由引擎按分片键精确路由，order_id % 8 = 片号 |
| EX-02 无键广播 | 无键查询 → 4 条物理 SQL（**同库两片合并 UNION ALL 下推**） | 执行引擎按数据源分组：同库多片合并为 UNION ALL，减少往返 |
| EX-03 AVG 改写 | `AVG(amount)` → 每片 `SUM(amount), COUNT(amount) AS AVG_DERIVED_*` 下推 | 跨片均值不能直接下推 AVG：改写为 SUM+COUNT，归并端相除（正确性改写） |
| EX-03 IN 拆分粒度 | 同片 IN(0,8,...,40) → 1 条；跨库 IN(0,2) → 2 条 | 拆条粒度是**数据源**不是分片：片 0/1 同库合并 UNION ALL |
| EX-04 offset 深翻页 | 每片下推 `LIMIT 0, 9020`（8 条独立 SQL，排序不合并） | 内存归并模式取足 offset+limit 归并端丢弃；扫描行数随深度线性放大 |
| EX-04 keyset 恒定 | 每片下推 `LIMIT 20`；中位延迟 4ms vs offset=9000 的 41ms | 游标把"绝对深度"换成恒定 limit——代价是只能顺序翻 |
| **EX-05 跨片 JOIN 静默丢数据** | 期望 1000 行，JOIN 只回 260 行（**丢 740 行**）；下推仅覆盖 ds0/ds1 | 无绑定 JOIN 不做跨库数据移动（无 federation）：只有"同库本地表组合"参与，user 表不在的库（ds2/ds3）其 order 分片直接缺席——**比报错更危险** |
| EX-06 搬迁比例 | SQL 实测 8→16 迁移 0.5000（连续键子集） | x mod 8 = x mod 16 ⟺ x mod 16 < 8 → 迁 1/2，gcd 公式实证 |
| EX-07 连接按库 | 广播 8 片后 processlist 仅 4 连接（每库 1 个）；上限 = 4 池 × 4 = 16 | 连接预算按库算不按片算：8 片共用 4 个池 |

### 实验过程中发现的真实机制（比表格更有价值）

- **EX-02/03 执行引擎按"数据源"分组下推**：同库多片合并为 `UNION ALL` 单条 SQL（COUNT/SELECT 无排序场景），跨库才拆条——"连接按库算"在物理 SQL 层面的直接体现。但 **ORDER BY 排序场景不合并**（EX-04：8 条独立下推），归并排序需要每片有序流。
- **EX-05 无绑定 JOIN 是"静默丢数据"而非报错**：ShardingSphere 5.4.1 对无绑定 JOIN 不做报错、也不做跨库数据移动（federation 未启用），只下推"同库本地表组合"——user 表只分布在 ds0/ds1 时，ds2/ds3 的 order 行在 JOIN 结果里整体缺席。文章 shard-03 L4 说"跨片 JOIN 默认不支持"，实测语义比"报错"更危险：**错误数据**。生产上必须显式配置绑定表/广播表或应用层关联，否则要核对 JOIN 行数。
- **EX-01 雪花键"均匀幻觉"**：若序列号跨毫秒持续递增（错误实现），低 12 位遍历 0~4095，取模 8 依旧均匀——真实雪花"每毫秒序列重置"才导致低速率下集中（序列恒 0/1）。demo 首版就是这个 bug，修正后分布从均匀变成集中——bug 本身就是机制。
- **EX-03 AVG 改写保留了 AVG(amount) 原列**：下推 SQL 是 `AVG(amount), COUNT(amount) AS AVG_DERIVED_COUNT_0, SUM(amount) AS AVG_DERIVED_SUM_0`——中间件下推原 AVG（兼容直连 MySQL 的降级）再加派生列，归并端用派生列重算，不是纯 SUM/COUNT。
- **EX-06 迁移比例必须先看数据分布**：混入集中型键（雪花低速率全在片 0/1，mod 16 后仍不动）后，全量实测迁移只有 0.2——公式前提是均匀分布，真实扩容评估要先做分布审计。
- **INLINE 算法默认禁止范围查询**：`WHERE order_id > ?` 直接报 `Unsupported SQL operation`（allow-range-query-with-inline-sharding=false），keyset 翻页必须显式开启该属性——范围路由开启后广播到全部分片过滤。

---

## 🌐 测试边界声明

**本模块数据不能断言**（也请勿这样引用）：
- 不能据此宣称 ShardingSphere 的绝对吞吐/延迟上限（单容器 MySQL + 2.5 万行教学量级 ≠ 生产）
- 不能外推大分片规模下的归并成本、真实业务键分布下的迁移比例、复杂查询的改写矩阵
- 结论形态是"机制发生了 + 相对差异方向"，参数敏感度的绝对值需生产复核
- 所有"下推形态"为 **ShardingSphere-JDBC 5.4.1 实测行为**，其他版本可能不同（版本行为标注，非规范保证）

---

## 🤖 CI 集成

`scripts/verify-sharding-demos.sh`（JDK 21 专区）：编译 + `ShardingSmokeApp` 纯逻辑冒烟（gcd 搬迁公式 / 同余判据 / 路由片数 / 翻页下推形态），几秒完成。
EX-01~07 依赖 docker 容器，CI 跳过，本地跑法见上方"快速开始"。