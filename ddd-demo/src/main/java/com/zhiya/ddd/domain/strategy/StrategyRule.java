package com.zhiya.ddd.domain.strategy;

import java.util.Objects;

/** 聚合内部实体：一条推荐规则（保持最小实现，重点是它只能经聚合根加入）。 */
public record StrategyRule(String name) {
    public StrategyRule {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("rule name is blank");
        }
    }

    public static StrategyRule of(String name) {
        return new StrategyRule(name);
    }

    public static StrategyRule require(StrategyRule rule) {
        return Objects.requireNonNull(rule, "rule");
    }
}
