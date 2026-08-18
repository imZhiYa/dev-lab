package com.zhiya.ddd.ports.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox 登记项：与业务状态同事务写入的"待发事件"（ddd-05 Level 2）。
 * status 只表达"是否已交给发送通道"，不表达消费方已处理。
 */
public record OutboxEntry(
        UUID eventId,
        String eventType,
        String payload,
        int schemaVersion,
        Instant createdAt,
        boolean published
) {
    public static OutboxEntry pending(UUID eventId, String eventType, String payload, int schemaVersion) {
        return new OutboxEntry(eventId, eventType, payload, schemaVersion, Instant.now(), false);
    }

    public OutboxEntry markPublished() {
        return new OutboxEntry(eventId, eventType, payload, schemaVersion, createdAt, true);
    }
}
