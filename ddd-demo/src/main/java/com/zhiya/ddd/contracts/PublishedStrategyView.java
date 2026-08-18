package com.zhiya.ddd.contracts;

import java.time.Instant;

/**
 * 发布语言（Published Language）：策略上下文 -> 在线决策的稳定读模型契约。
 * 只用原生类型，不依赖任何上下文内部模型 —— 由 EX-03 架构规则守护（contracts 只依赖 JDK）。
 * 对应 ddd-03 Level 6 的 PublishedStrategyView / ddd-04 读路径。
 */
public record PublishedStrategyView(
        String strategyCode,
        String scene,
        int strategyVersion,
        int trafficPercent,
        Instant publishedAt
) {
    /** 无已发布策略时的兜底视图（用例编排决策，不是聚合行为）。 */
    public static PublishedStrategyView fallback(String scene) {
        return new PublishedStrategyView("FALLBACK", scene, 0, 0, Instant.now());
    }
}
