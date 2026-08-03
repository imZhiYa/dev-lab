package com.zhiya.redis;

import com.zhiya.redis.support.RedisSupport;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Level 9 支线 A：Stream——小票箱(PEL)的规矩，交付语义 at-least-once。
 * <p>
 * 对应层级：Level 9。
 * 演示主题：消费组 + PEL 已取未认清单 + XAUTOCLAIM 过户。
 * 验证目标：XACK 才是交付点；骑手崩溃后小票留在箱里、按超时过户重投；
 *           内存卫生 = 写入方修剪(MAXLEN ~) + 消费方认票两份责任。
 */
public final class RedisLevel9StreamPelDemo {

    private RedisLevel9StreamPelDemo() {
    }

    record Entry(String id, String data) {}

    static class Group {
        final String name;
        String lastDeliveredId = "0-0";
        final Map<String, String> pel = new LinkedHashMap<>();   // entryId -> consumer
        final Map<String, Long> pelSince = new LinkedHashMap<>(); // entryId -> 入箱时间(ms)
        Group(String n) { name = n; }
    }

    static class Stream {
        final List<Entry> entries = new ArrayList<>();
        final Map<String, Group> groups = new LinkedHashMap<>();
        long lastMs = 0, lastSeq = 0;
        long clock = 0;             // 模拟时钟（ms）

        String xadd(String data) {
            long ms = clock;
            long seq = (ms == lastMs) ? lastSeq + 1 : 0;
            lastMs = ms; lastSeq = seq;
            String id = ms + "-" + seq;
            entries.add(new Entry(id, data));
            return id;
        }

        void xgroupCreate(String name) { groups.put(name, new Group(name)); }

        Entry xreadgroup(String group, String consumer) {
            Group g = groups.get(group);
            for (Entry e : entries) {
                if (compareId(e.id, g.lastDeliveredId) > 0) {
                    g.lastDeliveredId = e.id;
                    g.pel.put(e.id, consumer);
                    g.pelSince.put(e.id, clock);
                    return e;
                }
            }
            return null;
        }

        boolean xack(String group, String id) {
            Group g = groups.get(group);
            if (g.pel.remove(id) != null) {
                g.pelSince.remove(id);
                return true;
            }
            return false;
        }

        List<String> xpending(String group) {
            Group g = groups.get(group);
            List<String> out = new ArrayList<>();
            for (var e : g.pel.entrySet()) {
                long idle = clock - g.pelSince.get(e.getKey());
                out.add("    " + e.getKey() + " → 消费者 " + e.getValue() + "，压了 " + idle + " ms");
            }
            return out;
        }

        /** XAUTOCLAIM：把 idle 超过 minIdle 的票过户给新消费者 */
        List<Entry> xautoclaim(String group, long minIdleMs, String consumer) {
            Group g = groups.get(group);
            List<Entry> claimed = new ArrayList<>();
            for (var it = g.pel.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                long idle = clock - g.pelSince.get(e.getKey());
                if (idle >= minIdleMs) {
                    it.remove();
                    g.pelSince.remove(e.getKey());
                    g.pel.put(e.getKey(), consumer);
                    g.pelSince.put(e.getKey(), clock);
                    entries.stream().filter(x -> x.id.equals(e.getKey())).findFirst().ifPresent(claimed::add);
                }
            }
            return claimed;
        }

        /** 近似修剪 MAXLEN ~ n：按“宏节点”整块释放（教学：一节点 10 条），可能多留一个节点 */
        void xtrimMaxlenApprox(int n) {
            int node = 10;
            int keep = ((n / node) + 1) * node;
            if (entries.size() > keep) entries.subList(0, entries.size() - keep).clear();
            System.out.printf("    XADD … MAXLEN ~ %d → 修剪后剩 %d 条（按宏节点整块释放，可多留一个节点——官方示例 300 条配 ~50 可能留 ~100 🔒）%n",
                    n, entries.size());
        }

        private static int compareId(String a, String b) {
            long aMs = Long.parseLong(a.split("-")[0]), bMs = Long.parseLong(b.split("-")[0]);
            if (aMs != bMs) return Long.compare(aMs, bMs);
            return Long.compare(Long.parseLong(a.split("-")[1]), Long.parseLong(b.split("-")[1]));
        }
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("Level 9 支线 A · Stream 的小票箱：队列凭什么敢说“至少一次”",
                "平行世界的 C：一条订单消息的一生");

        RedisSupport.sec("① 病案：List 当队列 = 弹出即焚");
        RedisSupport.print("    LPUSH orders msg → BRPOP orders：消息被弹出的一瞬间消费者宕机 →");
        RedisSupport.err("    这条消息从宇宙里消失（没有副本、没有回执）；想加第二个消费组各消费一份，List 没有这个器官。");
        RedisSupport.print("    交付语义 = at-most-once 甚至 exactly-once(消失) —— 银行流水、订单事件当场去世。");

        RedisSupport.sec("② Stream：XADD → 消费组 → PEL 小票箱");
        var s = new Stream();
        s.xgroupCreate("g1");
        String id1 = s.xadd("订单#1 sku:1001 qty 2");
        String id2 = s.xadd("订单#2 sku:1002 qty 1");
        s.clock += 1000;
        System.out.println("    XADD orders * item sku:1001 qty 2 → 小票号 " + id1);
        System.out.println("    XADD orders * item sku:1002 qty 1 → 小票号 " + id2);

        Entry taken = s.xreadgroup("g1", "riderA");
        System.out.println("    XREADGROUP GROUP g1 riderA → 取到 " + taken.id() + " “"
                + taken.data() + "”；消息没有删除，而是进 PEL（已取未认）");

        RedisSupport.sec("③ 骑手 A 连人带餐消失：小票留在箱里");
        s.clock += 30_000;
        System.out.println("    （riderA 处理完之前宕机，XACK 没发）XPENDING orders g1：");
        s.xpending("g1").forEach(System.out::println);

        RedisSupport.sec("④ XAUTOCLAIM：按超时把票过户给 riderB → 消息复活");
        var claimed = s.xautoclaim("g1", 30_000, "riderB");
        for (Entry e : claimed) {
            System.out.printf("    XAUTOCLAIM orders g1 riderB 30000 → %s 过户给 riderB，riderB 重新处理%n", e.id());
        }
        System.out.println("    ★ 交付语义 = at-least-once：riderA 可能已干过活（死前没来得及确认），");
        System.out.println("      riderB 会再干一遍 → 重复投递由消费端幂等兜底（订单号唯一键/去重表）。");

        RedisSupport.sec("⑤ 正常闭环：XACK 销毁小票");
        boolean ack = s.xack("g1", id1);
        System.out.println("    XACK orders g1 " + id1 + " → " + (ack ? "小票销毁 ✓（这笔交付成立）" : "？"));
        System.out.println("    ★ 线性化点：XACK 把条目从 PEL 摘除的那一行——“这笔交付成立”从这一瞬才成立。");
        System.out.println("    剩余 PEL：" + (s.xpending("g1").isEmpty() ? "空" : String.join("; ", s.xpending("g1"))));

        RedisSupport.sec("⑥ 内存卫生 = 写入方修剪 + 消费方认票（两份责任，少一份都漏）");
        var big = new Stream();
        big.xgroupCreate("g1");
        for (int i = 0; i < 150; i++) big.xadd("msg" + i);
        big.xtrimMaxlenApprox(50);
        System.out.println("    XLEN/XPENDING 积压 + used_memory 爬坡 = Stream 的监控三件套。");

        RedisSupport.sec("⑦ 一句话边界");
        RedisSupport.print("    Stream 不是“小号 Kafka”：它活在内存里，没有磁盘多副本留存层；");
        RedisSupport.print("    它的不变量是 PEL（消费语义），不是磁盘 offset（留存语义）。");
        RedisSupport.print("    要长期回放/事件溯源/海量堆积 → 回 Level 5 想想介质账，去选真正的日志系统。");
        RedisSupport.mantra("队列凭小票：XACK 才是交付点");
    }
}
