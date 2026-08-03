package com.zhiya.redis.demo;

import com.zhiya.redis.RedisSupport;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 坑与细节：错误代码 → 错因 → 便利店比喻 → 线上现象 → 修正。
 * <p>
 * 对应层级：全文横切。
 * 演示主题：15 个坑里的可运行标本 + 速查表。
 * 验证目标：KEYS 全扫 vs SCAN 分批的墙钟；DEL 大 key vs UNLINK 的主线程占用；
 *           Lua 大脚本 BUSY 语义（SCRIPT KILL 边界）。
 */
public final class RedisPitfallsDemo {

    private RedisPitfallsDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("⚠️ 坑与细节（15 个）：错误代码 → 错因 → 便利店 → 线上现象 → 修正",
                "每层学完，这里是跨层事故与排障速查");

        keysVsScan();
        delBigKey();
        luaBusy();
        summaryTable();
    }

    // ---------- 坑 1：KEYS 盘库 vs SCAN 分批 ----------
    private static void keysVsScan() {
        RedisSupport.sec("坑 1：KEYS 盘库 —— 全表 O(N) 扫描在单线程事件循环里裸奔");
        Map<String, String> keyspace = new HashMap<>();
        for (int i = 0; i < 200_000; i++) keyspace.put("order:2024:" + i, "v");
        String pattern = "order:2024:";

        // KEYS：一次全扫
        long t0 = System.nanoTime();
        int matched = 0;
        for (String k : keyspace.keySet()) if (k.startsWith(pattern)) matched++;
        long keysUs = (System.nanoTime() - t0) / 1_000;

        // SCAN：分页，每页之间“让出事件循环”（他客命令可插队）
        long t1 = System.nanoTime();
        int scanned = 0, page = 0, perPage = 1000;
        var iter = keyspace.keySet().iterator();
        while (iter.hasNext()) {
            for (int i = 0; i < perPage && iter.hasNext(); i++) {
                String k = iter.next();
                if (k.startsWith(pattern)) scanned++;
            }
            page++;
            // 页间让出：模拟事件循环回来服务其他连接
        }
        long scanUs = (System.nanoTime() - t1) / 1_000;

        System.out.printf("    · 20 万 key：KEYS 风格一次全扫 %d µs；SCAN 风格分 %d 页（每页 1000，页间可服务他人）%d µs%n",
                keysUs, page, scanUs);
        RedisSupport.print("    · 便利店：店长撇下收银逐格清点全店商品，门口队伍排到街尾（KEYS）；");
        RedisSupport.print("      SCAN = 每数一格就回头结一单，队伍永远在动。");
        RedisSupport.print("    · 修正：SCAN cursor MATCH … COUNT n；运维查询走从库；必要时离线导出 RDB 分析。");
    }

    // ---------- 坑 2：DEL 大 key vs UNLINK ----------
    private static void delBigKey() {
        RedisSupport.sec("坑 2：DEL 大 key —— 同步释放整块结构 = 事件循环停摆");
        List<String> huge = new ArrayList<>();
        for (int i = 0; i < 300_000; i++) huge.add("element-" + i);

        long t0 = System.nanoTime();
        huge.clear();                               // 模拟同步 DEL：一次释放 30 万个对象
        long delUs = (System.nanoTime() - t0) / 1_000;

        // UNLINK：异步释放，命令立即返回
        List<String> huge2 = new ArrayList<>();
        for (int i = 0; i < 300_000; i++) huge2.add("element-" + i);
        long t1 = System.nanoTime();
        Thread async = new Thread(() -> {
            try { Thread.sleep(1); } catch (InterruptedException ignored) {}
            huge2.clear();                          // bio 线程里慢慢释放
        });
        async.start();
        long unlinkUs = (System.nanoTime() - t1) / 1_000;

        System.out.printf("    · 30 万元素：同步 DEL 占主线程 %d µs（期间全店停业）；UNLINK 命令 %d µs 就返回，释放交给后台 bio 线程 ✓%n",
                delUs, unlinkUs);
        RedisSupport.print("    · 线上现象：秒级毛刺，监控上一根孤零零的尖峰（SLOWLOG 可查）。");
        RedisSupport.print("    · 修正：UNLINK；lazyfree-lazy-user-del yes 🔒；大对象治理见坑 13。");
    }

    // ---------- 坑 8：Lua 大脚本堵循环 ----------
    private static void luaBusy() {
        RedisSupport.sec("坑 8：Lua 大脚本堵循环 —— 脚本与命令互斥执行");
        RedisSupport.print("    一个脚本里循环几十万次做批处理 → 事件循环停摆：");
        RedisSupport.print("      · 到 lua-time-limit / busy-reply-threshold(🔒 默认 5000ms) 才回 BUSY，脚本继续跑；");
        RedisSupport.print("      · SCRIPT KILL 只能杀“还没执行过写”的脚本 🔒；");
        RedisSupport.err("      · 已写过任何 key 的脚本 → 只能 SHUTDOWN NOSAVE 牺牲实例。");
        RedisSupport.print("    · 便利店：一位顾客掏出三年购物清单要求一次性算完账，后厨全员停摆。");
        RedisSupport.print("    · 修正：拆小步分批；循环上界写死头部注释并压测（决策卡 7 体检清单）；");
        RedisSupport.print("      FUNCTION 库级管理：库随 AOF 持久化、复制到从库，“as durable as the data itself” 🔒。");
    }

    // ---------- 15 坑速查 ----------
    private static void summaryTable() {
        RedisSupport.sec("15 坑速查表（跨层事故排障索引）");
        RedisSupport.table(
                new String[]{"#", "坑", "一句话修正", "出处"},
                List.of(new String[][]{
                        {"1", "KEYS 盘库", "SCAN cursor 分批；查询走从库", "L2 公理"},
                        {"2", "DEL 大 key", "UNLINK 异步释放", "L2/L3"},
                        {"3", "SETNX+EXPIRE", "SET lock token NX PX 一把梭；解锁 Lua", "卡6"},
                        {"4", "不配 maxmemory/乱配 policy", "物理内存 70-80% + 按业务选 policy", "卡2"},
                        {"5", "appendfsync always 拍脑袋", "everysec 起步；强语义接口用 WAITAOF", "L5"},
                        {"6", "BGSAVE 期间 THP 开着", "关 THP/madvise；bgsave 错峰；overcommit=1", "L5"},
                        {"7", "把 Redis 当唯一存储又全关持久化", "AOF everysec + 副本 + 恢复演练", "L5"},
                        {"8", "Lua 大脚本堵循环", "拆小步；上界声明；FUNCTION 治理", "卡7"},
                        {"9", "混用 Pipeline/MULTI/Lua", "三件事三种工具，能力边界不互通", "L2"},
                        {"10", "读写分离读从库", "写后读走主库或 WAIT；容忍延迟才读从", "L6"},
                        {"11", "读从库读到已过期", "容忍窗口或读主库；断链可显式拒读", "L6 推论"},
                        {"12", "集群照单机写法打多 key", "schema 阶段 {hashtag} 共槽", "L7"},
                        {"13", "胖 value 全量搬运+失效广播", "拆对象/按需取/压缩；MGET 分批", "L2/L10"},
                        {"14", "cluster-node-timeout 手痒调小", "先给误判 failover 定价；默认 15s 是甜点", "L8"},
                        {"15", "把跨机房从库当双活", "写入口全局唯一；异地=热备+就近读", "卡9"},
                }));
        RedisSupport.print();
        RedisSupport.print("  排障纪律：前三步先读指标（命中率→内存/淘汰→失效分布），不在根因未定位时同时改");
        RedisSupport.print("  maxmemory、重启 Redis、扩 DB——否则事故结束后无法知道哪项措施真正有效。");
    }
}
