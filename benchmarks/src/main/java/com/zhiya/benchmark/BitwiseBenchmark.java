package com.zhiya.benchmark;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

/**
 * 二进制位运算 vs 传统算术指令及逻辑判定基准测试
 * 验证人：imZhiYa | 运行方式：mvn clean install && java -jar target/benchmarks.jar
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BitwiseBenchmark {

    private int valueA = 1147483648;
    // 严谨同步前文叙事：统一维持 2,147,483,646
    private int valueB = 2147483646; 
    // 模拟真实的复杂网络包标记位：包含无关的高位标记，用以检验位运算短路精准度
    private int clientFlags = 0b1000_0010; 
    private int TRUSTED_IP = 1 << 1;  // 0b0000_0010
    private int NORMAL_RATE = 1 << 2; // 0b0000_0100

    @Benchmark
    public int testMidDivision() {
        return (valueA + valueB) / 2; 
    }

    @Benchmark
    public int testMidRightShift() {
        return (valueA + valueB) >>> 1;
    }

    @Benchmark
    public boolean testDeMorganConventional() {
        return (~clientFlags & TRUSTED_IP) != 0 && (~clientFlags & NORMAL_RATE) != 0;
    }

    @Benchmark
    public boolean testDeMorganOptimized() {
        // 严谨修复：通过复合掩码聚合，直接判断目标位是否全为0，完美规避无关高位取反导致的脏数据错判
        return (clientFlags & (TRUSTED_IP | NORMAL_RATE)) == 0;
    }
}
