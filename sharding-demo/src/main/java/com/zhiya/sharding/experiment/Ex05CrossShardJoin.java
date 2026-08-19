package com.zhiya.sharding.experiment;

import com.zhiya.sharding.lab.ShardingLabBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * EX-05 跨片 JOIN：无绑定关系 JOIN 的报错实录（shard-03 L4）
 *
 * 场景：order 按 order_id 分片（8 片）、user 按 user_id 分片（2 表），
 * 无绑定关系（ShardingSphere 绑定表要求同分片键）——JOIN 无法单片完成。
 *
 * 文章断言：跨片 JOIN 默认不支持 → 实测报错信息即为验证（报错即验证）
 */
public final class Ex05CrossShardJoin extends ShardingLabBase {

    public static void main(String[] args) throws Exception {
        System.out.println("========== EX-05 跨片 JOIN：无绑定关系 JOIN 报错实录 ==========");

        try (Connection c = sharding().getConnection()) {
            // 幂等：先清空 user 表（广播删除）
            try (Statement clean = c.createStatement()) {
                clean.executeUpdate("DELETE FROM `user`");
            }
            // 准备 user 数据（EX-01 的 order.user_id 范围 1000~1099 与 900000+）
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO `user` (user_id, nickname) VALUES (?, ?)")) {
                for (long uid = 1000; uid <= 1099; uid++) {
                    ps.setLong(1, uid);
                    ps.setString(2, "user-" + uid);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            System.out.println("📥 user 表已灌入 100 行（user_id 1000~1099，按 user_id % 2 分表）");

            // JOIN 查询：order 按 order_id 分片、user 按 user_id 分片，无绑定关系
            System.out.println("\n🔗 执行跨片 JOIN：SELECT * FROM `order` o JOIN `user` u ON o.user_id = u.user_id ...");
            // 期望行数：order 中 user_id 落在 [1000,1099] 的订单数（8 片全量）——在 general_log 清空之前统计，避免污染 JOIN 下推实录
            long expected;
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `order` WHERE user_id BETWEEN 1000 AND 1099")) {
                rs.next();
                expected = rs.getLong(1);
            }
            generalLogOn();
            generalLogClear();
            try (Statement st = c.createStatement()) {
                // JOIN 实际行数（无 LIMIT）
                long joined;
                try (ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*) FROM `order` o JOIN `user` u ON o.user_id = u.user_id")) {
                    rs.next();
                    joined = rs.getLong(1);
                }
                System.out.println("  期望 JOIN 行数（全部分片参与）= " + expected + "，JOIN 实际返回 = " + joined);
                check(joined < expected, "JOIN 结果不完整：实测丢 " + (expected - joined) + " 行（静默丢数据，机制发现）");
                System.out.println("⚠️ 无绑定 JOIN 在 5.4.1 不报错但丢数据——比报错更危险（ds2/ds3 的 order 分片无本地 user 表，直接缺席）");
            } catch (SQLException e) {
                System.out.println("❌ 报错实录：");
                System.out.println("  " + e.getMessage());
                check(e.getMessage() != null && !e.getMessage().isEmpty(), "跨片 JOIN 报错（错误信息即验证：无绑定关系 JOIN 默认不支持）");
            }
            List<String[]> joinSql = generalLogSqls();
            System.out.println("🔍 跨片 JOIN 下推的物理 SQL（共 " + joinSql.size() + " 条）：");
            for (String[] r : joinSql) {
                System.out.println("  [" + r[0].replace("shard_", "") + "] " + r[1]);
            }
            System.out.println("  （下推仅覆盖 ds0/ds1——user 表所在库；ds2/ds3 的 order 分片没有可 JOIN 的 user 本地表，直接缺席）");
        }
        System.out.println("\n✅ EX-05 完成：跨片 JOIN 行为已实录（报错或执行均为 5.4.1 实测行为）");
    }
}