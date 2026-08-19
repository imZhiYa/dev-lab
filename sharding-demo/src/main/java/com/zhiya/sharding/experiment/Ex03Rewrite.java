package com.zhiya.sharding.experiment;

import com.zhiya.sharding.lab.ShardingLabBase;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * EX-03 改写引擎：AVG→SUM+COUNT、同片 IN 合并（shard-02 L4/L5）
 *
 * 文章断言：
 *  - AVG(amount) 跨片无法直接下推平均 → 改写为 SUM+COUNT 下推，归并端相除（正确性改写）
 *  - 带分片键的 IN 列表若命中同一分片 → 改写成单条 SQL（无需拆分多条）
 *
 * 观测手段：general_log 下推 SQL 形态实录
 */
public final class Ex03Rewrite extends ShardingLabBase {

    public static void main(String[] args) throws Exception {
        System.out.println("========== EX-03 改写引擎：AVG→SUM+COUNT、同片 IN 合并 ==========");

        generalLogOn();
        try (Connection c = sharding().getConnection();
             Statement st = c.createStatement()) {

            // ---------- 1. AVG 改写 ----------
            generalLogClear();
            try (ResultSet rs = st.executeQuery("SELECT AVG(amount) FROM `order`")) {
                rs.next();
                System.out.println("\nAVG(amount) 结果 = " + rs.getBigDecimal(1));
            }
            List<String[]> sqls = generalLogSqls();
            System.out.println("🔍 AVG 查询下推的物理 SQL（共 " + sqls.size() + " 条）：");
            for (String[] r : sqls) {
                System.out.println("  [" + r[0].replace("shard_", "") + "] " + r[1]);
            }
            boolean avgRewritten = true;
            for (String[] r : sqls) {
                String s = r[1].toLowerCase();
                if (!(s.contains("sum(") && s.contains("count("))) {
                    avgRewritten = false;
                }
            }
            check(avgRewritten, "AVG 被改写为 SUM+COUNT 下推（归并端相除，正确性改写）");

            // ---------- 2. 同片 IN 合并 ----------
            // 片 0 上的 order_id：0, 8, 16, 24, 32（order_id % 8 = 0 → 片 0 → ds0.order_0）
            generalLogClear();
            try (ResultSet rs = st.executeQuery(
                    "SELECT order_id, amount FROM `order` WHERE order_id IN (0, 8, 16, 24, 32, 40)")) {
                int n = 0;
                while (rs.next()) n++;
                System.out.println("\n同片 IN 查询命中行数 = " + n);
            }
            List<String[]> inSqls = generalLogSqls();
            System.out.println("🔍 同片 IN 查询下推的物理 SQL（共 " + inSqls.size() + " 条）：");
            for (String[] r : inSqls) {
                System.out.println("  [" + r[0].replace("shard_", "") + "] " + r[1]);
            }
            check(inSqls.size() == 1, "IN 列表同片 → 合并为 1 条物理 SQL（不改写拆多条）");

            // ---------- 3. 跨库 IN 拆分（对照组） ----------
            // 片 0（ds0.order_0）与片 2（ds1.order_0）——跨库才拆分，同库合并为 UNION ALL
            generalLogClear();
            try (ResultSet rs = st.executeQuery("SELECT order_id FROM `order` WHERE order_id IN (0, 2)")) {
                int n = 0;
                while (rs.next()) n++;
                System.out.println("\n跨库 IN 查询命中行数 = " + n);
            }
            List<String[]> inMulti = generalLogSqls();
            System.out.println("🔍 跨库 IN 查询下推的物理 SQL（共 " + inMulti.size() + " 条）：");
            for (String[] r : inMulti) {
                System.out.println("  [" + r[0].replace("shard_", "") + "] " + r[1]);
            }
            // 实测行为（5.4.1）：执行引擎按"数据源"分组下推——同库多片合并 UNION ALL，跨库才拆条
            check(inMulti.size() == 2, "IN 列表跨 2 库 → 拆为 2 条物理 SQL（按数据源拆分；片 0/1 同库则合并为 UNION ALL）");
        }
        System.out.println("\n✅ EX-03 完成：AVG 改写 SUM+COUNT、同片 IN 合并、跨库 IN 拆分，断言全部通过");
    }
}