# 📐 Binary 二进制底层思维与位运算 (binary-demo)

对应知识库文档 `docs/01-cs-foundation/binary/`。
很多程序员只写 CRUD 业务，对二进制和位掩码有着天然的恐惧。本模块旨在通过最真实的互联网大厂场景（状态压缩、防缓存穿透、布隆过滤器、空间索引），带你体会**用 64 根电线控制世界**的快感。

## 🧪 实验模块与能力矩阵

| 源代码文件 | 业务场景 | 核心探究机制与验证 |
| :--- | :--- | :--- |
| `BinaryUtils.java` | **基础操作工具** | 补码的本质、原反补机制，各种移位与掩码运算的基础封装。 |
| `DynamicStateGuard.java` | **订单状态压缩** | 利用位运算在一整型 `int` 里塞下十几种布尔状态，用 `&` 判断，用 `|` 开启，取代长长的 `boolean` 字段。 |
| `BloomFilterBitMapGuard.java` | **防缓存穿透** | 通过 BitMap 与 Hash 算法结合，演示只要 1MB 内存即可阻挡千万级黑客恶意查询。 |
| `ConsistentHashBinaryRing.java` | **分布式路由** | 一致性哈希环。用二进制圆环解决动态扩缩容，以及引入虚拟节点抵御“数据倾斜”。 |
| `ShardKeyGeneRouter.java` | **订单分片路由** | 分片键基因法。把用户基因缝进订单号固定比特位（同雪花位缝合思路），按订单号/按用户双维度查询都 O(1) 单片路由，免索引表免广播；演示同用户订单亲和、双路径一致、扩容迁移比例（8→16 迁 1/2）与 gcd 公式对账。 |
| `GeohashBitwiseSpatialIndex.java` | **空间经纬度** | 外卖/打车必备算法。如何用位运算把二维的经纬度交叉降维到一维，再转 Base32 字符串。 |
| `HyperLogLogBitwiseEstimator.java` | **UV 基数估计** | 不用 `Set` 存，只用 12KB 内存即可估算十亿级 UV，误差控制在 0.81%。揭秘位操作里的“伯努利实验”。 |
| `SnowflakeBitwiseGenerator.java` | **分布式主键** | 手写原汁原味的 Twitter 雪花算法。位操作是如何将时间戳、机器号和自增序列缝进一个 `long` 里的。 |
| `LeetCodeBitwiseClassics.java` | **大厂笔试** | 各种 LeetCode 经典题目的极致装 X 解法。 |

## 🚀 运行方式

环境要求：**JDK 8+**。无任何依赖。

```bash
cd src/main/java
javac com/zhiya/binary/*.java
java com.zhiya.binary.BloomFilterBitMapGuard
```
