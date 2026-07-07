package com.zhiya.benchmark;

import com.zhiya.tree.BST;
import com.zhiya.tree.MaxHeap;
import com.zhiya.tree.MinHeap;
import com.zhiya.tree.RedBlackTree;
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

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 树形数据结构 · 微基准测试套件
 *
 * 覆盖：BST / MaxHeap / MinHeap / RedBlackTree
 * 规模：1K / 10K / 100K 三档参数化对比
 * 维度：插入 (bulk build) / 查找 (命中 / 未命中) / 取顶 (堆)
 *
 * 验证人：imZhiYa
 * 运行方式：
 *   cd benchmarks
 *   mvn clean package -DskipTests
 *   java -jar target/benchmarks.jar TreeBenchmark
 *
 * ⚠️ 范围说明（基于现有 tree-demo 公开 API）：
 *   - BST 跳过 delete：BST.delete 内部含 System.out.println，干扰 stdout 与计时。
 *   - RedBlackTree 跳过 get/contains：当前实现未对外暴露 get 方法，
 *     只能测 put + 通过 Blackhole.consume 防止 JIT 消除。
 *   - MaxHeap / MinHeap：均使用无参构造器公平对比（MinHeap 无容量构造器）。
 *     MaxHeap 额外提供 maxHeapBulkInsertPrealloc 测扩容开销。
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class TreeBenchmark {

    // ====================================================================
    // 工具方法
    // ====================================================================

    /** Fisher-Yates 洗牌：构造 [0, n) 的伪随机排列（确定性 seed） */
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

    /** 构造绝对命中不到的 miss 集（高偏移区间） */
    private static int[] missSet(int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i + 1_000_000_000;
        return a;
    }

    /** 从数组中随机取一个元素（防止 JIT 折叠） */
    private static int pickRandom(int[] a) {
        return a[ThreadLocalRandom.current().nextInt(a.length)];
    }

    // ====================================================================
    // BST 基准
    // ====================================================================

    @State(Scope.Thread)
    public static class BstState {
        @Param({"1000", "10000", "100000"})
        int n;

        BST tree;
        int[] hitSet;
        int[] missSet;

        @Setup(Level.Trial)
        public void setup() {
            int[] data = randomUnique(n, 42L);
            tree = new BST();
            for (int v : data) tree.insert(v);
            hitSet = data;
            //missSet = TreeBenchmark.missSet(n);
            missSet = randomUnique(n, 99L);  // 用不同 seed 避免和 hit 集重复
            for (int i = 0; i < missSet.length; i++) missSet[i] += n;
        }
    }

    /** BST 单次查找（命中） */
    @Benchmark
    public boolean bstSearchHit(BstState s) {
        return s.tree.search(pickRandom(s.hitSet));
    }

    /** BST 单次查找（未命中） */
    @Benchmark
    public boolean bstSearchMiss(BstState s) {
        return s.tree.search(pickRandom(s.missSet));
    }

    /**
     * BST 批量插入 N 个元素的整体耗时
     * 每轮 invocation 新建一棵树，测得的是"建一棵 N 节点 BST"的总时间
     */
    @Benchmark
    public void bstBulkInsert(BstState s, Blackhole bh) {
        BST t = new BST();
        for (int v : s.hitSet) t.insert(v);
        bh.consume(t);
    }

    // ====================================================================
    // MaxHeap 基准
    // ====================================================================

    @State(Scope.Thread)
    public static class MaxHeapState {
        @Param({"1000", "10000", "100000"})
        int n;

        int[] data;

        @Setup(Level.Trial)
        public void setup() {
            data = randomUnique(n, 42L);
        }
    }

    /** MaxHeap 批量插入（无参构造，含扩容开销） */
    @Benchmark
    public void maxHeapBulkInsert(MaxHeapState s, Blackhole bh) {
        MaxHeap h = new MaxHeap();
        for (int v : s.data) h.insert(v);
        bh.consume(h);
    }

    /** MaxHeap 批量插入（预分配容量 n，跳过扩容路径） */
    @Benchmark
    public void maxHeapBulkInsertPrealloc(MaxHeapState s, Blackhole bh) {
        MaxHeap h = new MaxHeap(s.n);
        for (int v : s.data) h.insert(v);
        bh.consume(h);
    }

    @State(Scope.Thread)
    public static class MaxHeapPeekState {
        @Param({"1000", "10000", "100000"})
        int n;

        MaxHeap heap;

        @Setup(Level.Trial)
        public void setup() {
            heap = new MaxHeap();
            int[] data = randomUnique(n, 42L);
            for (int v : data) heap.insert(v);
        }
    }

    /** MaxHeap 单次 peek（堆顶访问） */
    @Benchmark
    public int maxHeapPeek(MaxHeapPeekState s) {
        return s.heap.peek();
    }

    @State(Scope.Thread)
    public static class MaxHeapExtractState {
        @Param({"1000", "10000", "100000"})
        int n;

        int[] data;

        @Setup(Level.Trial)
        public void setup() {
            data = randomUnique(n, 42L);
        }
    }

    /** MaxHeap 单次 extractMax（含 siftDown 路径） */
    @Benchmark
    public int maxHeapExtract(MaxHeapExtractState s, Blackhole bh) {
        MaxHeap h = new MaxHeap(s.n);
        for (int v : s.data) h.insert(v);
        int x = h.extractMax();
        bh.consume(h);
        return x;
    }

    // ====================================================================
    // MinHeap 基准（接口与 MaxHeap 镜像，无参构造）
    // ====================================================================

    @State(Scope.Thread)
    public static class MinHeapState {
        @Param({"1000", "10000", "100000"})
        int n;

        int[] data;

        @Setup(Level.Trial)
        public void setup() {
            data = randomUnique(n, 42L);
        }
    }

    /** MinHeap 批量插入（无参构造，含扩容开销） */
    @Benchmark
    public void minHeapBulkInsert(MinHeapState s, Blackhole bh) {
        MinHeap h = new MinHeap();
        for (int v : s.data) h.insert(v);
        bh.consume(h);
    }

    @State(Scope.Thread)
    public static class MinHeapPeekState {
        @Param({"1000", "10000", "100000"})
        int n;

        MinHeap heap;

        @Setup(Level.Trial)
        public void setup() {
            heap = new MinHeap();
            int[] data = randomUnique(n, 42L);
            for (int v : data) heap.insert(v);
        }
    }

    /** MinHeap 单次 peek */
    @Benchmark
    public int minHeapPeek(MinHeapPeekState s) {
        return s.heap.peek();
    }

    /** MinHeap 单次 extractMin */
    @Benchmark
    public int minHeapExtract(MinHeapState s, Blackhole bh) {
        MinHeap h = new MinHeap();
        for (int v : s.data) h.insert(v);
        int x = h.extractMin();
        bh.consume(h);
        return x;
    }

    // ====================================================================
    // RedBlackTree 基准
    // ====================================================================

    @State(Scope.Thread)
    public static class RbtState {
        @Param({"1000", "10000", "100000"})
        int n;

        int[] data;

        @Setup(Level.Trial)
        public void setup() {
            data = randomUnique(n, 42L);
        }
    }

    /**
     * RedBlackTree 批量 put N 个键值对
     * 当前 RBT 没有对外暴露 get/delete，所以本测试只覆盖 put。
     * 通过 Blackhole.consume(tree) 双重防御 JIT 消除。
     */
    @Benchmark
    public void rbtBulkPut(RbtState s, Blackhole bh) {
        RedBlackTree<Integer, String> tree = new RedBlackTree<>();
        for (int v : s.data) tree.put(v, "v" + v);
        bh.consume(tree);
    }

    /**
     * RedBlackTree 重复 put 同一 key（覆盖"键已存在则更新 val"分支）
     */
    @Benchmark
    public void rbtPutUpdate(RbtState s, Blackhole bh) {
        RedBlackTree<Integer, String> tree = new RedBlackTree<>();
        // 第一次插入：建树
        for (int v : s.data) tree.put(v, "v" + v);
        // 第二次插入：同 key 不同 val，走"更新 val"分支
        for (int v : s.data) tree.put(v, "u" + v);
        bh.consume(tree);
    }
}
