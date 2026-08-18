package com.zhiya.ddd.application;

import com.zhiya.ddd.ports.outbox.OutboxEntry;
import com.zhiya.ddd.ports.outbox.OutboxStore;
import com.zhiya.ddd.ports.outbox.IntegrationEventSender;

import java.util.List;

/**
 * 发件侧：轮询 Outbox -> 投递 -> 标记已发（ddd-05 Level 4）。
 * 投递失败不标记：下轮重试 —— 消费侧必须幂等，这是"至多一次/至少一次"的代价（ddd-05 Level 5）。
 */
public final class OutboxPublisher {

    private final OutboxStore store;
    private final IntegrationEventSender sender;

    public OutboxPublisher(OutboxStore store, IntegrationEventSender sender) {
        this.store = store;
        this.sender = sender;
    }

    /** 投递一轮，返回 {delivered, failed}。 */
    public DeliveryReport deliverAll() {
        List<OutboxEntry> pending = store.findPending();
        int delivered = 0;
        int failed = 0;
        for (OutboxEntry entry : pending) {
            try {
                sender.send(entry);
                store.markPublished(entry.eventId());
                delivered++;
            } catch (Exception e) {
                failed++;
            }
        }
        return new DeliveryReport(delivered, failed);
    }

    public record DeliveryReport(int delivered, int failed) {
    }
}
