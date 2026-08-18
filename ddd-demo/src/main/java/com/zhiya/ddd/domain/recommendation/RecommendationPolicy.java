package com.zhiya.ddd.domain.recommendation;

import com.zhiya.ddd.contracts.PublishedStrategyView;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 领域策略：什么结果允许返回、模型不可用时兜底返回什么（ddd-04 Level 6）。
 * 注意它只依赖本地模型 + 发布语言契约，不依赖策略聚合 —— 上下文隔离由 EX-03 守护。
 */
public final class RecommendationPolicy {

    private static final int MAX_ITEMS = 10;

    public RecommendationResult decide(
            RecommendationFeatures features,
            ListRanked ranked,
            PublishedStrategyView strategy
    ) {
        Set<String> seen = new LinkedHashSet<>();
        for (String item : ranked.items()) {
            if (!features.personalizationAllowed() && item.startsWith("personalized:")) {
                continue; // 关闭个性化的用户不允许收到个性化商品
            }
            if (seen.size() >= MAX_ITEMS) {
                break;
            }
            seen.add(item);
        }
        if (seen.isEmpty()) {
            return fallback(features, "empty-after-policy");
        }
        return RecommendationResult.normal(List.copyOf(seen));
    }

    /** 模型不可用时的业务兜底：热门商品，由领域决定而不是适配器决定。 */
    public RecommendationResult fallback(RecommendationFeatures features, String reason) {
        return RecommendationResult.fallback(
                List.of("popular:1001", "popular:1002", "popular:1003"),
                reason
        );
    }

    /** 排序结果的最小载体（避免领域依赖模型 SDK 类型）。 */
    public record ListRanked(List<String> items) {
        public ListRanked {
            items = List.copyOf(items);
        }
    }
}
