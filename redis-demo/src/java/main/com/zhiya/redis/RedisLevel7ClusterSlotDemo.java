package com.zhiya.redis;

import com.zhiya.redis.support.RedisSupport;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Level 7：Cluster 分片——槽位的本质是“以后能搬家”。
 * <p>
 * 对应层级：Level 7。
 * 演示主题：16384 槽位、{hashtag}、MOVED/ASK、逐槽迁移。
 * 验证目标：真实 CRC16(key)&16383 分槽；{hashtag} 让多 key 共槽躲开 CROSSSLOT；
 *           扩容 = 计划不是开关，每把 key 有且只有一个合法店主。
 */
public final class RedisLevel7ClusterSlotDemo {

    private RedisLevel7ClusterSlotDemo() {
    }

    /** 节点：认领若干槽位，且有自己的键空间 */
    static class Node {
        final String name;
        final TreeMap<Integer, Integer> slots = new TreeMap<>();  // 本节点认领的槽段 [start,end]
        final Map<String, String> data = new HashMap<>();
        boolean importing = false;   // 迁移期目标
        Node(String n) { name = n; }

        void claim(int start, int end) { slots.put(start, end); }
        boolean owns(int slot) {
            return slots.entrySet().stream().anyMatch(e -> slot >= e.getKey() && slot <= e.getValue());
        }
        @Override public String toString() {
            StringBuilder sb = new StringBuilder(name).append("{");
            slots.forEach((s, e) -> sb.append(s.equals(e) ? s : s + "-" + e).append(','));
            if (!slots.isEmpty()) sb.setLength(sb.length() - 1);
            return sb.append('}').toString();
        }
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("Level 7 · Cluster 分片：16384 个槽位，槽位的本质是“以后能搬家”",
                "C 要决定进哪家店——以及这家店以后怎么搬家");

        RedisSupport.sec("① 取模与一致性哈希各死一头");
        RedisSupport.table(
                new String[]{"朴素方案", "它想解决什么", "它留下的致命账"},
                List.of(new String[][]{
                        {"hash(key) mod N", "分片", "N 一变全量重 hash，缓存大面积失效，回源压死后端（扩容风暴）"},
                        {"一致性哈希环", "平滑扩缩容", "偏斜要虚拟节点补救；迁移/再均衡/路由发现另起炉灶"},
                }));

        RedisSupport.sec("② CRC16(key) & 16383：键→槽，与节点数无关");
        Node a = new Node("node-A"), b = new Node("node-B"), c = new Node("node-C");
        a.claim(0, 5460); b.claim(5461, 10922); c.claim(10923, 16383);

        List<String> sampleKeys = List.of("sku:1001:stock", "user:42", "order:2026", "goods:hot:1",
                "session:abc", "cart:9:a", "cart:9:b");
        var rows = new ArrayList<String[]>();
        for (String k : sampleKeys) {
            int slot = RedisSupport.slot(k);
            String owner = ownsWhich(a, b, c, slot);
            rows.add(new String[]{k, String.valueOf(slot), String.format("CRC16=%04X", RedisSupport.crc16(k.getBytes())), owner});
        }
        RedisSupport.table(new String[]{"key", "槽位", "CRC16", "归属节点"}, rows, new int[]{-1, 1, -1, -1});
        System.out.printf("  节点布局：%s | %s | %s（示例：key 只认槽，槽只认节点）%n", a, b, c);

        RedisSupport.sec("③ hashtag：{…} 让多 key 共槽（CROSSSLOT 的唯一解法）");
        RedisSupport.print("  多 key 命令/事务/Lua 的原子边界 = 槽，不是集群。");
        String k1 = "cart:{42}:a", k2 = "cart:{42}:b", k3 = "cart:42:a";
        System.out.printf("    cart:{42}:a → slot %d%n", RedisSupport.slot(k1));
        System.out.printf("    cart:{42}:b → slot %d   ← 和上面同槽 ⇒ MSET/MULTI/Lua 可以原子%n", RedisSupport.slot(k2));
        System.out.printf("    cart:42:a   → slot %d   ← 没写花括号，落到别的槽 ⇒ CROSSSLOT 报错%n", RedisSupport.slot(k3));
        int s1 = RedisSupport.slot(k1), s3 = RedisSupport.slot(k3);
        if (s1 != s3) {
            RedisSupport.err("    MSET cart:{42}:a 1 cart:42:a 2 → (error) CROSSSLOT Keys in request don't hash to the same slot");
        }

        RedisSupport.sec("④ 路由：客户端缓存 + 服务器纠错（-MOVED / -ASK）");
        System.out.println("    客户端本地缓存 slot→node 表，错了由服务器纠正：");
        System.out.println("      -MOVED slot ip:port  永久：这槽以后归那家店，请更新路由表");
        System.out.println("      -ASK   slot ip:port  临时：槽正在搬，先去那边办这一笔（先发 ASKING）");
        System.out.println("    ——省的是“每笔都问中心路由”的那一跳。");

        RedisSupport.sec("⑤ 扩容 = 逐槽搬家：每把 key 有且只有一个合法店主");
        System.out.println("    场景：把 node-A 的槽 0 整个搬到 node-C（迁移单位 = 槽，原子单位 = key）");
        int migratingSlot = 0;
        String keyInSlot = "sku:1001:stock";
        System.out.printf("    key=%s 在槽 %d，当前店主 = node-A%n", keyInSlot, RedisSupport.slot(keyInSlot));
        // 准备：node-A 标记 MIGRATING，node-C 标记 IMPORTING
        System.out.println("    CLUSTER SETSLOT 0 MIGRATING node-C  （node-A 侧）");
        System.out.println("    CLUSTER SETSLOT 0 IMPORTING node-A  （node-C 侧）");
        // 逐 key MIGRATE
        System.out.println("    MIGRATE node-C 6379 key sku:1001:stock 0 5000 → 把 key 原子搬走并删除源侧");
        // 迁移中：A 上该 key 已不在 → ASK
        System.out.println("    迁移窗口内客户端再来：node-A 回 -ASK 0 node-C:6379");
        System.out.println("    客户端先 ASKING 再发命令到 node-C → 受理（迁移期临时通行证）");
        // 收尾
        System.out.println("    CLUSTER SETSLOT 0 NODE node-C → 全拓扑收敛，路由表永久指向 node-C");
        System.out.println("    乱不了的原因：每把 key 的写永远只在某一个主线程里被串行受理——");
        System.out.println("    只是那个主线程在搬家前后换了台机器（Level 2 的线性化点在集群尺度重演）。");

        RedisSupport.sec("⑥ 为什么是 16384（设计轶闻 🔒，理解辅助）");
        System.out.println("    心跳包内嵌全槽位 bitmap = 16384 bit = 2KB，gossip 包里不痛不痒；");
        System.out.println("    官方建议集群规模控制在千节点以内，16384 对均衡粒度已足够；");
        System.out.println("    槽数再大 4 倍，gossip 带宽 ×4，换不到实际收益。");
        System.out.println("    → 这是“网络开销 × 均衡粒度”的工程甜点，不是魔法常数。");

        RedisSupport.sec("⑦ 带走的判断");
        RedisSupport.print("  ① 分片单元数 ≫ 节点数，搬迁按片走（Kafka partition / ES shard 同款思路）；");
        RedisSupport.print("  ② 路由发现 = 客户端缓存 + 服务器纠错（MOVED/ASK），可照搬到自研分片中间件；");
        RedisSupport.print("  ③ 扩容是计划不是开关：灰度 10% 槽 → 观测 → 回滚预案（卡 5 演练清单）；");
        RedisSupport.print("  ④ 热点不归拓扑管：单 key 几百万 QPS 迁到哪个槽都是那一台着火 → 本地缓存/拆 key。");
        RedisSupport.mantra("槽位一万六，搬迁按片走");
    }

    private static String ownsWhich(Node a, Node b, Node c, int slot) {
        if (a.owns(slot)) return a.name;
        if (b.owns(slot)) return b.name;
        return c.name;
    }
}
