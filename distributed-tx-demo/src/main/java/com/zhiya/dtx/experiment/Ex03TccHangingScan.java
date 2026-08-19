package com.zhiya.dtx.experiment;

import com.zhiya.dtx.lab.DtxLabBase;
import com.zhiya.dtx.tcc.HangingScan;

import java.util.List;

/**
 * EX-03：TCC 悬挂检测与阈值校准（distributed-tx-02 L3，对应知识库 EX-04）
 *
 * 验证断言：
 *  - 扫描目标 = FROZEN 且超过阈值未闭环（二阶段结果丢失 / 防御未覆盖的残留）
 *  - 防误伤：刚创建的 FROZEN（可能是正常长事务）不被阈值内的扫描误判
 *  - 只告警/标记，不自动清理（清理前必须二次校验全局事务已结束）
 *  - 阈值权衡：阈值放大 → 发现延迟（连真实悬挂也漏报）
 */
public class Ex03TccHangingScan extends DtxLabBase {

    public static void main(String[] args) {
        System.out.println("========== EX-03：TCC 悬挂检测与阈值校准 ==========");

        reset();

        // ① 制造两条 FROZEN：一条 2 小时前的「真悬挂」，一条刚创建的「正常长事务」
        exec(payment(),
                "INSERT INTO freeze (freeze_id, global_tx_id, branch_id, resource_id, freeze_amount, status, created_at) "
                        + "VALUES ('f-hang', 'gtx-hang', 'br-1', 'U1', 1000, 'FROZEN', NOW() - INTERVAL 2 HOUR)");
        exec(payment(),
                "INSERT INTO freeze (freeze_id, global_tx_id, branch_id, resource_id, freeze_amount, status, created_at) "
                        + "VALUES ('f-fresh', 'gtx-fresh', 'br-1', 'U1', 500, 'FROZEN', NOW())");

        HangingScan scanner = new HangingScan(payment());

        // ② 阈值 60s：只扫到 2 小时前的真悬挂，不误伤刚创建的 FROZEN
        System.out.println("--- [1] 阈值 60s：只检出真悬挂，不误伤刚创建的 FROZEN ---");
        List<HangingScan.Hanging> found = scanner.scan(60);
        for (HangingScan.Hanging h : found) {
            System.out.println("    [悬挂] freezeId=" + h.freezeId() + " gtx=" + h.globalTxId()
                    + " resource=" + h.resourceId() + " amount=" + h.freezeAmount());
        }
        check(found.size() == 1 && "f-hang".equals(found.get(0).freezeId()),
                "阈值 60s 只检出 f-hang（真悬挂），f-fresh 未被误判（防误伤）");

        // ③ 只告警不清理：扫描后 f-hang 仍是 FROZEN（分阶段：先告警、人工确认后清理）
        System.out.println("--- [2] 只告警不清理：悬挂记录仍为 FROZEN，待人工二次校验 ---");
        checkStr("FROZEN", freezeStatus("f-hang"), "扫描后 f-hang 仍 FROZEN（未自动清理）");
        checkStr("FROZEN", freezeStatus("f-fresh"), "f-fresh 仍 FROZEN（正常长事务不被碰）");

        // ④ 阈值权衡：阈值放大到 3 小时，连 2 小时前的真悬挂也扫不到（发现延迟）
        System.out.println("--- [3] 阈值放大到 3 小时：发现延迟，连真悬挂也漏报 ---");
        List<HangingScan.Hanging> none = scanner.scan(3 * 3600);
        check(none.isEmpty(), "阈值 3h > 悬挂时长 2h → 扫不到（阈值越大发现越晚，无最优值）");

        System.out.println("✅ EX-03 通过：悬挂检出 + 防误伤 + 只告警不清理 + 阈值权衡");
    }

    private static String freezeStatus(String freezeId) {
        return queryStr(payment(), "SELECT status FROM freeze WHERE freeze_id=?", freezeId);
    }

    private static void reset() {
        exec(payment(), "UPDATE resource SET total=10000, frozen=0 WHERE resource_id='U1'");
        exec(payment(), "DELETE FROM freeze WHERE global_tx_id LIKE 'gtx-%'");
        exec(payment(), "DELETE FROM tcc_fence WHERE global_tx_id LIKE 'gtx-%'");
    }
}
