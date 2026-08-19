package com.zhiya.dtx.experiment;

import com.zhiya.dtx.lab.DtxLabBase;
import com.zhiya.dtx.saga.SagaOrchestrator;

/**
 * EX-04：Saga 编排器——持久化状态机 + 倒序补偿（distributed-tx-02 L4）
 *
 * 验证断言：
 *  - T1 扣库存成功、T2 扣款明确失败（余额不足）→ 倒序补偿 C1（加回库存）
 *  - 编排器状态机落库：saga 状态 COMPENSATED、步骤状态可回查（持久化，非 try-catch）
 *  - 补偿对偶：补偿后库存回到原位，资金未被扣（净效果 = 零）
 */
public class Ex04SagaCompensation extends DtxLabBase {

    public static void main(String[] args) {
        System.out.println("========== EX-04：Saga 编排器——持久化状态机 + 倒序补偿 ==========");

        reset();
        SagaOrchestrator saga = new SagaOrchestrator(order(), inventory(), payment());

        // 让扣款明确失败：余额 500 < 需扣 1000
        exec(payment(), "UPDATE resource SET total=500, frozen=0 WHERE resource_id='U1'");

        System.out.println("--- [1] T1 扣库存成功 → T2 扣款失败（余额不足）→ 倒序补偿 ---");
        saga.run("saga-ex04", SagaOrchestrator.CHARGE_NORMAL, s -> "FAILURE");

        // ① 状态机落库
        checkStr(SagaOrchestrator.COMPENSATED, saga.sagaState("saga-ex04"), "saga 状态 = COMPENSATED");
        checkStr(SagaOrchestrator.STEP_COMPENSATED, saga.stepStatus("saga-ex04", 1), "step1（扣库存）已补偿");
        checkStr(SagaOrchestrator.STEP_FAILED, saga.stepStatus("saga-ex04", 2), "step2（扣款）FAILED");

        // ② 补偿对偶：库存回到原位，资金未被扣
        checkEq(100L, inventoryTotal(), "C1 加回库存后 SKU1 total=100（净效果零）");
        checkEq(500L, paymentTotal(), "扣款失败后 U1 total 仍 500（未扣，也无需退款）");

        System.out.println("✅ EX-04 通过：Saga 失败 → 持久化状态机记录 → 倒序补偿 → 净效果归零");
    }

    private static long inventoryTotal() {
        Long v = queryLong(inventory(), "SELECT total FROM resource WHERE resource_id='SKU1'");
        return v == null ? -1 : v;
    }

    private static long paymentTotal() {
        Long v = queryLong(payment(), "SELECT total FROM resource WHERE resource_id='U1'");
        return v == null ? -1 : v;
    }

    private static void reset() {
        exec(inventory(), "UPDATE resource SET total=100, frozen=0 WHERE resource_id='SKU1'");
        exec(payment(), "UPDATE resource SET total=10000, frozen=0 WHERE resource_id='U1'");
        exec(order(), "DELETE FROM saga WHERE saga_id LIKE 'saga-%'");
        exec(order(), "DELETE FROM saga_step WHERE saga_id LIKE 'saga-%'");
    }
}
