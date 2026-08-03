package com.zhiya.redis;

import com.zhiya.redis.RedisSupport;


import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Level 1：Redis 的“快”不是品牌，是跨介质墙的物理账。
 * <p>
 * 对应层级：Level 1。
 * 演示主题：介质墙——主存 / SSD / 机械盘的延迟数量级。
 * 验证目标：实测 HashMap 内存读与磁盘随机读的差距（教学量级），证明“加机器买吞吐，买不到延迟”；
 *           并说清缓存 = 用一致性换延迟，数据两份必自带失效路径。
 */
public final class RedisLevel1MediaLatencyDemo {

    private RedisLevel1MediaLatencyDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("Level 1 · Redis 的“快”是介质账，不是魔法",
                "C = SET sku:1001:stock 42 —— 出发前，先看它为什么要离开 MySQL");

        // 1) 介质墙
        RedisSupport.sec("① 介质墙：数据住在哪一层，决定了你的延迟地板");
        RedisSupport.table(
                new String[]{"介质", "数量级(教学参考)", "Redis/MySQL 的处境", "墙"},
                java.util.List.of(new String[][]{
                        {"L1/L2 Cache", "1 ~ 10 ns", "CPU 内部", "——"},
                        {"DRAM 主存", "~ 100 ns", "Redis 住在这里", "← 纳秒墙"},
                        {"NVMe SSD", "~ 100 µs", "MySQL 的 B+树页", "← 微秒墙 (×10³)"},
                        {"机械盘寻道", "~ 10 ms", "最底层", "← 毫秒墙 (×10⁵)"},
                        {"跨机房网络", "几 ms 起", "MySQL 分布式下限", "← 毫秒墙+"},
                }),
                new int[]{-1, 1, -1, -1});
        RedisSupport.print();
        RedisSupport.print("  结论先行：MySQL 即便全命中 buffer pool，一条 SQL 也要过协议解析、优化器、");
        RedisSupport.print("  MVCC 快照、锁管理——它的延迟地板被“介质 + 通用数据库执行栈”焊死。");
        RedisSupport.print("  而 Redis 是 Remote Dictionary：把热点小数据抽进一个专用进程的 DRAM，");
        RedisSupport.print("  用 O(1)~O(log N) 的字典操作读写。这一步只负责“换介质”。");
        RedisSupport.mantra("内存是主存，磁盘只是备份");

        // 2) 真实小实验
        RedisSupport.sec("② 真实小实验：内存读 vs 磁盘随机读（教学量级，以你的硬件实测为准）");
        int keys = 1_000_000;
        var map = new HashMap<String, Integer>(keys * 2);
        for (int i = 0; i < keys; i++) map.put("key:" + i, i);

        Path tmp = null;
        try {
            tmp = Files.createTempFile("rdd-disk-", ".bin");
            RandomAccessFile raf = new RandomAccessFile(tmp.toFile(), "rw");
            byte[] page = new byte[4096];
            new Random(7).nextBytes(page);
            for (int i = 0; i < 4096; i++) {          // 16MB 文件
                raf.write(page);
            }
            raf.getFD().sync();

            // ---- 内存读 ----
            int n = 300_000;
            String[] probes = new String[n];
            for (int i = 0; i < n; i++) probes[i] = "key:" + (i % keys);
            // 预热
            int sink = 0;
            for (int i = 0; i < 50_000; i++) sink += map.get(probes[i]);
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) sink += map.get(probes[i]);
            long t1 = System.nanoTime();
            double nsMem = (t1 - t0) / (double) n;

            // ---- 磁盘随机读（每次 seek 到随机位置读 4KB）----
            int dn = 30_000;
            long fileLen = tmp.toFile().length();
            // 预热
            for (int i = 0; i < 3_000; i++) {
                raf.seek(ThreadLocalRandom.current().nextLong(0, fileLen - 4096));
                raf.read(page);
            }
            long t2 = System.nanoTime();
            for (int i = 0; i < dn; i++) {
                raf.seek(ThreadLocalRandom.current().nextLong(0, fileLen - 4096));
                raf.read(page);
            }
            long t3 = System.nanoTime();
            double nsDisk = (t3 - t2) / (double) dn;
            raf.close();

            RedisSupport.table(
                    new String[]{"操作", "实测均值", "换算", "量级"},
                    java.util.List.of(new String[][]{
                            {"HashMap get（DRAM 主存）", fmtNs(nsMem), String.format("%.0f ns/次", nsMem), "百纳秒级"},
                            {"磁盘文件随机读 4KB（NVMe/宿主盘）", fmtNs(nsDisk), String.format("%.1f µs/次", nsDisk / 1e3), "百微秒级"},
                            {"墙的倍数", "", String.format("≈ %.0f ×", nsDisk / Math.max(nsMem, 1)), "10³ 量级"},
                    }),
                    new int[]{-1, 1, 1, -1});

            RedisSupport.print();
            RedisSupport.print("  同一把“读一个热点 key”的算盘：");
            RedisSupport.print("    内存字典   → 百纳秒级，十万级 QPS 的主场；");
            RedisSupport.print("    磁盘页访问 → 百微秒级，把 Level 1 翻过的墙原样翻回来。");
            RedisSupport.print(RedisSupport.dim("    注：若测试文件仍被页缓存热住，磁盘数字会被低估——drop caches 或换更大文件后重测，"));
            RedisSupport.print(RedisSupport.dim("        冷盘随机读应回到百微秒级，与内存隔着 10³ 的墙。"));
            RedisSupport.print("  sink = " + sink + "（防编译器优化）  " + RedisSupport.dim("文件大小 " + fileLen / 1024 / 1024 + " MB"));
        } catch (Exception e) {
            RedisSupport.err("  (磁盘实验失败，跳过：" + e + ")");
        } finally {
            try { if (tmp != null) Files.deleteIfExists(tmp); } catch (Exception ignored) {}
        }

        // 3) 为什么不多加一台 MySQL
        RedisSupport.sec("③ 为什么“加机器”买不到延迟");
        RedisSupport.table(
                new String[]{"朴素方案", "它本来想解决什么", "它留下的致命账"},
                java.util.List.of(new String[][]{
                        {"只留 MySQL，只读实例+连接池硬顶",
                                "数据只有一份真相，永远没有一致性问题",
                                "延迟地板被介质+通用执行栈焊死在微秒~毫秒；加实例只能买吞吐，买不到延迟"},
                }));
        RedisSupport.print();
        RedisSupport.print("  大促零点，库存计数 QPS 冲到几十万（教学场景），线程全堵在 InnoDB 行锁与页闩上");
        RedisSupport.print("  排队，连接池打满——钱烧了，延迟纹丝不动。");

        // 4) 一致性账
        RedisSupport.sec("④ 缓存不是免费加速：数据从此有两份");
        RedisSupport.print("  缓存 = 用【一致性】换【延迟】。把同一份数据放两个地方的那一刻，");
        RedisSupport.print("  “以谁为准、多久收敛”这笔账同时出生——设计时必须同时给出失效路径，");
        RedisSupport.print("  不允许“先上线再说”。(决策卡 1 偿还这笔账：写 DB → 删缓存 → 失败重试)");
        RedisSupport.hr();
        RedisSupport.print("  带走三条：① 评估加速先问“跨过哪面介质墙、几个数量级”；");
        RedisSupport.print("            ② 一份数据放两处 ⇒ 必须自带失效路径；");
        RedisSupport.print("            ③ DRAM 单位成本 ≫ SSD ⇒ 容量受限 ⇒ 逼出 Level 4 的过期与淘汰。");
    }

    private static String fmtNs(double ns) {
        if (ns >= 1e6) return String.format("%.1f ms", ns / 1e6);
        if (ns >= 1e3) return String.format("%.1f µs", ns / 1e3);
        return String.format("%.0f ns", ns);
    }
}
