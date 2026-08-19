package com.zhiya.dtx.saga;

import com.zhiya.dtx.lab.DtxLabBase;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Saga 编排器：持久化状态机 + 补偿对偶表 + sagaId 幂等（distributed-tx-02 L4）
 *
 * 状态机：
 *   PENDING -> 逐步 DONE -> 全部完成 -> DONE（终态）
 *   任一步 FAILED -> COMPENSATING（倒序补偿）-> COMPENSATED（终态）
 *   步骤超时且无法确认 -> UNKNOWN（禁止自动补偿，交人工/对账）
 *
 * 补偿对偶表（本文聚焦扣库存 + 扣款）：
 *   T1 扣库存 <-> C1 加回库存
 *   T2 扣款   <-> C2 退款
 *   对偶性：T 与 C 的净效果 = 零
 *
 * 关键纪律（文章 L4「超时怎么办」）：
 *   步骤超时 = 结果未知，不能直接补偿——直接退款可能造成「扣款成功 + 退款」双花。
 *   正确顺序：先查询确认（幂等键 (sagaId, step)）→ 成功继续推进 / 失败才补偿 /
 *   一直无法确认 → 置 UNKNOWN，禁止自动补偿。
 */
public class SagaOrchestrator extends DtxLabBase {

    // saga 状态
    public static final String PENDING = "PENDING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";
    public static final String COMPENSATING = "COMPENSATING";
    public static final String COMPENSATED = "COMPENSATED";
    public static final String UNKNOWN = "UNKNOWN";

    // 步骤状态
    public static final String STEP_PENDING = "PENDING";
    public static final String STEP_DONE = "DONE";
    public static final String STEP_FAILED = "FAILED";
    public static final String STEP_COMPENSATED = "COMPENSATED";
    public static final String STEP_UNKNOWN = "UNKNOWN";

    // 扣款步骤模式
    public static final int CHARGE_NORMAL = 0;            // 正常扣款（失败抛 StepFailure）
    public static final int CHARGE_TIMEOUT_CHARGED = 1;   // 超时且实际已扣（结果未知）
    public static final int CHARGE_TIMEOUT_NOT_CHARGED = 2; // 超时且实际未扣（结果未知）

    /** 明确失败（可安全补偿） */
    public static class StepFailureException extends RuntimeException {
        public StepFailureException(String msg) { super(msg); }
    }

    /** 结果未知（超时，必须先查询确认，禁止直接补偿） */
    public static class UnknownOutcomeException extends RuntimeException {
        public UnknownOutcomeException(String msg) { super(msg); }
    }

    /** 查询确认器：SUCCESS / FAILURE / UNKNOWN（幂等键 sagaId，模拟支付侧对账查询） */
    @FunctionalInterface
    public interface Verifier {
        String verify(String sagaId);
    }

    private final DataSource orderDs;
    private final DataSource inventoryDs;
    private final DataSource paymentDs;
    private final String sku;
    private final String accountId;
    private final long stockAmount;
    private final long moneyAmount;

    /** 记录每笔 saga 扣款的真实结果（模拟支付侧账本），供 verifier 与断言使用 */
    private final Map<String, Boolean> charges = new HashMap<>();

    public SagaOrchestrator(DataSource orderDs, DataSource inventoryDs, DataSource paymentDs) {
        this(orderDs, inventoryDs, paymentDs, "SKU1", "U1", 5L, 1000L);
    }

    public SagaOrchestrator(DataSource orderDs, DataSource inventoryDs, DataSource paymentDs,
                            String sku, String accountId, long stockAmount, long moneyAmount) {
        this.orderDs = orderDs;
        this.inventoryDs = inventoryDs;
        this.paymentDs = paymentDs;
        this.sku = sku;
        this.accountId = accountId;
        this.stockAmount = stockAmount;
        this.moneyAmount = moneyAmount;
    }

    // ------------------------------------------------------------------
    // 主流程：持久化推进 + 失败倒序补偿 + 超时查询确认
    // ------------------------------------------------------------------

    public void run(String sagaId, int chargeMode, Verifier verifier) {
        exec(orderDs, "INSERT INTO saga (saga_id, state) VALUES (?, ?)", sagaId, PENDING);

        // T1 扣库存
        exec(orderDs, "INSERT INTO saga_step (saga_id, step_id, step_name, status) VALUES (?, 1, 'T1-扣库存', ?)",
                sagaId, STEP_PENDING);
        try {
            deductInventory();
        } catch (StepFailureException e) {
            markStep(sagaId, 1, STEP_FAILED);
            // T1 失败，无已提交步骤需要补偿，saga 直接收尾
            setState(sagaId, COMPENSATED);
            System.out.println("    [Saga] T1 扣库存失败（" + e.getMessage() + "），无前置步骤可补偿，saga 收尾");
            return;
        }
        markStep(sagaId, 1, STEP_DONE);
        System.out.println("    [Saga] T1 扣库存 DONE（-5）");

        // T2 扣款
        exec(orderDs, "INSERT INTO saga_step (saga_id, step_id, step_name, status) VALUES (?, 2, 'T2-扣款', ?)",
                sagaId, STEP_PENDING);
        try {
            chargePayment(sagaId, chargeMode);
        } catch (StepFailureException e) {
            markStep(sagaId, 2, STEP_FAILED);
            compensate(sagaId, 2);
            System.out.println("    [Saga] T2 扣款明确失败（" + e.getMessage() + "）→ 倒序补偿");
            return;
        } catch (UnknownOutcomeException e) {
            // 超时结果未知：先查询确认，禁止直接补偿
            String verdict = verifier.verify(sagaId);
            System.out.println("    [Saga] T2 扣款超时，查询确认结果 = " + verdict);
            if ("SUCCESS".equals(verdict)) {
                markStep(sagaId, 2, STEP_DONE);
                setState(sagaId, DONE);
                System.out.println("    [Saga] 确认成功 → 标记 DONE，saga 完成");
                return;
            }
            if ("FAILURE".equals(verdict)) {
                markStep(sagaId, 2, STEP_FAILED);
                compensate(sagaId, 2);
                System.out.println("    [Saga] 确认失败 → 才进入补偿");
                return;
            }
            // UNKNOWN：无法确认，禁止自动补偿
            markStep(sagaId, 2, STEP_UNKNOWN);
            setState(sagaId, UNKNOWN);
            System.out.println("    [Saga] 无法确认 → 置 UNKNOWN，交人工/对账（禁止自动补偿，防双花）");
            return;
        }
        markStep(sagaId, 2, STEP_DONE);
        setState(sagaId, DONE);
        System.out.println("    [Saga] T2 扣款 DONE，saga 完成（DONE）");
    }

    /** 倒序补偿已完成的步骤（幂等：只补偿状态仍为 DONE 的步骤，补偿后置 COMPENSATED） */
    private void compensate(String sagaId, int failedIndex) {
        setState(sagaId, COMPENSATING);
        for (int i = failedIndex - 1; i >= 1; i--) {
            String st = stepStatus(sagaId, i);
            if (STEP_DONE.equals(st)) {
                compensateStep(i);
                markStep(sagaId, i, STEP_COMPENSATED);
            }
        }
        setState(sagaId, COMPENSATED);
    }

    // ------------------------------------------------------------------
    // 正向步骤 / 补偿步骤（每步独立提交，Saga 无隔离性的物化）
    // ------------------------------------------------------------------

    private void deductInventory() {
        int n = exec(inventoryDs,
                "UPDATE resource SET total = total - ? WHERE resource_id=? AND (total - frozen) >= ?",
                stockAmount, sku, stockAmount);
        if (n == 0) {
            throw new StepFailureException("库存不足");
        }
    }

    private void chargePayment(String sagaId, int mode) {
        switch (mode) {
            case CHARGE_NORMAL -> {
                int n = exec(paymentDs,
                        "UPDATE resource SET total = total - ? WHERE resource_id=? AND (total - frozen) >= ?",
                        moneyAmount, accountId, moneyAmount);
                if (n == 0) {
                    throw new StepFailureException("余额不足");
                }
                charges.put(sagaId, true);
            }
            case CHARGE_TIMEOUT_CHARGED -> {
                exec(paymentDs, "UPDATE resource SET total = total - ? WHERE resource_id=?", moneyAmount, accountId);
                charges.put(sagaId, true);
                throw new UnknownOutcomeException("扣款响应丢失（实际已扣，结果未知）");
            }
            case CHARGE_TIMEOUT_NOT_CHARGED -> {
                charges.put(sagaId, false);
                throw new UnknownOutcomeException("扣款响应丢失（实际未扣，结果未知）");
            }
            default -> throw new IllegalArgumentException("未知扣款模式 " + mode);
        }
    }

    private void compensateStep(int stepId) {
        if (stepId == 1) {
            // C1 加回库存
            exec(inventoryDs, "UPDATE resource SET total = total + ? WHERE resource_id=?", stockAmount, sku);
            System.out.println("    [Saga-C1] 加回库存（+" + stockAmount + "）");
        } else if (stepId == 2) {
            // C2 退款
            exec(paymentDs, "UPDATE resource SET total = total + ? WHERE resource_id=?", moneyAmount, accountId);
            System.out.println("    [Saga-C2] 退款（+" + moneyAmount + "）");
        }
    }

    // ------------------------------------------------------------------
    // 状态读写 / 观测
    // ------------------------------------------------------------------

    private void setState(String sagaId, String state) {
        exec(orderDs, "UPDATE saga SET state=? WHERE saga_id=?", state, sagaId);
    }

    private void markStep(String sagaId, int stepId, String status) {
        exec(orderDs, "UPDATE saga_step SET status=? WHERE saga_id=? AND step_id=?", status, sagaId, stepId);
    }

    public String sagaState(String sagaId) {
        return queryStr(orderDs, "SELECT state FROM saga WHERE saga_id=?", sagaId);
    }

    public String stepStatus(String sagaId, int stepId) {
        return queryStr(orderDs, "SELECT status FROM saga_step WHERE saga_id=? AND step_id=?", sagaId, stepId);
    }

    /** 真实扣款结果（模拟支付侧账本） */
    public Boolean chargeResult(String sagaId) {
        return charges.get(sagaId);
    }
}
