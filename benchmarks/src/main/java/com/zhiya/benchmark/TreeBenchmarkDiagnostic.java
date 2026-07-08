package com.zhiya.benchmark;

import com.zhiya.tree.BST;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * TreeBenchmark 诊断版 · 排查 bstSearchHit/Miss 13x 差异的根因
 *
 * <h3>背景</h3>
 * TreeBenchmark 在 n=10000 时:
 *   bstSearchHit   = 0.093 us/op (93 ns)
 *   bstSearchMiss  = 0.016 us/op (16 ns)  ← 比 hit 快 5.8x, 反直觉
 *
 * <h3>7 个对照实验</h3>
 * <ul>
 *   <li>diag1_randomHit    复现 TreeBenchmark 的 bstSearchHit (用预生成下标)</li>
 *   <li>diag2_randomMiss   复现 TreeBenchmark 的 bstSearchMiss</li>
 *   <li>diag3_fixedHit     固定下标,剥离 ThreadLocalRandom 开销 (验证 H4)</li>
 *   <li>diag4_fixedMiss    固定下标,剥离 ThreadLocalRandom 开销</li>
 *   <li>diag5_alternating  交替 hit/miss,观察 deopt 触发</li>
 *   <li>diag6_searchHitFirst   固定查 hitSet[0] (短路径参考)</li>
 *   <li>diag7_searchMissFirst  固定查 missSet[0] (长路径参考)</li>
 * </ul>
 *
 * <h3>关键设计</h3>
 * 所有 @Benchmark 方法返回 <b>void</b>,通过 Blackhole.consume 消费结果。
 * 绝对不能用 {@code return bh.consume(x)} (consume 返回 void,Java 编译错误)。
 *
 * <h3>使用方式</h3>
 * <pre>
 *   # 1) 默认输出 (stdout 含 [DIAG] 调试信息)
 *   java -jar target/benchmarks.jar TreeBenchmarkDiagnostic -p n=10000
 *
 *   # 2) 加 stack profiler,看每个方法真实烧 CPU 时间
 *   java -jar target/benchmarks.jar TreeBenchmarkDiagnostic -p n=10000 -prof stack
 * </pre>
 *
 * <h3>预期 #45 诊断结果 (n=10000)</h3>
 * <ul>
 *   <li>diag1 ≈ 93 ns, diag2 ≈ 16 ns  → 复现 TreeBenchmark 的 5.8x 差异</li>
 *   <li>diag3 ≈ diag1, diag4 ≈ diag2  → 否定 H4 (随机数开销不是根因)</li>
 *   <li>diag5 ≈ diag3 + diag4 之和     → 排除 deopt 切换</li>
 *   <li>diag6 vs diag7                  → 验证 H3 (路径深度)</li>
 *   <li>stack profiler                  → 验证 H2 (JIT 内联/特化)</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)   // 纳秒精度,看细节
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class TreeBenchmarkDiagnostic {

    @Param({"10000"})
    int n;

    BST tree;
    int[] hitSet;
    int[] missSet;
    int[] randomOrder;  // 预生成的下标序列,模拟 ThreadLocalRandom.nextInt 行为
    int cursor;         // randomOrder 游标

    @Setup(Level.Trial)
    public void setup() {
        // ===== 1. 构建 BST (与 TreeBenchmark.BstState 同样的方式) =====
        int[] data = randomUnique(n, 42L);
        tree = new BST();
        for (int v : data) tree.insert(v);
        hitSet = data;

        // ===== 2. 构建 miss 集 (与 TreeBenchmark.BstState commit 945722e 同样的方式) =====
        missSet = randomUnique(n, 99L);
        for (int i = 0; i < missSet.length; i++) missSet[i] += n;

        // ===== 3. 预生成 1024 个随机下标 (与 ThreadLocalRandom.nextInt(n) 行为近似) =====
        Random r = new Random(7777L);
        randomOrder = new int[1024];
        for (int i = 0; i < randomOrder.length; i++) {
            randomOrder[i] = r.nextInt(n);
        }
        cursor = 0;

        // ===== 4. ⭐ 关键调试输出 (CI 日志可直接看到) ⭐ =====
        // 验证 H1: hitSet 和 missSet 是否真的不重叠
        System.out.println("========================================");
        System.out.println("[DIAG] n = " + n);
        System.out.println("[DIAG] hitSet[0..4]    = " + Arrays.toString(Arrays.copyOf(hitSet, 5)));
        System.out.println("[DIAG] missSet[0..4]   = " + Arrays.toString(Arrays.copyOf(missSet, 5)));
        System.out.println("[DIAG] hitSet[n-1]     = " + hitSet[n - 1]);
        System.out.println("[DIAG] missSet[n-1]    = " + missSet[n - 1]);
        System.out.println("[DIAG] search(hitSet[0])     = " + tree.search(hitSet[0]));
        System.out.println("[DIAG] search(hitSet[n/2])   = " + tree.search(hitSet[n / 2]));
        System.out.println("[DIAG] search(hitSet[last])  = " + tree.search(hitSet[n - 1]));
        System.out.println("[DIAG] search(missSet[0])    = " + tree.search(missSet[0]));
        System.out.println("[DIAG] search(missSet[n/2])  = " + tree.search(missSet[n / 2]));
        System.out.println("[DIAG] hitSet contains missSet[0]? " + contains(hitSet, missSet[0]));
        System.out.println("[DIAG] missSet contains hitSet[0]? " + contains(missSet, hitSet[0]));
        System.out.println("========================================");
    }

    /** O(n) 包含检查,只在 @Setup 跑一次 */
    private static boolean contains(int[] arr, int v) {
        for (int x : arr) if (x == v) return true;
        return false;
    }

    /** Fisher-Yates 洗牌 (与 TreeBenchmark.randomUnique 一致) */
    private static int[] randomUnique(int n, long seed) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i;
        Random r = new Random(seed);
        for (int i = n - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
        return a;
    }

    // ====================================================================
    // 实验 1: 复现 TreeBenchmark.bstSearchHit (用预生成下标,不用 ThreadLocalRandom)
    // ====================================================================
    @Benchmark
    public void diag1_randomHit(Blackhole bh) {
        int v = hitSet[randomOrder[cursor++ & 1023]];
        bh.consume(tree.search(v));
    }

    // ====================================================================
    // 实验 2: 复现 TreeBenchmark.bstSearchMiss
    // ====================================================================
    @Benchmark
    public void diag2_randomMiss(Blackhole bh) {
        int v = missSet[randomOrder[cursor++ & 1023]];
        bh.consume(tree.search(v));
    }

    // ====================================================================
    // 实验 3: 固定下标 hit (验证 H4: 剥离 ThreadLocalRandom 开销)
    // ====================================================================
    @Benchmark
    public void diag3_fixedHit(Blackhole bh) {
        bh.consume(tree.search(hitSet[5000]));
    }

    // ====================================================================
    // 实验 4: 固定下标 miss
    // ====================================================================
    @Benchmark
    public void diag4_fixedMiss(Blackhole bh) {
        bh.consume(tree.search(missSet[5000]));
    }

    // ====================================================================
    // 实验 5: 交替 hit/miss (观察 deopt 触发 / 缓存污染)
    // ====================================================================
    @Benchmark
    public void diag5_alternating(Blackhole bh) {
        bh.consume(tree.search(hitSet[5000]));
        bh.consume(tree.search(missSet[5000]));
    }

    // ====================================================================
    // 实验 6: 查 hitSet[0] (BST 第一个插入,可能路径深度比较随机)
    // ====================================================================
    @Benchmark
    public void diag6_searchHitFirst(Blackhole bh) {
        bh.consume(tree.search(hitSet[0]));
    }

    // ====================================================================
    // 实验 7: 查 missSet[0] (固定一个 miss 元素)
    // ====================================================================
    @Benchmark
    public void diag7_searchMissFirst(Blackhole bh) {
        bh.consume(tree.search(missSet[0]));
    }
}
