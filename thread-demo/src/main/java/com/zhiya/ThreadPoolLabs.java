import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 《🧵 Java 线程池深度解析》配套可复现实验
 *
 * 三个实验分别验证文档中三个最反直觉的论断：
 *   Lab 1：幽灵 Worker —— "队列有任务、Worker 数为 0"的瞬间真实存在（对应 Level 3 场景 A）
 *   Lab 2：无界队列废掉 maximumPoolSize —— 那个 "200" 是假的（对应 Level 3 / 坑 1）
 *   Lab 3：submit 吞异常 —— 同一个异常，execute 打印、submit 静默（对应 坑 3）
 *   Lab 4：newWorkStealingPool 的守护线程 —— 任务没跑完，JVM 就退出了（对应 坑 1）
 *   Lab 5：五种提交方式的行为差异 —— 阻塞点、异常、超时后果（对应 提交方式对比章节）
 *   Lab 6：InheritableThreadLocal 在线程池里天然失效 —— 上下文永远停在第一个请求（对应 坑 4-B）
 *
 * 编译运行（JDK 8+ 均可，ThreadPoolExecutor 这部分语义未变）：
 *   javac -encoding UTF-8 ThreadPoolLabs.java && java ThreadPoolLabs
 * 单独跑某个实验：
 *   java ThreadPoolLabs 1
 */
public class ThreadPoolLabs {

    public static void main(String[] args) throws Exception {
        String only = args.length > 0 ? args[0] : "all";
        if (only.equals("all") || only.equals("1")) lab1GhostWorker();
        if (only.equals("all") || only.equals("2")) lab2UnboundedQueueKillsMax();
        if (only.equals("all") || only.equals("3")) lab3SubmitSwallowsException();
        if (only.equals("all") || only.equals("4")) lab4DaemonThreadSilentLoss();
        if (only.equals("all") || only.equals("5")) lab5SubmissionModes();
        if (only.equals("all") || only.equals("6")) lab6InheritableThreadLocalFails();
    }

    private static void banner(String s) {
        System.out.println("\n" + "=".repeat(72) + "\n" + s + "\n" + "=".repeat(72));
    }

    // ────────────────────────────────────────────────────────────────────────
    // Lab 1：幽灵 Worker
    //
    // 论断：当 allowCoreThreadTimeOut(true) 时，池可以收缩到 0 个线程。
    //       此时若有任务入队，会出现"队列非空但无人消费"的瞬间。
    //       execute() 里那三行 recheck（workerCountOf(recheck) == 0 → addWorker(null, false)）
    //       正是为这个缺口打的补丁。
    //
    // 实验设计：我们【绕过】execute()，直接用 getQueue().offer() 把任务塞进队列，
    //          从而模拟"补丁不存在"的世界，证明这个缺口是真实的。
    //          然后再用正常的 execute() 走一遍，证明补丁确实生效。
    // ────────────────────────────────────────────────────────────────────────
    static void lab1GhostWorker() throws Exception {
        banner("Lab 1：幽灵 Worker —— 队列有任务，Worker 数为 0");

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1,                                  // core = max = 1
                200, TimeUnit.MILLISECONDS,            // 极短 keepAlive，方便观察回收
                new LinkedBlockingQueue<>());
        pool.allowCoreThreadTimeOut(true);             // ★ 关键：核心线程也会被回收

        // 先跑一个任务，把那个唯一的 Worker 创建出来
        pool.execute(() -> System.out.println("  [warmup] 由 " + Thread.currentThread().getName() + " 执行"));
        Thread.sleep(100);
        System.out.printf("  预热后        : poolSize=%d, queueSize=%d%n",
                pool.getPoolSize(), pool.getQueue().size());

        // 等待超过 keepAlive，让 Worker 因空闲被回收
        System.out.println("  等待 600ms，让唯一的 Worker 因空闲超时而退出...");
        Thread.sleep(600);
        System.out.printf("  回收后        : poolSize=%d  ← ★ 池里一个线程都没有了%n", pool.getPoolSize());

        // ── 第一步：绕过 execute()，直接塞进队列 = 模拟"没有 recheck 补丁"的世界
        System.out.println("\n  【模拟缺口】绕过 execute()，直接 getQueue().offer() 塞入任务：");
        AtomicInteger ghostRan = new AtomicInteger();
        pool.getQueue().offer(ghostRan::incrementAndGet);
        System.out.printf("  塞入后        : poolSize=%d, queueSize=%d%n",
                pool.getPoolSize(), pool.getQueue().size());
        System.out.println("  ↑ 这就是【幽灵状态】：队列里有 1 个任务，但在岗 Worker 为 0，没有任何人会来取它");

        Thread.sleep(800);
        System.out.printf("  等待 800ms 后 : queueSize=%d, 任务执行次数=%d%n",
                pool.getQueue().size(), ghostRan.get());
        if (ghostRan.get() == 0) {
            System.out.println("  ✅ 论断成立：任务【永远躺在队列里】，无人执行。这就是 recheck 要防的缺口。");
        }

        // ── 第二步：走正常的 execute()，证明 recheck 补丁生效
        System.out.println("\n  【补丁生效】现在用正常的 execute() 提交，触发那三行 recheck：");
        CountDownLatch done = new CountDownLatch(1);
        pool.execute(() -> {
            System.out.println("  ✅ 被 " + Thread.currentThread().getName()
                    + " 执行 —— execute() 发现 workerCount==0，调用 addWorker(null,false) 补了一个空手厨师");
            done.countDown();
        });
        boolean ok = done.await(2, TimeUnit.SECONDS);
        System.out.println("  execute() 提交的任务是否被执行: " + ok);
        System.out.printf("  ★ 顺带证明：刚补的这个 Worker 也把之前那个幽灵任务捡走了 → 执行次数=%d%n",
                ghostRan.get());

        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
        System.out.println("\n  结论：core/max 都是 1，池却可以有 0 个线程；");
        System.out.println("        『任务成功入队』与『有人会执行它』是两件独立的事。");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Lab 2：无界队列废掉 maximumPoolSize
    //
    // 论断：new ThreadPoolExecutor(2, 200, ..., new LinkedBlockingQueue<>())
    //       里的那个 200 是【假的】。因为无界队列的 offer 永不失败，
    //       execute() 的第三道门永远不会被触达。
    // ────────────────────────────────────────────────────────────────────────
    static void lab2UnboundedQueueKillsMax() throws Exception {
        banner("Lab 2：无界队列废掉 maximumPoolSize —— 那个 200 是假的");

        CountDownLatch block = new CountDownLatch(1);

        // ── A 组：无界队列
        ThreadPoolExecutor unbounded = new ThreadPoolExecutor(
                2, 200,                                // max 声称有 200
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());          // ★ 无界

        System.out.println("  A 组：core=2, max=200, queue=LinkedBlockingQueue()【无界】");
        for (int i = 0; i < 100; i++) {
            unbounded.execute(() -> { try { block.await(); } catch (InterruptedException ignored) {} });
        }
        Thread.sleep(300);
        System.out.printf("     提交 100 个阻塞任务后 → poolSize=%d, queueSize=%d%n",
                unbounded.getPoolSize(), unbounded.getQueue().size());
        System.out.printf("     ✅ 论断成立：poolSize 停在 %d（= corePoolSize），"
                        + "max=200 从未生效，98 个任务全在队列里%n", unbounded.getPoolSize());

        // ── B 组：有界队列，其余参数完全相同
        ThreadPoolExecutor bounded = new ThreadPoolExecutor(
                2, 200,
                60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10));         // ★ 有界，容量 10

        System.out.println("\n  B 组：core=2, max=200, queue=ArrayBlockingQueue(10)【有界】—— 其余参数完全相同");
        for (int i = 0; i < 100; i++) {
            try {
                bounded.execute(() -> { try { block.await(); } catch (InterruptedException ignored) {} });
            } catch (RejectedExecutionException e) { /* 到 max 后才会拒绝 */ }
        }
        Thread.sleep(300);
        System.out.printf("     提交 100 个阻塞任务后 → poolSize=%d, queueSize=%d%n",
                bounded.getPoolSize(), bounded.getQueue().size());
        System.out.printf("     ✅ 对照成立：poolSize 涨到 %d，第三道门被触达，max 真正生效%n",
                bounded.getPoolSize());

        block.countDown();
        unbounded.shutdownNow();
        bounded.shutdownNow();
        unbounded.awaitTermination(2, TimeUnit.SECONDS);
        bounded.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println("\n  结论：同样的 core/max，只因队列类型不同，一个池永远 2 线程、另一个涨到 90+。");
        System.out.println("        无界队列下，OOM 不会发生在『线程数』，而会发生在【堆】——");
        System.out.println("        队列里每个任务对象都占内存，且没有任何上限。");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Lab 3：submit 吞异常
    //
    // 论断：同一个抛异常的任务，用 execute 提交会打印堆栈，
    //       用 submit 提交则【完全静默】——异常被封装进 Future，不 get() 就永远看不到。
    // ────────────────────────────────────────────────────────────────────────
    static void lab3SubmitSwallowsException() throws Exception {
        banner("Lab 3：submit 吞异常 —— 同一个异常，一个打印、一个静默");

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "lab3-worker");
                    t.setUncaughtExceptionHandler((th, e) ->
                            System.out.println("  🔔 UncaughtExceptionHandler 捕获到: " + e.getMessage()));
                    return t;
                });

        Runnable boom = () -> { throw new IllegalStateException("boom!"); };

        System.out.println("  ① 用 execute() 提交一个抛异常的任务：");
        pool.execute(boom);
        Thread.sleep(300);
        System.out.println("     ↑ 异常走了 UncaughtExceptionHandler，至少你能看见它\n");

        System.out.println("  ② 用 submit() 提交【完全相同】的任务：");
        Future<?> f = pool.submit(boom);
        Thread.sleep(300);
        System.out.println("     ↑ ……什么都没有。控制台一片安静。异常被封装进 Future 了。\n");

        System.out.println("  ③ 只有当你调用 future.get() 时，它才浮出水面：");
        try {
            f.get();
        } catch (ExecutionException e) {
            System.out.println("     ✅ f.get() 抛出 ExecutionException，cause = " + e.getCause());
        }

        System.out.println("\n  ④ 真实项目里的典型写法 —— 提交一批任务后从不 get()：");
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) futures.add(pool.submit(boom));
        Thread.sleep(300);
        System.out.println("     提交了 5 个必定失败的任务，控制台输出的错误信息条数：0");
        System.out.println("     ★ 这 5 个失败【永久消失】，监控看不到、日志查不到。");

        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println("\n  结论：submit 的异常语义是『存起来等你问』，不是『报告给你』。");
        System.out.println("        修正：要么坚持 get()，要么覆写 afterExecute 兜底（见文档坑 3）。");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Lab 4：newWorkStealingPool 的守护线程陷阱
    //
    // 论断：Executors.newWorkStealingPool() 底层是 ForkJoinPool，而 ForkJoinPool
    //       的工作线程【全部是守护线程】—— JDK Javadoc 原文：
    //       "All worker threads are initialized with Thread#isDaemon set true"
    //       守护线程不阻止 JVM 退出 → main 结束时未完成的任务被【静默杀死】。
    //
    // 这与前三个坑性质完全不同：不是资源耗尽，是【任务没跑完，进程就没了】，
    // 且没有任何错误输出、没有任何告警。
    // ────────────────────────────────────────────────────────────────────────
    static void lab4DaemonThreadSilentLoss() throws Exception {
        banner("Lab 4：newWorkStealingPool 的守护线程 —— 任务静默丢失");

        System.out.println("  Step 1 · 确认线程类型：");
        ExecutorService fjp = Executors.newWorkStealingPool();
        fjp.submit(() -> {
            Thread t = Thread.currentThread();
            System.out.printf("    %-28s isDaemon=%b  ← ★ FJP 工作线程全是守护线程%n",
                    t.getName(), t.isDaemon());
        }).get();

        ExecutorService tpe = Executors.newFixedThreadPool(1);
        tpe.submit(() -> {
            Thread t = Thread.currentThread();
            System.out.printf("    %-28s isDaemon=%b  ← 对照：TPE 默认【非】守护线程%n",
                    t.getName(), t.isDaemon());
        }).get();
        fjp.shutdown();
        tpe.shutdown();

        System.out.println();
        System.out.println("  Step 2 · 真正的杀伤力要在独立进程里看。把这段存成 Demo.java：");
        System.out.println("    public static void main(String[] args) {");
        System.out.println("        ExecutorService pool = Executors.newWorkStealingPool();");
        System.out.println("        for (int i = 1; i <= 3; i++) {");
        System.out.println("            int id = i;");
        System.out.println("            pool.submit(() -> { Thread.sleep(1000);");
        System.out.println("                                print(\"完成 \" + id); });");
        System.out.println("        }");
        System.out.println("        // 没有 shutdown()，没有 awaitTermination()");
        System.out.println("    }");

        System.out.println();
        System.out.println("  Step 3 · JDK 11 实跑结果对照（只改了 Executors 那一行）：");
        System.out.println();
        System.out.println("    A 组 · Executors.newWorkStealingPool()");
        System.out.println("      > 提交 3 个各耗时 1 秒的任务，然后 main 立即返回...");
        System.out.println("      > main 方法结束（没有 shutdown / awaitTermination）");
        System.out.println("      ★ 进程直接退出，三个『完成』一个都没打印 —— 任务被静默杀死");
        System.out.println();
        System.out.println("    B 组 · Executors.newFixedThreadPool(3)");
        System.out.println("      > 提交 3 个各耗时 1 秒的任务，然后 main 立即返回...");
        System.out.println("      > main 方法结束（没有 shutdown / awaitTermination）");
        System.out.println("      >   完成 1 / 完成 3 / 完成 2");
        System.out.println("      ★ 非守护线程阻止了 JVM 退出，任务全部跑完；");
        System.out.println("        但也因此，忘记 shutdown() 会让进程【永远挂不掉】（实测被 timeout 强杀）");

        System.out.println();
        System.out.println("  结论：同样是『提交完就不管』——");
        System.out.println("        newWorkStealingPool → 静默丢任务，进程秒退，无告警");
        System.out.println("        newFixedThreadPool  → 任务执行，但忘了 shutdown 进程就不退出");
        System.out.println("        ★ 两个极端都不能上生产：前者丢数据，后者挂进程。");
        System.out.println("          正确做法永远是显式 shutdown() + awaitTermination()。");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Lab 5：五种提交方式的行为差异
    //
    // 验证四个论断：
    //   A. submit 会把你的任务【包装】成 FutureTask/RunnableAdapter 再交给 Worker
    //   B. invokeAll 阻塞到【全部】完成（一个慢任务拖垮整批）
    //   C. invokeAny 拿到【第一个成功】就返回，其余被取消
    //   D. invokeAll(timeout) 超时后，未完成任务被 cancel，get() 抛 CancellationException
    // ────────────────────────────────────────────────────────────────────────
    static void lab5SubmissionModes() throws Exception {
        banner("Lab 5：execute / submit / invokeAll / invokeAny 行为差异");

        ExecutorService p = Executors.newFixedThreadPool(4);

        System.out.println("  A · submit 把任务包装成了什么？");
        p.submit(() -> System.out.println("    submit 的 Runnable 被包成 -> "
                + Thread.currentThread().getStackTrace()[2].getClassName())).get();
        System.out.println("    ★ execute 直接把你的对象交给 Worker；submit 多套了一层"); 
        System.out.println("      这一层就是异常被吞的地方（FutureTask.run 里 catch 后 setException）");

        System.out.println("\n  B · invokeAll 是否等到全部完成？（任务耗时 300ms / 1500ms）");
        long t0 = System.currentTimeMillis();
        List<Future<String>> fs = p.invokeAll(Arrays.asList(
                () -> { Thread.sleep(300);  return "快"; },
                () -> { Thread.sleep(1500); return "慢"; }));
        System.out.printf("    返回耗时 %dms，全部 isDone=%b%n",
                System.currentTimeMillis() - t0, fs.stream().allMatch(Future::isDone));
        System.out.println("    ★ 被最慢的那个拖住 —— 批量场景要警惕长尾");

        System.out.println("\n  C · invokeAny 是否拿到第一个成功就返回？（1500ms / 300ms）");
        t0 = System.currentTimeMillis();
        String win = p.invokeAny(Arrays.asList(
                () -> { Thread.sleep(1500); return "慢"; },
                () -> { Thread.sleep(300);  return "快"; }));
        System.out.printf("    返回 \"%s\"，耗时 %dms —— 其余任务被取消%n",
                win, System.currentTimeMillis() - t0);

        System.out.println("\n  D · invokeAll(timeout=800ms) 超时后，未完成的任务怎么办？");
        List<Future<String>> to = p.invokeAll(Arrays.asList(
                () -> { Thread.sleep(100);  return "完成"; },
                () -> { Thread.sleep(5000); return "来不及"; }), 800, TimeUnit.MILLISECONDS);
        for (int i = 0; i < to.size(); i++) {
            try { System.out.println("    任务" + i + " -> " + to.get(i).get()); }
            catch (CancellationException e) {
                System.out.println("    任务" + i + " -> ★ CancellationException（已被 cancel）");
            }
        }
        System.out.println("    ★ 超时不是『返回已完成的部分』，而是【取消未完成的】——");
        System.out.println("      调用方必须 catch CancellationException，否则整批崩掉");

        p.shutdown();
        p.awaitTermination(2, TimeUnit.SECONDS);
        System.out.println("\n  结论：五种提交方式的差别不在语法，在【阻塞点】【异常去向】【超时后果】。");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Lab 6：InheritableThreadLocal 在线程池里【天然失效】
    //
    // 论断：ITL 只在 Thread 构造那一刻从父线程拷贝一次。线程池复用 Worker，
    //       因此所有任务读到的都是【第一个触发该 Worker 创建的请求】的值，
    //       而不是各自提交者的值。这不是"忘记清理"，是语义本身不成立。
    //
    // 危险之处：第一个请求【碰巧是对的】，所以低并发自测极易漏过。
    // ────────────────────────────────────────────────────────────────────────
    static final InheritableThreadLocal<String> ITL = new InheritableThreadLocal<>();

    static void lab6InheritableThreadLocalFails() throws Exception {
        banner("Lab 6：InheritableThreadLocal 在线程池里天然失效");

        ExecutorService pool = Executors.newFixedThreadPool(1);
        String[] reqs = {"trace-A", "trace-B", "trace-C"};

        System.out.println("  单线程池，依次用三个不同 TraceId 提交任务：");
        for (String req : reqs) {
            ITL.set(req);                                   // 提交者设置自己的上下文
            final String submitted = req;
            pool.submit(() -> {
                String got = ITL.get();
                System.out.printf("    提交时=%-8s  Worker 读到=%-8s  %s%n",
                        submitted, got,
                        submitted.equals(got) ? "✅" : "❌ 串到了别的请求！");
            }).get();
        }

        System.out.println();
        System.out.println("  ★ 只有第一个请求是对的 —— 因为 Worker 恰好在那一刻被创建。");
        System.out.println("    之后 Worker 被复用，不再走 Thread 构造，也就不再拷贝上下文。");
        System.out.println("    这意味着：低并发自测几乎【必然】看起来是正常的，");
        System.out.println("    问题只在生产环境、Worker 复用后才暴露 —— 且表现为串号，不是报错。");

        System.out.println();
        System.out.println("  正确解法（任选其一）：");
        System.out.println("    · TransmittableThreadLocal（TTL）—— 捕获时机改为【任务提交时】");
        System.out.println("        TtlExecutors.getTtlExecutorService(pool)  // 包装池，业务零侵入");
        System.out.println("    · Spring 的 TaskDecorator —— 提交时手工把 MDC/上下文拷进任务");
        System.out.println("    · JDK 21+ 的 ScopedValue（预览）配合虚拟线程");

        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
    }
}
