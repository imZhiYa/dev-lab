package com.zhiya.redis;

import com.zhiya.redis.RedisSupport;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Level 4：过期与淘汰——删除必须是设计，不是运气（淘汰篇）。
 * <p>
 * 对应层级：Level 4。
 * 演示主题：maxmemory 保险丝与 8 种淘汰策略。
 * 验证目标：allkeys-* / volatile-* / noeviction 的可淘汰域；近似 LRU 的采样误差实测
 *           （maxmemory-samples 越大淘汰越接近全球最冷）；noeviction 让写失败报警而不是偷偷丢数据。
 */
public final class RedisLevel4EvictionDemo {

    private RedisLevel4EvictionDemo() {
    }

    enum Policy { NOEVICTION, ALLKEYS_LRU, ALLKEYS_LFU, ALLKEYS_RANDOM, VOLATILE_LRU, VOLATILE_LFU, VOLATILE_RANDOM, VOLATILE_TTL }

    static class Key {
        String name;
        long lastAccess;       // 时间戳（越大越新）
        long freq;             // 访问频率（LFU）
        long ttl;              // 过期时间戳，0=永不过期
        Key(String n, long l, long f, long t) { name = n; lastAccess = l; freq = f; ttl = t; }
    }

    static class Store {
        final Map<String, Key> keys = new HashMap<>();
        long maxmemory;
        Policy policy;
        int samples = 5;
        long now = 0;
        String lastEvicted = null;   // 教学：记录上一轮被淘汰的 key

        Store(long mm, Policy p) { maxmemory = mm; policy = p; }

        long used() { return keys.size() * 100L; }   // 教学：每个 key 折 100 字节

        String get(String k) {
            Key x = keys.get(k);
            if (x == null) return null;
            x.lastAccess = now;
            x.freq++;
            return "v";
        }

        String set(String k) {
            now++;
            if (used() + 100 > maxmemory) {
                long freed = performEvictions();
                if (freed <= 0) {
                    RedisSupport.err("      ! OOM error（写拒绝）：maxmemory 已满且 policy=" + policy + " 无可淘汰");
                    return "ERR OOM";
                }
            }
            Key old = keys.get(k);
            keys.put(k, new Key(k, now, 1, old != null ? old.ttl : 0));
            return "OK";
        }

        /** 返回释放的字节数；0 = 没得可删 */
        long performEvictions() {
            lastEvicted = null;
            List<Key> candidates = new ArrayList<>();
            for (Key k : keys.values()) {
                boolean eligible = switch (policy) {
                    case ALLKEYS_LRU, ALLKEYS_LFU, ALLKEYS_RANDOM -> true;
                    case VOLATILE_LRU, VOLATILE_LFU, VOLATILE_RANDOM, VOLATILE_TTL -> k.ttl > 0;
                    case NOEVICTION -> false;
                };
                if (eligible) candidates.add(k);
            }
            if (candidates.isEmpty()) return 0;

            // 随机抽样 samples 个（教学：真实 Redis 有采样池淘汰策略，这是简化版）
            Random rnd = new Random(7);
            List<Key> pool = new ArrayList<>();
            for (int i = 0; i < Math.min(samples, candidates.size()); i++) {
                pool.add(candidates.get(rnd.nextInt(candidates.size())));
            }
            Key victim = switch (policy) {
                case ALLKEYS_LRU, VOLATILE_LRU -> pool.stream().min((a, b) -> Long.compare(a.lastAccess, b.lastAccess)).orElseThrow();
                case ALLKEYS_LFU, VOLATILE_LFU -> pool.stream().min((a, b) -> Long.compare(a.freq, b.freq)).orElseThrow();
                case ALLKEYS_RANDOM, VOLATILE_RANDOM -> pool.get(0);
                case VOLATILE_TTL -> pool.stream().min((a, b) -> Long.compare(a.ttl, b.ttl)).orElseThrow();
                default -> throw new IllegalStateException();
            };
            keys.remove(victim.name);
            lastEvicted = victim.name;
            return 100;
        }
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("Level 4 · 过期与淘汰：删除是设计，不是运气（淘汰篇）",
                "内存撞线的保险丝：8 种 policy，LRU/LFU 是抽样近似");

        RedisSupport.sec("① 8 种策略一表看懂");
        RedisSupport.table(
                new String[]{"策略", "可淘汰对象", "选择逻辑"},
                List.of(new String[][]{
                        {"noeviction", "无", "写失败回 OOM（特性不是故障）"},
                        {"allkeys-lru", "全部 key", "抽样里选最久未访问"},
                        {"volatile-lru", "仅带 TTL 的 key", "抽样里选最久未访问"},
                        {"allkeys-lfu", "全部 key", "抽样里选访问最少（4.0+）"},
                        {"volatile-lfu", "仅带 TTL 的 key", "抽样里选访问最少（4.0+）"},
                        {"allkeys-random", "全部 key", "随机"},
                        {"volatile-random", "仅带 TTL 的 key", "随机"},
                        {"volatile-ttl", "仅带 TTL 的 key", "抽样里选剩余 TTL 最短"},
                }));
        RedisSupport.print("  ⚠️ volatile-* 的暗坑：若大多数 key 根本没 TTL → 无可淘汰 ≈ noeviction 假象（坑 4）。");

        RedisSupport.sec("② 近似 LRU vs 精确 LRU：采样误差实测");
        RedisSupport.print("  构造 1 万个 key，其中 100 个“热 key”反复访问，其余只访问一次；");
        RedisSupport.print("  然后连续淘汰 100 次，统计“被淘汰 key 的平均冷度百分位”（0=全球最冷，100=全球最热）：");
        RedisSupport.print("  近似 LRU = 每次从 samples 个随机候选里挑最冷 —— 没有全局有序链表：");

        for (int samples : new int[]{1, 5, 20}) {
            var s = new Store(10_000 * 100, Policy.ALLKEYS_LRU);
            s.samples = samples;
            Random rnd = new Random(1);
            for (int i = 0; i < 10_000; i++) s.set("k:" + i);
            for (int i = 0; i < 500; i++) s.get("k:" + (rnd.nextInt(100)));   // 打热 100 个
            List<Key> sorted = s.keys.values().stream()
                    .sorted((a, b) -> Long.compare(a.lastAccess, b.lastAccess)).toList();
            Map<String, Integer> rank = new HashMap<>();
            for (int i = 0; i < sorted.size(); i++) rank.put(sorted.get(i).name, i);
            double avgPct = 0;
            for (int e = 0; e < 100; e++) {
                s.lastEvicted = null;
                s.set("evict:" + e);                       // 每次淘汰一个
                int r = rank.getOrDefault(s.lastEvicted, 0);
                avgPct += r / (double) sorted.size() * 100;
            }
            avgPct /= 100;
            System.out.printf("    · maxmemory-samples=%-3d → 被淘汰 key 平均冷度百分位 = %5.1f%%（精确 LRU 应为 0.x%%）%n",
                    samples, avgPct);
        }
        RedisSupport.print("  → samples 调大更准但更费 CPU（数学上 = 取 min 的抽样数越大越接近全局最冷）；");
        RedisSupport.print("    真实 Redis 还用 16 槽持久采样池（🔒 私有）进一步逼近。近似 LRU 买的是");
        RedisSupport.print("    “去掉全局有序链表”，代价是淘汰不完美——误差要证明不伤业务再上线。");

        RedisSupport.sec("③ noeviction：让写失败报警，而不是偷偷丢数据");
        var s2 = new Store(5 * 100, Policy.NOEVICTION);
        for (int i = 0; i < 5; i++) s2.set("k:" + i);
        String ret = s2.set("k:overflow");
        RedisSupport.print("  maxmemory=500B，写满 5 个 key 后再写 → " + ret);
        RedisSupport.print("  这是特性：持久语义的数据宁可拒写，也不可偷偷淘汰。");

        RedisSupport.sec("④ 决策准则（决策卡 2）");
        RedisSupport.table(
                new String[]{"业务性质", "推荐策略", "理由"},
                List.of(new String[][]{
                        {"纯缓存（可从 DB 重建）", "allkeys-lru / allkeys-lfu", "允许淘汰，重建成本可接受"},
                        {"持久语义（订单/计数）", "noeviction", "宁可写失败报警，不可偷偷丢"},
                        {"混合但 TTL 覆盖全", "volatile-*", "只淘汰临时的，保永久的"},
                }));
        RedisSupport.print("  验收：used_memory 稳低于 maxmemory；evicted_keys 入监控，涨太快按预期淘汰排查，");
        RedisSupport.print("  不要直接当“Redis 丢数据”——前提是缓存确实可从 DB 重建。");
    }
}
