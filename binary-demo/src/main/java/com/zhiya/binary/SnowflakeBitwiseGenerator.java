package com.zhiya.binary;

/**
 * 分布式高并发雪花主键引擎
 * 演示：利用纯正的左移 << 、按位或 | 与 掩码归零，拼装 64 位独立数字
 */
public class SnowflakeBitwiseGenerator {

    // ==========================================
    // 1. 各部分位线界限与偏移规定
    // ==========================================
    // 起始纪元时间戳 (2026-01-01 00:00:00)
    private static final long TWEPOCH = 1767225600000L; 
    // 各层分配长宽
    private static final long WORKER_ID_BITS = 10L; // 节点标识独占 10 位 (支持 1024 台机器)
    private static final long SEQUENCE_BITS  = 12L; // 毫秒内自增序列 12 位 (每毫秒 4096 个 ID)

    // 左移偏置标尺
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS; // 机器号向左移 12 位
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS; // 时间戳向左移 22 位

    // 掩码大闸：利用补元律生成 12 位全 1 掩码 (0b111111111111 = 4095)
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    // 运行实态参数
    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeBitwiseGenerator(long workerId) {
        this.workerId = workerId;
    }

    /**
     * 🔵 纯二进制位移组装核心逻辑
     */
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("时钟回调异常，拒绝派发主键。");
        }

        if (lastTimestamp == timestamp) {
            // 在同一毫秒内，利用掩码取余序列号 (超过 4095 瞬间归零)
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 当前毫秒内编号耗尽，借由死循环轮询下一毫秒
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L; // 新毫秒归位
        }

        lastTimestamp = timestamp;

        // 🚀 核心大招：利用左移 << 推入插槽，依靠按位或 | 强行合并！
        return ((timestamp - TWEPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    public static void main(String[] args) {
        // 配置运行在第 1 号主机节点
        SnowflakeBitwiseGenerator snowflake = new SnowflakeBitwiseGenerator(1L);
        System.out.println("==================== [ 分布式雪花主键位移合成 ] ====================");

        // 连续派发 3 个高频分布式 ID
        long id1 = snowflake.nextId();
        long id2 = snowflake.nextId();
        long id3 = snowflake.nextId();

        System.out.println("🚀 派发主键 1: " + id1 + " | 内存位线: " + BinaryUtils.toPrettyBinary((int)id1));
        System.out.println("🚀 派发主键 2: " + id2 + " | 内存位线: " + BinaryUtils.toPrettyBinary((int)id2));
        System.out.println("🚀 派发主键 3: " + id3 + " | 内存位线: " + BinaryUtils.toPrettyBinary((int)id3));
        System.out.println("✅ 雪花主键顺位生成完毕，全局唯一，递增高效。");
    }
}
