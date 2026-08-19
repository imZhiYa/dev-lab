package com.zhiya.binary;

import java.util.ArrayList;
import java.util.List;

/**
 * 分片键基因法：订单号携带用户基因，双维度单点路由
 *
 * 与一致性哈希（ConsistentHashBinaryRing）解决不同的问题：
 * - 一致性哈希：节点增减时"最小迁移"（哈希环上只动相邻段）
 * - 基因法：分库分表后"双维度查询都单片命中"（按订单号 / 按用户都能定位，免索引表、免广播）
 *
 * 核心机制：order_id 用位缝合把"用户基因"缝进固定比特位（与雪花算法同思路）
 *   order_id = [ 时间戳位段 | 基因位段 M | 序列位段 S ]
 *   基因 = user_id % 2^M（M 位，2^M 片）
 *   按订单号路由：从 order_id 中"拆位"提取基因 → 定位片号
 *   按用户路由：user_id 直接取模 → 定位片号（同一片）
 * 两条查询路径都 O(1) 位运算单片命中，无需映射表（对照 shard-01 L4 索引表方案）
 */
public class ShardKeyGeneRouter {

    // 序列位段宽度：同用户同毫秒最多 2^S = 1024 单（超出回绕会撞号，生产需监控）
    private static final int SEQ_BITS = 10;
    private static final long SEQ_MASK = (1L << SEQ_BITS) - 1;

    // 基因位段宽度 M（由片数推导：片数必须是 2 的幂）
    private final int geneBits;
    private final long shardCount;
    private final long geneMask;

    public ShardKeyGeneRouter(int shardCount) {
        if (shardCount <= 0 || (shardCount & (shardCount - 1)) != 0) {
            throw new IllegalArgumentException("片数必须是 2 的幂：" + shardCount);
        }
        this.shardCount = shardCount;
        this.geneBits = Integer.numberOfTrailingZeros(shardCount);
        this.geneMask = shardCount - 1L;
    }

    /**
     * 🟢 用户基因：user_id % 2^M
     * 2 的幂取模等价于位与（&），位运算 O(1)；
     * 取模与位与在 user_id 非负时完全等价，这里用位与展示二进制视角
     */
    public long geneOfUser(long userId) {
        return userId & geneMask;
    }

    /**
     * 🔵 订单号编码：把基因缝进固定比特位
     * 高位时间戳 | 中间基因位段 | 低位序列——位缝合，与雪花算法同思路
     */
    public long encodeOrderId(long tsMs, long userId, long seq) {
        return (tsMs << (geneBits + SEQ_BITS))
                | (geneOfUser(userId) << SEQ_BITS)
                | (seq & SEQ_MASK);
    }

    /**
     * 🧮 订单号拆位提取基因：(order_id >>> SEQ_BITS) & (2^M - 1)
     * 无符号右移越过序列位段，掩码截出基因位段——路由无需查表
     */
    public long geneOfOrder(long orderId) {
        return (orderId >>> SEQ_BITS) & geneMask;
    }

    /** 按订单号路由：拆位 → 片号（查单路径） */
    public long routeByOrderId(long orderId) {
        return geneOfOrder(orderId);
    }

    /** 按用户路由：直接取基因 → 片号（查用户订单路径） */
    public long routeByUserId(long userId) {
        return geneOfUser(userId);
    }

    /** 订单号位段可视化：标出 [时间戳|基因|序列] 三段 */
    public String describeOrderId(long orderId) {
        StringBuilder sb = new StringBuilder();
        for (int bit = 63; bit >= 0; bit--) {
            sb.append(((orderId >>> bit) & 1L) == 1L ? '1' : '0');
            if (bit == geneBits + SEQ_BITS || bit == SEQ_BITS) {
                sb.append('|');
            }
        }
        long ts = orderId >>> (geneBits + SEQ_BITS);
        long gene = geneOfOrder(orderId);
        long seq = orderId & SEQ_MASK;
        return sb + "  (ts=" + ts + " gene=" + gene + " seq=" + seq + ")";
    }

    // =====================================================================
    // 扩容迁移测算：旧片数 N → 新片数 N'（均为 2 的幂，N' 是 N 的整数倍）
    // 不迁移 ⟺ x mod N = x mod N' ⟺ x mod N' < N（N | N' 时成立）
    // 不迁移占比 = N / N'，迁移比例 = 1 - N / N' = 1 - gcd(N,N')/max(N,N')
    // （与 shard-04 L2 gcd 公式同源：x mod N = x mod M ⟺ x ≡ r (mod lcm)，r ∈ [0, min)）
    // =====================================================================
    public static class MigrationReport {
        public final long oldShards;
        public final long newShards;
        public final long movedUsers;
        public final long totalUsers;
        public final double formulaRatio;

        public MigrationReport(long oldShards, long newShards, long movedUsers, long totalUsers, double formulaRatio) {
            this.oldShards = oldShards;
            this.newShards = newShards;
            this.movedUsers = movedUsers;
            this.totalUsers = totalUsers;
            this.formulaRatio = formulaRatio;
        }

        public double measuredRatio() {
            return (double) movedUsers / totalUsers;
        }
    }

    public static MigrationReport measureMigration(int oldShards, int newShards, int sampleUsers) {
        ShardKeyGeneRouter oldRouter = new ShardKeyGeneRouter(oldShards);
        ShardKeyGeneRouter newRouter = new ShardKeyGeneRouter(newShards);
        long moved = 0;
        for (long userId = 0; userId < sampleUsers; userId++) {
            if (oldRouter.routeByUserId(userId) != newRouter.routeByUserId(userId)) {
                moved++;
            }
        }
        double formulaRatio = 1.0 - (double) oldShards / newShards;
        return new MigrationReport(oldShards, newShards, moved, sampleUsers, formulaRatio);
    }

    // =====================================================================
    // 断言工具：失败即退出非零，供演示自检
    // =====================================================================
    private static void check(boolean cond, String name) {
        if (!cond) {
            System.out.println("❌ 断言失败：" + name);
            System.exit(1);
        }
        System.out.println("✅ 断言通过：" + name);
    }

    public static void main(String[] args) {
        System.out.println("==================== [ 分片键基因法 · 订单号携带用户基因 ] ====================");

        // ---------- 1. 亲和验证：同用户订单落同片 ----------
        int baseShards = 8;
        ShardKeyGeneRouter router = new ShardKeyGeneRouter(baseShards);
        System.out.println("\n📦 初始化：" + baseShards + " 片（基因位段 " + router.geneBits + " 位，序列位段 " + SEQ_BITS + " 位）\n");

        long ts = 1750000000000L; // 模拟当前毫秒时间戳
        List<Long> ordersOf1001 = new ArrayList<>();
        List<Long> ordersOf1002 = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ordersOf1001.add(router.encodeOrderId(ts + i, 1001, i));
        }
        for (int i = 0; i < 3; i++) {
            ordersOf1002.add(router.encodeOrderId(ts + i, 1002, i));
        }

        System.out.println("🧾 用户 1001 的 5 笔订单（二进制位段：时间戳|基因|序列）：");
        for (long oid : ordersOf1001) {
            System.out.println("  " + router.describeOrderId(oid) + " → 路由片 " + router.routeByOrderId(oid));
        }
        System.out.println("🧾 用户 1002 的 3 笔订单：");
        for (long oid : ordersOf1002) {
            System.out.println("  " + router.describeOrderId(oid) + " → 路由片 " + router.routeByOrderId(oid));
        }

        // 断言：同用户订单路由同片
        long shard1001 = router.routeByUserId(1001);
        long shard1002 = router.routeByUserId(1002);
        boolean affinityOk = true;
        for (long oid : ordersOf1001) {
            if (router.routeByOrderId(oid) != shard1001) affinityOk = false;
        }
        for (long oid : ordersOf1002) {
            if (router.routeByOrderId(oid) != shard1002) affinityOk = false;
        }
        check(affinityOk, "同用户订单全部路由同片（user 1001 → 片 " + shard1001 + "，user 1002 → 片 " + shard1002 + "）");

        // ---------- 2. 双路径一致：按订单号路由 == 按用户路由 ----------
        System.out.println("\n🔀 双路径一致性（按订单号拆位路由 vs 按用户直接路由）：");
        boolean dualOk = true;
        for (long oid : ordersOf1001) {
            long byOrder = router.routeByOrderId(oid);
            long byUser = router.routeByUserId(1001);
            if (byOrder != byUser) dualOk = false;
            System.out.println("  order_id=" + oid + "  按订单号→片" + byOrder + "  按用户→片" + byUser + "  " + (byOrder == byUser ? "✓" : "✗"));
        }
        for (long oid : ordersOf1002) {
            if (router.routeByOrderId(oid) != router.routeByUserId(1002)) dualOk = false;
        }
        check(dualOk, "按订单号路由 == 按用户路由（两条查询路径都单片命中，免索引表、免广播）");

        // ---------- 3. 8 片分布统计（用户 0~9999 各下单一次） ----------
        long[] dist = new long[baseShards];
        for (long userId = 0; userId < 10000; userId++) {
            dist[(int) router.routeByUserId(userId)]++;
        }
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < baseShards; i++) {
            bar.append("  片").append(i).append(": ").append(dist[i]).append("\n");
        }
        System.out.println("\n📊 10,000 用户 × 1 单的 8 片分布（用户基因取模天然均匀）：\n" + bar);

        // ---------- 4. 参数化扩容：旧 8 片 → 目标片数（默认 16，可选 32） ----------
        int targetShards = args.length >= 1 ? Integer.parseInt(args[0]) : 16;
        if ((targetShards & (targetShards - 1)) != 0) {
            System.out.println("❌ 目标片数必须是 2 的幂：" + targetShards);
            System.exit(1);
        }
        System.out.println("\n🚀【扩容公审】" + baseShards + " 片 → " + targetShards + " 片（基因位段 " + router.geneBits + " → " + (Integer.numberOfTrailingZeros(targetShards)) + " 位）...");
        MigrationReport report = measureMigration(baseShards, targetShards, 10000);
        System.out.println("  实测：10,000 用户中迁移 " + report.movedUsers + " 人，比例 " + String.format("%.4f", report.measuredRatio()));
        System.out.println("  公式：迁移比例 = 1 - gcd(" + baseShards + "," + targetShards + ")/max = 1 - " + baseShards + "/" + targetShards + " = " + String.format("%.4f", report.formulaRatio));
        check(Math.abs(report.measuredRatio() - report.formulaRatio) < 0.005, "实测迁移比例与 gcd 公式一致（shard-04 L2：x mod N = x mod M ⟺ x mod M < N）");

        System.out.println("\n🧭 与一致性哈希对照：");
        System.out.println("  · 一致性哈希（ConsistentHashBinaryRing）：节点增减只迁移环上相邻段——迁移最小化");
        System.out.println("  · 基因法：扩容翻倍即基因位段加 1 位，迁移比例固定 = 1 - 旧片数/新片数（8→16 迁 1/2，8→32 迁 3/4）");
        System.out.println("  · 代价差异：一致性哈希偏缓存/无状态路由；基因法偏订单表强亲和（同用户同片）场景，迁移是明账");

        System.out.println("\n⚖️ Trade-off（对照 shard-01 L4 映射表方案）：");
        System.out.println("  ✅ 零索引表：无映射表维护、无写放大、无二次查询");
        System.out.println("  ✅ O(1) 位运算路由：拆位/取模，不查表不广播");
        System.out.println("  ✅ 同用户订单同片：用户维度查询/事务天然亲和");
        System.out.println("  ⚠️ 基因位宽锁死最大片数：M 位基因最多 2^M 片，超了必须改 ID 结构（重新生成存量 ID）");
        System.out.println("  ⚠️ 订单号不可读：内部嵌基因位段，不能当纯业务流水号对外展示");
        System.out.println("  ⚠️ 扩容迁移固定比例：翻倍扩容迁 1/2（与一致性哈希『最小迁移』相反，但有公式可算明账）");
        System.out.println("  ⚠️ 序列位段约束：同用户同毫秒超过 2^S 单会撞号，需监控或扩位");

        System.out.println("\n✅ 基因法演示完毕：亲和性、双路径路由、扩容比例全部断言通过");
    }
}