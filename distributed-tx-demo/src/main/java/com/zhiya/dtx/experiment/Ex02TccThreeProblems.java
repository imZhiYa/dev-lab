package com.zhiya.dtx.experiment;

import com.zhiya.dtx.lab.DtxLabBase;
import com.zhiya.dtx.tcc.TccResource;

/**
 * EX-02：TCC 三难题——空回滚 / 悬挂 / 幂等（distributed-tx-02 L2，对应知识库 EX-06）
 *
 * 场景注入（乱序 + 重复）：
 *  A 正常：Try 冻结 → Confirm 正式扣减；重复 Confirm 幂等（不二次扣减）
 *  B 空回滚 + 防悬挂：Cancel 先到（Try 未执行）→ 写 SUSPENDED 标记；迟到 Try 被拦截
 *  C 正常回滚：Try 冻结 → Cancel 释放；重复 Cancel 幂等
 *  D 可用量不足：Try 冻结超过可用量 → 整个本地事务回滚（含控制表插入）
 */
public class Ex02TccThreeProblems extends DtxLabBase {

    public static void main(String[] args) {
        System.out.println("========== EX-02：TCC 三难题（空回滚 / 悬挂 / 幂等）==========");

        resetTcc();
        TccResource pay = new TccResource("余额", payment());

        // ---------------- 场景 A：正常 Try → Confirm，重复 Confirm 幂等 ----------------
        System.out.println("--- [A] 正常 Try→Confirm + 重复 Confirm 幂等 ---");
        check(pay.tryFreeze("gtx-a", "br-1", "f-a-1", "U1", 1000) == TccResource.TccOutcome.TRY_FROZEN,
                "A.Try 首次冻结 1000 → TRY_FROZEN");
        checkEq(10000, pay.totalOf("U1"), "A.Try 后 total 不变（10000）");
        checkEq(1000, pay.frozenOf("U1"), "A.Try 后 frozen=1000（可用量 9000）");
        checkStr("TRIED", pay.fenceStatusOf("gtx-a", "br-1"), "A.Try 后控制表 TRIED");

        check(pay.confirm("gtx-a", "br-1", "f-a-1", "U1", 1000) == TccResource.TccOutcome.CONFIRMED,
                "A.Confirm 首次 → CONFIRMED");
        checkEq(9000, pay.totalOf("U1"), "A.Confirm 后 total=9000（正式扣减）");
        checkEq(0, pay.frozenOf("U1"), "A.Confirm 后 frozen=0（释放冻结）");
        checkStr("COMMITTED", pay.fenceStatusOf("gtx-a", "br-1"), "A.Confirm 后控制表 COMMITTED");

        check(pay.confirm("gtx-a", "br-1", "f-a-1", "U1", 1000) == TccResource.TccOutcome.CONFIRM_IDEMPOTENT,
                "A.重复 Confirm → CONFIRM_IDEMPOTENT（副作用在门后）");
        checkEq(9000, pay.totalOf("U1"), "A.重复 Confirm 后 total 仍 9000（未二次扣减）");

        // ---------------- 场景 B：空回滚 + 防悬挂 ----------------
        System.out.println("--- [B] 空回滚（Cancel 先到）+ 迟到 Try 防悬挂 ---");
        check(pay.cancel("gtx-b", "br-1", "f-b-1", "U1", 1000) == TccResource.TccOutcome.SUSPENDED_MARKED,
                "B.Cancel 先到且 Try 未执行 → 写 SUSPENDED 标记（空回滚，不报错）");
        checkStr("SUSPENDED", pay.fenceStatusOf("gtx-b", "br-1"), "B.空回滚后控制表 SUSPENDED");
        checkEq(0, pay.frozenOf("U1"), "B.空回滚后 frozen 仍 0（Try 从未冻结）");

        check(pay.tryFreeze("gtx-b", "br-1", "f-b-1", "U1", 1000) == TccResource.TccOutcome.TRY_REJECTED,
                "B.迟到 Try 撞 SUSPENDED → TRY_REJECTED（防悬挂）");
        checkEq(0, pay.frozenOf("U1"), "B.迟到 Try 被拦截后 frozen 仍 0（无悬挂预留）");
        checkEq(9000, pay.totalOf("U1"), "B.全程 total 未变（9000）");

        // ---------------- 场景 C：正常 Try → Cancel，重复 Cancel 幂等 ----------------
        System.out.println("--- [C] 正常 Try→Cancel + 重复 Cancel 幂等 ---");
        check(pay.tryFreeze("gtx-c", "br-1", "f-c-1", "U1", 1000) == TccResource.TccOutcome.TRY_FROZEN,
                "C.Try 冻结 1000 → TRY_FROZEN");
        checkEq(1000, pay.frozenOf("U1"), "C.Try 后 frozen=1000");

        check(pay.cancel("gtx-c", "br-1", "f-c-1", "U1", 1000) == TccResource.TccOutcome.CANCELLED,
                "C.Cancel 首次 → CANCELLED（释放冻结）");
        checkEq(9000, pay.totalOf("U1"), "C.Cancel 后 total 仍 9000（未扣减）");
        checkEq(0, pay.frozenOf("U1"), "C.Cancel 后 frozen=0");
        checkStr("ROLLBACKED", pay.fenceStatusOf("gtx-c", "br-1"), "C.Cancel 后控制表 ROLLBACKED");

        check(pay.cancel("gtx-c", "br-1", "f-c-1", "U1", 1000) == TccResource.TccOutcome.CANCEL_IDEMPOTENT,
                "C.重复 Cancel → CANCEL_IDEMPOTENT");
        checkEq(0, pay.frozenOf("U1"), "C.重复 Cancel 后 frozen 仍 0");

        // ---------------- 场景 D：可用量不足，Try 整体回滚 ----------------
        System.out.println("--- [D] 可用量不足：Try 冻结超过可用量 → 本地事务回滚 ---");
        boolean insufficient = false;
        try {
            pay.tryFreeze("gtx-d", "br-1", "f-d-1", "U1", 999999);
        } catch (TccResource.InsufficientResourceException e) {
            insufficient = true;
        }
        check(insufficient, "D.Try 冻结 999999（> 可用量 9000）→ 抛 InsufficientResourceException");
        check(null == pay.fenceStatusOf("gtx-d", "br-1"), "D.回滚后控制表无残留（插入一并回滚）");
        checkEq(0, pay.frozenOf("U1"), "D.回滚后 frozen 仍 0");

        System.out.println("✅ EX-02 通过：空回滚 / 悬挂 / 幂等 / 可用量不足 全部按状态机正确判定");
    }

    private static void resetTcc() {
        exec(payment(), "UPDATE resource SET total=10000, frozen=0 WHERE resource_id='U1'");
        exec(payment(), "DELETE FROM freeze WHERE global_tx_id LIKE 'gtx-%'");
        exec(payment(), "DELETE FROM tcc_fence WHERE global_tx_id LIKE 'gtx-%'");
    }
}
