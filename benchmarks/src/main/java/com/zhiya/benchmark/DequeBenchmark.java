package com.zhiya.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 📚 ArrayDeque vs LinkedList vs Stack 全维度纳秒级基准测试套件
 *
 * 知识库对应：
 * - Level 7.5 · Queue/Deque 家族概览 —— 被忽视的第三条线
 *
 * 验证人：imZhiYa
 * 运行方式：
 *   java -jar target/benchmarks.jar DequeBenchmark
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DequeBenchmark {

    @Param({"100", "1000", "10000"})
    private int ops;

    @Benchmark
    public void arrayDeque_Stack(Blackhole bh) {
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        for (int i = 0; i < ops; i++) {
            stack.push(i);
        }
        for (int i = 0; i < ops; i++) {
            stack.pop();
        }
        bh.consume(stack);
    }

    @Benchmark
    public void linkedList_Stack(Blackhole bh) {
        LinkedList<Integer> stack = new LinkedList<>();
        for (int i = 0; i < ops; i++) {
            stack.push(i);
        }
        for (int i = 0; i < ops; i++) {
            stack.pop();
        }
        bh.consume(stack);
    }

    @Benchmark
    public void vectorStack_Stack(Blackhole bh) {
        Stack<Integer> stack = new Stack<>();
        for (int i = 0; i < ops; i++) {
            stack.push(i);
        }
        for (int i = 0; i < ops; i++) {
            stack.pop();
        }
        bh.consume(stack);
    }

    @Benchmark
    public void arrayDeque_Queue(Blackhole bh) {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < ops; i++) {
            queue.offer(i);
        }
        for (int i = 0; i < ops; i++) {
            queue.poll();
        }
        bh.consume(queue);
    }

    @Benchmark
    public void linkedList_Queue(Blackhole bh) {
        LinkedList<Integer> queue = new LinkedList<>();
        for (int i = 0; i < ops; i++) {
            queue.offer(i);
        }
        for (int i = 0; i < ops; i++) {
            queue.poll();
        }
        bh.consume(queue);
    }
}
