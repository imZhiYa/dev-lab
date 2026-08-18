package com.zhiya.ddd.application;

import com.zhiya.ddd.contracts.PublishedStrategyView;
import com.zhiya.ddd.domain.recommendation.RecommendationFeatures;
import com.zhiya.ddd.domain.recommendation.RecommendationPolicy;
import com.zhiya.ddd.domain.recommendation.RecommendationPolicy.ListRanked;
import com.zhiya.ddd.domain.recommendation.RecommendationResult;
import com.zhiya.ddd.ports.CandidateRecallPort;
import com.zhiya.ddd.ports.ExposurePublisher;
import com.zhiya.ddd.ports.FeatureProviderPort;
import com.zhiya.ddd.ports.PublishedStrategyQuery;
import com.zhiya.ddd.ports.RankerPort;
import com.zhiya.ddd.ports.RankingUnavailable;

import java.util.List;

/**
 * 读路径用例编排（ddd-04）：端口注入 -> 流程稳定 -> 异常兜底由领域策略决定。
 * 本类不做任何具体技术决策（不 new RedisClient / 不 new HttpUtil）。
 */
public final class GetRecommendationsHandler {

    private final FeatureProviderPort features;
    private final PublishedStrategyQuery strategyQuery;
    private final CandidateRecallPort recall;
    private final RankerPort ranker;
    private final ExposurePublisher exposure;
    private final RecommendationPolicy policy;

    public GetRecommendationsHandler(
            FeatureProviderPort features,
            PublishedStrategyQuery strategyQuery,
            CandidateRecallPort recall,
            RankerPort ranker,
            ExposurePublisher exposure,
            RecommendationPolicy policy
    ) {
        this.features = features;
        this.strategyQuery = strategyQuery;
        this.recall = recall;
        this.ranker = ranker;
        this.exposure = exposure;
        this.policy = policy;
    }

    public RecommendationResult get(String userId, String scene) {
        // 1) 特征：能力不可用 -> 用"零特征"继续，而不是直接 500（降级不是崩溃）
        RecommendationFeatures featureSet = loadFeaturesOrEmpty(userId, scene);

        // 2) 读策略视图（发布语言契约）
        PublishedStrategyView strategy =
                strategyQuery.findFor(scene).orElse(PublishedStrategyView.fallback(scene));

        // 3) 召回 + 排序；排序不可用 -> 领域兜底
        List<String> candidates = recall.recall(userId, scene);
        RecommendationResult result;
        try {
            ListRanked ranked = ranker.rank(candidates, strategy);
            result = policy.decide(featureSet, ranked, strategy);
        } catch (RankingUnavailable e) {
            result = policy.fallback(featureSet, "ranker-unavailable");
        }

        // 4) 曝光：尽力而为（写日志/发指标），失败不影响主流程
        try {
            exposure.publish(userId, result.items());
        } catch (Exception ignored) {
            // 可观测性动作，失败不改变推荐结果
        }
        return result;
    }

    private RecommendationFeatures loadFeaturesOrEmpty(String userId, String scene) {
        try {
            return features.load(userId, scene);
        } catch (Exception e) {
            // 特征缺失 -> 按"无个性化"处理，请求仍可完成
            return new RecommendationFeatures(userId, scene, false, java.util.Set.of());
        }
    }
}
