package com.zhiya.dtx.experiment;

import com.zhiya.dtx.lab.DtxLabBase;
import com.zhiya.dtx.outbox.OutboxPublisher;

/**
 * EX-06：Outbox——状态与待发事件同本地事务（distributed-tx-00 L4 / DC-05）
 *
 * 边界声明（文章 L4）：Outbox 只覆盖「一个本地事务 + 一次投递」的原子性，
 *   机制细节回链 knowledge/ddd-05 与 knowledge/mq-07；本主题只做选型定位。
 *
 * 验证断言：
 *  - 下单与登记待发事件在同一本地事务，不会出现「订单已提交、事件丢失」的双写之缝
 *  - 反例：若登记事件一步失败，下单也一并回滚（同一事务的物化）
 *  - 轮询发布幂等：已 PUBLISHED 的事件不会重复投递
 */
public class Ex06OutboxAtomicity extends DtxLabBase {

    public static void main(String[] args) {
        System.out.println("========== EX-06：Outbox——状态与待发事件同本地事务 ==========");

        reset();
        OutboxPublisher outbox = new OutboxPublisher(order());

        // ① 下单 + 登记事件，同一本地事务
        System.out.println("--- [1] 下单 + 登记事件（同一本地事务）---");
        outbox.createOrderWithEvent(7001, "OrderCreated", "{\"orderId\":7001}");
        checkStr("CREATED", outbox.orderStatus(7001), "订单 7001 已提交（CREATED）");
        checkEq(1L, outbox.pendingCount(), "outbox 登记 1 条 PENDING 事件");

        // ② 轮询发布，PENDING → PUBLISHED
        System.out.println("--- [2] 轮询发布：PENDING → PUBLISHED ---");
        int published = outbox.publishPending();
        checkEq(1L, published, "本轮发布 1 条事件");
        checkEq(0L, outbox.pendingCount(), "发布后 PENDING=0");
        checkEq(1L, outbox.publishedCount(), "发布后 PUBLISHED=1");

        // ③ 幂等：已 PUBLISHED 的事件不会被重复投递
        System.out.println("--- [3] 幂等：重复轮询不再投递已发布事件 ---");
        int again = outbox.publishPending();
        checkEq(0L, again, "重复轮询发布 0 条（PUBLISHED 不重复投递）");

        // ④ 反例：登记事件失败 → 下单也回滚（双写之缝被同一事务堵住）
        System.out.println("--- [4] 反例：登记事件失败 → 下单一并回滚 ---");
        boolean rolledBack = false;
        try {
            inTx(order(), c -> {
                exec(c, "INSERT INTO orders (order_id, status) VALUES (7002, 'CREATED')");
                throw new RuntimeException("模拟 outbox 登记失败（第二步失败）");
            });
        } catch (RuntimeException e) {
            rolledBack = true;
        }
        check(rolledBack, "事务抛出异常（模拟登记失败）");
        check(outbox.orderStatus(7002) == null, "订单 7002 不存在（下单随登记失败一起回滚）");

        System.out.println("✅ EX-06 通过：状态与事件同事务，双写之缝被消除，发布幂等");
    }

    private static void reset() {
        exec(order(), "DELETE FROM orders WHERE order_id IN (7001, 7002)");
        exec(order(), "DELETE FROM outbox WHERE aggregate_id IN ('7001', '7002')");
    }
}
