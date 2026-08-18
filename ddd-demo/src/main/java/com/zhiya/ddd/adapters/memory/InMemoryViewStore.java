package com.zhiya.ddd.adapters.memory;

import com.zhiya.ddd.contracts.PublishedStrategyView;
import com.zhiya.ddd.ports.PublishedStrategyQuery;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 读模型：策略上下文发布事件后，由消费侧维护的快照视图。
 * 支持"业务键幂等"——同一 (strategyId, version) 只落一次（EX-05 场景 4）。
 */
public final class InMemoryViewStore {

    private final Map<String, PublishedStrategyView> byScene = new HashMap<>();
    private final java.util.Set<String> appliedBusinessKeys = new java.util.HashSet<>();

    /** 业务键幂等：返回是否真正落库（false = 重复事件被忽略）。 */
    public boolean upsert(String strategyId, String scene, int version) {
        String key = strategyId + "@" + version;
        if (appliedBusinessKeys.contains(key)) {
            return false;
        }
        appliedBusinessKeys.add(key);
        byScene.put(scene, new PublishedStrategyView(
                "s_" + strategyId.substring(0, Math.min(8, strategyId.length())),
                scene,
                version,
                50,
                java.time.Instant.now()
        ));
        return true;
    }

    public Optional<PublishedStrategyView> findFor(String scene) {
        return Optional.ofNullable(byScene.get(scene));
    }

    public int appliedKeys() {
        return appliedBusinessKeys.size();
    }

    public static PublishedStrategyQuery asQuery(InMemoryViewStore store) {
        return store::findFor;
    }
}