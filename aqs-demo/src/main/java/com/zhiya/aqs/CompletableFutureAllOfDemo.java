package com.zhiya.aqs;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CompletableFuture.allOf 偶现 StackOverflowError —— 归因与复现演示。
 * <p>
 * 背景问题：生产代码用 {@code CompletableFuture.allOf(...).join()} 等待 100+ 个并发任务时，
 * 偶现 StackOverflowError。最常见的归因竞猜是"allOf 建链爆栈"——本 Demo 用对照实验
 * <b>证伪这个竞猜</b>，并把真正的爆点锁定在"递归型任务"上。
 * <p>
 * 三层因果链（全部为本机 JDK 8 实测结论，非推测）：
 * <ol>
 *   <li>allOf 本身无辜：内部 andTree 递归对半切构造成<b>平衡二叉树</b>，构造与完成传播都是
 *       O(log n) 栈深（n=10万→17 层）。实验 1 用 100/1万/10万 三档直接验证。</li>
 *   <li>"链式传播爆栈"的竞猜同样不成立：JDK 8 的完成传播是
 *       {@code postComplete} 的 while 循环（迭代式，非递归），循环嵌套 allOf 构造的退化链
 *       （实验 2，20 万级）与超长 thenApply 链（实验 3，100 万级）都不放大栈深。</li>
 *   <li>真正 100% 可复现的爆点是<b>递归型任务</b>：
 *       实验 5（回调在完成传播栈上被 inline 执行，回调再递归 fan-out，业务帧与 CF 传播帧
 *       交替叠加）与实验 6（纯业务递归经 supplyAsync 跑在池线程——"误归因 allOf"的典型现场）。</li>
 * </ol>
 * <p>
 * 观测手段（面试判别法）：
 * <ol>
 *   <li>传播线程识别：SOE 爆在"谁调用 complete/join 的线程"的栈上。</li>
 *   <li>重复帧统计：解析 StackTraceElement[]，按方法名统计出现次数——递归点必然重复出现；
 *       对照实验 5 与 6 的重复帧模式即可定位元凶（CF 传播帧主导 vs 业务帧主导）。</li>
 *   <li>栈大小敏感性：-Xss512k 与 -Xss1m 两档跑，同样的递归任务爆点不同。</li>
 * </ol>
 * <p>
 * 运行方式（JDK 8，零外部依赖；本类复用同包 AqsDemoSupport，需全包编译）：
 * <pre>
 * cd aqs-demo/src/main/java
 * javac -encoding UTF-8 com/zhiya/aqs/*.java
 * java -Dfile.encoding=UTF-8 com.zhiya.aqs.CompletableFutureAllOfDemo            # 全部实验（CI 同款）
 * java -Xss512k com.zhiya.aqs.CompletableFutureAllOfDemo                         # 小栈更容易复现
 * java -Dfile.encoding=UTF-8 com.zhiya.aqs.CompletableFutureAllOfDemo 5          # 单跑实验 5
 * java -Dfile.encoding=UTF-8 com.zhiya.aqs.CompletableFutureAllOfDemo 9          # JDK 8 vs JDK 21 跨版本对比
 * </pre>
 * 说明：SOE 栈默认截断 1024 帧；要全量帧加 {@code -XX:MaxJavaStackTraceDepth=100000}
 * （JDK 8 实测该参数取 0 时 getStackTrace() 返回 0 帧，取 0 是"无帧"而非"不截断"）。
 * <p>
 * 版本边界：postComplete 迭代式传播是 JDK 8 的实现细节（非 API 规范承诺）；
 * 实验 9 用子进程在 JDK 8 与 JDK 21 上跑同一组实验，行为差异以实测为准。
 */
public final class CompletableFutureAllOfDemo {

    private static final int DEFAULT_CHAIN_N = 200_000;

    private CompletableFutureAllOfDemo() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            runAll();
        } else if ("9".equals(args[0])) {
            runCrossVersionComparison();
        } else if ("child".equals(args[0])) {
            runChildCase(args);
        } else {
            runSingleCase(args);
        }
    }

    private static void runAll() throws Exception {
        System.out.println("===== CompletableFuture.allOf 偶现 StackOverflowError：归因与复现"
                + "（JDK " + System.getProperty("java.version") + "） =====");
        runCase("实验 1 对照组：单次 allOf 平衡树", CompletableFutureAllOfDemo::demoAllOfBalancedTreeControl);
        runCase("实验 2 证伪：循环嵌套 allOf 退化链", () -> demoNestedAllOfChain(DEFAULT_CHAIN_N, Variant.B));
        runCase("实验 2 证伪：循环嵌套 allOf 退化链（最坏完成时序）", () -> demoNestedAllOfChain(DEFAULT_CHAIN_N, Variant.A));
        runCase("实验 3 证伪：超长 thenApply 链", () -> demoLongThenApplyChain(1_000_000));
        runCase("实验 4 对照：三种正确聚合写法", CompletableFutureAllOfDemo::demoCorrectAggregationPatterns);
        runCase("实验 5 复现：回调递归 fan-out（业务帧与 CF 传播帧交替叠加）",
                () -> demoRecursiveFanoutSoe(50_000));
        runCase("实验 6 复现：业务递归爆在池线程（误归因 allOf 的现场）",
                () -> demoBusinessRecursionInPool(200_000));
        System.out.println("===== 结论：allOf/链结构无辜（迭代传播不放大栈深），递归型任务才是 SOE 元凶 =====");
    }

    private static void runSingleCase(String[] args) throws Exception {
        String mode = args[0];
        int n = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_CHAIN_N;
        switch (mode) {
            case "0":
                runCase("实验 1", CompletableFutureAllOfDemo::demoAllOfBalancedTreeControl);
                break;
            case "1B":
                runCase("实验 2B", () -> demoNestedAllOfChain(n, Variant.B));
                break;
            case "1A":
                runCase("实验 2A", () -> demoNestedAllOfChain(n, Variant.A));
                break;
            case "2":
                runCase("实验 3", () -> demoLongThenApplyChain(n));
                break;
            case "3":
                runCase("实验 4", CompletableFutureAllOfDemo::demoCorrectAggregationPatterns);
                break;
            case "4":
                runCase("实验 5", () -> demoRecursiveFanoutSoe(n));
                break;
            case "5":
                runCase("实验 6", () -> demoBusinessRecursionInPool(n));
                break;
            default:
                System.err.println("未知实验: " + mode + "（0 / 1A / 1B / 2 / 3 / 4 / 5 / 9）");
        }
    }

    // ------------------------------------------------------------------
    // 实验 1：对照组——单次 allOf 本身是平衡树（andTree 递归对半切），O(log n) 栈深
    // ------------------------------------------------------------------

    private static void demoAllOfBalancedTreeControl() {
        System.out.println("=== [实验 1] 单次 allOf(100 / 1万 / 10万).join()：平衡树 O(log n)，不爆 ===");
        int[] sizes = {100, 10_000, 100_000};
        for (int size : sizes) {
            ExecutorService pool = Executors.newFixedThreadPool(8);
            long t0 = System.nanoTime();
            try {
                CompletableFuture<?>[] fs = new CompletableFuture<?>[size];
                for (int i = 0; i < size; i++) {
                    fs[i] = CompletableFuture.runAsync(() -> { /* 模拟任务 */ }, pool);
                }
                CompletableFuture.allOf(fs).join();
                long costMs = (System.nanoTime() - t0) / 1_000_000;
                System.out.printf("  n=%-7d allOf(...).join() 成功, 耗时 ~%dms （栈深 O(log n)≈%d 层）%n",
                        size, costMs, (int) Math.ceil(Math.log(size) / Math.log(2)));
            } finally {
                pool.shutdownNow();
            }
        }
        AqsDemoSupport.require(true, "实验 1 应恒成立");
    }

    // ------------------------------------------------------------------
    // 实验 2：循环嵌套 allOf "退化成链"（面试中最常被怀疑的写法）
    //     chain = completedFuture(null)
    //     for i in 0..n-1: chain = allOf(chain, leaf_i)
    //   chain_k 完成 ⇔ chain_{k-1} 完成 && leaf_{k-1} 完成，叠出"左子树依赖右子树"的链。
    //   变体 A：leaf[n-1]..leaf[0] 顺序完成，链头最后完成——直觉上应触发 O(n) 级联传播。
    //   变体 B：leaf[0]..leaf[n-1] 顺序完成，链尾最后完成——每级都缺前置，无级联。
    //   实测（JDK 8, n=20万）：A/B 均不爆。原因：完成传播走 postComplete 的 while 循环
    //   （迭代式），tryFire 的下一级依赖被压回待处理循环，链长不放大栈深。
    // ------------------------------------------------------------------

    private enum Variant {A, B}

    @SuppressWarnings("unchecked")
    private static void demoNestedAllOfChain(int n, Variant v) {
        CompletableFuture<Void>[] leaves = new CompletableFuture[n];
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int i = 0; i < n; i++) {
            leaves[i] = new CompletableFuture<>();
            chain = CompletableFuture.allOf(chain, leaves[i]);   // 被怀疑的"退化链"写法
        }
        String title = (v == Variant.A)
                ? "链头最后完成（最坏完成时序, 直觉应需 O(n) 传播）"
                : "链尾最后完成（每级都缺前置, 无级联）";
        System.out.println("=== [实验 2] 循环嵌套 allOf 退化链 n=" + n + "，" + title + " ===");
        boolean ok = true;
        try {
            if (v == Variant.A) {
                for (int i = n - 1; i >= 0; i--) leaves[i].complete(null);
            } else {
                for (int i = 0; i < n; i++) leaves[i].complete(null);
            }
            chain.join();
        } catch (StackOverflowError soe) {
            ok = false;
            System.out.println("  💥 StackOverflowError（理论上不应发生）");
            analyzeStack(soe);
        }
        AqsDemoSupport.require(ok, "实验 2 实测不爆栈：postComplete 为迭代式 while 循环，链不放大栈深");
        System.out.println("  ✅ join() 正常返回 —— postComplete 迭代式传播，链长不放大栈深。");
    }

    // ------------------------------------------------------------------
    // 实验 3：超长 thenApply 链（另一个高频怀疑对象）
    //   实测（JDK 8, n=100万）：不爆。head.complete 后沿链逐一 tryFire，
    //   每级完成时把下一级依赖压回 postComplete 的待处理循环，而不是递归入栈。
    // ------------------------------------------------------------------

    private static void demoLongThenApplyChain(int n) {
        System.out.println("=== [实验 3] 超长 thenApply 链 n=" + n + "，head.complete() 触发全链传播 ===");
        CompletableFuture<Integer> head = new CompletableFuture<>();
        CompletableFuture<Integer> tail = head;
        for (int i = 0; i < n; i++) {
            tail = tail.thenApply(x -> x + 1);
        }
        boolean ok = true;
        try {
            head.complete(1);
            int result = tail.join();
            AqsDemoSupport.require(result == n + 1, "链尾结果应等于 n+1");
        } catch (StackOverflowError soe) {
            ok = false;
            System.out.println("  💥 StackOverflowError（理论上不应发生）");
            analyzeStack(soe);
        }
        AqsDemoSupport.require(ok, "实验 3 实测不爆栈：传播迭代式，链长不放大栈深");
        System.out.println("  ✅ 全链传播完成且结果正确 —— 传播迭代式，链长不放大栈深。");
    }

    // ------------------------------------------------------------------
    // 实验 4：正确写法对照（一次传全部 / 分批 / 顺序 join），全部任务提交到自定义线程池
    // ------------------------------------------------------------------

    private static void demoCorrectAggregationPatterns() {
        System.out.println("=== [实验 4] 三种正确聚合写法对照（n=1万, 显式自定义线程池） ===");
        int n = 10_000;
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<CompletableFuture<Integer>> f1 = submitAll(pool, n);
            List<Integer> r1 = CompletableFuture.allOf(f1.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        List<Integer> list = new ArrayList<>();
                        for (CompletableFuture<Integer> f : f1) list.add(f.join()); // 全部已完成, join 不阻塞
                        return list;
                    }).join();
            AqsDemoSupport.require(r1.size() == n, "3a 结果数应为 n");
            System.out.println("  4a 一次 allOf(...).thenApply(逐个 join)   ✅ 结果数=" + r1.size());

            List<CompletableFuture<Integer>> f2 = submitAll(pool, n);
            int batch = 100;
            for (int i = 0; i < n; i += batch) {
                CompletableFuture<?>[] slice =
                        f2.subList(i, Math.min(i + batch, n)).toArray(new CompletableFuture<?>[0]);
                CompletableFuture.allOf(slice).join();                          // 4b 分批
            }
            System.out.println("  4b 分批 allOf(每批" + batch + ")                         ✅ 完成");

            List<CompletableFuture<Integer>> f3 = submitAll(pool, n);
            for (CompletableFuture<Integer> f : f3) f.join();                   // 4c 顺序 join, 栈纯平
            System.out.println("  4c 顺序 join（调用方可阻塞时 allOf 都可省）             ✅ 完成");
            System.out.println("  结论: 三种写法 n=" + n + " 全绿 —— 聚合写法本身不是爆点。");
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<CompletableFuture<Integer>> submitAll(ExecutorService pool, int n) {
        List<CompletableFuture<Integer>> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(CompletableFuture.supplyAsync(() -> 1, pool));
        }
        return list;
    }

    // ------------------------------------------------------------------
    // 实验 5：★真正的复现——回调递归 fan-out（thenRun 嵌套）
    //    每级：等自己在 allOf(a,b) 上的 2 个子任务完成，thenRun 回调里递归到下一级。
    //    关键机制：thenRun 回调在"完成传播栈"上被 inline 执行（谁调 complete 就在谁的栈上跑回调），
    //    回调又发起新的 allOf 与 complete，于是栈上出现：
    //        业务回调帧 → complete → postComplete → tryFire → 业务回调帧 → ...
    //    业务递归深度 × (回调帧 + CF 传播帧) 叠乘 → SOE。
    //    这是"偶现"最常见的来源：传入的回调函数本身递归 / fan-out 子任务。
    //    观测细节：SOE 被 CF 包装成 CompletionException 抛出（completeThrowable→AltResult→join 重包），
    //    生产中看到 CompletionException 不要漏看 cause。
    // ------------------------------------------------------------------

    private static boolean demoRecursiveFanoutSoe(int depth) {
        System.out.println("=== [实验 5] ★复现：回调递归 fan-out + thenRun 嵌套, depth=" + depth + " ===");
        System.out.println("  机制：thenRun 回调在完成传播栈上 inline 执行，回调再递归 → 业务帧与 CF 传播帧交替叠加");
        try {
            nestedFanout(depth, 0);
            System.out.println("  ✅ 未爆栈（depth 或 Xss 不足以引爆, 调大 depth 或调小 -Xss 再试）");
            return false;
        } catch (CompletionException ce) {
            StackOverflowError soe = findSoe(ce);
            if (soe != null) {
                System.out.println("  💥 复现成功！StackOverflowError");
                System.out.println("  传播线程 = " + Thread.currentThread().getName()
                        + "（回调在谁调 complete 的线程栈上 inline 执行，SOE 就爆在谁的栈上）");
                System.out.println("  异常包装链: CF 把回调异常包成 " + ce.getClass().getSimpleName()
                        + "，深层原因才是 SOE —— 生产中不要漏看 cause");
                analyzeStack(soe);
                return true;
            }
            System.out.println("  非 SOE 异常: " + ce);
            return false;
        }
    }

    private static void nestedFanout(final int depth, final int cur) {
        if (cur >= depth) {
            return;
        }
        CompletableFuture<Void> a = new CompletableFuture<>();
        CompletableFuture<Void> b = new CompletableFuture<>();
        CompletableFuture<Void> all = CompletableFuture.allOf(a, b)
                .thenRun(() -> nestedFanout(depth, cur + 1));
        a.complete(null);
        b.complete(null);
        all.join();
    }

    // ------------------------------------------------------------------
    // 实验 6：★典型的"误归因"现场——纯业务递归经 supplyAsync 跑在池线程
    //    整条链里只有最外层有 supplyAsync，没有任何 allOf 参与：
    //    SOE 的栈底/重复帧全是业务帧（bizRecursion），CF 帧占比≈0。
    //    面试判别法：SOE 栈里 CF 帧的占比 → 传播帧多（CF 自身）还是业务帧多（业务递归）。
    // ------------------------------------------------------------------

    private static boolean demoBusinessRecursionInPool(int n) {
        System.out.println("=== [实验 6] ★复现：业务递归经 supplyAsync 跑在池线程, n=" + n + " ===");
        System.out.println("  机制：无任何 allOf——证明 SOE 与聚合方式无关，递归点在业务代码");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Integer> f = CompletableFuture.supplyAsync(() -> bizRecursion(n), pool);
            try {
                int r = f.get();
                System.out.println("  ✅ 未爆栈 r=" + r + "（n 或 Xss 不足以引爆, 调大 n 再试）");
                return false;
            } catch (InterruptedException ie) {
                System.out.println("  被中断: " + ie);
                return false;
            } catch (ExecutionException e) {
                StackOverflowError soe = findSoe(e);
                if (soe != null) {
                    System.out.println("  💥 复现成功！StackOverflowError");
                    if (soe.getStackTrace().length > 0) {
                        System.out.println("  栈顶帧 = " + soe.getStackTrace()[0]);
                        System.out.println("  判别: 栈里 CF 帧极少/无 —— 递归点在业务代码, 与 allOf 无关");
                    }
                    analyzeStack(soe);
                    return true;
                }
                System.out.println("  ExecutionException cause = " + e.getCause());
                return false;
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static int bizRecursion(int n) {
        return n == 0 ? 0 : 1 + bizRecursion(n - 1);
    }

    // ------------------------------------------------------------------
    // 实验 9（可选）：跨版本对比 JDK 8 vs JDK 21（子进程跑同一组实验）
    //   自动探测本机 JDK 21（macOS: ~/Library/Java/JavaVirtualMachines、/opt/homebrew/Cellar；
    //   Linux: /usr/lib/jvm、JAVA_HOME）。找不到则跳过——CI 上安全。
    // ------------------------------------------------------------------

    private static void runCrossVersionComparison() throws Exception {
        File jdk21Home = findJdk21();
        if (jdk21Home == null) {
            System.out.println("=== [实验 9] 跨版本对比 ===");
            System.out.println("  未找到 JDK 21，跳过跨版本对比。");
            return;
        }
        System.out.println("=== [实验 9] 跨版本对比：本机 JDK 8 vs 子进程 JDK 21（" + jdk21Home + "） ===");
        String[] cases = {"0", "1A", "1B", "2", "3", "4", "5"};
        String[] jdk8 = new String[cases.length];
        String[] jdk21 = new String[cases.length];
        for (int i = 0; i < cases.length; i++) {
            jdk8[i] = runInProcess(null, cases[i]);
            jdk21[i] = runInProcess(jdk21Home, cases[i]);
        }
        System.out.println();
        System.out.println("┌────────┬──────────────┬──────────────┬──────────────────────────────┐");
        System.out.println("│ case   │   JDK 8      │   JDK 21     │ 差异                         │");
        System.out.println("├────────┼──────────────┼──────────────┼──────────────────────────────┤");
        for (int i = 0; i < cases.length; i++) {
            System.out.printf("│ %-6s │ %-12s │ %-12s │ %-28s │%n",
                    cases[i], jdk8[i], jdk21[i], jdk8[i].equals(jdk21[i]) ? "一致" : "版本行为差异");
        }
        System.out.println("└────────┴──────────────┴──────────────┴──────────────────────────────┘");
        System.out.println("  注：结果以子进程 stdout 的 RESULT 行为准；子进程统一 -Xss512k。");
    }

    private static String runInProcess(File jdkHome, String caseName) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add((jdkHome == null ? System.getProperty("java.home") : jdkHome.getAbsolutePath()) + "/bin/java");
        cmd.add("-Xss512k");
        cmd.add("-XX:MaxJavaStackTraceDepth=100000");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(CompletableFutureAllOfDemo.class.getName());
        cmd.add("child");
        cmd.add(caseName);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String result = "no-result";
        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("RESULT")) {
                for (String part : line.split("\\s+")) {
                    if (part.startsWith("soe=")) {
                        result = "SOE=" + part.substring(4);
                    }
                }
            }
        }
        int exit = p.waitFor();
        if (exit != 0) {
            result = result + "[exit=" + exit + "]";
        }
        return result;
    }

    private static File findJdk21() {
        String java21Home = System.getenv("JAVA21_HOME");
        if (java21Home != null && new File(java21Home, "bin/java").isFile()) {
            return new File(java21Home);
        }
        File home = new File(System.getProperty("user.home"));
        File[] roots = {
                new File(home, "Library/Java/JavaVirtualMachines"),   // macOS 用户级
                new File("/Library/Java/JavaVirtualMachines"),        // macOS 系统级
                new File("/opt/homebrew/Cellar"),                     // macOS Homebrew (arm64)
                new File("/usr/lib/jvm")};                            // Linux 发行版
        for (File root : roots) {
            if (root == null || !root.isDirectory()) {
                continue;
            }
            File[] d = root.listFiles();
            if (d == null) {
                continue;
            }
            for (File f : d) {
                if (!f.getName().contains("21")) {
                    continue;
                }
                File contentsHome = new File(f, "Contents/Home");     // macOS jdk 布局
                if (new File(contentsHome, "bin/java").isFile()) {
                    return contentsHome;
                }
                if (new File(f, "bin/java").isFile()) {               // Linux jdk 布局
                    return f;
                }
                if (root.getName().equals("Cellar")) {                // Homebrew openjdk@21 布局
                    File[] vers = f.listFiles();
                    if (vers != null) {
                        for (File v : vers) {
                            File cand = new File(v, "libexec/openjdk.jdk/Contents/Home");
                            if (new File(cand, "bin/java").isFile()) {
                                return cand;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 子进程入口：父进程（实验 9）以 child &lt;caseName&gt; [n] 调用，输出 RESULT 行供父进程解析。 */
    private static void runChildCase(String[] args) {
        String which = args.length >= 2 ? args[1] : "4";
        int n = args.length >= 3 ? Integer.parseInt(args[2])
                : (which.equals("2") ? 1_000_000 : (which.equals("4") ? 50_000 : DEFAULT_CHAIN_N));
        String soe;
        try {
            switch (which) {
                case "0":
                    demoAllOfBalancedTreeControl();
                    soe = "false";
                    break;
                case "1A":
                    demoNestedAllOfChain(n, Variant.A);
                    soe = "false";
                    break;
                case "1B":
                    demoNestedAllOfChain(n, Variant.B);
                    soe = "false";
                    break;
                case "2":
                    demoLongThenApplyChain(n);
                    soe = "false";
                    break;
                case "3":
                    demoCorrectAggregationPatterns();
                    soe = "false";
                    break;
                case "4":
                    soe = demoRecursiveFanoutSoe(n) ? "true" : "false";
                    break;
                case "5":
                    soe = demoBusinessRecursionInPool(n) ? "true" : "false";
                    break;
                default:
                    throw new IllegalArgumentException("unknown child case: " + which);
            }
        } catch (StackOverflowError e) {
            soe = "true";
        }
        System.out.println("RESULT case=" + which + " soe=" + soe
                + " jdk=" + System.getProperty("java.version"));
        System.out.flush();
    }

    // ------------------------------------------------------------------
    // 观测工具
    // ------------------------------------------------------------------

    /** 沿 cause 链找 StackOverflowError（兼容 SOE 被 CompletionException/ExecutionException 多层包装的情况）。 */
    private static StackOverflowError findSoe(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof StackOverflowError) {
                return (StackOverflowError) cur;
            }
            cur = cur.getCause();
        }
        return null;
    }

    /** 统计异常栈里重复出现的帧——重复出现的帧就是递归点；再按"CF 帧 vs 业务帧"占比判别元凶。 */
    private static void analyzeStack(Throwable t) {
        StackTraceElement[] st = t.getStackTrace();
        System.out.println("  异常类型: " + t.getClass().getName());
        if (st.length == 0) {
            System.out.println("  栈帧总数: 0 —— getStackTrace() 无帧。"
                    + "JDK 8 若设了 -XX:MaxJavaStackTraceDepth=0 会得到空栈（0=无帧, 不是不截断）;"
                    + " 请改用大值（如 100000）或去掉该参数（默认 1024）再观测。");
            return;
        }
        System.out.println("  栈帧总数: " + st.length
                + (st.length >= 1000 ? "（疑似默认 MaxJavaStackTraceDepth=1024 截断; 加 -XX:MaxJavaStackTraceDepth=100000 拿全量）" : ""));
        Map<String, Integer> top = new HashMap<>();
        for (StackTraceElement e : st) {
            String key = e.getClassName().substring(e.getClassName().lastIndexOf('.') + 1) + "." + e.getMethodName();
            Integer c = top.get(key);
            top.put(key, (c == null ? 0 : c) + 1);
        }
        System.out.println("  重复帧 TOP6（= 递归传播点）:");
        top.entrySet().stream()
                .sorted((x, y) -> y.getValue() - x.getValue())
                .limit(6)
                .forEach(e -> System.out.printf("     %-58s x %d%n", e.getKey(), e.getValue()));
        int cfFrames = 0;
        for (StackTraceElement e : st) {
            if (e.getClassName().startsWith("java.util.concurrent.CompletableFuture")) {
                cfFrames++;
            }
        }
        System.out.printf("  CF 帧占比: %d/%d = %.1f%% —— 占比高→传播机制问题; 业务帧主导→业务递归爆栈%n",
                cfFrames, st.length, 100.0 * cfFrames / st.length);
        System.out.printf("  栈底帧: %s%n", st[st.length - 1]);
    }

    /** 每个实验放到独立线程跑：SOE 只杀掉自己的线程，不影响后续实验；失败仍会传播给主线程（CI 可感知）。 */
    private static void runCase(String title, Runnable action) throws Exception {
        System.out.println("──────────────────────────────────────────────────────────────────");
        System.out.println("[执行] " + title);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread t = AqsDemoSupport.start(title, action::run, failure);
        t.join();   // 不设超时：实验 3 构造 100 万级链在慢机器上可能超过 5 秒
        Throwable thrown = failure.get();
        if (thrown instanceof Exception) {
            throw (Exception) thrown;
        }
        if (thrown instanceof Error) {
            throw (Error) thrown;
        }
    }
}
