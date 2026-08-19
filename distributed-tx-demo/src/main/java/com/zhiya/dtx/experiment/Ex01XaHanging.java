package com.zhiya.dtx.experiment;

import com.zhiya.dtx.lab.DtxLabBase;
import com.zhiya.dtx.xa.XaHelper;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * EX-01：2PC/XA 的 prepare 后悬挂与 in-doubt（distributed-tx-00 L2 / DC-01，对应知识库 EX-01/02/05）
 *
 * 验证断言：
 *  - prepare 后三库进入 in-doubt，XA RECOVER 能列出（协调者崩溃的排查入口）
 *  - prepare 后参与者持锁：新连接写同一行会锁等待超时（「悬挂」的物化）
 *  - 人工/工具裁决 COMMIT 全部后，三库最终一致，in-doubt 清空
 */
public class Ex01XaHanging extends DtxLabBase {

    public static void main(String[] args) throws Exception {
        System.out.println("========== EX-01：2PC/XA 的 prepare 后悬挂与 in-doubt ==========");

        // 复位三库 + 清理上一轮遗留的 in-doubt（down -v 之外的健壮性兜底）
        reset();
        clearStaleInDoubt();

        // ① 三个参与者各自 prepare（协调者收集完投票、尚未广播 commit 时崩溃）
        System.out.println("--- [1] 三库各自 XA PREPARE（模拟协调者在 prepare 后、commit 前崩溃）---");
        Connection cOrder = rawConnection("order_db");
        Connection cInv = rawConnection("inventory_db");
        Connection cPay = rawConnection("payment_db");

        XaHelper.xaStart(cOrder, "b-order");
        exec(cOrder, "INSERT INTO orders (order_id, status) VALUES (9001, 'CREATED')");
        XaHelper.xaEnd(cOrder, "b-order");
        XaHelper.xaPrepare(cOrder, "b-order");
        System.out.println("    [XA] order_db 分支 b-order 已 PREPARE（订单 9001 未提交，in-doubt）");

        XaHelper.xaStart(cInv, "b-inv");
        exec(cInv, "UPDATE resource SET total = total - 5 WHERE resource_id='SKU1'");
        XaHelper.xaEnd(cInv, "b-inv");
        XaHelper.xaPrepare(cInv, "b-inv");
        System.out.println("    [XA] inventory_db 分支 b-inv 已 PREPARE（库存 -5 未提交，in-doubt）");

        XaHelper.xaStart(cPay, "b-pay");
        exec(cPay, "UPDATE resource SET total = total - 1000 WHERE resource_id='U1'");
        XaHelper.xaEnd(cPay, "b-pay");
        XaHelper.xaPrepare(cPay, "b-pay");
        System.out.println("    [XA] payment_db 分支 b-pay 已 PREPARE（余额 -1000 未提交，in-doubt）");

        // ② 协调者崩溃后，XA RECOVER 是唯一排查入口
        System.out.println("--- [2] 协调者崩溃，XA RECOVER 列出 in-doubt ---");
        try (Connection probe = rawConnection("order_db")) {
            List<String> inDoubt = XaHelper.xaRecover(probe);
            System.out.println("    [XA RECOVER] in-doubt 事务：" + inDoubt);
            check(inDoubt.size() == 3, "XA RECOVER 列出 3 个 in-doubt 事务（悬挂）");
        }

        // ③ 悬挂的物化：prepare 后参与者持行锁，新连接写同一行锁等待超时
        System.out.println("--- [3] prepare 后持锁（悬挂）：新连接写 SKU1 锁等待超时 ---");
        boolean locked = false;
        try (Connection probe = rawConnection("inventory_db")) {
            try (Statement st = probe.createStatement()) {
                st.execute("SET SESSION innodb_lock_wait_timeout=1");
            }
            try {
                exec(probe, "UPDATE resource SET total = total - 1 WHERE resource_id='SKU1'");
            } catch (SQLException e) {
                locked = true;   // 锁等待超时（errorCode 1205）
            }
        }
        check(locked, "prepare 后 SKU1 行被锁：新连接 UPDATE 触发锁等待超时（业务冻结）");

        // ④ 人工裁决 COMMIT 全部，三库最终一致
        System.out.println("--- [4] 人工裁决：XA COMMIT 全部 → 三库最终一致 ---");
        XaHelper.xaCommit(cOrder, "b-order");
        XaHelper.xaCommit(cInv, "b-inv");
        XaHelper.xaCommit(cPay, "b-pay");
        cOrder.close();
        cInv.close();
        cPay.close();

        checkEq(1L, countOrders(9001L), "订单 9001 已提交（order_db）");
        checkEq(95L, inventoryTotal(), "库存已扣减到 95（inventory_db）");
        checkEq(9000L, paymentTotal(), "余额已扣减到 9000（payment_db）");

        try (Connection probe = rawConnection("order_db")) {
            check(XaHelper.xaRecover(probe).isEmpty(), "裁决后 in-doubt 清空");
        }

        System.out.println("✅ EX-01 通过：prepare 后悬挂 → XA RECOVER 排查 → 人工裁决 → 三库一致");
    }

    private static void reset() {
        exec(order(), "DELETE FROM orders WHERE order_id=9001");
        exec(inventory(), "UPDATE resource SET total=100, frozen=0 WHERE resource_id='SKU1'");
        exec(payment(), "UPDATE resource SET total=10000, frozen=0 WHERE resource_id='U1'");
    }

    /** 清理上一轮遗留的 in-doubt（保证可重复执行；run-all 的 down -v 之外的双保险） */
    private static void clearStaleInDoubt() throws SQLException {
        try (Connection c = rawConnection("order_db")) {
            for (String xid : XaHelper.xaRecover(c)) {
                XaHelper.xaRollback(c, xid);
            }
        }
    }

    private static long inventoryTotal() {
        Long v = queryLong(inventory(), "SELECT total FROM resource WHERE resource_id='SKU1'");
        return v == null ? -1 : v;
    }

    private static long paymentTotal() {
        Long v = queryLong(payment(), "SELECT total FROM resource WHERE resource_id='U1'");
        return v == null ? -1 : v;
    }

    private static long countOrders(long orderId) {
        Long v = queryLong(order(), "SELECT COUNT(*) FROM orders WHERE order_id=?", orderId);
        return v == null ? 0 : v;
    }
}
