package com.zhiya.sharding.core;

/**
 * 纯逻辑冒烟（CI 公审用，零中间件依赖，几秒完成）
 *
 * 验证与知识库文章的对应断言：
 *  - 搬迁比例 gcd 公式（shard-04 L2）：迁移比例 = 1 - gcd(N,M)/max(N,M)
 *  - 同余不迁移判据：x mod N = x mod M ⟺ x ≡ r (mod lcm)，r ∈ [0, min(N,M))
 *  - 路由片数模拟（shard-02 L3）：带分片键 → 单片；无分片键 → 广播全片
 *  - 翻页下推形态（shard-03 L3）：offset 翻页下推 offset+limit；keyset 下推 limit
 *
 * 输出约定：末尾一行 "通过 N / 失败 0"，verify 脚本按此断言
 */
public final class ShardingSmokeApp {

    private static int pass = 0;
    private static int fail = 0;

    private static void expect(boolean cond, String name) {
        if (cond) {
            pass++;
        } else {
            fail++;
            System.out.println("  ❌ " + name);
        }
    }

    // ---- 搬迁比例（shard-04 L2）----

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    /** 迁移比例 = 1 - gcd(N,M)/max(N,M)（N 旧片数、M 新片数） */
    private static double migrationRatio(int oldShards, int newShards) {
        return 1.0 - (double) gcd(oldShards, newShards) / Math.max(oldShards, newShards);
    }

    /** 实测迁移比例：遍历样本用户逐个比较新旧路由（2 的幂对 2 的幂） */
    private static double migrationMeasured(int oldShards, int newShards, int samples) {
        int moved = 0;
        for (int x = 0; x < samples; x++) {
            if (x % oldShards != x % newShards) {
                moved++;
            }
        }
        return (double) moved / samples;
    }

    /** 不迁移 ⟺ x mod N = x mod M ⟺ x mod M < N（当 N | M，即 M 是 N 的整数倍） */
    private static boolean staysPut(int x, int oldShards, int newShards) {
        return x % newShards < oldShards;
    }

    // ---- 路由模拟（shard-02 L3）----

    private static int[] routePlan(long orderId, int shardCount, boolean hasShardKey) {
        if (!hasShardKey) {
            int[] all = new int[shardCount];
            for (int i = 0; i < shardCount; i++) {
                all[i] = i;
            }
            return all;
        }
        return new int[]{(int) (orderId % shardCount)};
    }

    // ---- 翻页下推（shard-03 L3）----

    /** offset 翻页：每片下推 limit offset+limit（内存归并模式）；keyset：下推 limit N */
    private static long pushedRows(long offset, long limit, boolean keyset, int shardCount) {
        return keyset ? limit * shardCount : (offset + limit) * shardCount;
    }

    public static void main(String[] args) {
        System.out.println("========== ShardingSmokeApp：纯逻辑冒烟 ==========");

        System.out.println("--- [1] 搬迁比例 gcd 公式（shard-04 L2）---");
        expect(migrationRatio(8, 16) == 0.5, "8→16 公式迁移比例 = 1/2");
        expect(migrationRatio(8, 32) == 0.75, "8→32 公式迁移比例 = 3/4");
        expect(Math.abs(migrationRatio(8, 9) - 8.0 / 9.0) < 1e-9, "8→9 公式迁移比例 = 8/9（gcd=1，lcm=72，仅 8 个余数类不动）");
        expect(Math.abs(migrationMeasured(8, 16, 100000) - 0.5) < 0.005, "8→16 实测 ≈ 1/2");
        expect(Math.abs(migrationMeasured(8, 32, 100000) - 0.75) < 0.005, "8→32 实测 ≈ 3/4");

        System.out.println("--- [2] 同余不迁移判据（x mod M < N ⟺ 不动）---");
        boolean congruentOk = true;
        for (int x = 0; x < 10000; x++) {
            for (int[] pair : new int[][]{{8, 16}, {8, 32}}) {
                if (staysPut(x, pair[0], pair[1]) != (x % pair[0] == x % pair[1])) {
                    congruentOk = false;
                }
            }
        }
        expect(congruentOk, "x mod N = x mod M ⟺ x mod M < N（N | M，全样本一致）");

        System.out.println("--- [3] 路由片数（shard-02 L3）---");
        expect(routePlan(42, 8, true).length == 1, "带分片键 → 路由 1 片");
        expect(routePlan(42, 8, false).length == 8, "无分片键 → 广播 8 片");
        expect(routePlan(42, 8, true)[0] == 42 % 8, "带键路由片号 = order_id % 8（42 → 片 2）");

        System.out.println("--- [4] 翻页下推形态（shard-03 L3）---");
        expect(pushedRows(1000, 20, false, 8) == 8160, "offset=1000 下推 = 8×(1000+20)");
        expect(pushedRows(0, 20, true, 8) == 160, "keyset 下推 = 8×20（不随深度放大）");

        System.out.println();
        System.out.printf("通过 %d / 失败 %d%n", pass, fail);
        if (fail > 0) {
            System.exit(1);
        }
    }
}