package com.zhiya.redis;

import com.zhiya.redis.support.RedisSupport;


import java.util.ArrayList;
import java.util.List;

/**
 * Level 6：复制与哨兵——控制面要投票，数据面认异步（哨兵篇）。
 * <p>
 * 对应层级：Level 6。
 * 演示主题：Sentinel 投票团故障转移全流程。
 * 验证目标：SDOWN→ODOWN→选 leader→挑 offset 最大从库→REPLICAOF NO ONE→客户端重连；
 *           min-replicas-to-write 收窄脑裂写窗口。
 */
public final class RedisLevel6SentinelDemo {

    private RedisLevel6SentinelDemo() {
    }

    static class Replica {
        final String name;
        int offset;      // 复制进度
        int priority;    // replica-priority，越小越优先
        Replica(String n, int off, int pri) { name = n; offset = off; priority = pri; }
    }

    static class Sentinel {
        final String name;
        boolean sawMasterDown;   // 自己是否判定超时（SDOWN 视角）
        Sentinel(String n) { name = n; }
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("Level 6 · 复制与哨兵：控制面要投票，数据面认异步（哨兵篇）",
                "某个深夜，主店着火了——居委会三位老人开始投票");

        RedisSupport.sec("① 拓扑：1 主 2 从 + 3 个 Sentinel（奇数，跨机房摆放）");
        var master = "master-A";
        var reps = new ArrayList<Replica>();
        reps.add(new Replica("replica-1", 990, 1));   // 抄账最新
        reps.add(new Replica("replica-2", 970, 5));
        var sentinels = List.of(new Sentinel("s1"), new Sentinel("s2"), new Sentinel("s3"));
        int quorum = 2;

        RedisSupport.sec("② 主库失联 → 两阶段确诊 → 选主 → 升主");
        System.out.println("    [t0] 主库 master-A 失联（宕机/分区）");
        // ① SDOWN
        for (var s : sentinels) s.sawMasterDown = true;   // 都超时
        System.out.println("    [t1] 三个 sentinel 各自心跳超时 → 各自记“主观下线 SDOWN”（私房怀疑）");
        // ② ODOWN
        int agree = (int) sentinels.stream().filter(s -> s.sawMasterDown).count();
        System.out.printf("    [t2] ≥ quorum=%d 个同判 → 客观下线 ODOWN ✓（%d/3 附议）%n", quorum, agree);
        // ③ 选 sentinel leader
        System.out.println("    [t3] 哨兵之间再选一个 leader（多数派，Raft 风格）→ s2 当选，主持 failover");
        // ④ 挑从库
        Replica best = reps.stream()
                .max((a, b) -> a.offset != b.offset
                        ? Integer.compare(a.offset, b.offset)
                        : Integer.compare(b.priority, a.priority))
                .orElseThrow();
        System.out.printf("    [t4] leader 挑从库：按 offset 最大 → %s（offset=%d，priority=%d）→ REPLICAOF NO ONE 升主%n",
                best.name, best.offset, best.priority);
        // ⑤ 其余从
        for (var r : reps) {
            if (r != best) System.out.printf("         %s → REPLICAOF %s（指向新主）%n", r.name, best.name);
        }
        System.out.println("        客户端从 sentinel 拿到新主地址，重连 → 故障转移完成");

        RedisSupport.sec("③ 那笔坏账（与复制篇的窗口呼应）");
        System.out.printf("    C 已被旧主确认（+OK 已给客户端），但还没 propagate 到被升主的 %s —— 这张小票被赖掉了。%n",
                best.name);
        System.out.println("    offset 差 = 丢失窗口的实测值（卡 4 要求演练时把这条数入档）。");

        RedisSupport.sec("④ min-replicas-to-write：把脑裂写窗口再收窄");
        System.out.println("    配置 min-replicas-to-write 1 + min-replicas-max-lag 10：");
        System.out.println("    · 从库数 < 1 或最大 lag > 10s → 主库拒写（宁可不可写，不可双主）；");
        System.out.println("    · 旧主在分区里继续收写？被这条规则挡住大半——双主脑裂的止血带。");
        System.out.println("    但这不是 fencing：要真正的终裁权移交，见决策卡 6（fencing token）。");

        RedisSupport.sec("⑤ 与 Cluster 自治的体制对照（一句话版）");
        RedisSupport.table(
                new String[]{"维度", "Sentinel", "Cluster 自治(L8)"},
                List.of(new String[][]{
                        {"裁判是谁", "外挂哨兵进程组(奇数,跨机房)", "集群成员自己(gossip 大喇叭)"},
                        {"疑似/确诊", "SDOWN → ODOWN(≥quorum)", "PFAIL → FAIL(多数master附议+广播)"},
                        {"换主方式", "leader 主动挑 offset 最大的从", "各副本按 rank 延迟自己起跑拉票"},
                        {"一轮几票", "哨兵间选 leader", "每 master 每纪元一票,落盘 nodes.conf"},
                }));
        RedisSupport.mantra("控制面要投票，数据面认异步");
    }
}
