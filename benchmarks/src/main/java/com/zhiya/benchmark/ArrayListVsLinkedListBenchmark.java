package com.zhiya.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 📦 ArrayList vs LinkedList 全维度纳秒级基准测试套件
 *
 * 知识库对应：
 * - Level 2 · List 的三种面孔 —— ArrayList vs LinkedList vs Vector
 *   - 连续内存 vs 离散内存、CPU 缓存行（Cache Line）预取机制
 *   - 均摊 O(1) 扩容 vs O(1) 挂节点
 *   - System.arraycopy() native 指令优化
 *
 * 【测试维度与全量方法矩阵】
 * 1. 随机访问 (Random Access):
 *    - arrayList_RandomGet   : ArrayList O(1) 直接算地址 base + i × 4
 *    - linkedList_RandomGet  : LinkedList O(n) 从头遍历到第 i 个节点
 *
 * 2. 尾部追加 (Append):
 *    - arrayList_Append      : ArrayList 均摊 O(1)，偶尔扩容 O(n)
 *    - linkedList_Append     : LinkedList O(1) 直接挂尾节点
 *
 * 3. 头部插入 (Prepend):
 *    - arrayList_Prepend     : ArrayList O(n) 后面元素全移
 *    - linkedList_Prepend    : LinkedList O(1) 改指针
 *
 * 4. 顺序遍历 (Iteration):
 *    - arrayList_ForEach     : 连续内存，CPU 缓存预取命中率高
 *    - linkedList_ForEach    : 离散内存，缓存命中率低
 *
 * 验证人：imZhiYa
 * 运行方式：
 *   cd benchmarks
 *   mvn clean package -DskipTests
 *   java -jar target/benchmarks.jar ArrayListVsLinkedListBenchmark
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ArrayListVsLinkedListBenchmark {

    @Param({"100", "1000", "10000"})
    private int size;

    private ArrayList<Integer> arrayList;
    private LinkedList<Integer> linkedList;
    private Random random;

    @Setup
    public void setup() {
        arrayList = new ArrayList<>();
        linkedList = new LinkedList<>();
        random = new Random(42);
        for (int i = 0; i < size; i++) {
            arrayList.add(i);
            linkedList.add(i);
        }
    }

    /**
     * ArrayList 随机访问：直接算地址 base + i × 4，O(1)
     * CPU 缓存预取机制命中率极高
     */
    @Benchmark
    public int arrayList_RandomGet(Blackhole bh) {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += arrayList.get(random.nextInt(size));
        }
        bh.consume(sum);
        return sum;
    }

    /**
     * LinkedList 随机访问：从头遍历到第 i 个节点，O(n)
     * 内存不连续，CPU 缓存预取失效
     */
    @Benchmark
    public int linkedList_RandomGet(Blackhole bh) {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += linkedList.get(random.nextInt(size));
        }
        bh.consume(sum);
        return sum;
    }

    /**
     * ArrayList 尾部追加：均摊 O(1)，触发扩容时 O(n)
     * 扩容策略：1.5 倍（oldCapacity + (oldCapacity >> 1)）
     */
    @Benchmark
    public void arrayList_Append(Blackhole bh) {
        ArrayList<Integer> copy = new ArrayList<>(arrayList);
        copy.add(999);
        bh.consume(copy);
    }

    /**
     * LinkedList 尾部追加：O(1)，直接挂尾节点
     * 每个节点额外 24 字节指针开销（prev + next + item）× 8
     */
    @Benchmark
    public void linkedList_Append(Blackhole bh) {
        LinkedList<Integer> copy = new LinkedList<>(linkedList);
        copy.add(999);
        bh.consume(copy);
    }

    /**
     * ArrayList 头部插入：O(n)，System.arraycopy() 移动所有元素
     * native 方法有 CPU 指令优化，但仍需搬移整个数组
     */
    @Benchmark
    public void arrayList_Prepend(Blackhole bh) {
        ArrayList<Integer> copy = new ArrayList<>(arrayList);
        copy.add(0, 999);
        bh.consume(copy);
    }

    /**
     * LinkedList 头部插入：O(1)，直接改 head 指针
     * 这是 LinkedList 唯一的理论优势场景
     */
    @Benchmark
    public void linkedList_Prepend(Blackhole bh) {
        LinkedList<Integer> copy = new LinkedList<>(linkedList);
        copy.addFirst(999);
        bh.consume(copy);
    }

    /**
     * ArrayList 顺序遍历：连续内存，CPU 缓存行预取命中率高
     * 每次加载一个缓存行（通常 64 字节），预取下一个
     */
    @Benchmark
    public int arrayList_ForEach(Blackhole bh) {
        int sum = 0;
        for (int i : arrayList) {
            sum += i;
        }
        bh.consume(sum);
        return sum;
    }

    /**
     * LinkedList 顺序遍历：离散内存，缓存预取失效
     * 每次访问下一个节点都可能触发缓存未命中（Cache Miss）
     */
    @Benchmark
    public int linkedList_ForEach(Blackhole bh) {
        int sum = 0;
        for (int i : linkedList) {
            sum += i;
        }
        bh.consume(sum);
        return sum;
    }
}
