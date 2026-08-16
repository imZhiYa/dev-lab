package com.zhiya.mq.rabbitcq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EX-05 RabbitMQ 经典队列（CQv1，3.13 默认）vs quorum 队列（CQv2 载体）资源形态对比（mq-02 存储引擎）。
 * <p>串行灌库（先 v1 观测 → 删队列 → 再 v2），各 3 万条 1KB 持久化消息；
 * 观测走 management HTTP API（rabbitmq:3.13-management，guest/guest 仅本地 lab，已放开 loopback 限制）。
 * 3.13 字段口径：message_bytes（总）、message_bytes_ram（RAM 驻留）、message_bytes_paged_out（换页）、memory（进程内存）。
 */
public class Ex05CqComparison {

    private static final String HOST = "localhost";
    private static final int TOTAL = 30_000;
    private static final String PAYLOAD = "x".repeat(1024);

    public static void main(String[] args) throws Exception {
        System.out.println("============ EX-05 经典队列(CQv1) vs quorum 队列(CQv2) ============");
        System.out.println("各灌 " + TOTAL + " 条 1KB 持久化消息，串行观测（避免内存叠加），走 management API\n");

        List<Object[]> rows = new ArrayList<>();
        rows.add(measure("ex05-v1-" + stamp(), null, "经典队列（CQv1，3.13 默认）"));
        rows.add(measure("ex05-v2-" + stamp(), Map.of("x-queue-type", "quorum"), "quorum 队列（CQv2）"));

        System.out.println("\n| 队列 | 消息数 | 队列内存 | 消息 bytes |");
        System.out.println("| --- | ---: | ---: | ---: |");
        for (Object[] r : rows) {
            System.out.printf("| %s | %s | %s | %s |%n", r[0], r[1], r[2], r[3]);
        }
        System.out.println("\n机制解读：同样的 3 万条持久化消息——经典队列（CQv1）消息体挂在消息存储，rabbitmqctl 可见 bytes、进程内存低；quorum 队列（CQv2）是 Raft 日志式存储，消息写 segment 落盘，bytes 由 RA 内部管理不对外暴露，进程内存含索引/日志元数据。"
                + "本实验教学量级（3 万条 1KB），仅验证形态差异存在，不做容量外推。");
    }

    /** 每轮全新队列名，规避 quorum 同名重建竞态（删除中的 RA 队列与新建同名冲突） */
    private static String stamp() {
        return Long.toString(System.currentTimeMillis() % 1_000_000);
    }

    private static Object[] measure(String queue, Map<String, Object> args, String label) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setPort(5672);

        long t0 = System.currentTimeMillis();
        try (Connection conn = factory.newConnection(); Channel ch = conn.createChannel()) {
            ch.queueDeclare(queue, true, false, false, args);
            ch.confirmSelect();
            // 注意：不用 addConfirmListener——它与 waitForConfirms 组合时 5.21 客户端存在
            // 确认帧消费冲突（listener 存在时 publish 不落库），纯 waitForConfirms 稳定
            int sent = 0;
            while (sent < TOTAL) {
                ch.basicPublish("", queue, MessageProperties.PERSISTENT_TEXT_PLAIN, PAYLOAD.getBytes());
                sent++;
                if (sent % 500 == 0) {
                    ch.waitForConfirms(5000);
                }
            }
            ch.waitForConfirms(10_000);
        }
        long loadMs = System.currentTimeMillis() - t0;

        Thread.sleep(8000);
        // 观测走 rabbitmqctl（运维真实方式）：management API 与 rabbitmqctl 对 quorum 队列
        // 均不暴露 message_bytes（RA 内部存储），故统一用 messages + memory 两列口径
        long[] stats = queueStats(queue);
        System.out.printf("[EX-05] %s：灌 %,d 条耗时 %,d ms → 消息数 %,d / 队列内存 %s%s%n",
                label, TOTAL, loadMs, stats[0], fmt(stats[2]),
                stats[1] >= 0 ? " / 消息 bytes " + fmt(stats[1]) : "");

        // 删队列（v1 观测完清理，再灌 v2）；quorum 删除异步（RA 退出），等待确认删净
        try (Connection conn = factory.newConnection(); Channel ch = conn.createChannel()) {
            ch.queueDelete(queue);
        }
        Thread.sleep(3000);
        System.out.println("[EX-05] " + label + " 已删除（测试数据随队列销毁）");
        return new Object[]{label, String.format("%,d", stats[0]), fmt(stats[2]),
                stats[1] >= 0 ? fmt(stats[1]) : "（RA 不暴露）"};
    }

    /** docker exec rabbitmqctl list_queues：返回 [messages, message_bytes(-1=不暴露), memory] */
    private static long[] queueStats(String queue) throws Exception {
        Process p = new ProcessBuilder("docker", "exec", "mqlab-rabbit",
                "rabbitmqctl", "list_queues", "name", "messages", "message_bytes", "memory")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        for (String line : out.split("\n")) {
            String[] cols = line.trim().split("\\s+");
            // quorum 队列 message_bytes 为空列（3 列），classic 为 4 列
            if (cols.length >= 3 && queue.equals(cols[0])) {
                long messages = Long.parseLong(cols[1]);
                long bytes = cols.length >= 4 && !cols[2].isEmpty() ? Long.parseLong(cols[2]) : -1;
                long memory = Long.parseLong(cols[cols.length - 1]);
                return new long[]{messages, bytes, memory};
            }
        }
        throw new IllegalStateException("rabbitmqctl 未找到队列: " + queue);
    }

    private static String fmt(long bytes) {
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
