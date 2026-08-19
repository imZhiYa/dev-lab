package com.zhiya.dtx.tcc;

import com.zhiya.dtx.lab.DtxLabBase;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * TCC 悬挂扫描任务（distributed-tx-02 L3）：二阶段结果丢失 + 防御未覆盖路径的兜底保险
 *
 * 悬挂 = Try 已生效（FROZEN），但 Confirm/Cancel 永远没来 → 预留记录无人推进。
 * 扫描策略（文章 L3）：
 *  - 扫描目标：FROZEN 且超过阈值未闭环
 *  - 两个变量：扫描周期（成本 vs 发现延迟）、超时阈值（必须 > 正常 Try→Confirm/Cancel 最长路径）
 *  - 防误伤：阈值由业务 SLO 决定；扫描「只告警/标记，不自动清理」，清理前二次校验全局事务已结束
 *  - 规模：生产上按 create_time 分桶/分区，或单独落轻量扫描表，避免大范围扫热点冻结表
 */
public class HangingScan extends DtxLabBase {

    private final DataSource ds;

    public HangingScan(DataSource ds) {
        this.ds = ds;
    }

    /** 一条悬挂候选（只读报告，不修改） */
    public record Hanging(String freezeId, String globalTxId, String resourceId, long freezeAmount) {
    }

    /**
     * 扫描 FROZEN 且 created_at 早于 now - thresholdSeconds 的冻结记录。
     * 只查不删：本实现遵守「先告警、人工确认后再清理」的分阶段处置。
     */
    public List<Hanging> scan(int thresholdSeconds) {
        List<Hanging> result = new ArrayList<>();
        String sql = "SELECT freeze_id, global_tx_id, resource_id, freeze_amount FROM freeze "
                + "WHERE status='FROZEN' AND created_at < (NOW() - INTERVAL ? SECOND)";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, thresholdSeconds);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Hanging(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("悬挂扫描失败", e);
        }
        return result;
    }
}
