package com.zhiya.dtx.outbox;

import com.zhiya.dtx.lab.DtxLabBase;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 事务性 Outbox（distributed-tx-00 L4 / DC-05）：状态与待发事件同本地事务
 *
 * 边界（文章 L4 与 topic 铁律）：Outbox 只覆盖「一个本地事务 + 一次投递」这一种原子性；
 *   机制细节回链 knowledge/ddd-05（Outbox）与 knowledge/mq-07（双写之缝）。
 *   本主题只做「分布式事务选型定位」——证明「状态变更 + 发消息」形态复用本地事务原子性即可。
 *
 * 关键不变量：INSERT orders 与 INSERT outbox 在同一本地事务，任一步失败一起回滚，
 *   不会出现「订单已提交、事件丢失」的双写之缝。
 */
public class OutboxPublisher extends DtxLabBase {

    private final DataSource orderDs;

    public OutboxPublisher(DataSource orderDs) {
        this.orderDs = orderDs;
    }

    /** 下单 + 登记待发事件，同一本地事务（@Transactional 语义的物化） */
    public void createOrderWithEvent(long orderId, String eventType, String payload) {
        inTx(orderDs, c -> {
            exec(c, "INSERT INTO orders (order_id, status) VALUES (?, 'CREATED')", orderId);
            exec(c, "INSERT INTO outbox (aggregate_id, event_type, payload, status) VALUES (?, ?, ?, 'PENDING')",
                    String.valueOf(orderId), eventType, payload);
            return null;
        });
        System.out.println("    [Outbox] 下单 " + orderId + " + 登记事件 " + eventType + "（同一本地事务）");
    }

    /** 轮询发布：把 PENDING 事件逐个标记 PUBLISHED（模拟投递），返回本轮发布数 */
    public int publishPending() {
        List<Long> ids = new ArrayList<>();
        try (Connection c = orderDs.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT event_id FROM outbox WHERE status='PENDING' ORDER BY event_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("扫描 outbox 失败", e);
        }
        for (Long id : ids) {
            exec(orderDs, "UPDATE outbox SET status='PUBLISHED' WHERE event_id=?", id);
            String payload = queryStr(orderDs, "SELECT payload FROM outbox WHERE event_id=?", id);
            System.out.println("    [Outbox] 投递事件 " + id + "：payload=" + payload);
        }
        return ids.size();
    }

    public long pendingCount() {
        Long v = queryLong(orderDs, "SELECT COUNT(*) FROM outbox WHERE status='PENDING'");
        return v == null ? 0 : v;
    }

    public long publishedCount() {
        Long v = queryLong(orderDs, "SELECT COUNT(*) FROM outbox WHERE status='PUBLISHED'");
        return v == null ? 0 : v;
    }

    public String orderStatus(long orderId) {
        return queryStr(orderDs, "SELECT status FROM orders WHERE order_id=?", orderId);
    }
}
