package com.zhiya.ddd.domain.strategy;

/** 业务标识：运营和外部系统怎么称呼这份策略（可能改名，不能当永久身份）。 */
public record StrategyCode(String value) {
    public StrategyCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("strategy code is blank");
        }
    }
}
