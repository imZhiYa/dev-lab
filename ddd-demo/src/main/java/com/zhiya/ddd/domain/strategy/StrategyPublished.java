package com.zhiya.ddd.domain.strategy;

import java.time.Instant;

/** 领域事件：策略聚合内部发生的业务事实（跨上下文契约是 contracts 里的集成事件，见 ddd-05 Level 1）。 */
public record StrategyPublished(
        StrategyId strategyId,
        StrategyScene scene,
        StrategyVersion version,
        Instant occurredAt
) {
    public static StrategyPublished of(StrategyId id, StrategyScene scene, StrategyVersion version) {
        return new StrategyPublished(id, scene, version, Instant.now());
    }
}
