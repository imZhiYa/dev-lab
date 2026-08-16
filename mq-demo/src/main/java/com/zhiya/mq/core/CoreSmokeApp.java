package com.zhiya.mq.core;

import java.util.List;

/**
 * 纯逻辑冒烟入口（CI 专用，无任何中间件依赖）：
 * 状态机乱序拒绝 / 幂等键确定性 / 重试退避序列，输出断言供 verify-mq-demos.sh 检查。
 */
public class CoreSmokeApp {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        check("状态机：PAID 先于 CREATED 到达 → 拒绝并等待",
                OrderStateMachine.apply(OrderStateMachine.State.NONE, OrderStateMachine.Event.PAID)
                        .result() == OrderStateMachine.Result.REJECTED_WAITING);

        check("状态机：CREATED 后 PAID → 合法推进",
                OrderStateMachine.apply(OrderStateMachine.State.CREATED, OrderStateMachine.Event.PAID)
                        .result() == OrderStateMachine.Result.APPLIED);

        check("状态机：SHIPPED 终态后重复 PAID → DUPLICATE（幂等层拦截信号）",
                OrderStateMachine.apply(OrderStateMachine.State.SHIPPED, OrderStateMachine.Event.PAID)
                        .result() == OrderStateMachine.Result.DUPLICATE);

        check("幂等键：同一业务事件生成键确定且唯一",
                IdempotencyKey.of("O-1001", "PAID", 1).equals("O-1001:PAID:1")
                        && !IdempotencyKey.of("O-1001", "PAID", 1).equals(IdempotencyKey.of("O-1001", "PAID", 2)));

        RetryPolicy policy = new RetryPolicy(3, 100);
        List<Long> delays = java.util.stream.IntStream.rangeClosed(1, 4)
                .mapToObj(policy::delayFor).toList();
        check("重试策略：指数退避 100/200/400，第 4 次超上限返回 -1（进 DLQ）",
                delays.equals(List.of(100L, 200L, 400L, -1L)));

        System.out.println("========================================");
        System.out.println("CoreSmokeApp 结果: 通过 " + passed + " / 失败 " + failed);
        System.out.println("========================================");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }
}
