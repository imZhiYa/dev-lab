package com.zhiya.sharding.lab;

import com.zhiya.sharding.config.ShardingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 实验公共基类：数据源创建、断言工具、general_log 观测（下推 SQL 实录）
 *
 * 观测手段说明：
 *   ShardingSphere 5.4.1 的下推 SQL 不在应用侧打印（SQL 日志走 SPI，默认不输出），
 *   因此用 MySQL 侧 general_log（log_output=TABLE）做"穿透运行时"观测——
 *   中间件替应用发的每条物理 SQL 都落在 mysql.general_log，能看到：
 *   路由目标（db）、改写后的 SQL 形态（AVG→SUM+COUNT、同片 IN 合并、limit 下推）
 */
public abstract class ShardingLabBase {

    /** 实验观测用直连（不走中间件，避免污染 general_log 的时间窗口） */
    private static DataSource observer;
    private static DataSource sharding;

    protected static DataSource sharding() {
        try {
            if (sharding == null) {
                sharding = ShardingDataSource.create();
            }
            return sharding;
        } catch (Exception e) {
            throw new RuntimeException("创建 ShardingSphere 数据源失败", e);
        }
    }

    protected static DataSource observer() {
        try {
            if (observer == null) {
                com.zaxxer.hikari.HikariConfig cfg = new com.zaxxer.hikari.HikariConfig();
                cfg.setJdbcUrl(ShardingDataSource.JDBC_URL + "/mysql");
                cfg.setUsername(ShardingDataSource.USER);
                cfg.setPassword(ShardingDataSource.PASSWORD);
                cfg.setMaximumPoolSize(1);
                cfg.setMinimumIdle(1);
                observer = new com.zaxxer.hikari.HikariDataSource(cfg);
            }
            return observer;
        } catch (Exception e) {
            throw new RuntimeException("创建观测连接失败", e);
        }
    }

    // ------------------------------------------------------------------
    // 断言工具（失败即退出非零，与 verify 脚本联动）
    // ------------------------------------------------------------------

    protected static void check(boolean cond, String name) {
        if (!cond) {
            System.out.println("❌ 断言失败：" + name);
            System.exit(1);
        }
        System.out.println("✅ 断言通过：" + name);
    }

    protected static void checkNear(double measured, double expected, double eps, String name) {
        check(Math.abs(measured - expected) <= eps,
                name + "（实测 " + String.format("%.4f", measured) + " vs 期望 " + expected + "）");
    }

    // ------------------------------------------------------------------
    // general_log 观测
    // ------------------------------------------------------------------

    protected static void generalLogOn() {
        execObserver("SET GLOBAL log_output='TABLE'");
        execObserver("SET GLOBAL general_log=ON");
    }

    protected static void generalLogClear() {
        // 日志表禁止 DELETE（You can't use locks with log tables），需先关 log 再 TRUNCATE
        execObserver("SET GLOBAL general_log=OFF");
        execObserver("TRUNCATE TABLE mysql.general_log");
        execObserver("SET GLOBAL general_log=ON");
    }

    /**
     * 取观测窗口内中间件下推的物理 SQL（按每库专属用户 shard_ds* 识别路由目标）
     * 返回 [ds 名, sql] 列表；记录按 event_time 升序
     */
    protected static List<String[]> generalLogSqls() {
        List<String[]> result = new ArrayList<>();
        try (Connection c = observer().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT user_host, argument FROM mysql.general_log "
                             + "WHERE user_host LIKE 'shard\\_ds%' ORDER BY event_time ASC")) {
            while (rs.next()) {
                String userHost = rs.getString("user_host");
                String sql = rs.getString("argument");
                // user_host 形如 shard_ds0[shard_ds0] @ localhost [] → 解析 ds 名
                String ds = userHost.split("@")[0].split("\\[")[0].trim();
                result.add(new String[]{ds, sql});
            }
        } catch (Exception e) {
            throw new RuntimeException("读取 general_log 失败", e);
        }
        return result;
    }

    private static void execObserver(String sql) {
        try (Connection c = observer().getConnection();
             Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (Exception e) {
            throw new RuntimeException("观测连接执行失败: " + sql, e);
        }
    }

    // ------------------------------------------------------------------
    // 通用查询/统计
    // ------------------------------------------------------------------

    protected static void printRows(ResultSet rs) throws Exception {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        StringBuilder head = new StringBuilder();
        for (int i = 1; i <= cols; i++) {
            head.append(md.getColumnLabel(i)).append(i < cols ? " | " : "");
        }
        System.out.println(head);
        int n = 0;
        while (rs.next()) {
            StringBuilder row = new StringBuilder();
            for (int i = 1; i <= cols; i++) {
                row.append(rs.getString(i)).append(i < cols ? " | " : "");
            }
            System.out.println(row);
            n++;
        }
        System.out.println("（共 " + n + " 行）");
    }
}