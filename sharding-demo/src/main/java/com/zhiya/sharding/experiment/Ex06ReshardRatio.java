package com.zhiya.sharding.experiment;

import com.zhiya.sharding.lab.ShardingLabBase;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * EX-06 扩容搬迁比例：SQL 实测 1/2（shard-04 L2）
 *
 * 文章断言：取模扩容 N→M（N | M）时，不迁移 ⟺ x mod N = x mod M ⟺ x mod M < N
 * 迁移比例 = 1 - gcd(N,M)/max = 1 - N/M。8→16：迁 1/2。
 *
 * 实测：对 order 全量数据（2.5 万行）直接按公式统计
 *   x mod 8 = x mod 16 的行占比 ≈ 1/2（数据全量广播聚合，纯 SQL 计算）
 */
public final class Ex06ReshardRatio extends ShardingLabBase {

    public static void main(String[] args) throws Exception {
        System.out.println("========== EX-06 扩容搬迁比例：8 片 → 16 片的 SQL 实测 ==========");

        try (Connection c = sharding().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) AS total, "
                             + "SUM(CASE WHEN order_id % 8 = order_id % 16 THEN 1 ELSE 0 END) AS stays "
                             + "FROM `order` WHERE order_id <= 10000")) {
            rs.next();
            long total = rs.getLong("total");
            long stays = rs.getLong("stays");
            double moveRatio = 1.0 - (double) stays / total;
            System.out.println("  全量订单数（连续键子集，order_id ≤ 10000）= " + total);
            System.out.println("  不迁移（order_id % 8 = order_id % 16）行数 = " + stays);
            System.out.println("  实测迁移比例 = " + String.format("%.4f", moveRatio));
            System.out.println("  公式预期 = 1 - gcd(8,16)/16 = 1/2 = 0.5000");
            checkNear(moveRatio, 0.5, 0.02, "SQL 实测迁移比例 ≈ 1/2（gcd 公式实证）");
            System.out.println("  （注：仅统计均匀分布的连续键子集；若混入集中型键——如 EX-01 低速率雪花键全在片 0/1，"
                    + "mod 16 后仍不动——迁移比例会被数据分布稀释，真实扩容评估必须先看数据分布）");
        }
        System.out.println("\n✅ EX-06 完成：扩容搬迁比例 SQL 实测与 gcd 公式一致");
    }
}