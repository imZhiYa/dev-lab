package com.zhiya.ddd.domain.strategy;

/**
 * 值对象：业务版本 —— 规则对外发布了第几版。
 * 与 persistenceRevision（并发修订号）是两件事，禁止共用（ddd-03 坑 5）。
 */
public record StrategyVersion(int value) {
    public StrategyVersion {
        if (value < 1) {
            throw new IllegalArgumentException("strategy version must be positive");
        }
    }
}
