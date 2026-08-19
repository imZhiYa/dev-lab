package com.zhiya.dtx.xa;

import com.zhiya.dtx.lab.DtxLabBase;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL XA 助手（distributed-tx-00 L2）：XA START/END/PREPARE/COMMIT/ROLLBACK/RECOVER
 *
 * 语义边界（文章 L2 与 topic 铁律）：
 *  - XA 是接口规范（X/Open XA），2PC 是实现该协调模型的主流协议
 *  - MySQL InnoDB 通过 SQL 暴露该能力（Implementation，8.0 行为）
 *  - XA PREPARE 后：参与者持锁 + 写日志 + 进入 in-doubt，只能等协调者裁决
 *  - XA RECOVER 列出 in-doubt（已 prepare 未裁决）事务 = 协调者崩溃时的排查入口
 *
 * ⚠️ 生产坑（文章 L6）：XA 事务从 PREPARE 到 COMMIT/ROLLBACK 必须独占一条连接，
 *   不能还给连接池——并发稍高连接池可能先于行锁被耗尽。
 */
public class XaHelper extends DtxLabBase {

    private XaHelper() {
    }

    public static void xaStart(Connection c, String xid) throws SQLException {
        exec0(c, "XA START '" + xid + "'");
    }

    public static void xaEnd(Connection c, String xid) throws SQLException {
        exec0(c, "XA END '" + xid + "'");
    }

    public static void xaPrepare(Connection c, String xid) throws SQLException {
        exec0(c, "XA PREPARE '" + xid + "'");
    }

    public static void xaCommit(Connection c, String xid) throws SQLException {
        exec0(c, "XA COMMIT '" + xid + "'");
    }

    public static void xaRollback(Connection c, String xid) throws SQLException {
        exec0(c, "XA ROLLBACK '" + xid + "'");
    }

    /** XA RECOVER：列出 in-doubt 事务的 XID（协调者崩溃后的人/自动排查入口） */
    public static List<String> xaRecover(Connection c) throws SQLException {
        List<String> out = new ArrayList<>();
        // XA RECOVER 返回列：formatID, gtrid_length, bqual_length, data
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("XA RECOVER")) {
            while (rs.next()) {
                out.add(rs.getString(4));   // data 列 = XID 字符串
            }
        }
        return out;
    }

    private static void exec0(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }
}
