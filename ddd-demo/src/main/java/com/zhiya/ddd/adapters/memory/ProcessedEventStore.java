package com.zhiya.ddd.adapters.memory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消费侧"已处理事件"存储：按 eventId 去重（EX-05 场景 3：重复投递只生效一次）。
 * 真实实现是消费偏移 + 幂等键，这里用内存集合模拟，机制相同。
 */
public final class ProcessedEventStore {

    private final Set<UUID> processed = ConcurrentHashMap.newKeySet();

    public boolean isProcessed(UUID eventId) {
        return processed.contains(eventId);
    }

    public boolean markProcessed(UUID eventId) {
        return processed.add(eventId);
    }

    public int size() {
        return processed.size();
    }
}