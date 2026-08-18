package com.zhiya.ddd.domain.recommendation;

import java.util.List;

/**
 * 推荐结果：是否走兜底、原因是什么，由策略产出而不是适配器决定（ddd-04 Level 6）。
 */
public record RecommendationResult(
        List<String> items,
        boolean fallback,
        String reason
) {
    public RecommendationResult {
        items = List.copyOf(items);
    }

    public static RecommendationResult normal(List<String> items) {
        return new RecommendationResult(items, false, "ranked");
    }

    public static RecommendationResult fallback(List<String> items, String reason) {
        return new RecommendationResult(items, true, reason);
    }
}
