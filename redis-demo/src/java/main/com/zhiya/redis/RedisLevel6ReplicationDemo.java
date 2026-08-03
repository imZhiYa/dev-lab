package com.zhiya.redis;

import com.zhiya.redis.support.RedisSupport;


import java.util.ArrayList;
import java.util.List;

/**
 * Level 6：复制与哨兵——控制面要投票，数据面认异步（复制篇）。
 * <p>
 * 对应层级：Level 6。
 * 演示主题：异步复制流水线 + replication backlog + PSYNC 断点续传。
 * 验证目标：offset 落在 backlog 窗口内=部分重同步、出窗=全量；
 *           “已确认写 vs 切换”的丢失窗口客观存在，只能压缩不能消灭。
 */
public final class RedisLevel6ReplicationDemo {

    private RedisLevel6ReplicationDemo() {
    }

    /** 主库：命令日志 + backlog 窗口 */
    static class Master {
        final List<String> log = new ArrayList<>();       // 已执行命令（按序）
        final List<Integer> sizes = new ArrayList<>();    // 每条命令字节数
        int replBacklogSize;                              // backlog 字节上限
        int backlogBytes = 0;

        Master(int backlogSize) { replBacklogSize = backlogSize; }

        int masterOffset() { return sizes.stream().mapToInt(Integer::intValue).sum(); }

        int execute(String cmd) {
            int bytes = cmd.length() + 3;                 // 教学：命令的协议字节数
            log.add(cmd);
            sizes.add(bytes);
            backlogBytes += bytes;
            while (backlogBytes > replBacklogSize && !sizes.isEmpty()) {
                backlogBytes -= sizes.remove(0);          // 环形：挤掉最老的
            }
            return bytes;
        }

        /** backlog 窗口的起点字节（绝对 offset） */
        int backlogStartOffset() { return masterOffset() - backlogBytes; }
    }

    static class Replica {
        String name;
        int appliedBytes = 0;
        boolean connected = true;

        Replica(String n) { name = n; }
        int offset() { return appliedBytes; }
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("Level 6 · 复制与哨兵：控制面要投票，数据面认异步（复制篇）",
                "C 的复印件顺风吹向分店");

        RedisSupport.sec("① 异步复制流水线");
        var m = new Master(4096);                          // backlog 4KB（教学缩小版）
        var r1 = new Replica("replica-1");
        var r2 = new Replica("replica-2");

        for (int i = 0; i < 10; i++) {
            int b = m.execute("SET k" + i + " v" + i);
            // 从库异步追上（r1 落后 2 条、r2 跟到最新）
            if (r1.appliedBytes < m.masterOffset() - 2 * 12) r1.appliedBytes += b;
            if (r2.appliedBytes < m.masterOffset()) r2.appliedBytes += b;
        }
        System.out.printf("    主库执行 10 条后：master_offset=%d B，replica-1=%d B（落后 2 条），replica-2=%d B（最新）%n",
                m.masterOffset(), r1.offset(), r2.offset());
        System.out.println("    主库每条命令执行完立刻回 +OK——不等从库确认。这是 AP 语义的章程：");

        RedisSupport.sec("② 已确认写 vs 切换：那笔赖掉的账");
        int mOffset = m.masterOffset();
        int r1Off = r1.offset();
        System.out.printf("    主库已给客户端 +OK 的写（offset ≤ %d），replica-1 才抄到 %d ——%n", mOffset, r1Off);
        System.out.println("    若此刻主库死掉、replica-1 被升主：两者之间那几条写就“赖掉”了。");
        System.out.println("    这不是 bug，是章程：复制异步 ⇒ 已确认的写仍可能丢（切换窗口）。");
        System.out.println("    收窄旋钮：WAIT n（等 n 个副本确认收到）、WAITAOF（7.2+，等 fsync）、");
        System.out.println("    min-replicas-to-write 1 + min-replicas-max-lag N —— 只能压缩窗口，不能消灭。");

        RedisSupport.sec("③ PSYNC 断点续传：offset 在不在 backlog 窗口里？");
        RedisSupport.print("    backlog 上限 4KB（教学），当前窗口 = [" + m.backlogStartOffset() + ", " + m.masterOffset() + ")");

        // 掉线：从库落后很多
        r1.connected = false;
        int lagBytes = 0;
        for (int i = 0; i < 200; i++) lagBytes += m.execute("SET busy" + i + " x");
        System.out.printf("    replica-1 掉线期间主库又写了 200 条（%d B）→ 它的 offset %d 已滑出窗口（起点 %d）%n",
                lagBytes, r1.offset(), m.backlogStartOffset());
        if (r1.offset() < m.backlogStartOffset()) {
            System.out.println("    → PSYNC 判“offset 出窗” → 全量重同步（fork RDB 流过去），而不是补缺口。");
        }

        // 短掉线：窗口内
        r2.connected = false;
        int r2OffBefore = r2.offset();
        for (int i = 0; i < 5; i++) m.execute("SET brief" + i + " x");
        if (r2OffBefore >= m.backlogStartOffset()) {
            int missing = m.masterOffset() - r2OffBefore;
            System.out.printf("    replica-2 短掉线后归来：offset %d 仍在窗口内 → 部分重同步，只补 %d B 缺口 ✓%n",
                    r2OffBefore, missing);
        }

        RedisSupport.sec("④ 一句话记忆 + 账");
        RedisSupport.print("    runid + offset + 环形 backlog = 断点续传三件套（同构：Kafka offset、MySQL GTID+binlog 位点）。");
        RedisSupport.print("    跨机房重算 backlog：峰值写速率 × 可忍断链时长（自测 #20；卡 9）。");
        RedisSupport.mantra("控制面要投票，数据面认异步");
    }
}
