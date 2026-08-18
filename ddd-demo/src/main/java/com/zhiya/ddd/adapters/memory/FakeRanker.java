package com.zhiya.ddd.adapters.memory;

import com.zhiya.ddd.contracts.PublishedStrategyView;
import com.zhiya.ddd.domain.recommendation.RecommendationPolicy.ListRanked;
import com.zhiya.ddd.ports.RankerPort;
import com.zhiya.ddd.ports.RankingUnavailable;

import java.util.List;

/** 排序适配器（模拟算法服务）：可注入不可用，演示领域兜底。 */
public final class FakeRanker implements RankerPort {

    private volatile boolean available = true;

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public ListRanked rank(List<String> candidates, PublishedStrategyView strategy) {
        if (!available) {
            throw new RankingUnavailable("ranker timeout", null);
        }
        // 简单排序：个性化在前、热门在后（只是可验证的假算法）
        List<String> sorted = candidates.stream()
                .sorted((a, b) -> {
                    boolean pa = a.startsWith("personalized:");
                    boolean pb = b.startsWith("personalized:");
                    return Boolean.compare(pb, pa);
                })
                .toList();
        return new ListRanked(sorted);
    }
}
