package com.zhiya.redis;

import com.zhiya.redis.RedisSupport;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Level 4：过期与淘汰——删除必须是设计，不是运气（过期篇）。
 * <p>
 * 对应层级：Level 4。
 * 演示主题：双字典 + 惰性班 + 抽查班两班倒。
 * 验证目标：任何读写先过 expireIfNeeded；僵尸 key 靠抽查班兜底；
 *           TTL 是内存契约不是业务闹钟；删除动作要传播（AOF/复制）。
 */
public final class RedisLevel4ExpirationDemo {

    private RedisLevel4ExpirationDemo() {
    }

    static class RedisSim {
        final Map<String, String> values = new HashMap<>(); // 键空间
        final Map<String, Long> expires = new HashMap<>();  // 过期时间戳(ms)
        long deletedLazy = 0, deletedActive = 0, propagated = 0;

        void set(String k, String v, long ttlMs) {
            values.put(k, v);
            if (ttlMs > 0) expires.put(k, System.currentTimeMillis() + ttlMs);
        }

        /** 惰性班：访问前必查 */
        String get(String k) {
            if (expireIfNeeded(k)) {
                return null;
            }
            return values.get(k);
        }

        private boolean expireIfNeeded(String k) {
            Long t = expires.get(k);
            if (t == null) return false;
            if (t <= System.currentTimeMillis()) {
                values.remove(k);
                expires.remove(k);
                deletedLazy++;
                propagated++;                       // 删除动作进 AOF + 复制流
                return true;
            }
            return false;
        }

        /** 抽查班：activeExpireCycle 的一轮（真实实现随机抽 ~20 个 + 时间预算 🔒） */
        int activeExpireCycle() {
            int sampled = 0, expired = 0;
            long budgetEnd = System.nanoTime() + 50_000;    // 时间预算（教学量级）
            while (System.nanoTime() < budgetEnd && !expires.isEmpty()) {
                String k = expires.keySet().iterator().next();
                sampled++;
                if (expires.get(k) <= System.currentTimeMillis()) {
                    values.remove(k);
                    expires.remove(k);
                    expired++;
                    deletedActive++;
                    propagated++;
                }
                if (sampled >= 20) break;                   // 每轮上限 ~20 个
            }
            return expired;
        }
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("Level 4 · 过期与淘汰：删除是设计，不是运气（过期篇）",
                "C 落了户——它和邻居们什么时候死、被谁埋？");

        RedisSupport.sec("① 为什么“每 key 一个定时器”与“纯惰性”各死一头");
        RedisSupport.table(
                new String[]{"朴素方案", "它想解决什么", "它留下的致命账"},
                List.of(new String[][]{
                        {"每 key 一个定时器准点删", "准时、内存零浪费", "定时器随 key 数膨胀；到期集中触发=定时风暴"},
                        {"纯惰性（访问才删）", "零维护成本", "僵尸 key 永不释放，内存慢泄漏到 OOM"},
                }));

        RedisSupport.sec("② 双字典结构");
        RedisSupport.print("  键空间 dict   key ──────────────▶ robj(value)");
        RedisSupport.print("  expires dict  key(同一指针,不双份存) ▶ 过期时间戳(ms)");
        RedisSupport.print("  惰性班：任何读写碰 key 前先 expireIfNeeded；已过期当场删除+传播 DEL，回复 nil。");

        RedisSupport.sec("③ 模拟：惰性班 + 抽查班两班倒");
        var r = new RedisSim();
        for (int i = 0; i < 5; i++) r.set("lazy:" + i, "v" + i, 5);       // 5ms 后过期
        for (int i = 0; i < 5; i++) r.set("active:" + i, "v" + i, 15);    // 15ms 后过期
        r.set("zombie:0", "z", 40);                                        // 永不被访问的僵尸
        r.set("forever:0", "x", 0);                                        // 永不过期

        sleep(7);
        System.out.println("  [t=7ms] lazy:* 已到过期点。客户端 GET lazy:2 → 惰性班当场删除、回复 nil：");
        System.out.println("          GET lazy:2 = " + r.get("lazy:2") + "（命中惰性班删除）");
        System.out.println("          GET lazy:3 = " + r.get("lazy:3") + "（同上）");
        System.out.println("          → 惰性班累计删除 " + r.deletedLazy + " 个，传播 DEL " + r.propagated + " 次");

        int rounds = 0;
        while (!r.expires.isEmpty() && rounds < 60) {
            r.activeExpireCycle();
            rounds++;
        }
        System.out.println("  [抽查班连跑 " + rounds + " 轮] 抽查班累计删除 " + r.deletedActive + " 个");
        System.out.println("  剩余键 = " + r.values.keySet());
        System.out.println("  → lazy:*、active:* 已清；zombie:0 直到被抽查班扫到才释放；forever:0 永驻。");

        RedisSupport.sec("④ TTL 是内存契约，不是业务闹钟");
        RedisSupport.print("  · 主库上读不到“已过期”的值：任何访问先过 expireIfNeeded；");
        RedisSupport.print("  · 但“TTL 到点 ≠ 立即释放”：过期 key 在被删除前仍占内存（惰性）；");
        RedisSupport.print("  · keyspace notification 的 expired 事件是删除时才发，不是到点就发；");
        RedisSupport.print("  · 需要“到点必执行”的事（关单/发券）→ 业务延迟队列 + 幂等，别拿过期当闹钟。");
        RedisSupport.print("  · 从库不删 key、只等主库 DEL——那是 Level 6 的墙钟判定，此处不预支。");

        RedisSupport.mantra("过期不追时，访问才扫地");
        RedisSupport.print("  昂贵且非紧急的工作：摊到触点上（惰性）+ 配一个后台预算化抽检（抽查班）。");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
