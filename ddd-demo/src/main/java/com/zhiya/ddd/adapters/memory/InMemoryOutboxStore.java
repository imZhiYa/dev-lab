package com.zhiya.ddd.adapters.memory;

import com.zhiya.ddd.ports.outbox.OutboxEntry;
import com.zhiya.ddd.ports.outbox.OutboxStore;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存 Outbox 存储。注意与真实 DB 的差异：真实 Outbox 依赖"业务写入与登记同一事务"，
 * 这里用 InMemoryTransactionRunner 直接执行近似模拟 —— 结论只代表机制，不代表 SQL 事务（README 边界）。
 */
public final class InMemoryOutboxStore implements OutboxStore {

    private final Map<UUID, OutboxEntry> entries = new ConcurrentHashMap<>();

    @Override
    public void append(OutboxEntry entry) {
        entries.put(entry.eventId(), entry);
    }

    @Override
    public List<OutboxEntry> findPending() {
        return entries.values().stream()
                .filter(e -> !e.published())
                .collect(Collectors.toList());
    }

    @Override
    public void markPublished(UUID eventId) {
        entries.computeIfPresent(eventId, (k, e) -> e.markPublished());
    }

    public int size() {
        return entries.size();
    }

    public int pendingCount() {
        return findPending().size();
    }
}
