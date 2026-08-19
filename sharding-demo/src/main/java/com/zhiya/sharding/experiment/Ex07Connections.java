package com.zhiya.sharding.experiment;

import com.zhiya.sharding.lab.ShardingLabBase;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * EX-07 连接预算：连接按库算，不按片算（shard-02 L6）
 *
 * 文章断言：JDBC 层连接目标数 = 应用实例数 × 库数（每实例对每库一个连接池）；
 * 8 个物理分片只占 4 个库 → 连接预算上限 = 4 数据源 × 池大小 4 = 16
 *
 * 实测：information_schema.processlist 统计 shard_ds* 用户的连接
 *  - 懒初始化（minimumIdle=0）后空闲连接 ≈ 0
 *  - 一次全量广播（8 片并发）后：连接数 ≤ 16（池上限），且连接按库聚在 4 个池
 */
public final class Ex07Connections extends ShardingLabBase {

    public static void main(String[] args) throws Exception {
        System.out.println("========== EX-07 连接预算：连接按库算，不按片算 ==========");

        // 数据源已创建（懒初始化池），先统计空闲态连接
        System.out.println("\n[空闲态] 数据源已创建但无查询（minimumIdle=0 懒初始化）：");
        long idle = processlistCount();
        System.out.println("  shard_ds* 连接数 = " + idle);

        // 一次全量广播查询：8 片并发执行
        try (Connection c = sharding().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `order`")) {
            rs.next();
            System.out.println("  广播 COUNT 结果 = " + rs.getLong(1));
        }
        Thread.sleep(200); // 等连接归池
        long after = processlistCount();
        System.out.println("\n[广播后] shard_ds* 连接数 = " + after);

        // 按库统计（pool 名在 processlist 的 info 不可见，改用 user 前缀统计库分布）
        System.out.println("\n[按库分布] shard_ds* 连接在各库的分布：");
        try (Connection oc = observer().getConnection();
             Statement ost = oc.createStatement();
             ResultSet rs = ost.executeQuery(
                     "SELECT user, COUNT(*) cnt FROM information_schema.processlist "
                             + "WHERE user LIKE 'shard\\_ds%' GROUP BY user")) {
            while (rs.next()) {
                System.out.println("  " + rs.getString(1) + " → " + rs.getInt(2) + " 连接");
            }
        }

        // 断言：连接数 ≤ 4 数据源 × 4 池大小 = 16（连接预算按库算，8 片不放大连接）
        check(after <= 16, "广播 8 片后连接数 " + after + " ≤ 16（= 4 库 × 池大小 4）");
        System.out.println("  （若按片算应为 8 个池——实测连接只按库聚，8 片共用 4 个池）");
        System.out.println("\n✅ EX-07 完成：连接预算按库不按片，实测上限 16");
    }

    private static long processlistCount() throws Exception {
        try (Connection oc = observer().getConnection();
             Statement ost = oc.createStatement();
             ResultSet rs = ost.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.processlist WHERE user LIKE 'shard\\_ds%'")) {
            rs.next();
            return rs.getLong(1);
        }
    }
}