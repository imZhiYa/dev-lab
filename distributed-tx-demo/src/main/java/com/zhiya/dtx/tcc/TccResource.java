package com.zhiya.dtx.tcc;

import com.zhiya.dtx.lab.DtxLabBase;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * TCC 资源方：Try/Confirm/Cancel + 冻结表 + 事务控制表（distributed-tx-02 L2）
 *
 * 核心不变量（与文章逐条对应）：
 *  - 幂等 = 唯一约束 + 状态机终态判定：Confirm/Cancel 先做状态 CAS，
 *    影响行数为 0 就幂等返回，不再执行任何库存副作用（「副作用在门后」）
 *  - 空回滚：Cancel 先到、Try 未执行 → 控制表写 SUSPENDED 标记再返回成功（不报错）
 *  - 防悬挂：迟到 Try 第一步向控制表 INSERT（唯一键 globalTxId+branchId），
 *    插入冲突 = 已被处理过 → 拒绝执行；这条 SUSPENDED 标记是拦截迟到 Try 的唯一依据
 *  - 两张表（freeze + tcc_fence）与资源更新必须在同一本地事务里
 *
 * 协议纪律（文章 L2 补充）：Try 全部成功后只能走向 Confirm；
 *  Confirm/Cancel 不允许返回业务失败，只能重试直到成功或进人工/对账。
 */
public class TccResource extends DtxLabBase {

    /** 资源类型标签（库存/余额），仅用于输出可读性，不参与 SQL 逻辑 */
    private final String resourceType;
    private final DataSource ds;

    public TccResource(String resourceType, DataSource ds) {
        this.resourceType = resourceType;
        this.ds = ds;
    }

    /** Try/Confirm/Cancel 的结果，实验按此断言（与 fence/freeze 状态机一一对应） */
    public enum TccOutcome {
        TRY_FROZEN,          // Try 首次执行：冻结成功
        TRY_IDEMPOTENT,      // Try 已执行过（fence TRIED/COMMITTED）：幂等成功
        TRY_REJECTED,        // 迟到 Try 被拦截（fence ROLLBACKED/SUSPENDED）：防悬挂
        CONFIRMED,           // Confirm 首次执行：正式扣减 + 释放冻结
        CONFIRM_IDEMPOTENT,  // Confirm 重复（freeze 已终态）：幂等成功，不做扣减
        CANCELLED,           // Cancel 首次执行：释放冻结
        CANCEL_IDEMPOTENT,   // Cancel 重复（freeze 已终态/空回滚重复）：幂等成功
        SUSPENDED_MARKED     // 空回滚：Try 未执行，Cancel 写 SUSPENDED 标记
    }

    /** 可用量不足：Try 预留失败，整个本地事务回滚（含控制表插入） */
    public static class InsufficientResourceException extends RuntimeException {
        public InsufficientResourceException(String msg) {
            super(msg);
        }
    }

    // ------------------------------------------------------------------
    // Try（预留）：① 控制表 INSERT（唯一键）→ ② 条件 UPDATE 冻结可用量 → ③ 写冻结记录
    // ------------------------------------------------------------------

    public TccOutcome tryFreeze(String globalTxId, String branchId, String freezeId,
                                String resourceId, long amount) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                // ① 第一步：向控制表 INSERT（唯一键 globalTxId+branchId，状态 TRIED）
                try {
                    exec(c, "INSERT INTO tcc_fence (global_tx_id, branch_id, status) VALUES (?, ?, 'TRIED')",
                            globalTxId, branchId);
                } catch (SQLException e) {
                    c.rollback();
                    if (!isDuplicateKey(e)) {
                        throw e;
                    }
                    // 插入冲突 → 已被处理过，按状态区分（靠「写」防御，不是先查）
                    String existing = queryStr(c,
                            "SELECT status FROM tcc_fence WHERE global_tx_id=? AND branch_id=?",
                            globalTxId, branchId);
                    if ("TRIED".equals(existing) || "COMMITTED".equals(existing)) {
                        return TccOutcome.TRY_IDEMPOTENT;   // Try 已执行过 → 幂等成功
                    }
                    return TccOutcome.TRY_REJECTED;         // ROLLBACKED/SUSPENDED → 拦截迟到 Try
                }
                // ② 插入成功 → 条件 UPDATE 原子校验并冻结可用量（total - frozen >= amount）
                int n = exec(c,
                        "UPDATE resource SET frozen = frozen + ? WHERE resource_id = ? AND (total - frozen) >= ?",
                        amount, resourceId, amount);
                if (n == 0) {
                    c.rollback();
                    throw new InsufficientResourceException(
                            resourceType + " 资源 " + resourceId + " 可用量不足（冻结 " + amount + " 失败）");
                }
                // ③ 写冻结记录（freeze_id 唯一约束 = 幂等锚点）
                exec(c, "INSERT INTO freeze (freeze_id, global_tx_id, branch_id, resource_id, freeze_amount, status) "
                        + "VALUES (?, ?, ?, ?, ?, 'FROZEN')",
                        freezeId, globalTxId, branchId, resourceId, amount);
                c.commit();
                System.out.println("    [TCC-Try] " + resourceType + " 冻结 " + amount + "（freezeId=" + freezeId + "）");
                return TccOutcome.TRY_FROZEN;
            } catch (SQLException e) {
                c.rollback();
                throw new RuntimeException("Try 执行失败", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Try 连接失败", e);
        }
    }

    // ------------------------------------------------------------------
    // Confirm（确认）：幂等门在最前，副作用在门后
    // ------------------------------------------------------------------

    public TccOutcome confirm(String globalTxId, String branchId, String freezeId,
                              String resourceId, long amount) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                // ① 幂等门：状态 CAS FROZEN → CONFIRMED，影响行数=0 → 已终态，直接成功返回
                int n = exec(c, "UPDATE freeze SET status='CONFIRMED' WHERE freeze_id=? AND status='FROZEN'", freezeId);
                if (n == 0) {
                    c.rollback();
                    return TccOutcome.CONFIRM_IDEMPOTENT;   // 重复 Confirm，不做任何扣减
                }
                // ② 影响行数=1：正式扣减 + 释放冻结（同一行一次更新）
                exec(c, "UPDATE resource SET total = total - ?, frozen = frozen - ? WHERE resource_id = ?",
                        amount, amount, resourceId);
                // ③ 控制表 TRIED → COMMITTED
                exec(c, "UPDATE tcc_fence SET status='COMMITTED' WHERE global_tx_id=? AND branch_id=? AND status='TRIED'",
                        globalTxId, branchId);
                c.commit();
                System.out.println("    [TCC-Confirm] " + resourceType + " 正式扣减 " + amount + "（freezeId=" + freezeId + "）");
                return TccOutcome.CONFIRMED;
            } catch (SQLException e) {
                c.rollback();
                // 协议纪律：Confirm 不允许返回业务失败，只能重试（此处演示为抛错待重试）
                throw new RuntimeException("Confirm 执行失败（只能重试，不得返回业务失败）", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Confirm 连接失败", e);
        }
    }

    // ------------------------------------------------------------------
    // Cancel（取消）：先过门；空回滚写 SUSPENDED，副作用在门后
    // ------------------------------------------------------------------

    public TccOutcome cancel(String globalTxId, String branchId, String freezeId,
                             String resourceId, long amount) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                // ① 控制表处置：TRIED → ROLLBACKED；无记录 → INSERT SUSPENDED（空回滚）
                String existing = queryStr(c,
                        "SELECT status FROM tcc_fence WHERE global_tx_id=? AND branch_id=?",
                        globalTxId, branchId);
                if (existing == null) {
                    // 空回滚：Try 未执行 → 写 SUSPENDED 标记（拦截迟到 Try 的唯一依据），不报错
                    try {
                        exec(c, "INSERT INTO tcc_fence (global_tx_id, branch_id, status) VALUES (?, ?, 'SUSPENDED')",
                                globalTxId, branchId);
                    } catch (SQLException e) {
                        if (!isDuplicateKey(e)) {
                            throw e;
                        }
                        // 撞已有 SUSPENDED（重复空回滚）→ 幂等
                    }
                } else if ("TRIED".equals(existing)) {
                    exec(c, "UPDATE tcc_fence SET status='ROLLBACKED' WHERE global_tx_id=? AND branch_id=? AND status='TRIED'",
                            globalTxId, branchId);
                }
                // COMMITTED / ROLLBACKED / SUSPENDED → 已处理过，fence 不动

                // ② 幂等门：freeze FROZEN → CANCELLED，影响行数=0 → 终态或不存在（空回滚）
                int n = exec(c, "UPDATE freeze SET status='CANCELLED' WHERE freeze_id=? AND status='FROZEN'", freezeId);
                if (n == 0) {
                    c.commit();
                    return (existing == null) ? TccOutcome.SUSPENDED_MARKED : TccOutcome.CANCEL_IDEMPOTENT;
                }
                // ③ 仅当 ② 影响行数=1：释放冻结（空回滚路径不执行——Try 从未冻结过）
                exec(c, "UPDATE resource SET frozen = frozen - ? WHERE resource_id = ?", amount, resourceId);
                c.commit();
                System.out.println("    [TCC-Cancel] " + resourceType + " 释放冻结 " + amount + "（freezeId=" + freezeId + "）");
                return TccOutcome.CANCELLED;
            } catch (SQLException e) {
                c.rollback();
                throw new RuntimeException("Cancel 执行失败", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cancel 连接失败", e);
        }
    }

    // ------------------------------------------------------------------
    // 只读观测（实验断言用）
    // ------------------------------------------------------------------

    public long totalOf(String resourceId) {
        Long v = queryLong(ds, "SELECT total FROM resource WHERE resource_id=?", resourceId);
        return v == null ? -1 : v;
    }

    public long frozenOf(String resourceId) {
        Long v = queryLong(ds, "SELECT frozen FROM resource WHERE resource_id=?", resourceId);
        return v == null ? -1 : v;
    }

    /** 可用量 = total - frozen */
    public long availableOf(String resourceId) {
        return totalOf(resourceId) - frozenOf(resourceId);
    }

    public String fenceStatusOf(String globalTxId, String branchId) {
        return queryStr(ds, "SELECT status FROM tcc_fence WHERE global_tx_id=? AND branch_id=?",
                globalTxId, branchId);
    }

    public String freezeStatusOf(String freezeId) {
        return queryStr(ds, "SELECT status FROM freeze WHERE freeze_id=?", freezeId);
    }
}
