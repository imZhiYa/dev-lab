package com.zhiya.mq.core;

/**
 * 订单状态机：CREATED -> PAID -> SHIPPED。
 * <p>验证点（mq-10 L3/L5）：顺序最后一道闸不是 MQ，是状态机——
 * PAID 先于 CREATED 到达时拒绝 + 返回缺口，供补偿层延迟重试；
 * 已终态重复到达返回 DUPLICATE，供幂等层拦截。
 */
public class OrderStateMachine {

    public enum State { NONE, CREATED, PAID, SHIPPED }

    public enum Event { CREATED, PAID, SHIPPED }

    public enum Result {
        /** 合法转移，已推进 */
        APPLIED,
        /** 乱序/缺口：事件先到，前置状态缺失（等前置事件） */
        REJECTED_WAITING,
        /** 重复事件：该状态已发生过（幂等层应拦截） */
        DUPLICATE,
        /** 非法转移：状态机定义之外的边 */
        ILLEGAL
    }

    public record Outcome(Result result, State state) {}

    public static Outcome apply(State current, Event event) {
        return switch (event) {
            case CREATED -> switch (current) {
                case NONE -> new Outcome(Result.APPLIED, State.CREATED);
                case CREATED, PAID, SHIPPED -> new Outcome(Result.DUPLICATE, current);
            };
            case PAID -> switch (current) {
                case CREATED -> new Outcome(Result.APPLIED, State.PAID);
                case NONE -> new Outcome(Result.REJECTED_WAITING, current);
                case PAID, SHIPPED -> new Outcome(Result.DUPLICATE, current);
            };
            case SHIPPED -> switch (current) {
                case PAID -> new Outcome(Result.APPLIED, State.SHIPPED);
                case NONE, CREATED -> new Outcome(Result.REJECTED_WAITING, current);
                case SHIPPED -> new Outcome(Result.DUPLICATE, current);
            };
        };
    }
}
