package com.zhiya.redis;

import com.zhiya.redis.support.RedisSupport;


import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Level 2：单线程不是缺陷，是用“串行”换“免锁”的主动选择。
 * <p>
 * 对应层级：Level 2。
 * 演示主题：事件循环六步、线性化点与 io-threads。
 * 验证目标：慢命令堵死全店的墙钟账；多线程无锁计数丢失、加锁退化成单核；
 *           IO 可以并行（解析线程）而命令必须串行（唯一执行线程）。
 */
public final class RedisLevel2EventLoopDemo {

    private RedisLevel2EventLoopDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("Level 2 · 单线程是取舍：串行执行 = 免费原子性",
                "C 到门口了——感应门铃响，唯一店长开始工作");

        RedisSupport.sec("① 事件循环六步（C = SET sku:1001:stock 42）");
        String[] steps = {
                "① epoll_wait 返回：6379 的 fd 可读（门铃响）",
                "② readQueryFromClient：字节读入缓冲，按 RESP 切出完整命令（半包/粘包在此吸收）",
                "③ lookupCommand：命令表里找到 \"set\" → setCommand",
                "④ call()：前置检查 → setCommand 写全局 dict → dirty++ → signalModifiedKey",
                "⑤ \"+OK\" 追加进该 client 输出缓冲，注册可写事件",
                "⑥ epoll 报可写 → writeToClient 发出去",
        };
        for (String s : steps) RedisSupport.print("  " + s);
        RedisSupport.print();
        RedisSupport.ok("  ★ 线性化点 = 第④步改 dict 的那一行。因为同一时刻不存在第二个正在执行的命令，");
        RedisSupport.print("    全系统所有命令构成一个全序，序 = 主线程的执行顺序。INCR 不需要锁。");

        RedisSupport.sec("② 模拟：单线程事件循环 + 慢命令堵死全店");
        simulateSlowCommand();

        RedisSupport.sec("③ INCR 的账：单线程免锁 vs 多线程加锁");
        counterComparison();

        RedisSupport.sec("④ io-threads：IO 可以并行，命令必须串行");
        ioThreadsDemo();

        RedisSupport.mantra("IO 可以并行，命令必须串行");
    }

    // ---------- ② 慢命令模拟 ----------
    private static void simulateSlowCommand() {
        RedisSupport.print("  场景：店长一个人收银。三个客人排队，其中一个人点了“盘库”(KEYS * 全表扫描)。");
        RedisSupport.print("  每个普通命令耗时 ~2µs（教学量级）；盘库耗时 ~50ms。");

        long t0 = System.nanoTime();
        long total = 0;
        for (int i = 0; i < 3; i++) {
            if (i == 1) {
                long s = System.nanoTime();
                doSlowScan();                       // 模拟 KEYS * 盘库
                total += System.nanoTime() - s;
                RedisSupport.warn("    · 盘库命令执行中…… 期间店里一切客人都在等，超时、心跳全被打断");
            } else {
                busy(2_000);                        // 普通 GET
                total += 2_000;
            }
        }
        long wall = System.nanoTime() - t0;
        System.out.printf("    · 三个命令总墙钟：%,d µs；其中普通命令合计只占 %,d µs —— 慢命令吃掉了 %.1f%%%n",
                wall / 1_000, total / 1_000, (wall - total) / (double) wall * 100);
        RedisSupport.print();
        RedisSupport.print("  线上现象：KEYS * 一执行，全体连接延迟飙升/超时；从库心跳超时，连锁误判。");
        RedisSupport.print("  修正：SCAN cursor MATCH … COUNT n 分批（坑 1），事件循环内不许慢。");
    }

    private static void doSlowScan() {
        long sum = 0;
        for (int i = 0; i < 200_000; i++) sum += i * 3;   // ~几十 ms 的教学量级
        if (sum < 0) System.out.println();
    }

    private static void busy(long nanos) {
        long end = System.nanoTime() + nanos;
        while (System.nanoTime() < end) { /* 空转 */ }
    }

    // ---------- ③ 计数器对比 ----------
    private static void counterComparison() {
        final int THREADS = 8;
        final int PER = 200_000;

        // 多线程 + 锁
        AtomicLong locked = new AtomicLong();
        ReentrantLock lock = new ReentrantLock();
        long t0 = System.nanoTime();
        List<Thread> ts = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            Thread th = new Thread(() -> {
                for (int i = 0; i < PER; i++) {
                    lock.lock();
                    try { locked.incrementAndGet(); } finally { lock.unlock(); }
                }
            });
            ts.add(th); th.start();
        }
        joinAll(ts);
        long t1 = System.nanoTime();

        // 多线程无锁（竞态）：读旧值 → 放大“读-写回”窗口 → 写回。
        // 这正是 Level 2 说的“GET+1 再 SET 两步有竞态”——非原子读改写必然丢计数。
        long[] plain = {0};
        List<Thread> ts2 = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            Thread th = new Thread(() -> {
                for (int i = 0; i < PER; i++) racyIncrement(plain);
            });
            ts2.add(th); th.start();
        }
        joinAll(ts2);
        long t2 = System.nanoTime();

        // 单线程（事件循环里就是这种：一条命令一个串行 step）
        long serial = 0;
        long t3 = System.nanoTime();
        for (int i = 0; i < THREADS * PER; i++) serial++;
        long t4 = System.nanoTime();

        long expect = (long) THREADS * PER;
        RedisSupport.require(locked.get() == expect, "多线程+锁的结果必须等于期望值");
        // 竞态是否丢数是概率事件：连跑 3 次，要求至少 1 次观察到丢失，才能证明“无锁不保证正确”。
        int lostRuns = 0;
        for (int trial = 0; trial < 3; trial++) {
            long[] probe = {0};
            List<Thread> ts3 = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                Thread th = new Thread(() -> { for (int i = 0; i < PER; i++) racyIncrement(probe); });
                ts3.add(th); th.start();
            }
            joinAll(ts3);
            if (probe[0] != expect) lostRuns++;
        }
        RedisSupport.require(lostRuns >= 1, "无锁并发在重复试验中必须至少一次观察到计数丢失");
        RedisSupport.require(plain[0] != expect, "本轮的“GET+SET 两步”无锁计数必须观察到丢失");
        RedisSupport.require(serial == expect, "单线程串行结果必须等于期望值");
        RedisSupport.table(
                new String[]{"方案", "结果", "是否=期望值", "耗时(教学量级)"},
                List.of(new String[][]{
                        {"多线程 + 全局锁", Long.toString(locked.get()), locked.get() == expect ? "是 ✓" : "否 ✗",
                                String.format("%,d µs", (t1 - t0) / 1_000)},
                        {"多线程 无锁", Long.toString(plain[0]), plain[0] == expect ? "是 ✓" : "否 ✗  ← 丢了！",
                                String.format("%,d µs", (t2 - t1) / 1_000)},
                        {"单线程串行（Redis 式）", Long.toString(serial), serial == expect ? "是 ✓" : "否 ✗",
                                String.format("%,d µs", (t4 - t3) / 1_000)},
                }),
                new int[]{-1, 1, -1, 1});
        RedisSupport.print();
        RedisSupport.print("  三笔账：锁把多核串成一颗（热点 key 上锁竞争）；无锁则计数丢失（竞态）；");
        RedisSupport.print("  单线程串行——结果永远正确，且没有锁的开销。这就是 Redis 的取舍：");
        RedisSupport.print("  命令执行阶段只有一条线性序列，原子性免费。");
        RedisSupport.dimln("  （无锁行的耗时含人为放大“读-写回”窗口的空转，仅为稳定复现丢数，不计较耗时绝对值）");
        RedisSupport.print();
    }

    private static void joinAll(List<Thread> ts) {
        for (Thread t : ts) {
            try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    /**
     * 非原子“读-改-写”：先读旧值，再放大窗口（模拟 GET 后做点别的事），最后写回。
     * 多个线程同时读到同一个旧值 → 各自写回 → 计数丢失。这正是 INCR 必须命令级原子的原因。
     */
    private static void racyIncrement(long[] counter) {
        long v = counter[0];            // GET：读旧值
        for (int s = 0; s < 32; s++) {
            Thread.onSpinWait();        // 放大窗口：让并发线程更容易读到同一个旧值
        }
        counter[0] = v + 1;             // SET：写回（旧值 + 1 覆盖了别人的 +1）
    }

    // ---------- ④ io-threads ----------
    private static void ioThreadsDemo() {
        RedisSupport.print("  6.0 起 io-threads（🔒 默认关）并行做【读/解析/写回】；执行阶段始终串行。");
        RedisSupport.print("  模拟：2 个解析线程并行拆包，1 个执行线程按到达顺序串行执行——");
        RedisSupport.print("  观察执行序号：永远是递增的唯一序列，没有两条命令同时“在执行”。");

        Deque<Long> parsed = new ArrayDeque<>();          // 已解析待执行
        AtomicInteger execSeq = new AtomicInteger();
        AtomicInteger parseSeq = new AtomicInteger();
        int total = 6;

        Thread exec = new Thread(() -> {
            while (execSeq.get() < total) {
                synchronized (parsed) {
                    if (!parsed.isEmpty()) {
                        long cmd = parsed.poll();
                        long us = System.nanoTime() / 1000;
                        System.out.printf("       [执行线程] 序号=#%d  命令=%s  串行点 @%,dµs%n",
                                execSeq.incrementAndGet(), "CMD" + cmd, us);
                    }
                }
                Thread.onSpinWait();
            }
        }, "executor");
        exec.start();

        List<Thread> parsers = new ArrayList<>();
        for (int p = 0; p < 2; p++) {
            final int pid = p;
            Thread pt = new Thread(() -> {
                for (int i = pid; i < total; i += 2) {
                    synchronized (parsed) { parsed.addLast((long) i); }   // “拆包”放进执行队列
                    busy(1_000);                                          // 模拟解析耗时
                }
            }, "io-thread-" + pid);
            parsers.add(pt); pt.start();
        }
        joinAll(parsers);
        try { exec.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        RedisSupport.print();
        RedisSupport.print("  收益边界：命令本身是 ns~µs 级内存操作，并行执行唯一的新增物是锁——");
        RedisSupport.print("  没有任何加锁方案能在 O(1) 操作上比“不加锁”更快。");
        RedisSupport.print("  胖 value 的字节搬运才是贵的部分，所以并行被精确安放在 IO 上（坑 13 呼应）。");
    }
}
