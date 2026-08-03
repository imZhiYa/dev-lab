# ⚡ Redis 深度解析 (redis-demo)

本模块对应《⚡Redis深度解析》知识库文档，把文档里 9 层认知墙 + 15 个坑 + 25 道自测 + 10 张决策卡
的复杂逻辑全部**落地为可运行、可量化、可对照**的最小 Java 实现。

> 📚 **知识库** → 讲原理、推导、极端场景
> ⚡ **redis-demo** → 写代码落地验证（主线命令 C = `SET sku:1001:stock 42` 的一生）

三个原则与 dev-lab 仓库一致：
- **零外部依赖**：只用 JDK 标准库，单文件直接 `javac` 跑。
- **自包含 `main`**：每个源文件都是独立验证 demo，控制台输出可重放。
- **可量化**：介质延迟、计数器竞态、近似 LRU 误差、HLL 误差、误判率全部实测数字说话。

---

## 🧪 实验模块与能力矩阵

源码目录：`redis-demo/src/java/main/com/zhiya/redis/`，统一包 `com.zhiya.redis`，平铺无子包（24 个文件）：

```
redis-demo/src/java/main/com/zhiya/redis/
├── RunAllDemos.java                    # 入口路由：all / quick / <层级> / <名称>
├── RedisSupport.java                   # 统一支持库：打印 / 断言并发 / 哈希 三合一
├── MiniStructures.java                 # 迷你结构合集：Listpack + Intset + Skiplist
├── RedisLevel1MediaLatencyDemo.java    # Level 1
├── RedisLevel2EventLoopDemo.java       # Level 2
├── RedisLevel2RespProtocolDemo.java    # Level 2
├── RedisLevel2BatchingDemo.java        # Level 2
├── RedisLevel3EncodingDemo.java        # Level 3
├── RedisLevel3ProgressiveRehashDemo.java # Level 3
├── RedisLevel4ExpirationDemo.java      # Level 4
├── RedisLevel4EvictionDemo.java        # Level 4
├── RedisLevel4CacheThreeBrothersDemo.java # Level 4
├── RedisLevel5PersistenceDemo.java     # Level 5
├── RedisLevel6ReplicationDemo.java     # Level 6
├── RedisLevel6SentinelDemo.java        # Level 6
├── RedisLevel6DistributedLockDemo.java # 决策卡 6
├── RedisLevel7ClusterSlotDemo.java     # Level 7
├── RedisLevel8GossipFailoverDemo.java  # Level 8
├── RedisLevel9StreamPelDemo.java       # Level 9
├── RedisLevel9HyperLogLogDemo.java     # Level 9
├── RedisLevel9DecisionTableDemo.java   # Level 9（表 A/B + 九句口诀）
├── RedisPitfallsDemo.java              # 坑
├── RedisSelfTestDemo.java              # 自测
└── RedisProductionDemo.java            # 生产
```

| 源代码文件 | 知识深度 | 核心探究机制与验证 |
| :--- | :--- | :--- |
| `RedisLevel1MediaLatencyDemo.java` | Level 1 | 实测 HashMap 内存读(ns) vs 磁盘随机读(µs) 的 10³ 介质墙；缓存=用一致性换延迟。 |
| `RedisLevel2EventLoopDemo.java` | Level 2 | 事件循环六步与线性化点；慢命令堵死全店墙钟账；多线程无锁丢计数；io-threads 并行解析/串行执行。 |
| `RedisLevel2RespProtocolDemo.java` | Level 2 | 把 `SET sku:1001:stock 42` 编成 RESP3 字节帧并解析回显；半包/粘包与"超时≠失败"。 |
| `RedisLevel2BatchingDemo.java` | Level 2 | Pipeline 插队 / MULTI 执行屏障 / Lua 服务端分支——三种打包能力边界不互通（坑 9）。 |
| `RedisLevel3EncodingDemo.java` | Level 3 | listpack/intset/跳表实测；robj 头与 embstr 44 字节之谜；512/128/64B 阈值自动换编码。 |
| `RedisLevel3ProgressiveRehashDemo.java` | Level 3 | 新旧两张桶 + rehashidx：每次增删查搬一个非空桶，查找先新后旧，店长永不停业。 |
| `RedisLevel4ExpirationDemo.java` | Level 4 | 双字典 + 惰性班 + 抽查班两班倒；TTL 是内存契约不是业务闹钟。 |
| `RedisLevel4EvictionDemo.java` | Level 4 | 8 种淘汰策略；近似 LRU 采样误差实测（samples=1/5/20 的冷度百分位）；noeviction 拒写。 |
| `RedisLevel4CacheThreeBrothersDemo.java` | Level 4 | 穿透/雪崩/击穿三场景 DB 被打次数对照（空值占位 / TTL 抖动 / single-flight）；Cache-Aside 删缓存优于更新。 |
| `RedisLevel5PersistenceDemo.java` | Level 5 | 页表级 COW 模拟（fork 复制引用、写时复制、THP 4K→2M 写放大）；fsync 的墙；RPO/RTO 先写数字。 |
| `RedisLevel6ReplicationDemo.java` | Level 6 | 主从 offset + backlog 环形缓冲；offset 出窗=全量、在窗=部分重同步；已确认写丢失窗口。 |
| `RedisLevel6SentinelDemo.java` | Level 6 | SDOWN→ODOWN→选 leader→挑 offset 最大从库→升主→客户端重连全时序；min-replicas 收窄脑裂。 |
| `RedisLevel6DistributedLockDemo.java` | 决策卡 6 | SETNX+EXPIRE 死锁出厂设置；GC 停顿 35s 场景旧票据被资源侧拒绝（fencing）；WATCH+MULTI CAS；RedLock 边界。 |
| `RedisLevel7ClusterSlotDemo.java` | Level 7 | 真实 CRC16(key)&16383 分槽；`{hashtag}` 共槽躲开 CROSSSLOT；-MOVED/-ASK + ASKING；逐槽搬迁。 |
| `RedisLevel8GossipFailoverDemo.java` | Level 8 | PFAIL→FAIL 两阶段确诊；rank 延迟选举公式；每纪元一票；node-timeout 调小的误判率实测。 |
| `RedisLevel9StreamPelDemo.java` | Level 9 | XADD/XREADGROUP/XACK/XPENDING/XAUTOCLAIM 全流程；骑手崩溃→票过户→at-least-once；内存卫生两份责任。 |
| `RedisLevel9HyperLogLogDemo.java` | Level 9 | 真 16384 寄存器 HLL：误差 0.58%（对照真实基数）、幂等、PFMERGE、"只能并不能减"实测。 |
| `RedisLevel9DecisionTableDemo.java` | Level 9 | 表 A（场景→结构）+ 表 B（横切能力）+ 九句口诀连读。 |
| `RedisPitfallsDemo.java` | 坑 | KEYS vs SCAN 墙钟、DEL vs UNLINK 主线程占用、Lua BUSY 语义 + 15 坑速查表。 |
| `RedisSelfTestDemo.java` | 自测 | 25 题自测 → 必须答出的不变量 → 所属 Level 索引。 |
| `RedisProductionDemo.java` | 生产 | 监控指标群 / 雪崩排障顺序 / 评审四问 / 10 张决策卡速查。 |
| `RedisSupport.java` | 基础设施 | §1 中文对齐打印 / §2 require 断言与统一并发放行 / §3 CRC16+hashtag+槽位+HLL 哈希。 |
| `MiniStructures.java` | 迷你结构 | 连续紧凑 Listpack / 有序整数 Intset / 概率跳表 Skiplist 的教学复刻。 |

---

## 🚀 运行方式

零第三方依赖，两种入口等价：

```bash
# 一键脚本（需 JDK 17+，推荐 Azul Zulu 21）
./run.sh                 # 全量 21 项
./run.sh quick           # 精选
./run.sh 4               # 只跑 Level 4
./run.sh stream          # 只跑单个演示

# 或直接 javac / java
cd redis-demo/src/java/main
javac -encoding UTF-8 -d /tmp/classes com/zhiya/redis/*.java

java -cp /tmp/classes com.zhiya.redis.RunAllDemos                 # 全量
java -cp /tmp/classes com.zhiya.redis.RedisLevel9StreamPelDemo   # 单个（每文件自包含 main）
```
---

## 📌 边界声明

- 一切性能数字为**教学量级**，以你的硬件 + `redis-benchmark` / `fio` 实测为准。
- `🔒` 标注的默认值/私有实现（阈值、常量、线程数）以所部署版本的 `CONFIG GET` 与源码为准。
- 迷你实现（listpack/intset/跳表/HLL/Stream）是**教学复刻**，目标是让逻辑看得见摸得着，不是生产级等价物。
