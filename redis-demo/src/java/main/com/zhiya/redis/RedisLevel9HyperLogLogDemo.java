package com.zhiya.redis;

import com.zhiya.redis.support.RedisSupport;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Level 9 支线 B：HyperLogLog——不数人头，看水波。
 * <p>
 * 对应层级：Level 9。
 * 演示主题：16384 寄存器基数估计。
 * 验证目标：固定 12KB 换 ≈0.81% 可证误差（对照真实去重基数）；PFADD 幂等；PFMERGE 逐缸取 max；
 *           “今天 UV − 昨天 UV = 净增”是伪命题（只能并、不能减）。
 */
public final class RedisLevel9HyperLogLogDemo {

    private RedisLevel9HyperLogLogDemo() {
    }

    /** 迷你 HyperLogLog：P=14 → 16384 寄存器，6-bit 打包进 12KB */
    static class HyperLogLog {
        static final int P = 14;
        static final int M = 1 << P;
        static final double ALPHA = 0.7213 / (1 + 1.079 / M);
        final byte[] regs = new byte[M];          // 每个寄存器 0..63（最大 51，6-bit 够用）

        void pfadd(String elem) {
            long h = RedisSupport.hllHash(elem);
            int idx = (int) (h & (M - 1));        // 低位选缸
            long w = h >>> P;                     // 剩余 64-P = 50 位
            // 纹高 = 前导零个数 + 1（w 是 50 位值，Long.numberOfLeadingZeros 从 64 位看恒多 P 个前导零，减掉）
            int rho = Long.numberOfLeadingZeros(w) - P + 1;
            if (rho > (regs[idx] & 0x3F)) regs[idx] = (byte) rho;   // 只记最大纹高
        }

        long pfcount() {
            double sum = 0;
            int zeros = 0;
            for (byte r : regs) {
                int v = r & 0x3F;
                sum += 1.0 / (1L << v);
                if (v == 0) zeros++;
            }
            double E = ALPHA * M * M / sum;
            if (E <= 2.5 * M && zeros > 0) {      // 小基数：线性计数修正
                E = M * Math.log((double) M / zeros);
            }
            return Math.round(E);
        }

        /** PFMERGE：逐缸取 max —— 省的是“成本随基数增长”，不是“成本为零”（官方：O(N) 且常数因子高 🔒） */
        void pfmerge(HyperLogLog other) {
            for (int i = 0; i < M; i++) regs[i] = (byte) Math.max(regs[i] & 0x3F, other.regs[i] & 0x3F);
        }
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("Level 9 支线 B · HyperLogLog 的水波纹：12KB 装下亿级基数",
                "平行世界的 C：一次 UV 打卡");

        RedisSupport.sec("① 病案：Set 记亿级 UV = 内存线性膨胀");
        RedisSupport.print("    日活一亿的 App 用 Set 存当日 UV → 一个 key 一天奔着 GB 级去（教学量级，按你的 ID 长度实测）。");
        RedisSupport.print("    → 用 HLL：固定 ~12KB 🔒 换可证误差（≈0.81%），误差方向不可知但可量化。");

        RedisSupport.sec("② 实测：10 万个访问 → PFCOUNT 估计（用真实去重基数做裁判）");
        var hll = new HyperLogLog();
        Set<String> truth = new HashSet<>();
        Random rnd = new Random(42);
        for (int i = 0; i < 100_000; i++) {
            String u = "user:" + rnd.nextInt(1_000_000);
            hll.pfadd(u);
            truth.add(u);
        }
        long est = hll.pfcount();
        double errPct = Math.abs(est - truth.size()) * 100.0 / truth.size();
        System.out.printf("    PFADD 100,000 次 → 真实去重基数=%,d，PFCOUNT=%d，误差=%.2f%%（理论 σ≈0.81%%）%n",
                truth.size(), est, errPct);
        RedisSupport.require(errPct < 7.0, "HLL 单次估计误差必须在 7% 以内（理论 σ≈0.81%）");
        System.out.println("    内存：12,288 B 固定（16384 寄存器 × 6 bit），与基数无关。");

        RedisSupport.sec("③ 幂等：重复 PFADD 同一批元素，寄存器纹高不变");
        List<String> batch = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) batch.add("user:" + rnd.nextInt(1_000_000));
        var h2 = new HyperLogLog();
        for (String u : batch) h2.pfadd(u);
        long c1 = h2.pfcount();
        for (String u : batch) h2.pfadd(u);        // 同一批再来一遍
        long c2 = h2.pfcount();
        System.out.printf("    第一次 PFADD %d 个 → %d；重复加同一批 → %d（寄存器只记历史最大纹高 ⇒ 不变）✓%n",
                batch.size(), c1, c2);

        RedisSupport.sec("④ PFMERGE：周 UV = 日1..日7 逐缸取 max");
        var day1 = new HyperLogLog();
        var day2 = new HyperLogLog();
        Set<String> truthWeek = new HashSet<>();
        for (int i = 0; i < 60_000; i++) { String u = "u:" + rnd.nextInt(200_000); day1.pfadd(u); truthWeek.add(u); }
        for (int i = 0; i < 60_000; i++) { String u = "u:" + rnd.nextInt(200_000); day2.pfadd(u); truthWeek.add(u); }
        var week = new HyperLogLog();
        week.pfmerge(day1);
        week.pfmerge(day2);
        System.out.printf("    日1 估计=%d，日2 估计=%d，周合并估计=%d；真实并集=%,d，误差=%.2f%%%n",
                day1.pfcount(), day2.pfcount(), week.pfcount(), truthWeek.size(),
                Math.abs(week.pfcount() - truthWeek.size()) * 100.0 / truthWeek.size());
        System.out.println("    ⚠️ 代价：O(N) 合并 N 个 HLL，常数因子较高（每个来源过一遍 16384 个寄存器）。");

        RedisSupport.sec("⑤ 铁律：不能减 —— “今天 UV − 昨天 UV = 净增”是伪命题");
        // 两个互不相交、规模相同的集合：真实“净增”= 0，估计值之差却不为 0
        var hA = new HyperLogLog();
        var hB = new HyperLogLog();
        for (int i = 0; i < 100_000; i++) hA.pfadd("halfA:user:" + rnd.nextInt(1_000_000));
        for (int i = 0; i < 100_000; i++) hB.pfadd("halfB:user:" + rnd.nextInt(1_000_000));
        long eA = hA.pfcount(), eB = hB.pfcount();
        System.out.printf("    PFCOUNT(A)=%d，PFCOUNT(B)=%d，相减“净增”=%d —— 但 A、B 完全不相交，真实净增=0%n",
                eA, eB, eA - eB);
        System.out.println("    → 每个估计都偏 ±0.81% 且方向不可知 ⇒ 估计值之差没有数学意义。");
        System.out.println("    → 趋势/看板用 HLL（误差知情并公示）；精确对账口径用 Set/Bitmap/离线数仓。");
        RedisSupport.mantra("计数看水波：只能并，不能减");
    }
}
