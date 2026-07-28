package com.zhiya.threed.pool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 🌊 响应式 + 线程池 —— 零外部依赖版
 * ============================================================
 * 不引入 Project Reactor / RxJava，全部用 JDK 自带的
 * java.util.concurrent.Flow（Reactive Streams 规范的 JDK 实现）
 * 和 CompletableFuture 搭建，对应文档 Level 7 里"响应式"那一节。
 *
 * 两个实验：
 *   1 → 背压（backpressure）：submit()（阻塞式背压）vs offer()（非阻塞丢弃）
 *       —— 对应文档表格里 "背压请求量 request(n)" / "onBackpressureDrop"
 *   2 → CPU 池混入阻塞 I/O 的代价，以及独立 IO 池隔离后的效果
 *       —— 对应文档"为什么必须有 boundedElastic，而不是把 parallel() 调大"
 *
 * 运行环境：JDK 21（java.util.concurrent.Flow 自 JDK 9 起就是标准库的一部分）。
 *
 * 编译运行：
 *   javac -encoding UTF-8 ReactiveNoDepsDemo.java
 *   java ReactiveNoDepsDemo 1        # 或 2
 *   java ReactiveNoDepsDemo          # 不带参数：两个实验依次跑一遍
 */
public class ReactiveNoDepsDemo {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            demo1();
            pause();
            demo2();
            return;
        }
        switch (args[0]) {
            case "1" -> demo1();
            case "2" -> demo2();
            default -> printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("""
                用法: java ReactiveNoDepsDemo <1|2>
                  1  背压：submit()（阻塞式背压） vs offer()（非阻塞丢弃）
                  2  CPU 池混入阻塞 I/O 的代价，以及独立 IO 池隔离后的效果
                """);
    }

    private static void pause() throws InterruptedException {
        System.out.println("\n" + "=".repeat(60) + "\n");
        Thread.sleep(200);
    }

    // ============================================================
    // 实验 1：背压 —— submit() 与 offer() 的区别
    // ============================================================
    //
    // SubmissionPublisher 是 JDK 自带的 Flow.Publisher 实现，天然支持
    // Reactive Streams 的按需拉取（request(n)）。这里故意配一个很小的
    // 缓冲区（容量 4）+ 一个很慢的订阅者（每个元素 50ms），制造"下游
    // 跟不上"的场景，对比两种应对方式。
    private static void demo1() throws InterruptedException {
        System.out.println("【实验 1】背压：submit()（阻塞式背压） vs offer()（非阻塞丢弃）\n");
        System.out.println("前提：缓冲区容量=4，下游订阅者每个元素处理 50ms，一次性提交 20 个元素\n");

        System.out.println("① offer()：下游处理不过来时，多余的元素被直接丢弃（类似 onBackpressureDrop）：");
        runOfferDemo();

        System.out.println("\n② submit()：下游处理不过来时，上游被【阻塞】，直到有空位（真正的背压）：");
        runSubmitDemo();
    }

    private static void runOfferDemo() throws InterruptedException {
        ExecutorService deliveryPool = Executors.newFixedThreadPool(1, namedFactory("offer-deliver"));
        AtomicInteger delivered = new AtomicInteger();
        AtomicInteger dropped = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);

        SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>(deliveryPool, 4);
        publisher.subscribe(new SlowSubscriber(delivered, done, 50));

        long start = System.nanoTime();
        for (int i = 1; i <= 20; i++) {
            publisher.offer(i, (subscriber, item) -> {
                dropped.incrementAndGet();
                return false; // 不重试，直接丢弃
            });
        }
        long produceElapsedMs = (System.nanoTime() - start) / 1_000_000;
        publisher.close();

        done.await(5, TimeUnit.SECONDS);
        deliveryPool.shutdown();
        System.out.printf("   生产者提交 20 个元素耗时=%dms（几乎不被拖慢）%n", produceElapsedMs);
        System.out.printf("   最终：已投递=%d, 被丢弃=%d%n", delivered.get(), dropped.get());
        System.out.println("   ★ 生产者一次性把 20 个全提交完，没有被拖慢，但代价是数据丢失");
    }

    private static void runSubmitDemo() throws InterruptedException {
        ExecutorService deliveryPool = Executors.newFixedThreadPool(1, namedFactory("submit-deliver"));
        AtomicInteger delivered = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);

        SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>(deliveryPool, 4);
        publisher.subscribe(new SlowSubscriber(delivered, done, 50));

        long start = System.nanoTime();
        for (int i = 1; i <= 20; i++) {
            publisher.submit(i); // 缓冲区满时会阻塞，直到订阅者腾出空位
        }
        long produceElapsedMs = (System.nanoTime() - start) / 1_000_000;
        publisher.close();

        done.await(5, TimeUnit.SECONDS);
        deliveryPool.shutdown();
        System.out.printf("   生产者提交 20 个元素耗时=%dms（★ 被下游拖慢，≈20×50ms）%n", produceElapsedMs);
        System.out.printf("   最终：已投递=%d, 被丢弃=0%n", delivered.get());
        System.out.println("   ★ 零丢失，但生产者的节奏被订阅者的处理速度接管 —— 这才是真背压");
    }

    /** 每次只 request(1)，且处理很慢的订阅者，用来制造"下游跟不上"的场景。 */
    static class SlowSubscriber implements Flow.Subscriber<Integer> {
        private Flow.Subscription subscription;
        private final AtomicInteger counter;
        private final CountDownLatch done;
        private final long delayMs;

        SlowSubscriber(AtomicInteger counter, CountDownLatch done, long delayMs) {
            this.counter = counter;
            this.done = done;
            this.delayMs = delayMs;
        }

        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1); // 按需拉取：一次只要一个
        }

        @Override public void onNext(Integer item) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            counter.incrementAndGet();
            subscription.request(1); // 处理完才继续要下一个
        }

        @Override public void onError(Throwable throwable) { done.countDown(); }
        @Override public void onComplete() { done.countDown(); }
    }

    // ============================================================
    // 实验 2：CPU 池（parallel 风格）混入阻塞 I/O 的代价
    // ============================================================
    //
    // 用 CompletableFuture.supplyAsync(fn, executor) 搭一条最小的"异步/
    // 响应式管道"：这正是文档里反复强调的 thenApplyAsync(fn, myPool) 用法。
    // 对照两种调度方式，唯一变量是"阻塞调用跑在哪个 Executor 上"。
    private static void demo2() throws InterruptedException {
        System.out.println("【实验 2】CPU 池混入阻塞 I/O 的代价（CompletableFuture 版异步管道）\n");
        System.out.println("前提：cpuPool 固定 2 个线程；10 个订单各自触发一次 200ms 的阻塞调用；");
        System.out.println("      同时向 cpuPool 提交 4 个很快的纯 CPU 计算任务，用它们的完成耗时衡量是否被拖慢\n");

        System.out.println("① 错误写法：阻塞调用与 CPU 计算共用同一个小池（模拟把 parallel() 调大来兼容阻塞操作）：");
        runAsyncPipeline(true);

        System.out.println("\n② 正确写法：阻塞调用隔离到独立的、更大的 IO 池（模拟 boundedElastic）：");
        runAsyncPipeline(false);
    }

    private static void runAsyncPipeline(boolean shareSamePool) throws InterruptedException {
        int cpuPoolSize = 2;
        ExecutorService cpuPool = Executors.newFixedThreadPool(cpuPoolSize, namedFactory("cpu-pool"));
        ExecutorService ioPool = shareSamePool
                ? cpuPool
                : Executors.newFixedThreadPool(16, namedFactory("io-pool"));

        int orders = 10;
        List<CompletableFuture<Integer>> pipeline = new ArrayList<>();
        for (int i = 1; i <= orders; i++) {
            int id = i;
            // “响应式管道”的下游阶段：一次模拟的阻塞 RPC/JDBC 调用
            pipeline.add(CompletableFuture.supplyAsync(() -> blockingRpc(id), ioPool));
        }

        int cpuTasks = 4;
        List<CompletableFuture<Long>> cpuFutures = new ArrayList<>();
        long start = System.nanoTime();
        for (int i = 0; i < cpuTasks; i++) {
            cpuFutures.add(CompletableFuture.supplyAsync(() -> fibonacci(30), cpuPool));
        }

        try {
            CompletableFuture.allOf(cpuFutures.toArray(new CompletableFuture[0]))
                    .orTimeout(3, TimeUnit.SECONDS)
                    .join();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.printf("   纯 CPU 任务（%d 个）完成耗时: %dms%n", cpuTasks, elapsedMs);
        } catch (CompletionException e) {
            System.out.printf("   ⚠️ 纯 CPU 任务（%d 个）超过 3 秒仍未全部完成！（%s）%n",
                    cpuTasks, e.getCause().getClass().getSimpleName());
        }

        CompletableFuture.allOf(pipeline.toArray(new CompletableFuture[0])).join();
        System.out.printf("   响应式管道处理完成的订单数: %d/%d%n", orders, orders);

        cpuPool.shutdown();
        if (ioPool != cpuPool) ioPool.shutdown();
    }

    private static Integer blockingRpc(int orderId) {
        try { Thread.sleep(200); } catch (InterruptedException ignored) {} // 模拟阻塞 I/O
        return orderId;
    }

    private static long fibonacci(int n) {
        return n <= 1 ? n : fibonacci(n - 1) + fibonacci(n - 2);
    }

    // ============================================================
    // 工具类
    // ============================================================

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger(1);
        return r -> new Thread(r, prefix + "-" + seq.getAndIncrement());
    }
}
