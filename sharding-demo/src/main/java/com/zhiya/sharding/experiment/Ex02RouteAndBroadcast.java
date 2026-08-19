package com.zhiya.sharding.experiment;

import com.zhiya.sharding.lab.ShardingLabBase;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * EX-02 路由与广播：带分片键 vs 无分片键（shard-02 L3）
 *
 * 观测手段：MySQL general_log（log_output=TABLE）——中间件替应用发的每条
 * 物理 SQL 都落在 mysql.general_log，从 db 列可见路由目标、从 argument 列
 * 可见改写后的物理 SQL。
 *
 * 文章断言：
 *  - 带分片键（WHERE order_id=?）→ 路由 1 个物理分片（1 条物理 SQL）
 *  - 无分片键（WHERE amount>?）→ 广播 8 个物理分片（8 条物理 SQL，4 库 × 2 表）
 */
public final class Ex02RouteAndBroadcast extends ShardingLabBase {

    public static void main(String[] args) throws Exception {
        System.out.println("========== EX-02 路由与广播：带键单片 vs 无键广播 ==========");

        generalLogOn();
        try (Connection c = sharding().getConnection();
             Statement st = c.createStatement()) {

            // ---------- 1. 带分片键：路由单片 ----------
            generalLogClear();
            try (ResultSet rs = st.executeQuery("SELECT order_id, user_id, amount FROM `order` WHERE order_id = 100")) {
                while (rs.next()) {
                    System.out.println("  命中订单：order_id=" + rs.getLong(1) + " user_id=" + rs.getLong(2) + " amount=" + rs.getBigDecimal(3));
                }
            }
            List<String[]> routed = generalLogSqls();
            System.out.println("\n🔍 带键查询后中间件下推的物理 SQL（共 " + routed.size() + " 条）：");
            for (String[] r : routed) {
                System.out.println("  [" + r[0] + "] " + r[1]);
            }
            check(routed.size() == 1, "带分片键 → 仅 1 条物理 SQL（路由单片）");
            String routedDs = routed.get(0)[0].replace("shard_", "");
            check(routedDs.equals("ds2"), "路由目标 = ds2（order_id=100 → 片 4 → 库 2、表 0，实测 " + routedDs + "）");

            // ---------- 2. 无分片键：广播（先看聚合形态，再看普通 SELECT 形态） ----------
            generalLogClear();
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `order` WHERE amount > 30")) {
                rs.next();
                System.out.println("\n无键 COUNT(amount>30) 结果 = " + rs.getLong(1));
            }
            List<String[]> broadcast = generalLogSqls();
            System.out.println("🔍 无键 COUNT 下推的物理 SQL（共 " + broadcast.size() + " 条）：");
            for (String[] r : broadcast) {
                System.out.println("  [" + r[0].replace("shard_", "") + "] " + r[1]);
            }

            generalLogClear();
            try (ResultSet rs = st.executeQuery("SELECT order_id FROM `order` WHERE amount > 30")) {
                int n = 0;
                while (rs.next()) n++;
                System.out.println("无键 SELECT 行数 = " + n);
            }
            List<String[]> broadcastSel = generalLogSqls();
            System.out.println("🔍 无键 SELECT 下推的物理 SQL（共 " + broadcastSel.size() + " 条）：");
            for (String[] r : broadcastSel) {
                System.out.println("  [" + r[0].replace("shard_", "") + "] " + r[1]);
            }
            check(broadcast.size() == 4 && broadcastSel.size() == 4,
                    "无分片键 → 广播全部 8 个物理分片（5.4.1 实测：同库两片合并为 UNION ALL 下推，每库 1 条，共 4 条物理 SQL）");
        }
        System.out.println("\n✅ EX-02 完成：带键单片 vs 无键广播 8 片，断言全部通过");
    }
}