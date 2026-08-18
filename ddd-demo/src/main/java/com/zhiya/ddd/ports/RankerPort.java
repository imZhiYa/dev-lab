package com.zhiya.ddd.ports;

import com.zhiya.ddd.contracts.PublishedStrategyView;
import com.zhiya.ddd.domain.recommendation.RecommendationPolicy.ListRanked;

import java.util.List;

/** 端口：排序能力。超时/不可用时抛 RankingUnavailable，由业务策略决定兜底（ddd-04 Level 6）。 */
public interface RankerPort {

    ListRanked rank(List<String> candidates, PublishedStrategyView strategy);
}
