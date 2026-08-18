package com.zhiya.ddd.adapters.memory;

import com.zhiya.ddd.ports.outbox.OutboxEntry;
import com.zhiya.ddd.ports.outbox.IntegrationEventSender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 事件发送适配器：记录已发事件，可注入"投递失败"来演示重试（EX-05）。
 */
public final class RecordingSender implements IntegrationEventSender {

    private final List<OutboxEntry> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failNext;

    /** 让下一次 send 失败，模拟 Broker 不可用。 */
    public void failNextOnce() {
        this.failNext = true;
    }

    @Override
    public void send(OutboxEntry entry) throws Exception {
        if (failNext) {
            failNext = false;
            throw new RuntimeException("simulated broker outage");
        }
        sent.add(entry);
    }

    public List<OutboxEntry> sent() {
        return new ArrayList<>(sent);
    }
}
