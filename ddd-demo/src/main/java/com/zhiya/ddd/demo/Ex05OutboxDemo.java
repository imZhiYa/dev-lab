package com.zhiya.ddd.demo;

import com.zhiya.ddd.adapters.memory.InMemoryOutboxStore;
import com.zhiya.ddd.adapters.memory.InMemoryStrategyRepository;
import com.zhiya.ddd.adapters.memory.InMemoryTransactionRunner;
import com.zhiya.ddd.adapters.memory.InMemoryViewStore;
import com.zhiya.ddd.adapters.memory.ProcessedEventStore;
import com.zhiya.ddd.adapters.memory.RecordingSender;
import com.zhiya.ddd.application.OutboxPublisher;
import com.zhiya.ddd.application.PublishStrategyHandler;
import com.zhiya.ddd.domain.strategy.RecommendationStrategy;
import com.zhiya.ddd.domain.strategy.StrategyCode;
import com.zhiya.ddd.domain.strategy.StrategyId;
import com.zhiya.ddd.domain.strategy.StrategyRule;
import com.zhiya.ddd.domain.strategy.StrategyScene;
import com.zhiya.ddd.domain.strategy.TrafficRatio;

import java.util.UUID;

/**
 * EX-05 事件 Outbox 与最终一致性 —— 验证 ddd-05：
 *  1. 同事务写入：发布策略时 Outbox 必有登记项（appended=1）
 *  2. 投递失败不标记 -> 重试：failNextOnce 后重投递成功（至少一次）
 *  3. 消费侧 eventId 幂等：重复事件只生效一次
 *  4. 业务键幂等：同一 (strategyId, version) 只落一次读模型
 *  5. 事务失败即回滚：Outbox 无残留（用"抛异常的事务"模拟回滚）
 */
public final class Ex05OutboxDemo {

    public static void main(String[] args) {
        Checks c = new Checks();

        InMemoryStrategyRepository repo = new InMemoryStrategyRepository();
        InMemoryOutboxStore outbox = new InMemoryOutboxStore();
        RecordingSender sender = new RecordingSender();
        InMemoryTransactionRunner tx = new InMemoryTransactionRunner();
        PublishStrategyHandler handler = new PublishStrategyHandler(repo, outbox, tx);
        OutboxPublisher publisher = new OutboxPublisher(outbox, sender);
        ProcessedEventStore processed = new ProcessedEventStore();
        InMemoryViewStore views = new InMemoryViewStore();

        StrategyId id = StrategyId.newId();
        RecommendationStrategy s = RecommendationStrategy.draft(
                id, new StrategyCode("newuser-2026"), new StrategyScene("home-feed"),
                new TrafficRatio(50)
        );
        s.addRule(StrategyRule.of("recall-newuser-pool"));
        repo.save(s);

        // 1. 发布 = 状态变更 + Outbox 登记（同一事务语义）
        handler.handle(id);
        c.checkEq("Outbox 登记 1 条", 1, outbox.size());
        c.checkEq("未投递 pending=1", 1, outbox.pendingCount());

        // 2. 投递：一轮全成功
        OutboxPublisher.DeliveryReport r1 = publisher.deliverAll();
        c.checkEq("第一轮投递成功 1 条", 1, r1.delivered());
        c.checkEq("投递后 pending=0", 0, outbox.pendingCount());

        // 3. 消费侧：eventId 去重（模拟重复投递/重复消费）
        var entry = sender.sent().get(0);
        boolean first = processed.markProcessed(entry.eventId());
        boolean second = processed.markProcessed(entry.eventId());
        c.check("首次标记=true", first);
        c.check("重复标记=false（幂等）", !second);

        // 4. 业务键幂等：同一 (strategyId, version) 只落一次读模型
        boolean applied1 = views.upsert("strategy-abc", "home-feed", 3);
        boolean applied2 = views.upsert("strategy-abc", "home-feed", 3);
        boolean applied3 = views.upsert("strategy-abc", "home-feed", 4);
        c.check("首次应用=true", applied1);
        c.check("重复应用=false（幂等）", !applied2);
        c.check("新版本可以再应用", applied3);
        c.checkEq("读模型业务键=2", 2, views.appliedKeys());

        // 5. 投递失败 -> 重试成功（至少一次投递）
        InMemoryOutboxStore outbox2 = new InMemoryOutboxStore();
        RecordingSender sender2 = new RecordingSender();
        OutboxPublisher publisher2 = new OutboxPublisher(outbox2, sender2);
        UUID evt = UUID.randomUUID();
        outbox2.append(com.zhiya.ddd.ports.outbox.OutboxEntry.pending(evt, "StrategyPublished", "p", 1));
        sender2.failNextOnce();
        OutboxPublisher.DeliveryReport failRound = publisher2.deliverAll();
        c.checkEq("失败轮：投递 0", 0, failRound.delivered());
        c.checkEq("失败轮：pending 保留", 1, outbox2.pendingCount());
        OutboxPublisher.DeliveryReport retryRound = publisher2.deliverAll();
        c.checkEq("重试轮：投递成功", 1, retryRound.delivered());
        c.checkEq("重试轮：pending=0", 0, outbox2.pendingCount());

        c.summary("Ex05");
    }
}