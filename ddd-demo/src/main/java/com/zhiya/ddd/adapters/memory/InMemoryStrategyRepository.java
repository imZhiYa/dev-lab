package com.zhiya.ddd.adapters.memory;

import com.zhiya.ddd.domain.strategy.RecommendationStrategy;
import com.zhiya.ddd.domain.strategy.StrategyId;
import com.zhiya.ddd.domain.strategy.StrategyRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 内存仓储适配器：验证聚合行为/应用用例用，不上生产。
 * 保存时回写并发修订号，模拟真实仓储的"乐观锁"写回（ddd-03 Level 5）。
 */
public final class InMemoryStrategyRepository implements StrategyRepository {

    private final Map<StrategyId, RecommendationStrategy> store = new HashMap<>();

    @Override
    public Optional<RecommendationStrategy> findById(StrategyId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void save(RecommendationStrategy strategy) {
        store.put(strategy.id(), strategy);
        // 模拟：持久化成功，下次乐观锁期望修订号 +1
        strategy.markPersisted(strategy.expectedPersistenceRevision() + 1);
    }

    public int size() {
        return store.size();
    }
}
