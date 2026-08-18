package com.zhiya.ddd.ports.outbox;

import java.util.List;
import java.util.UUID;

/** Outbox 存储 + 读取端口（内存/JDBC/Kafka 发布器都只是适配器）。 */
public interface OutboxStore {

    void append(OutboxEntry entry);

    List<OutboxEntry> findPending();

    void markPublished(UUID eventId);
}
