package com.zhiya.dtx.lab;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 实验公共基类：三库数据源、断言工具、事务助手
 *
 * 拓扑（知识库 distributed-tx 系列基线）：下单请求 B 一次动作写三库
 *   order_db（订单）/ inventory_db（库存）/ payment_db（支付）
 *   单 MySQL 容器承载三库（与 sharding-demo 同思路：物理实例 1、逻辑库 3）
 *
 * 数据源策略：每库一个独立 Hikari 连接池（模拟三服务三连接拓扑），
 *   禁止用一个连接跨库——这本身就是 L1「本地事务边界 = 单个连接」的物化。
 */
public abstract class DtxLabBase {

    public static final String JDBC_BASE = "jdbc:mysql://127.0.0.1:3308/";
    public static final String USER = "root";
    public static final String PASSWORD = "root";

    private static final Map<String, DataSource> POOLS = new HashMap<>();

    protected static DataSource order()     { return ds("order_db"); }
    protected static DataSource inventory() { return ds("inventory_db"); }
    protected static DataSource payment()   { return ds("payment_db"); }

    protected static DataSource ds(String db) {
        return POOLS.computeIfAbsent(db, k -> {
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(JDBC_BASE + k);
            cfg.setUsername(USER);
            cfg.setPassword(PASSWORD);
            cfg.setMaximumPoolSize(4);
            cfg.setMinimumIdle(0);
            return new HikariDataSource(cfg);
        });
    }

    /**
     * 裸连接（绕过连接池）：XA START/PREPARE 期间事务上下文必须独占一条连接，
     * 不能还给池（articles/distributed-tx-00 L6 的「XA 独占连接」坑）。
     */
    protected static Connection rawConnection(String db) throws SQLException {
        return DriverManager.getConnection(JDBC_BASE + db, USER, PASSWORD);
    }

    // ------------------------------------------------------------------
    // 断言工具（失败即退出非零，与 run-all / CI 联动）
    // ------------------------------------------------------------------

    protected static void check(boolean cond, String name) {
        if (!cond) {
            System.out.println("❌ 断言失败：" + name);
            System.exit(1);
        }
        System.out.println("✅ 断言通过：" + name);
    }

    protected static void checkEq(long expected, long actual, String name) {
        check(expected == actual, name + "（期望 " + expected + " vs 实际 " + actual + "）");
    }

    protected static void checkStr(String expected, String actual, String name) {
        check(expected.equals(actual), name + "（期望 " + expected + " vs 实际 " + actual + "）");
    }

    // ------------------------------------------------------------------
    // 事务助手（线性流程用；TCC 三难题需「冲突回滚后返回结果」的分支，自行管理连接）
    // ------------------------------------------------------------------

    @FunctionalInterface
    public interface TxFn<T> {
        T apply(Connection c) throws Exception;
    }

    protected static <T> T inTx(DataSource ds, TxFn<T> fn) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                T result = fn.apply(c);
                c.commit();
                return result;
            } catch (Exception e) {
                try { c.rollback(); } catch (SQLException ignored) { /* 已尽力回滚 */ }
                if (e instanceof RuntimeException re) throw re;
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("事务执行失败", e);
        }
    }

    // ------------------------------------------------------------------
    // SQL 工具
    // ------------------------------------------------------------------

    /** 在给定连接上执行写语句，返回影响行数 */
    protected static int exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps.executeUpdate();
        }
    }

    /** 在自动提交连接上执行单条写语句（独立本地事务） */
    protected static int exec(DataSource ds, String sql, Object... args) {
        try (Connection c = ds.getConnection()) {
            return exec(c, sql, args);
        } catch (SQLException e) {
            throw new RuntimeException("执行失败: " + sql, e);
        }
    }

    protected static Long queryLong(Connection c, String sql, Object... args) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询失败: " + sql, e);
        }
    }

    protected static String queryStr(Connection c, String sql, Object... args) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询失败: " + sql, e);
        }
    }

    protected static Long queryLong(DataSource ds, String sql, Object... args) {
        try (Connection c = ds.getConnection()) {
            return queryLong(c, sql, args);
        } catch (SQLException e) {
            throw new RuntimeException("查询失败: " + sql, e);
        }
    }

    protected static String queryStr(DataSource ds, String sql, Object... args) {
        try (Connection c = ds.getConnection()) {
            return queryStr(c, sql, args);
        } catch (SQLException e) {
            throw new RuntimeException("查询失败: " + sql, e);
        }
    }

    /** MySQL 唯一键冲突（errorCode 1062 / SQLState 23000），幂等与防悬挂靠它判定 */
    protected static boolean isDuplicateKey(SQLException e) {
        return e.getErrorCode() == 1062 || "23000".equals(e.getSQLState());
    }
}
