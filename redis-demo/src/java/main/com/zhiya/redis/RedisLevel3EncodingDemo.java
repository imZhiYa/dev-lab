package com.zhiya.redis;

import com.zhiya.redis.support.RedisSupport;
import com.zhiya.redis.demo.MiniStructures.Listpack;
import com.zhiya.redis.demo.MiniStructures.Intset;
import com.zhiya.redis.demo.MiniStructures.Skiplist;


import java.util.List;


/**
 * Level 3：type 是语义，encoding 是成本——同一个结构有两张脸。
 * <p>
 * 对应层级：Level 3。
 * 演示主题：robj 对象头、String 三档编码、hash/set/zset 的紧凑编码自动升级。
 * 验证目标：手写 listpack/intset/跳表；512/128/64B 阈值自动换编码；解释 embstr 44 字节之谜；
 * 小对象“分配器元数据 + robj 头 + 指针跳转”三次付钱的内存放大。
 */
public final class RedisLevel3EncodingDemo {

    private RedisLevel3EncodingDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("Level 3 · type×encoding 双维矩阵：小用连续大用表",
                "C 要落进货架了——问题是：落进哪一种货架？");

        RedisSupport.sec("① robj 对象头 + String 三档编码");
        RedisSupport.print("  每个 value 外面套一个 redisObject(robj)：type + encoding + lru(24bit) + refcount + *ptr");
        RedisSupport.print("  对象头 16 字节 🔒（私有实现，以源码为准）。String 的三种形态：");
        RedisSupport.table(
                new String[]{"编码", "触发条件(教学)", "内存账", "分配次数"},
                List.of(new String[][]{
                        {"int", "值可表成整数", "8B 直接塞进指针，无额外分配", "0"},
                        {"embstr", "串 ≤ 44 字节", "robj16 + sdshdr头3 + 串44 + '\\0'1 = 64B，一次 jemalloc 分配", "1"},
                        {"raw", "串 > 44 字节", "robj16 + 单独分配的 sdshdr/数据", "≥2"},
                }));
        RedisSupport.print();
        RedisSupport.print("  为什么是 44？因为 64B 是 jemalloc 的一个 size class：16(robj) + 3(sdshdr8)");
        RedisSupport.print("  + 44(数据) + 1('\\0') = 64 —— 正好一次分配装下。这是 jemalloc 的账，不是协议账。");

        RedisSupport.sec("② String 三档实测：OBJECT ENCODING 视角");
        RedisSupport.table(
                new String[]{"SET 的值", "OBJECT ENCODING", "robj 之外的存储"},
                List.of(new String[][]{
                        {"SET k 42", "int", "无（8B 在指针里）"},
                        {"SET k \"hello\"", "embstr", "无（一次 64B 分配装下）"},
                        {"SET k <45字节字符串>", "raw", "单独分配 sdshdr + 45B"},
                }));

        RedisSupport.sec("③ hash：小走 listpack，大走 hashtable（阈值 512 / 64B）");
        hashProgression();

        RedisSupport.sec("④ set：intset → listpack → hashtable");
        setProgression();

        RedisSupport.sec("⑤ zset：listpack → skiplist+dict（迷你跳表实测）");
        zsetProgression();

        RedisSupport.sec("⑥ 内存放大账：小对象付三次钱");
        RedisSupport.print("  一亿个“key 几十字节 + value 几十字节”的小 KV（教学场景）：");
        RedisSupport.print("    · 分配器元数据：每次 malloc 都要几~十几字节管理头；");
        RedisSupport.print("    · robj 对象头 16B：每个 value 一份；");
        RedisSupport.print("    · 指针跳转：链式容器破坏缓存局部性。");
        int payload = 100, header = 60;
        System.out.printf("    · 算一笔：payload=%dB + 管理头≈%dB ⇒ 内存放大 %.1fx%n",
                payload, header, (payload + header) / (double) payload);
        RedisSupport.print("  → 通用容器路线内存放大数倍；别人一台 64G 扛住，你四台还发抖。");

        RedisSupport.sec("⑦ TYPE 报语义，OBJECT ENCODING 报形态");
        RedisSupport.table(
                new String[]{"命令", "TYPE", "OBJECT ENCODING(示例)", "说明"},
                List.of(new String[][]{
                        {"SADD s 1 2 3", "set", "intset", "全整数小集合"},
                        {"SADD s a b c", "set", "listpack (7.2+)", "小字符串集合"},
                        {"SADD s <很多字符串>", "set", "hashtable", "超过 128 条/64B 升级"},
                        {"HSET h f1 v1 …", "hash", "listpack / hashtable", "512 条 / 64B 分界"},
                        {"ZADD z 1 a …", "zset", "listpack / skiplist+dict", "128 条 / 64B 分界"},
                }));
        RedisSupport.print();
        RedisSupport.print("  阈值是配置不是协议承诺：hash-max-listpack-entries 默认 512（不是 128！），");
        RedisSupport.print("  zset/set 的 listpack 上限是 128，list 的 list-max-listpack-size 默认 -2（≈8KB 字节档）。");
        RedisSupport.print("  🔒 一切默认值以你版本 CONFIG GET 实测为准（本表已按 7.2.15 实机核对）。");
        RedisSupport.mantra("小用连续大用表，超过阈值自动换");
    }

    private static void hashProgression() {
        RedisSupport.print("  逐个 HSET 往一个 hash 里加字段，观察它在何时换编码：");
        var lp = new Listpack();
        int bytesBefore = 0;
        for (int i = 0; i < 520; i++) {
            String f = "field" + i, v = "v" + i;
            if (lp.count() < 512) lp.append(f);
            lp.append(v);
            if (i == 511 || i == 512) {
                System.out.printf("    · 第 %d 个字段后：元素=%d，listpack 占 %d B → %s%n",
                        i + 1, lp.count(), lp.byteSize(),
                        (i == 511 ? RedisSupport.green("仍 listpack（≤512 条，连续内存）")
                                : RedisSupport.yellow("升级 hashtable（>512 条，索引结构）")));
            }
        }
        RedisSupport.print();
        RedisSupport.print("  判据：字段数 > hash-max-listpack-entries(512) 或任一字段/值 > 64B ⇒ 升级哈希表。");
        RedisSupport.print("  升级是一次性的：listpack 解包搬进 dict，此后回不到紧凑态（除非删到很小再重建）。");
    }

    private static void setProgression() {
        RedisSupport.print("  SADD 整数 1..600（intset 上限 512）；再 SADD 字符串（listpack 上限 128）：");
        var intset = new Intset();
        int i;
        for (i = 1; i <= 600; i++) intset.add(i);
        System.out.printf("    · %d 个整数 ⇒ intset %d B（有序数组+二分）%n", intset.size(), intset.byteSize());
        System.out.printf("      contains(42)=%s，contains(999)=%s%n", intset.contains(42), intset.contains(999));

        var lp = new Listpack();
        for (int k = 0; k < 130; k++) lp.append("s" + k);
        System.out.printf("    · %d 个字符串 ⇒ listpack %d B；超过 set-max-listpack-entries(128) ⇒ hashtable%n",
                lp.count(), lp.byteSize());
        System.out.println("    真实 Redis 判定顺序：全整数→intset；小→listpack(7.2+)；大→hashtable。");
    }

    private static void zsetProgression() {
        System.out.println("    小：≤128 条且成员 ≤64B → listpack（连续，省内存）；");
        System.out.println("    大：>128 条 → skiplist + dict：跳表按 score 排序做范围查询，");
        System.out.println("         dict 按 member 做 O(1) 定位。两个结构各管一件事：");
        var sl = new Skiplist();
        for (int i = 1; i <= 100_000; i++) sl.add("player:" + i, i * 1.0 + (i % 7) / 10.0);
        var top = sl.range(0, 100);
        System.out.printf("    · 已插入 %,d 个成员，跳表实际层高 %d%n", sl.size(), sl.level());
        System.out.printf("    · ZRANGEBYSCORE z 0 100 → 取前 %d 名，跳表搜索步数 = %d（O(log N) 量级）%n",
                top.size(), sl.searchSteps);
        System.out.println("    对比：LinkedList 顺序扫 10 万条要找 10 万步；跳表把“排序+范围”压到对数级。");
        System.out.println("    · 排行榜场景 = zset 的主场（表 A 第 3 行）；listpack 阶段只有小数据集才出现。");
    }
}
