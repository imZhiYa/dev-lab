package com.zhiya.binary;

/**
 * 亿级大并发去重基数评估：手写 HyperLogLog 核心算法
 * 演示：利用二进制哈希流中前置零极大值规律，以极致空间成本估算千万 UV
 */
public class HyperLogLogBitwiseEstimator {

    // 申请 16384 (2^14) 个独立注册分桶 (严格对齐 Redis 原生 HLL 结构)
    private static final int HLL_P = 14; 
    private static final int HLL_BUCKETS = 1 << HLL_P; // 16384
    private static final int BUCKET_MASK = HLL_BUCKETS - 1; // 0x3FFF (定位分桶掩码)

    // 分桶槽位，实际存储时每个桶仅需 6 Bit (此处为直观演示采用 byte[])
    private final byte[] buckets = new byte[HLL_BUCKETS];

    /**
     * 🔵 投入请求统计 (位操作提取分桶号与零流极大值)
     */
    public void add(String element) {
        long hash = murmurHash64(element);

        // 1. 低 14 位截取，决断投入哪一个抽屉分桶
        int bucketIndex = (int) (hash & BUCKET_MASK);

        // 2. 将其余 50 位向右偏移，测算剩余总线的连续前置领零个数 (抛硬币心智)
        long remainingStream = hash >>> HLL_P;
        
        // 3. 统计该槽道出现第一个 1 之前有多少个纯净 0 (即测算抛出正面之前的反面连胜局数)
        int zeros = Long.numberOfLeadingZeros(remainingStream) - HLL_P + 1;

        // 4. 只保存该分桶下出现过的最大领零长度
        if (zeros > buckets[bucketIndex]) {
            buckets[bucketIndex] = (byte) zeros;
        }
    }

    /**
     * 🟢 提取基数评估结果 (基于调和平均公式修正)
     */
    public long estimate() {
        double harmonicMeanSum = 0;
        int zeroBuckets = 0;

        for (byte maxZeros : buckets) {
            if (maxZeros == 0) {
                zeroBuckets++;
            }
            // 累加 2^(-maxZeros)
            harmonicMeanSum += 1.0 / (1L << maxZeros);
        }

        // HLL 常数估算修正系数 (0.7213 / (1 + 1.079 / BUCKETS))
        double alphaMM = 0.7213 / (1.0 + 1.079 / HLL_BUCKETS) * HLL_BUCKETS * HLL_BUCKETS;
        double rawEstimate = alphaMM / harmonicMeanSum;

        // 稀疏状态下的线性计数修正 (Linear Counting)
        if (rawEstimate <= 2.5 * HLL_BUCKETS && zeroBuckets > 0) {
            return (long) (HLL_BUCKETS * Math.log((double) HLL_BUCKETS / zeroBuckets));
        }

        return (long) rawEstimate;
    }

    // 独立 64 位纯正高阶散列
    private long murmurHash64(String key) {
        long h = 0xcafebabe12345678L;
        for (char c : key.toCharArray()) {
            h ^= c;
            h *= 0x5bd1e9955bd1e995L;
            h ^= h >>> 47;
        }
        return h;
    }

    public static void main(String[] args) {
        HyperLogLogBitwiseEstimator hll = new HyperLogLogBitwiseEstimator();
        System.out.println("==================== [ HyperLogLog 亿级去重推演 ] ====================");

        int targetUV = 100000; // 模拟 10 万个完全独立的防重复请求
        System.out.println("🚀 开启高流测试，模拟涌入独立访客数: " + targetUV);

        for (int i = 0; i < targetUV; i++) {
            hll.add("USER-TOKEN-HASH-ID-" + i);
        }
        
        // 顺手丢入大量重复访问 (检验去重免疫力)
        for (int i = 0; i < 50000; i++) {
            hll.add("USER-TOKEN-HASH-ID-" + i); // 属于已访问过的重复群体
        }

        long estimateUV = hll.estimate();
        double errorRate = Math.abs((double)(estimateUV - targetUV) / targetUV) * 100.0;

        System.out.println("📊 HLL 基数估算评估输出: " + estimateUV);
        System.out.println("🎯 真实值与计算容差值:   " + String.format("%.2f", errorRate) + "% (常态方差 < 1%，但省下 99.9% 内存！)");
    }
}
