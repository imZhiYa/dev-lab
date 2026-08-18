package com.zhiya.ddd.domain.strategy;

import java.util.Objects;
import java.util.UUID;

/**
 * 领域身份：跨状态变化识别"同一份策略"。
 * 与数据库主键、业务编码（StrategyCode）分离 —— 见 ddd-03 Level 2。
 */
public record StrategyId(UUID value) {
    public StrategyId {
        Objects.requireNonNull(value, "value");
    }

    public static StrategyId newId() {
        return new StrategyId(UUID.randomUUID());
    }
}
