package com.zhiya.ddd.ports.outbox;

/** 集成事件发送通道（Kafka/RocketMQ/HTTP 的抽象）。 */
public interface IntegrationEventSender {

    void send(OutboxEntry entry) throws Exception;
}
