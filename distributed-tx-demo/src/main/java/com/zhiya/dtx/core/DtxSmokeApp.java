package com.zhiya.dtx.core;

/**
 * 纯逻辑冒烟（CI 公审用，零中间件依赖，几秒完成）
 *
 * 验证与知识库文章对应的可判定断言（不碰 DB，只验证「状态机判定」本身）：
 *  - TCC 控制表状态机（distributed-tx-02 L2）：空回滚 / 悬挂 / 幂等的「写」判定
 *  - Saga 编排器状态机（distributed-tx-02 L4）：合法迁移 + 超时 UNKNOWN 不自动补偿
 *  - 谱系选型（distributed-tx-00 L5 / topic §9）：一致性契约 → 成本最低格子
 *
 * 输出约定：末尾一行 "通过 N / 失败 0"，CI 脚本按此断言
 */
public final class DtxSmokeApp {

    private static int pass = 0;
    private static int fail = 0;

    private static void expect(boolean cond, String name) {
        if (cond) {
            pass++;
        } else {
            fail++;
            System.out.println("  ❌ " + name);
        }
    }

    // ==================================================================
    // 1) TCC 控制表状态机：幂等 = 唯一约束 + 状态机终态判定（distributed-tx-02 L2）
    //    动作只有两种：Try 到达 / Cancel 到达；fence 状态：NONE/TRIED/COMMITTED/ROLLBACKED/SUSPENDED
    // ==================================================================

    private static String fenceOnTry(String existing) {
        return switch (existing) {
            case "NONE"          -> "PROCEED";    // 首次 Try，继续冻结
            case "TRIED", "COMMITTED" -> "IDEMPOTENT"; // Try 已执行过，幂等成功
            case "ROLLBACKED", "SUSPENDED" -> "REJECT"; // 已被 Cancel 处理过，拦截迟到 Try（防悬挂）
            default -> throw new IllegalStateException("未知状态 " + existing);
        };
    }

    private static String fenceOnCancel(String existing) {
        return switch (existing) {
            case "NONE"                    -> "SUSPEND";   // 空回滚：Try 未执行，写 SUSPENDED 标记
            case "TRIED"                   -> "ROLLBACK";  // 正常取消
            case "COMMITTED", "ROLLBACKED", "SUSPENDED" -> "IDEMPOTENT"; // 已终态，幂等成功
            default -> throw new IllegalStateException("未知状态 " + existing);
        };
    }

    private static void smokeTccFence() {
        System.out.println("--- [1] TCC 控制表状态机（空回滚 / 悬挂 / 幂等）---");
        // 悬挂：Cancel 先到写 SUSPENDED，迟到 Try 被拦截
        expect(fenceOnCancel("NONE").equals("SUSPEND"), "Cancel 先到且 Try 未执行 → 写 SUSPENDED（空回滚）");
        expect(fenceOnTry("SUSPENDED").equals("REJECT"), "迟到 Try 撞 SUSPENDED → 拒绝（防悬挂）");
        // 幂等：Try 已执行 / 已提交 → 幂等成功；Cancel 已处理 → 幂等成功
        expect(fenceOnTry("TRIED").equals("IDEMPOTENT"), "Try 撞 TRIED → 幂等成功");
        expect(fenceOnTry("COMMITTED").equals("IDEMPOTENT"), "Try 撞 COMMITTED → 幂等成功");
        expect(fenceOnCancel("ROLLBACKED").equals("IDEMPOTENT"), "Cancel 撞 ROLLBACKED → 幂等成功（重复空回滚）");
        // 正常路径
        expect(fenceOnTry("NONE").equals("PROCEED"), "首次 Try → 继续冻结");
        expect(fenceOnCancel("TRIED").equals("ROLLBACK"), "Cancel 撞 TRIED → 正常回滚");
        // 终态拦截（Confirm 后的 Cancel 不该撤销）
        expect(fenceOnCancel("COMMITTED").equals("IDEMPOTENT"), "Cancel 撞 COMMITTED → 幂等（不撤销已确认）");
    }

    // ==================================================================
    // 2) Saga 状态机：合法迁移 + 超时 UNKNOWN 不自动补偿（distributed-tx-02 L4）
    // ==================================================================

    private static boolean sagaCan(String from, String to) {
        return switch (from) {
            case "PENDING"      -> to.equals("DONE") || to.equals("COMPENSATING") || to.equals("UNKNOWN");
            case "DONE"         -> false;   // 终态（单步 DONE 只代表一步，整单由编排器推进）
            case "COMPENSATING" -> to.equals("COMPENSATED");
            case "UNKNOWN"      -> false;   // 超时结果未知：禁止自动推进，交人工/对账
            case "COMPENSATED"  -> false;
            case "FAILED"       -> to.equals("COMPENSATING");
            default -> false;
        };
    }

    private static void smokeSagaMachine() {
        System.out.println("--- [2] Saga 编排器状态机（持久化 + 补偿 + 超时 UNKNOWN）---");
        expect(sagaCan("PENDING", "DONE"), "PENDING → DONE 合法（逐步推进）");
        expect(sagaCan("FAILED", "COMPENSATING"), "任一步 FAILED → COMPENSATING（倒序补偿）");
        expect(sagaCan("COMPENSATING", "COMPENSATED"), "COMPENSATING → COMPENSATED 终态");
        expect(sagaCan("PENDING", "UNKNOWN"), "步骤超时且无法确认 → UNKNOWN");
        expect(!sagaCan("UNKNOWN", "COMPENSATING"), "UNKNOWN 禁止直接补偿（防扣款成功+退款双花）");
        expect(!sagaCan("COMPENSATED", "DONE"), "COMPENSATED 是终态，不可回退");
    }

    // ==================================================================
    // 3) 谱系选型：一致性契约 → 成本最低格子（distributed-tx-00 L5 / topic §9）
    // ==================================================================

    private static String selectScheme(String contract) {
        return switch (contract) {
            case "跨库强一致+量小"            -> "2PC/XA";
            case "侵入低+一致性中等"          -> "Seata AT";
            case "资源天然可预留"             -> "TCC";
            case "长流程+补偿可逆"            -> "Saga";
            case "状态变更+发消息"            -> "Outbox/半消息";
            case "能合并边界或异步化"          -> "边界重构";
            default -> throw new IllegalStateException("未知契约 " + contract);
        };
    }

    private static void smokeSpectrum() {
        System.out.println("--- [3] 谱系选型矩阵（先定一致性契约，再选最便宜格子）---");
        expect(selectScheme("跨库强一致+量小").equals("2PC/XA"), "跨库强一致+量小 → 2PC/XA");
        expect(selectScheme("资源天然可预留").equals("TCC"), "资源天然可预留（余额/库存/座位）→ TCC");
        expect(selectScheme("长流程+补偿可逆").equals("Saga"), "长流程+补偿可逆 → Saga");
        expect(selectScheme("状态变更+发消息").equals("Outbox/半消息"), "状态变更+发消息 → Outbox/半消息");
        expect(selectScheme("能合并边界或异步化").equals("边界重构"), "能合并边界/异步化 → 边界重构（首选，让分布式事务不出现）");
        expect(selectScheme("侵入低+一致性中等").equals("Seata AT"), "侵入低+一致性中等 → Seata AT（自动 undo_log 回放）");
    }

    public static void main(String[] args) {
        System.out.println("========== DtxSmokeApp：纯逻辑冒烟 ==========");
        smokeTccFence();
        smokeSagaMachine();
        smokeSpectrum();
        System.out.println();
        System.out.printf("通过 %d / 失败 %d%n", pass, fail);
        if (fail > 0) {
            System.exit(1);
        }
    }
}
