package com.zhiya.sharding.experiment;

import com.zhiya.sharding.lab.ShardingLabBase;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * EX-04 翻页代价：offset 深翻页 vs keyset 游标（shard-03 L3）
 *
 * 文章断言：
 *  - offset 翻页：每片下推 LIMIT offset+limit（内存归并模式），扫描行数随深度线性放大
 *  - keyset 游标（WHERE order_id > last_id）：每片下推 LIMIT limit，恒定成本
 *
 * 观测手段：general_log 下推 SQL 的 LIMIT 形态 + 本机计时（教学量级）
 */
public final class Ex04PaginationCost extends ShardingLabBase {

    public static void main(String[] args) throws Exception {
        System.out.println("========== EX-04 翻页代价：offset 深翻页 vs keyset 游标 ==========");

        generalLogOn();
        try (Connection c = sharding().getConnection();
             Statement st = c.createStatement()) {

            // ---------- 1. offset 深翻页 ----------
            generalLogClear();
            List<Long> page = fetch(st, "SELECT order_id FROM `order` ORDER BY order_id LIMIT 9000, 20");
            List<String[]> offsetSql = generalLogSqls();
            System.out.println("\n📄 offset=9000 翻页返回 " + page.size() + " 行（首页 order_id=" + page.get(0) + "）");
            System.out.println("🔍 下推的物理 SQL（共 " + offsetSql.size() + " 条）：");
            for (String[] r : offsetSql) {
                System.out.println("  [" + r[0].replace("shard_", "") + "] " + r[1]);
            }
            boolean offsetPushedDeep = true;
            for (String[] r : offsetSql) {
                if (!r[1].contains("LIMIT 0, 9020")) {
                    offsetPushedDeep = false;
                }
            }
            check(offsetPushedDeep, "offset 翻页每片下推 LIMIT 0, 9020（offset 归零、取足 offset+limit，扫描行数随深度线性放大）");
            check(offsetSql.size() == 8, "ORDER BY 排序场景不做 UNION 合并：8 片独立下推（归并排序需要每片有序流）");

            // ---------- 2. keyset 游标 ----------
            long lastId = page.get(page.size() - 1);
            generalLogClear();
            List<Long> page2 = fetch(st,
                    "SELECT order_id FROM `order` WHERE order_id > " + lastId + " ORDER BY order_id LIMIT 20");
            List<String[]> keysetSql = generalLogSqls();
            System.out.println("\n📄 keyset 翻页（order_id > " + lastId + "）返回 " + page2.size() + " 行（首页 order_id=" + page2.get(0) + "）");
            System.out.println("🔍 下推的物理 SQL（共 " + keysetSql.size() + " 条）：");
            for (String[] r : keysetSql) {
                System.out.println("  [" + r[0].replace("shard_", "") + "] " + r[1]);
            }
            boolean keysetShallow = true;
            for (String[] r : keysetSql) {
                if (!r[1].contains("LIMIT 20")) {
                    keysetShallow = false;
                }
            }
            check(keysetShallow, "keyset 每片下推 LIMIT 20（恒定成本，不随翻页深度放大）");
            check(page2.get(0) > lastId, "keyset 续页从 last_id 之后开始（无重叠无遗漏）");

            // ---------- 3. 计时对比（教学量级，3 次取中位） ----------
            System.out.println("\n⏱️ 计时对比（3 次取中位，教学量级）：");
            long offsetMed = median(timed(st, "SELECT order_id FROM `order` ORDER BY order_id LIMIT 9000, 20"));
            long keysetMed = median(timed(st, "SELECT order_id FROM `order` WHERE order_id > 1000000 ORDER BY order_id LIMIT 20"));
            System.out.println("  offset=9000 中位 " + offsetMed + " ms");
            System.out.println("  keyset 中位 " + keysetMed + " ms");
            System.out.println("  （数据量 2.5 万行教学量级，全在页缓存；延迟差异仅供参考，扫描行数差异才是机制证据）");
        }
        System.out.println("\n✅ EX-04 完成：offset 下推 LIMIT offset+limit vs keyset 下推 LIMIT N，断言全部通过");
    }

    private static List<Long> fetch(Statement st, String sql) throws Exception {
        List<Long> ids = new ArrayList<>();
        try (ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private static long[] timed(Statement st, String sql) throws Exception {
        long[] times = new long[3];
        for (int i = 0; i < 3; i++) {
            long t0 = System.nanoTime();
            fetch(st, sql);
            times[i] = (System.nanoTime() - t0) / 1_000_000;
        }
        return times;
    }

    private static long median(long[] a) {
        java.util.Arrays.sort(a);
        return a[1];
    }
}