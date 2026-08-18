package com.zhiya.ddd.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * 发布语言：StrategyPublished 的跨上下文集成事件契约。
 * 领域事件（domain.strategy.StrategyPublished）在应用层边界翻译成本契约 —— 不是同一个对象（ddd-05 Level 1）。
 */
public record StrategyPublishedIntegrationEvent(
        UUID eventId,
        String strategyId,
        String scene,
        int strategyVersion,
        int schemaVersion,
        Instant publishedAt
) {
}
