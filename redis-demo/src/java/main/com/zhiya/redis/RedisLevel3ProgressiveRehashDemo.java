package com.zhiya.redis;

import com.zhiya.redis.support.RedisSupport;


import java.util.List;

/**
 * Level 3 补充：渐进式 rehash——店长永不停业。
 * <p>
 * 对应层级：Level 3。
 * 演示主题：哈希表扩容如何“分两次搬家”。
 * 验证目标：新旧两张桶数组同时在岗、每次增删查顺手搬一个非空桶、查找先查新桌再查旧桌；
 * 把秒级全量搬迁摊成亿次常数小搬运。
 */
public final class RedisLevel3ProgressiveRehashDemo {

    private RedisLevel3ProgressiveRehashDemo() {
    }

    /**
     * 模拟一个 dict：两张桶数组 + 进度指针
     */
    static class Dict {
        String[][] ht0, ht1;
        int rehashidx = -1;      // -1 = 不在 rehash；>=0 = 下一个要搬的桶
        int size0, size1;
        int opsDuringRehash = 0; // 教学：rehash 期间的业务操作数

        Dict(int cap) {
            ht0 = new String[cap][];
            size0 = cap;
        }


        void set(String k, String v) {
            int idx0 = index(k, size0);
            if (rehashidx >= 0) {                    // 扩容中：每写一次顺手搬一个非空桶
                step();
                opsDuringRehash++;
            }
            if (rehashidx >= 0) {
                idx0 = index(k, size0);              // 可能已搬过，重算
                int idx1 = index(k, size1);
                if (ht1[idx1] != null && ht1[idx1][0].equals(k)) {
                    ht1[idx1][1] = v;
                    return;
                }
                if (ht0[idx0] != null && ht0[idx0][0].equals(k)) {
                    ht0[idx0][1] = v;
                    return;
                }
                if (ht1[idx1] == null) ht1[idx1] = new String[]{k, v};
                else ht0[idx0] = new String[]{k, v};   // 简化：新 key 落旧桌（真实 dict 落新桌）
            } else {
                if (ht0[idx0] == null) ht0[idx0] = new String[]{k, v};
                else ht0[idx0][1] = v;
            }
            maybeStartRehash();
        }

        String get(String k) {
            if (rehashidx >= 0) {
                step();
                opsDuringRehash++;
            }
            int i0 = index(k, size0);
            if (rehashidx < 0) {
                return ht0[i0] != null && ht0[i0][0].equals(k) ? ht0[i0][1] : null;
            }
            // 先查新桌，再查旧桌（真实 dict 也是两桌都查）
            int i1 = index(k, size1);
            if (ht1[i1] != null && ht1[i1][0].equals(k)) return ht1[i1][1];
            if (ht0[i0] != null && ht0[i0][0].equals(k)) return ht0[i0][1];
            return null;
        }

        private int index(String k, int cap) {
            return Math.floorMod(k.hashCode(), cap);
        }

        /**
         * 负载因子 >= 1 触发扩容（简化：直接翻倍）
         */
        private void maybeStartRehash() {
            if (rehashidx >= 0) return;
            int used = 0;
            for (String[] b : ht0) if (b != null) used++;
            if (used >= size0) {
                ht1 = new String[size0 * 2][];
                size1 = size0 * 2;
                rehashidx = 0;
                System.out.printf("      → 触发扩容：ht[0]=%d 桶已满 %d，新开 ht[1]=%d 桶，rehashidx=0%n",
                        size0, used, size1);
            }
        }

        /**
         * 搬一个非空桶（渐进式的一步）
         */
        private void step() {
            if (rehashidx < 0) return;
            while (rehashidx < size0) {
                if (ht0[rehashidx] != null) {
                    String[] kv = ht0[rehashidx];
                    ht1[index(kv[0], size1)] = kv;
                    ht0[rehashidx] = null;
                    rehashidx++;
                    return;                          // 本次只搬一个
                }
                rehashidx++;
            }
            ht0 = ht1;                               // 搬完：旧桌退役
            size0 = size1;
            ht1 = null;
            rehashidx = -1;
            System.out.printf("      ✓ 渐进式 rehash 完成：ht[0] 退役，现在单桌 %d 桶%n", size0);
        }

        boolean inRehash() {
            return rehashidx >= 0;
        }
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("Level 3 补充 · 渐进式 rehash：店长永不停业",
                "哈希表扩容要翻倍搬桶——单线程怎么敢在运行时扩？");

        RedisSupport.print("  答案：分两次搬家。新旧两张桌同时在岗，每次增删查顺手搬一个非空桶，");
        RedisSupport.print("  查找先查旧桌再查新桌。代价：查找多做一步；收益：把秒级搬迁摊成亿次常数小搬运。");

        RedisSupport.sec("① 逐操作演示：插入触发扩容，每写一次搬一个桶");
        var d = new Dict(4);
        for (int i = 0; i < 8; i++) d.set("k" + i, "v" + i);
        System.out.println();
        System.out.println("  继续写，观察 rehash 期间新旧两桌同时在岗（每写一次顺手搬一个非空桶）：");
        for (int i = 8; i < 14; i++) {
            boolean busy = d.inRehash();
            System.out.printf("      SET k%d … → %s%n", i,
                    busy ? RedisSupport.cyan("顺手搬一个非空桶（rehashidx 前进），累计业务操作 " + (d.opsDuringRehash + 1) + " 次")
                            : "常态写入（无搬迁）");
            d.set("k" + i, "v" + i);
        }
        System.out.printf("      GET k3 = %s（rehash 期间也能查：先新桌后旧桌）%n", d.get("k3"));

        RedisSupport.sec("② 账：一次全量搬迁 vs 摊到每次操作");
        RedisSupport.table(
                new String[]{"方案", "旧桌→新桌搬家成本", "事件循环影响", "本质"},
                List.of(new String[][]{
                        {"全量 rehash（朴素）", "一次搬光 N 个桶", "秒级停业，所有连接一起超时", "O(N) 在单线程里裸奔"},
                        {"渐进式 rehash（Redis）", "每次操作搬 1 个非空桶", "无感知，均摊 O(1)", "把大搬迁摊成小碎步"},
                }));
        RedisSupport.print();
        RedisSupport.print("  🔒 私有实现细节以 dict.c 为准（如 activerehashing 后台限时帮忙、缩容阈值等）。");
        RedisSupport.print("  同款思想在 Level 7 集群版重演：slot 迁移也是“摊到每次操作/每把 key”");
        RedisSupport.mantra("一切必须在运行时完成的扩容/迁移，都该问：能不能摊到每次操作上？");
    }
}
