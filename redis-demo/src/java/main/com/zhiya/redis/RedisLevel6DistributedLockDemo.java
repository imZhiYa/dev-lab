package com.zhiya.redis;

import com.zhiya.redis.support.RedisSupport;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 决策卡 6：分布式锁——锁 ≠ 互斥，终裁权在资源侧。
 * <p>
 * 对应层级：Level 6（决策卡 6）。
 * 演示主题：效率锁 / 正确性锁 / fencing token / WATCH+MULTI / RedLock 边界。
 * 验证目标：SETNX+EXPIRE 两行命令的死锁出厂设置；GC 停顿 35s 场景下旧票据被资源侧拒绝；
 *           unlock 返回 0 不得重试 DEL；WATCH 让 Redis 自己拒收旧账。
 */
public final class RedisLevel6DistributedLockDemo {

    private RedisLevel6DistributedLockDemo() {
    }

    /** 模拟 Redis：一把带 TTL 的锁 */
    static class LockStore {
        String key, token;
        long expireAt;
        final AtomicLong fenceCounter = new AtomicLong();   // INCR fence:xxx 票据源

        /** 上锁：SET lock token NX PX ttl */
        boolean tryLock(String k, String t, long ttlMs) {
            long now = System.currentTimeMillis();
            if (key != null && now < expireAt) return false;      // NX：已有人持锁且未过期
            key = k; token = t; expireAt = now + ttlMs;
            return true;
        }

        /** 解锁 Lua：GET==token 才 DEL */
        boolean unlock(String k, String t) {
            if (k.equals(key) && t.equals(token)) {
                key = null; token = null;
                return true;
            }
            return false;   // token 不匹配：锁已易主，不要重试 DEL
        }
    }

    /** 被保护的资源：维护 max_seen（fencing） */
    static class Resource {
        long maxSeen = 0;
        long accepted = 0, rejected = 0;
        String lastWriter = "";

        boolean write(String actor, long token) {
            if (token > maxSeen) {               // 只认最新票据
                maxSeen = token;
                accepted++;
                lastWriter = actor;
                return true;
            }
            rejected++;                          // 旧票据：拒绝
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("决策卡 6 · 分布式锁：锁 ≠ 互斥，终裁权在资源侧",
                "“每个环节都对”照样击穿互斥 —— 那 35 秒的 GC 停顿");

        RedisSupport.sec("① 坑 3：SETNX + EXPIRE 两条命令 = 死锁出厂设置");
        RedisSupport.print("    SETNX lock 1 然后 EXPIRE lock 30 —— 进程在两行之间崩了：");
        RedisSupport.err("    → 锁永久存在，业务永远卡在某把锁上，重启才能发现（寄存柜钥匙发了，期限纸条没写）。");
        RedisSupport.print("    修正：SET lock token NX PX 30000 原子一把梭。");

        RedisSupport.sec("② 正确性锁的完整时序：fencing token 救场");
        RedisSupport.print("    场景：C1 持锁写资源；C1 GC 停顿 35s（TTL 只有 30s）；锁到期；C2 获锁写资源；C1 苏醒继续写。");
        var lock = new LockStore();
        var r = new Resource();

        // t0: C1 获锁（票据 33）
        long t1 = 33;
        lock.tryLock("lock:order:42", "c1", 30_000);
        System.out.printf("    [t0] C1 获锁，票据=fence:%d（INCR 自增）；向资源写……%n", t1);
        System.out.printf("         资源侧 max_seen=33 → 接受（C1 的写 #%d）%n", ++r.maxSeen);

        // t1: GC 35s（模拟进程被冻结）
        RedisSupport.warn("    [t1] C1 stop-the-world 35s —— 没有任何告警，对 C1 而言世界只是快进了一帧");
        RedisSupport.warn("    [t2] 锁到期自然释放（这是特性不是 bug）；C2 获锁，票据 34，写资源 → 接受");
        long t2 = 34;
        lock.tryLock("lock:order:42", "c2", 30_000);
        boolean ok2 = r.write("C2", t2);
        System.out.printf("         资源侧 max_seen=%d，C2(票据%d) → %s%n", r.maxSeen, t2, ok2 ? "接受" : "拒绝");

        // t3: C1 苏醒，记忆里仍“我持锁”，写资源（票据 33）
        RedisSupport.warn("    [t3] C1 苏醒，记忆里仍“我持锁”，继续写资源（携带票据 33）");
        boolean ok1 = r.write("C1", t1);
        if (!ok1) {
            RedisSupport.ok("         → 资源侧 max_seen=34 > 33 ⇒ 拒绝旧票据 ✓ 互斥被 fencing 守住");
            RedisSupport.print("         没有 fencing 时：两条写都被接受 → 双写窗口（自测 #16：哪一环都没错，");
            RedisSupport.print("         lease 与业务耗时是两个互不知情的时钟，终裁必须移交资源侧）");
        } else {
            RedisSupport.err("         两条写都被接受 → 互斥击穿 ✗");
        }
        System.out.printf("         资源统计：接受=%d，拒绝=%d（fencing_rejected_total 演练时必须 >0 才证明 fencing 活着）%n",
                r.accepted, r.rejected);
        RedisSupport.require(r.rejected > 0, "旧票据必须被资源侧拒绝，fencing 才生效");

        RedisSupport.sec("③ 解锁必须 Lua 比对 token：unlock 返回 0 不要重试 DEL");
        boolean wrong = lock.unlock("lock:order:42", "c1");   // C1 用旧 token 解
        RedisSupport.print("    C1 用旧 token 解锁 → " + wrong + "（锁已易主，不匹配）");
        RedisSupport.print("    → 返回 0 说明锁可能已易主，重试 DEL 会误删别人的锁：走幂等/补偿，不重试。");

        RedisSupport.sec("④ WATCH + MULTI：数据全在 Redis 界内时的原生乐观锁");
        watchCas();

        RedisSupport.sec("⑤ RedLock 的本质与边界（2016 年公开辩论沉淀）");
        RedisSupport.print("    RedLock = 把 quorum 交集借到锁上：N=5 独立实例，拿到 N/2+1 才算持锁；");
        RedisSupport.print("    有效余量 MIN_VALIDITY = TTL − 获锁耗时 − 时钟漂移修正 🔒（官方 DLW 页公式）。");
        RedisSupport.print("    官方两条工程建议原文照录：① “You should implement fencing tokens.”（适用于任何");
        RedisSupport.print("    分布式锁）；② “Redis is not using monotonic clock for TTL expiration”（墙钟跳变可让锁");
        RedisSupport.print("    被多进程同时持有）。");
        RedisSupport.print("    结论不是谁赢：而是暴露边界——锁 ≠ 互斥。");

        RedisSupport.sec("⑥ 选型一句话");
        RedisSupport.table(
                new String[]{"场景", "做法"},
                List.of(new String[][]{
                        {"效率锁（防重复工作，重复只是浪费）", "单实例 SET NX PX 足矣，别上 RedLock 白交多数派写的税"},
                        {"正确性锁（并发写会坏数据）", "锁只做加速层，fencing token / DB 版本号 / 唯一约束终裁"},
                        {"要“确认即互斥”且不背 fencing", "etcd/ZooKeeper（原生单调 revision/zxid），或 DB 约束"},
                        {"最好的分布式锁", "常常是不用锁（任务队列 + 幂等）"},
                }));
        RedisSupport.mantra("锁只能回答“锁还在不在有效期”，回答不了“你的写还是不是最新的合法写”");
    }

    /** WATCH + MULTI 的 CAS 语义模拟 */
    private static void watchCas() {
        Map<String, Long> store = new HashMap<>();     // key -> version
        Map<String, String> value = new HashMap<>();
        value.put("stock", "10");
        store.put("stock", 0L);

        // 模拟：WATCH stock → 读旧值 → 另一个客户端改了它 → EXEC 拒绝
        long watchedVersion = store.get("stock");      // WATCH
        String cur = value.get("stock");               // GET
        store.put("stock", store.get("stock") + 1);    // 第三方改动（WATCH 失效）
        long versionAtExec = store.get("stock");       // EXEC 时比对
        if (versionAtExec != watchedVersion) {
            RedisSupport.warn("    WATCH stock → GET=10 → [期间被第三方改了] → EXEC → 返回 nil（整组拒绝），客户端重试");
        } else {
            RedisSupport.print("    WATCH stock → 无人动过 → EXEC 提交成功");
        }
        RedisSupport.print("    CAS 语义 = “确认无人动过”才提交：正好补齐 MULTI 无分支的短板（分支判断放客户端）。");
        RedisSupport.print("    一句话记忆：fencing 是让别人认账，WATCH 是让 Redis 自己拒收旧账。");
    }
}
