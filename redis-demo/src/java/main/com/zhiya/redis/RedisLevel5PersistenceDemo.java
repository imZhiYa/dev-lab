package com.zhiya.redis;

import com.zhiya.redis.RedisSupport;


import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Random;

/**
 * Level 5：RDB 与 AOF——“丢多少”与“停多久”的选型题。
 * <p>
 * 对应层级：Level 5。
 * 演示主题：fork + COW 快照与顺序追加 + 周期 fsync 日志。
 * 验证目标：页表级 COW 模拟（fork 复制引用、写时复制、THP 4K→2M 写放大）；fsync 的墙；
 *           everysec 的诚实语义；RPO/RTO 先写数字再谈配置。
 */
public final class RedisLevel5PersistenceDemo {

    private RedisLevel5PersistenceDemo() {
    }

    /** COW 内存模拟：pageSize 页 + 页表（引用数组）+ 每页归属标记 */
    static class CowMemory {
        final int pageSize;
        final byte[][] pages;          // 页表：每格指向一块物理页
        final boolean[] owned;         // 本进程是否独占该物理页
        int copyOnWriteCount = 0;
        long copyBytes = 0;

        CowMemory(int pages, int pageSize) {
            this.pageSize = pageSize;
            this.pages = new byte[pages][];
            this.owned = new boolean[pages];
            java.util.Arrays.fill(this.owned, true);
            for (int i = 0; i < pages; i++) this.pages[i] = new byte[pageSize];
        }

        /** fork：只复制页表（引用数组），物理页父子共享，双方都不再独占 */
        CowMemory fork() {
            CowMemory child = new CowMemory(pages.length, pageSize);
            System.arraycopy(pages, 0, child.pages, 0, pages.length);
            java.util.Arrays.fill(child.owned, false);
            java.util.Arrays.fill(this.owned, false);
            return child;
        }

        /** 写一个字节：若该页当前共享（不属于本进程独占），先复制这一页再写（COW） */
        void writeByte(int pageIdx) {
            if (!owned[pageIdx]) {
                pages[pageIdx] = pages[pageIdx].clone();   // 只复制这一页
                owned[pageIdx] = true;
                copyOnWriteCount++;
                copyBytes += pageSize;
            }
            pages[pageIdx][0]++;
        }
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("Level 5 · RDB 与 AOF：持久化是“丢多少”与“停多久”的选型题",
                "C 已生效，现在给它上账——上哪本账，是本层的仗");

        RedisSupport.sec("① 两个朴素方案各死一头");
        RedisSupport.table(
                new String[]{"朴素方案", "它想解决什么", "它留下的致命账"},
                List.of(new String[][]{
                        {"每命令同步 fsync(appendfsync always 极端)", "写确认=已落盘", "延迟地板回到微秒~毫秒级，吞吐退回磁盘量级"},
                        {"只按时全量快照(裸 RDB)", "平时零成本，恢复简单", "RPO=快照间隔，宕机丢一长段；大实例 fork 本身是毛刺源"},
                }));

        RedisSupport.sec("② fork + COW：为什么 fork 不会让内存翻倍");
        cowDemo();

        RedisSupport.sec("③ AOF：顺序追加 + 周期 fsync（fsync 的墙实测）");
        fsyncWall();

        RedisSupport.sec("④ everysec 的诚实语义");
        RedisSupport.print("  · everysec = “目标最多丢约 1 秒”，不是严格每秒刷；");
        RedisSupport.print("  · 若上一次 fsync 卡在磁盘没回来，下一次 write 会被拖延（上限秒级 🔒），");
        RedisSupport.print("    并计入 aof_delayed_fsync —— 磁盘抖，Redis 只能陪着抖。");
        RedisSupport.print("  · 数据流动：aof_buf → write()→内核 page cache ←(此刻掉电仍丢)→ bio 线程 fsync()→磁盘。");

        RedisSupport.sec("⑤ 先写两个数字，再谈配置（决策卡 3）");
        RedisSupport.print("  评审第一行永远是：最多丢几秒（RPO）、最多停几分钟（RTO）。");
        RedisSupport.table(
                new String[]{"配置组合", "RPO", "RTO", "适用"},
                List.of(new String[][]{
                        {"appendonly yes + everysec", "约 1s（目标值）", "快（混合 AOF）", "多数业务"},
                        {"appendonly no + save \"900 1\"", "最多 15 分钟", "取决于 RDB 大小", "纯缓存可重建"},
                        {"appendfsync always", "≈ 0", "快", "极少数强语义接口"},
                        {"什么都不配", "进程死=全丢", "重启即空库", "赌街区不停电（坑 7）"},
                }));
        RedisSupport.print("  前置条件成对核查：THP 关闭或 madvise；vm.overcommit_memory=1（=0 时内存吃紧 fork 直接失败：");
        RedisSupport.print("  Can't save in background: fork: Cannot allocate memory）。");
        RedisSupport.mantra("快照看 fork，日志看 fsync");
    }

    // ---------- COW ----------
    private static void cowDemo() {
        int PAGES = 10_000;
        int page4K = 4 * 1024;

        CowMemory m = new CowMemory(PAGES, page4K);
        for (int i = 0; i < PAGES; i++) m.writeByte(i);   // 先填满（fork 前，无 COW）

        long t0 = System.nanoTime();
        CowMemory child = m.fork();                        // 子进程：开始写 RDB 快照
        long forkUs = (System.nanoTime() - t0) / 1_000;
        System.out.printf("    fork() 耗时 ≈ %d µs（页表=%,d 个引用=%.1f MB 的复制；这就是 latest_fork_usec 的量级）%n",
                forkUs, PAGES, PAGES * 8.0 / 1024 / 1024);

        System.out.println("    fork 后：子进程读到的是冻结视图（共享物理页），父进程照常营业。");
        int writePages = 1_000;                            // 快照期间父进程写了 1000 页
        Random rnd = new Random(5);
        for (int i = 0; i < writePages; i++) m.writeByte(rnd.nextInt(PAGES));

        long extra4K = m.copyBytes;
        System.out.printf("    BGSAVE 期间父进程写 %d 页 → COW 复制 %d 页，额外内存 = %,d KB（%d×4K）%n",
                writePages, m.copyOnWriteCount, extra4K / 1024, m.copyOnWriteCount);

        // THP：粒度 4K → 2M
        long extraTHP = m.copyOnWriteCount * (2L * 1024 * 1024);
        System.out.printf("    若开着 THP（COW 粒度 4K→2M）：同样写 %d 个字节位置 → 额外内存 ≈ %,d MB（写放大 %.0f×）%n",
                writePages, extraTHP / 1024 / 1024, extraTHP / (double) Math.max(extra4K, 1));
        System.out.println("    → 生产建议：关闭 THP 或设 madvise；BGSAVE 与业务高峰错峰。");
        System.out.println("    → 快照期间写流量越猛，额外内存占用越高：used_memory_rss 会明显超过 used_memory。");
    }

    // ---------- fsync 墙 ----------
    private static void fsyncWall() {
        int n = 2_000;
        byte[] data = new byte[512];
        Path f1 = null, f2 = null;
        try {
            f1 = Files.createTempFile("rdd-aof-no", ".aof");
            f2 = Files.createTempFile("rdd-aof-fsync", ".aof");

            long t0 = System.nanoTime();
            try (FileChannel ch = FileChannel.open(f1, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (int i = 0; i < n; i++) ch.write(ByteBuffer.wrap(data));   // 只进 page cache
            }
            long t1 = System.nanoTime();

            long t2 = System.nanoTime();
            try (FileChannel ch = FileChannel.open(f2, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (int i = 0; i < n; i++) {
                    ch.write(ByteBuffer.wrap(data));
                    ch.force(false);                                            // 每条都 fsync（always 的形态）
                }
            }
            long t3 = System.nanoTime();

            RedisSupport.table(
                    new String[]{"模式", String.format("%d 次 512B 追加", n), "每命令耗时(教学量级)", "量级"},
                    List.of(new String[][]{
                            {"appendfsync no（只 write）", "全部进 page cache",
                                    String.format("%,.0f µs", (t1 - t0) / 1_000.0 / n), "微秒以内"},
                            {"appendfsync always（每条 force）", "每条都落到磁盘",
                                    String.format("%,.0f µs", (t3 - t2) / 1_000.0 / n), "微秒~毫秒"},
                    }),
                    new int[]{-1, -1, 1, -1});
            RedisSupport.print();
            RedisSupport.print("    ⚠️ 本沙箱/临时文件系统上 force() 的落盘开销会被稀释，倍数仅供参考——");
            RedisSupport.print("    真实 NVMe 上 fsync 每命令成本回到微秒~毫秒级（以 fio 实测为准），");
            RedisSupport.print("    这就是“把 Level 1 翻过去的微秒墙再翻回来”。");
            RedisSupport.print("    所以 everysec 是甜点：write 快（不进磁盘），每秒才让 bio 线程 fsync 一次。");
        } catch (Exception e) {
            RedisSupport.err("  (fsync 实测失败：" + e + ")");
        } finally {
            try { if (f1 != null) Files.deleteIfExists(f1); } catch (Exception ignored) {}
            try { if (f2 != null) Files.deleteIfExists(f2); } catch (Exception ignored) {}
        }
    }
}
