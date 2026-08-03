package com.zhiya.redis.demo;

import com.zhiya.redis.support.RedisSupport;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Level 8：没有居委会的老街——gossip 八卦、两阶段确诊与半部 Raft。
 * <p>
 * 对应层级：Level 8。
 * 演示主题：PFAIL→FAIL 两阶段失败检测与 rank 延迟选举。
 * 验证目标：私记怀疑、多数确诊、广播讣告；DELAY=500+rand(0,500)+RANK×1000；每纪元一票；
 *           node-timeout 调小 = 调低错杀阈值（误判率实测）。
 */
public final class RedisLevel8GossipFailoverDemo {

    private RedisLevel8GossipFailoverDemo() {
    }

    static class Node {
        final String name;
        final boolean isMaster;
        final String masterOf;          // 若为从：它的主是谁
        int offset;                     // 复制进度
        final Map<String, Integer> report = new HashMap<>();   // 我对其他节点的怀疑标记: 0=活, 1=PFAIL
        long currentEpoch = 0, configEpoch = 0;
        long lastVoteEpoch = 0;         // 我投过票的纪元（落盘 nodes.conf）
        int slots = 0;

        Node(String n, boolean m, String masterOf, int off) {
            name = n; isMaster = m; this.masterOf = masterOf; offset = off;
        }
        @Override public String toString() { return name; }
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("Level 8 · 没有居委会的老街：gossip、两阶段确诊与半部 Raft",
                "C 的槽主死了——但这条街没有居委会：谁宣判、谁继任？");

        RedisSupport.sec("① 为什么“每分片配一套 Sentinel”和“指定街长”都死");
        RedisSupport.table(
                new String[]{"朴素方案", "它想解决什么", "它留下的致命账"},
                List.of(new String[][]{
                        {"每分片外挂一套 Sentinel", "复用现成剧本", "裁判团随片数线性爆炸；外挂视角管不了槽位元数据的多对多收敛"},
                        {"指定中心“街长”", "决策集中、逻辑简单", "街长的高可用无限递归；元数据与请求路径压到单点"},
                }));

        RedisSupport.sec("② 拓扑与两条总线");
        RedisSupport.print("  数据总线：客户端 ↔ 6379；集群总线：节点↔节点（端口=数据端口+10000 🔒，如 16379）。");
        RedisSupport.print("  节点：3 主 A/B/C + 3 从 a1/b1/c1（a1 跟 A、b1 跟 B、c1 跟 C）。");

        RedisSupport.sec("③ 槽主 B 之死的完整时序（模拟）");
        List<Node> nodes = new ArrayList<>();
        Node A = new Node("A", true, null, 0);
        Node B = new Node("B", true, null, 0);
        Node C = new Node("C", true, null, 0);
        Node a1 = new Node("a1", false, "A", 800);
        Node b1 = new Node("b1", false, "B", 990);   // 抄账最新
        Node c1 = new Node("c1", false, "C", 600);
        for (Node n : List.of(A, B, C, a1, b1, c1)) nodes.add(n);
        A.slots = 5461; B.slots = 5461; C.slots = 5461;   // 每个主领 1/3 槽

        A.currentEpoch = B.currentEpoch = C.currentEpoch = 1;
        A.configEpoch = B.configEpoch = C.configEpoch = 1;

        System.out.println("    [t0] master B 断气（宕机/被分区）");
        // t1: A、C 各自超时 → 私记 PFAIL（只进小本本）
        for (Node n : List.of(A, C)) n.report.put("B", 1);
        System.out.println("    [t1] 街坊 A、C 各自超过 node-timeout(15s) 叫不应 B → 小本本记 PFAIL（私房怀疑，不算数）");
        // t2: gossip 传播 + 多数附议 → FAIL
        System.out.println("    [t2] A 的 gossip 包把“B=PFAIL”捎给 C；C 也把同样八卦捎给 A；");
        int masters = 3;
        int agree = 2;   // A、C 两个 master 在窗口内都报了 B PFAIL
        boolean fail = agree > masters / 2;
        System.out.printf("         多数 master(≥%d) 在 NODE_TIMEOUT×2 窗口内附议 B=PFAIL → %s → 广播 FAIL，收到者无条件标记%n",
                masters / 2 + 1, fail ? RedisSupport.green("确诊 FAIL") : "仍只是八卦");
        System.out.println("         ★ 线性化点①：这是全街对“B 之死”的共识时刻——此前一切只是八卦。");

        // t3: b1 起跑
        List<Node> bReplicas = List.of(b1);
        for (Node r : bReplicas) {
            int rank = 0;   // 只有 b1 一个副本 → rank 0
            long delay = 500 + ThreadLocalRandom.current().nextInt(500) + rank * 1000L;
            System.out.printf("    [t3] B 的分店员 %s（offset=%d，rank=%d）延迟最短：DELAY=%dms，第一个醒来%n",
                    r.name, r.offset, rank, delay);
            System.out.println("         没人指定“你最合适”——是让最合适的人先到（被动择优）。");
            // t4: 拉票
            r.currentEpoch = 2;                       // 自增纪元
            System.out.printf("    [t4] %s 自增 currentEpoch=%d，向全街 master 发 FAILOVER_AUTH_REQUEST 拉票%n",
                    r.name, r.currentEpoch);
        }
        System.out.println("         A、C 各投一票（每纪元每 master 只一票，lastVoteEpoch 落盘 nodes.conf）：");
        int votes = 2;
        System.out.printf("         得票 %d/3 > 多数(≥2) → ★线性化点②：多数票达成 = 新主合法继位。%n", votes);
        System.out.println("         一届一票 + 纪元递增 ⇒ 同一纪元不可能产生两个胜出者（两个多数派必有交集）。");

        // t5
        long newEpoch = 2;
        System.out.printf("    [t5] b1 用更大的 configEpoch=%d 广播认领 B 的全部槽位；旧 B 若复活，见印即认臣%n", newEpoch);
        System.out.println("    [t6] 客户端旧路由撞上 b1（或复活后已降级的 B）→ -MOVED 指路，C 的旅途继续");
        System.out.println("    ⚠️ t0–t4 期间落在槽上的写：B 死前已应答、未及 propagate 给 b1 的部分随 B 而去——");
        System.out.println("        Level 6 的丢失窗口在集群尺度原样存在，莫忘。");

        RedisSupport.sec("④ 选举延迟公式与“故意的钝感”");
        System.out.println("    DELAY = 500ms + rand(0,500) + RANK×1000ms   （RANK 按复制 offset 排位）");
        System.out.println("    两处钝感设计：PFAIL→FAIL 两阶段 + 多数制附议 + rank 延迟起步，三句话同一句：");
        System.out.println("    宁可为观察付钱，不可为错杀付命。");
        spuriousFailoverTrials();

        RedisSupport.sec("⑤ 与 Sentinel 的体制对照（速查）");
        RedisSupport.table(
                new String[]{"维度", "Sentinel", "Cluster 自治"},
                List.of(new String[][]{
                        {"疑似/确诊", "SDOWN → ODOWN(≥quorum 哨兵)", "PFAIL → FAIL(多数master附议+广播)"},
                        {"时间参数 🔒", "down-after-ms 默认 30s", "node-timeout 默认 15s ×2 窗口"},
                        {"换主", "leader 主动挑", "副本按 rank 延迟自己起跑拉票"},
                        {"仲裁", "哨兵权威通告", "configEpoch 大者赢"},
                }));

        RedisSupport.mantra("私记怀疑，多数确诊，一届一票");
        RedisSupport.print("  为什么只是半部 Raft？槽位状态只有“这一个槽归谁”一个事实，单 packet 就能全量表达，");
        RedisSupport.print("  不需要日志、不需要追赶进度——但“纪元递增、大者赢”的仲裁规则非借不可（antirez 2013 原话 🔒）。");
    }

    /** 手痒调小 node-timeout 的后果：网络抖动/GC 停顿被误判为死缓 */
    private static void spuriousFailoverTrials() {
        System.out.println();
        System.out.println("  实验：网络抖动/GC 会产生平均 1s 的“疑似断气”停顿（教学分布），");
        System.out.println("  停顿 ≥ node-timeout 就会被误判 FAIL，跑 20000 轮看误判率：");
        int trials = 20_000;
        Random rnd = new Random(9);
        for (int timeout : new int[]{15000, 5000, 1000}) {
            int spurious = 0;
            for (int t = 0; t < trials; t++) {
                // 指数分布：停顿长度均值 1s（长尾即“罕见的大停顿”）
                double pauseMs = -1000.0 * Math.log(1 - rnd.nextDouble());
                if (pauseMs >= timeout) spurious++;      // 停顿够长 → 被误判死亡
            }
            System.out.printf("    node-timeout=%-6d → 误判率 %.2f%%（每轮误判 = 一次无谓 failover = 全量重同步+重连风暴）%n",
                    timeout, spurious * 100.0 / trials);
        }
        System.out.println("    → 调小 node-timeout = 调低“错杀阈值”：1s 就把 36% 的普通抖动当死亡。");
        System.out.println("      先给“误判一次 failover 的代价”定价再动手（坑 14）。");
    }
}
