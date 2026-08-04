package com.zhiya.network.lifecycle;

import java.util.EnumMap;

/**
 * 高性能网络编程演示。
 * <p>
 * 对应层级：Level 5（生命周期）。
 * 演示主题：优雅停机 drain 的分类收口。
 * 验证目标：半包、业务中、待写与待 ACK 请求必须使用不同的关闭策略。
 */
public final class GracefulDrainDemo {
    private GracefulDrainDemo() {
    }

    public static void main(String[] args) {
        run();
    }

    public static void run() {
        System.out.println("\n===== Level 5 演示：drain 的分类收口 =====");
        EnumMap<State, String> actions = new EnumMap<>(State.class);
        actions.put(State.HALF_FRAME, "丢弃半包，不进入业务");
        actions.put(State.IN_BUSINESS, "等待 deadline 或取消");
        actions.put(State.OUTBOUND_PENDING, "尝试 flush 到 deadline");
        actions.put(State.WAITING_ACK, "保留结果查询或标记超时");
        System.out.println("进入 DRAINING：停止 accept 新连接。");
        actions.forEach((state, action) -> System.out.println(state + "：" + action));
        require(actions.size() == State.values().length, "drain 状态分类不完整");
        System.out.println("结论：close 不等于 drain；每种存活状态都要先定义死亡方式。");
    }

    private enum State {HALF_FRAME, IN_BUSINESS, OUTBOUND_PENDING, WAITING_ACK}

    private static void require(boolean c, String m) {
        if (!c) throw new AssertionError(m);
    }
}
