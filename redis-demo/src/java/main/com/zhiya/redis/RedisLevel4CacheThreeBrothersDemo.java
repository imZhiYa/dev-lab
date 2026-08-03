package com.zhiya.redis;

import com.zhiya.redis.RedisSupport;


import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Level 4 补充：缓存三兄弟——穿透 / 雪崩 / 击穿。
 * <p>
 * 对应层级：Level 4。
 * 演示主题：同一条读链路在不同根因下的三种故障形态。
 * 验证目标：空值短 TTL 挡穿透、TTL 随机抖动摊平雪崩、single-flight 互斥重建防击穿（狗桩），
 *           全部用 DB 被打次数做对照；并演示 Cache-Aside 写路径“删缓存优于更新缓存”。
 */
public final class RedisLevel4CacheThreeBrothersDemo {

    private RedisLevel4CacheThreeBrothersDemo() {
    }

    /** 简易缓存：key → (value, expireAtMs) */
    static class Cache {
        final Map<String, String[]> store = new ConcurrentHashMap<>();  // k -> [v, expireMs]
        long hits = 0, misses = 0, dbCalls = 0, expired = 0;

        String get(String k) {
            String[] e = store.get(k);
            if (e == null) { misses++; return null; }
            if (Long.parseLong(e[1]) < System.currentTimeMillis()) {
                store.remove(k); expired++;
                misses++;
                return null;
            }
            hits++;
            return e[0];
        }

        void set(String k, String v, long ttlMs) {
            store.put(k, new String[]{v, String.valueOf(System.currentTimeMillis() + ttlMs)});
        }

        void del(String k) { store.remove(k); }
    }

    static Cache cache = new Cache();
    static final Map<String, String> db = new HashMap<>();   // 唯一真相
    static AtomicLong dbCalls = new AtomicLong();

    /** 模拟一次真实 DB 查询：5ms 往返（教学量级） */
    static String slowDbGet(String k) {
        try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return db.get(k);
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("Level 4 补充 · 缓存三兄弟：同一根因树上的三种死法",
                "先判根因，再选机制；共用一把锁救不了三个不同的病");

        penetration();
        avalanche();
        breakdown();
        multiLevelAndCacheAside();
    }

    // ---------- 穿透 ----------
    private static void penetration() {
        RedisSupport.sec("① 穿透：请求的 key 本来就不存在");
        cache = new Cache();
        dbCalls.set(0);

        System.out.println("  场景：攻击者/错误请求反复查 order:999999（库里也没有）");
        for (int i = 0; i < 1000; i++) {
            String v = cache.get("order:999999");
            if (v == null) {
                v = db.get("order:999999");        // 必然 miss
                dbCalls.incrementAndGet();
                if (v != null) cache.set("order:999999", v, 30_000);
                // 不存在的 key 不缓存 → 下次还打 DB
            }
        }
        System.out.printf("    无防护：1000 次请求 → DB 被打了 %d 次（缓存永远 miss，等于没缓存）%n", dbCalls.get());

        cache = new Cache();
        dbCalls.set(0);
        for (int i = 0; i < 1000; i++) {
            String v = cache.get("order:999999");
            if (v == null) {
                v = db.get("order:999999");
                dbCalls.incrementAndGet();
                cache.set("order:999999", "null", 2_000);   // 空值短 TTL 占位
            }
        }
        System.out.printf("    空值短 TTL 占位：1000 次请求 → DB 只被打 %d 次（头一回回源，之后命中空值）%n", dbCalls.get());
        System.out.println("    更狠的防线：布隆过滤器在入口直接拦掉不存在的 key（0 次 DB）。");
    }

    // ---------- 雪崩 ----------
    private static void avalanche() {
        RedisSupport.sec("② 雪崩：大量 key 齐整 TTL 同时失效");
        cache = new Cache();
        dbCalls.set(0);
        Random rnd = new Random(3);

        // 对齐 TTL：1000 个 key 全在 t+100ms 一起到期
        long base = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            String k = "goods:" + i;
            db.put(k, "商品" + i);
            cache.set(k, "商品" + i, 100);          // 全部 100ms
        }
        sleep(120);
        // 全部同时过期后，来 1000 个请求
        for (int i = 0; i < 1000; i++) {
            String k = "goods:" + i;
            String v = cache.get(k);
            if (v == null) { v = db.get(k); dbCalls.incrementAndGet(); cache.set(k, v, 100); }
        }
        System.out.printf("    TTL 齐整：1000 个 key 同刻失效 → 1000 次请求瞬时回源 DB %d 次（回源尖峰）%n",
                dbCalls.get());

        // 加随机抖动
        cache = new Cache();
        dbCalls.set(0);
        for (int i = 0; i < 1000; i++) {
            db.put("goods:" + i, "商品" + i);
            cache.set("goods:" + i, "商品" + i, 100 + rnd.nextInt(80));   // base + jitter
        }
        sleep(120);
        // 请求到达时只有部分 key 过期
        for (int i = 0; i < 1000; i++) {
            String k = "goods:" + i;
            String v = cache.get(k);
            if (v == null) { v = db.get(k); dbCalls.incrementAndGet(); cache.set(k, v, 100 + rnd.nextInt(80)); }
        }
        System.out.printf("    TTL+随机抖动：同一时刻只回源 %d 次（其余 key 未到期直接命中缓存），压力摊平%n",
                dbCalls.get());
        System.out.println("    配方：baseTTL + random(jitter)（TTL 分布不能人为齐整）；分批预热；多级缓存兜底。");
    }

    // ---------- 击穿 ----------
    private static void breakdown() {
        RedisSupport.sec("③ 击穿：单个热点 key 高并发瞬间失效（狗桩效应）");
        cache = new Cache();
        dbCalls.set(0);
        db.put("hot:seckill", "秒杀库存页");
        cache.set("hot:seckill", "秒杀库存页", 50);
        sleep(60);                                    // 热点失效

        int n = 1000;
        AtomicInteger finished = new AtomicInteger();
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(n);
        // 无防护：1000 个并发全部回源（用屏障保证同时起跑，才能复现狗桩）
        for (int i = 0; i < n; i++) {
            new Thread(() -> {
                try { barrier.await(); } catch (Exception e) { Thread.currentThread().interrupt(); }
                String v = cache.get("hot:seckill");
                if (v == null) {
                    v = slowDbGet("hot:seckill");     // 模拟 5ms DB 往返
                    dbCalls.incrementAndGet();        // 每个人都查一次 DB！
                    cache.set("hot:seckill", v, 60_000);
                }
                finished.incrementAndGet();
            }).start();
        }
        while (finished.get() < n) Thread.onSpinWait();
        System.out.printf("    无防护：热点失效瞬间 %d 个并发（同刻起跑）→ DB 被打了 %d 次（同一个查询复制 N 份，狗桩效应）%n",
                n, dbCalls.get());

        // single-flight：SET lock NX PX 互斥重建
        cache = new Cache();
        dbCalls.set(0);
        cache.set("hot:seckill", "秒杀库存页", 50);
        sleep(60);
        Map<String, Long> lock = new ConcurrentHashMap<>();
        finished.set(0);
        java.util.concurrent.CyclicBarrier barrier2 = new java.util.concurrent.CyclicBarrier(n);
        for (int i = 0; i < n; i++) {
            new Thread(() -> {
                try { barrier2.await(); } catch (Exception e) { Thread.currentThread().interrupt(); }
                String v = cache.get("hot:seckill");
                if (v == null) {
                    long token = System.nanoTime();
                    if (lock.putIfAbsent("hot:seckill", token) == null) {   // SET lock NX
                        try {
                            v = slowDbGet("hot:seckill");                    // 只有 1 个做慢查询
                            dbCalls.incrementAndGet();                       // 只有 1 个
                            cache.set("hot:seckill", v, 60_000);
                        } finally {
                            if (lock.get("hot:seckill") == token) lock.remove("hot:seckill");
                        }
                    } else {
                        v = cache.get("hot:seckill");                        // 没抢到：重读（等新值）
                        if (v == null) v = "稍后重试";                        // 或短暂等待
                    }
                }
                finished.incrementAndGet();
            }).start();
        }
        while (finished.get() < n) Thread.onSpinWait();
        System.out.printf("    互斥重建（SET lock NX PX + token 校验）：%d 个并发 → DB 只打 %d 次%n", n, dbCalls.get());
        System.out.println("    拿不到重建锁不是“19 个用户失败”，而是被挡在 DB 外等新值/旧值。");
    }

    // ---------- 多级缓存 + Cache-Aside ----------
    private static void multiLevelAndCacheAside() {
        RedisSupport.sec("④ 读路径分层 + 写路径：为什么写后“删缓存”而不是“更新缓存”");
        RedisSupport.print("  读：本地缓存(零网络) → Redis(一次网络) → DB(最终源)，逐层回填；");
        RedisSupport.print("  写（Cache-Aside）：DB 事务成功 → 删除 Redis key → 本地缓存短 TTL 自愈。");
        RedisSupport.print();
        RedisSupport.print("  为什么删除优于更新？并发交错必脏：");
        RedisSupport.print("    t1  请求A 写 DB=新值");
        RedisSupport.print("    t2  请求A 写缓存=新值");
        RedisSupport.print("    t3  请求B 写 DB=旧值（更晚才提交的旧事务）");
        RedisSupport.print("    t4  请求B 写缓存=旧值 ← 旧值把新值覆盖，缓存比 DB 还旧");
        RedisSupport.print("    若 t2 是删缓存，t4 就无从覆盖 —— 下一次读变成一次受控回源。");
        RedisSupport.print();
        RedisSupport.print("  删除本身失败必须补偿：重试队列 / 延迟双删 / CDC 订阅 binlog 驱动失效，不能吞异常。");
        RedisSupport.hr();
        RedisSupport.print("  不变量：不存在的结果也要有短暂占位；TTL 分布不能人为齐整；");
        RedisSupport.print("          同一热点的回源重建同一时刻最多一个。");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
