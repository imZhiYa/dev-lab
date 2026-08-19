package com.zhiya.dtx.experiment;

import com.zhiya.dtx.lab.DtxLabBase;
import com.zhiya.dtx.saga.SagaOrchestrator;

/**
 * EX-05：Saga 步骤超时——结果未知不能直接补偿（distributed-tx-02 L4，对应知识库 EX-08）
 *
 * 验证「超时怎么办」的三条正确路径（先查询确认，禁止直接补偿）：
 *  (a) 查询确认成功 → 标记 DONE，saga 完成（不补偿）
 *  (b) 查询确认失败 → 才进入倒序补偿
 *  (c) 一直无法确认 → 置 UNKNOWN，交人工/对账，禁止自动补偿
 *       （若此时自动补偿退款，会造成「扣款成功 + 退款」双花）
 */
public class Ex05SagaTimeoutUnknown extends DtxLabBase {

    public static void main(String[] args) {
        System.out.println("========== EX-05：Saga 步骤超时——结果未知不能直接补偿 ==========");

        SagaOrchestrator saga = new SagaOrchestrator(order(), inventory(), payment());

        // ---------------- (a) 查询确认成功 ----------------
        System.out.println("--- [a] 超时 + 查询确认成功 → DONE（不补偿）---");
        reset();
        saga.run("saga-ex05a", SagaOrchestrator.CHARGE_TIMEOUT_CHARGED, s -> "SUCCESS");
        checkStr(SagaOrchestrator.DONE, saga.sagaState("saga-ex05a"), "a.saga 状态 = DONE");
        checkEq(95L, inventoryTotal(), "a.库存已扣（95，未补偿）");
        checkEq(9000L, paymentTotal(), "a.余额已扣（9000，确认成功无需退款）");

        // ---------------- (b) 查询确认失败 ----------------
        System.out.println("--- [b] 超时 + 查询确认失败 → 才进入补偿 ---");
        reset();
        saga.run("saga-ex05b", SagaOrchestrator.CHARGE_TIMEOUT_NOT_CHARGED, s -> "FAILURE");
        checkStr(SagaOrchestrator.COMPENSATED, saga.sagaState("saga-ex05b"), "b.saga 状态 = COMPENSATED");
        checkEq(100L, inventoryTotal(), "b.库存补偿回 100（实际未扣款，补偿 T1）");
        checkEq(10000L, paymentTotal(), "b.余额未扣（10000，实际未扣款）");

        // ---------------- (c) 一直无法确认 ----------------
        System.out.println("--- [c] 超时 + 无法确认 → UNKNOWN，禁止自动补偿 ---");
        reset();
        saga.run("saga-ex05c", SagaOrchestrator.CHARGE_TIMEOUT_CHARGED, s -> "UNKNOWN");
        checkStr(SagaOrchestrator.UNKNOWN, saga.sagaState("saga-ex05c"), "c.saga 状态 = UNKNOWN（交人工/对账）");
        checkStr(SagaOrchestrator.STEP_UNKNOWN, saga.stepStatus("saga-ex05c", 2), "c.step2 扣款 = UNKNOWN");
        checkEq(95L, inventoryTotal(), "c.库存未补偿（95，禁止自动补偿 T1）");
        checkEq(9000L, paymentTotal(), "c.余额已扣（9000），未退款（防「扣款成功+退款」双花）");

        System.out.println("✅ EX-05 通过：超时结果未知 → 先查询确认 → 成功推进 / 失败补偿 / 未知冻结");
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
