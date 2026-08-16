package com.zhiya.mq.core;

/**
 * 补偿重试策略（mq-10 L4）：指数退避 + 上限——乱序可以等（秒级），
 * 但每个"等"必须有上限，否则就是活锁。
 */
public final class RetryPolicy {

    private final int maxAttempts;
    private final long baseDelayMs;

    public RetryPolicy(int maxAttempts, long baseDelayMs) {
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
    }

    /** 第 n 次尝试（1-based）的等待毫秒：base * 2^(n-1)，超过上限返回 -1（放弃，进 DLQ） */
    public long delayFor(int attempt) {
        if (attempt < 1 || attempt > maxAttempts) {
            return -1;
        }
        long delay = baseDelayMs;
        for (int i = 1; i < attempt; i++) {
            delay *= 2;
        }
        return delay;
    }

    public int maxAttempts() {
        return maxAttempts;
    }
}
