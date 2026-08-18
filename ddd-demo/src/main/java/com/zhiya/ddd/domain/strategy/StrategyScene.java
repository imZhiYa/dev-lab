package com.zhiya.ddd.domain.strategy;

/** 值对象：策略生效场景，携带合法范围（ddd-03 Level 3）。 */
public record StrategyScene(String value) {
    public StrategyScene {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("scene is blank");
        }
    }
}
