package com.zhiya.sharding.experiment;

import com.zhiya.sharding.lab.ShardingLabBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

/**
 * EX-01 分片键分布：连续键 vs 雪花低速率键（shard-01 L3）
 *
 * 文章断言：
 *  - 连续递增键（自增）取模 → 8 片近似均匀
 *  - 块状/低速率键（雪花 ID：每毫秒 0~2 单）取模 → 分布集中在少数片
 *
 * 机制：order_id % 8 = 低 3 位。雪花 ID 低 12 位是"该毫秒内的序列号"——
 * 低速率下序列只在 0~2 徘徊，低 3 位被锁死在 0/1/2 → 订单集中在片 0/1/2。
 * 连续键低 3 位遍历 0~7 → 均匀。
 *
 * 顺带：插入的 1 万连续键订单是 EX-02~06 的共用数据底座
 */
public final class Ex01KeyDistribution extends ShardingLabBase {

    public static void main(String[] args) throws Exception {
        System.out.println("========== EX-01 分片键分布：连续键 vs 雪花低速率键 ==========");
        try (Connection c = sharding().getConnection()) {
            // 清空旧数据（广播删除：8 片都执行 DELETE，本身也是 EX-02 的广播预览）
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM `order`");
            }

            // ---------- 数据 A：连续递增键 1 万单 ----------
            System.out.println("\n📥 写入数据 A：连续键 order_id=1..10000 ...");
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO `order` (order_id, user_id, amount, status) VALUES (?, ?, ?, 0)")) {
                for (long id = 1; id <= 10000; id++) {
                    ps.setLong(1, id);
                    ps.setLong(2, 1000 + (id % 1000));
                    ps.setBigDecimal(3, java.math.BigDecimal.valueOf(10 + (id % 90)));
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // ---------- 数据 B：雪花低速率键 1 万单（每毫秒 1~2 单，序列 0~2 徘徊） ----------
            System.out.println("📥 写入数据 B：模拟雪花键（41 位毫秒时间戳 | 10 位机器 | 12 位序列，每 ms 1~2 单）...");
            long ts = 1750000000000L;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO `order` (order_id, user_id, amount, status) VALUES (?, ?, ?, 0)")) {
                for (int i = 0; i < 10000; i++) {
                    int perMs = (i % 2) + 1;
                    int msSeq = 0; // 真实雪花语义：每毫秒序列从 0 重新计数
                    for (int k = 0; k < perMs; k++) {
                        long snowId = (ts << 22) | (1L << 12) | (msSeq++ & 0xFFF);
                        ps.setLong(1, snowId);
                        ps.setLong(2, 900000 + (i % 500));
                        ps.setBigDecimal(3, java.math.BigDecimal.valueOf(50));
                        ps.addBatch();
                    }
                    ts++;
                }
                ps.executeBatch();
            }

            // ---------- 统计：按 order_id % 8 分片统计（全量广播查询，应用侧分组） ----------
            long[] distA = new long[8];
            long[] distB = new long[8];
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT order_id, user_id FROM `order`")) {
                while (rs.next()) {
                    long oid = rs.getLong("order_id");
                    int shard = (int) (oid % 8);
                    if (oid <= 10000) {
                        distA[shard]++;
                    } else {
                        distB[shard]++;
                    }
                }
            }

            System.out.println("\n📊 连续键 1 万单的 8 片分布：");
            System.out.println(Arrays.toString(distA));
            System.out.println("📊 雪花低速率键 1.5 万单的 8 片分布：");
            System.out.println(Arrays.toString(distB));

            long minA = Arrays.stream(distA).min().orElse(0);
            long maxA = Arrays.stream(distA).max().orElse(0);
            long maxB = Arrays.stream(distB).max().orElse(0);
            long totalB = Arrays.stream(distB).sum();
            int hotShardsB = (int) Arrays.stream(distB).filter(x -> x > totalB * 0.05).count();

            // 断言（教学量级，量级判断而非精确值）
            check((double) maxA / minA < 1.2, "连续键 8 片近似均匀（max/min = " + String.format("%.3f", (double) maxA / minA) + "）");
            check((double) maxB / totalB > 0.3, "雪花低速率键集中在少数片（最大片占比 " + String.format("%.1f", 100.0 * maxB / totalB) + "%）");
            check(hotShardsB <= 4, "雪花低速率键热片数 ≤ 4（实测 " + hotShardsB + " 片）");
        }
        System.out.println("\n✅ EX-01 完成：连续键均匀 vs 低速率键集中，断言全部通过");
    }
}