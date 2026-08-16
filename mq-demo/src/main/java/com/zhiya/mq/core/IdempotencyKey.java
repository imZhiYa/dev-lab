package com.zhiya.mq.core;

/**
 * 幂等键（mq-10 L3）：粒度 = 业务单元 = orderId + 事件类型 + 序号。
 * 确定性：同一业务事件永远生成同一个键，是"重复拦截"的前提。
 */
public final class IdempotencyKey {

    private IdempotencyKey() {
    }

    public static String of(String orderId, String eventType, long seq) {
        return orderId + ":" + eventType + ":" + seq;
    }
}
