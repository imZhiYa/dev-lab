package com.zhiya.ddd.application;

import com.zhiya.ddd.contracts.StrategyPublishedIntegrationEvent;
import com.zhiya.ddd.domain.strategy.RecommendationStrategy;
import com.zhiya.ddd.domain.strategy.StrategyId;
import com.zhiya.ddd.domain.strategy.StrategyPublished;
import com.zhiya.ddd.domain.strategy.StrategyRepository;
import com.zhiya.ddd.ports.TransactionRunner;
import com.zhiya.ddd.ports.outbox.OutboxEntry;
import com.zhiya.ddd.ports.outbox.OutboxStore;

import java.util.UUID;

/**
 * 应用用例：发布策略 = 改状态 + 记 Outbox，同一事务（ddd-05 Level 3）。
 * 事务边界在应用层显式声明，而不是散落在 Service 方法的注解里。
 */
public final class PublishStrategyHandler {

    private final StrategyRepository repository;
    private final OutboxStore outbox;
    private final TransactionRunner tx;

    public PublishStrategyHandler(StrategyRepository repository, OutboxStore outbox, TransactionRunner tx) {
        this.repository = repository;
        this.outbox = outbox;
        this.tx = tx;
    }

    public StrategyPublished handle(StrategyId id) {
        // 事务边界：策略状态变更 + Outbox 登记 要么都成功要么都回滚
        return tx.inTx(() -> {
            RecommendationStrategy strategy = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("strategy not found: " + id));
            StrategyPublished domainEvent = strategy.publish();
            repository.save(strategy);

            StrategyPublishedIntegrationEvent integration =
                    new StrategyPublishedIntegrationEvent(
                            UUID.randomUUID(),
                            id.value().toString(),
                            domainEvent.scene().value(),
                            domainEvent.version().value(),
                            1,
                            domainEvent.occurredAt()
                    );
            String payload = "strategyId=" + integration.strategyId()
                    + "&scene=" + integration.scene()
                    + "&version=" + integration.strategyVersion()
                    + "&schemaVersion=" + integration.schemaVersion();
            outbox.append(OutboxEntry.pending(
                    integration.eventId(), "StrategyPublished", payload, integration.schemaVersion()
            ));
            return domainEvent;
        });
    }
}
