package com.zhiya.ddd.domain.strategy;

/** 策略生命周期：状态迁移只能由聚合行为驱动。 */
public enum LifecycleStatus {
    DRAFT,
    PUBLISHED,
    RETIRED
}
