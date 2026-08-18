package com.zhiya.ddd.demo;

import com.zhiya.ddd.adapters.memory.FakeCandidateRecall;
import com.zhiya.ddd.adapters.memory.FakeExposurePublisher;
import com.zhiya.ddd.adapters.memory.FakeFeatureProvider;
import com.zhiya.ddd.adapters.memory.FakeRanker;
import com.zhiya.ddd.adapters.memory.InMemoryViewStore;
import com.zhiya.ddd.application.GetRecommendationsHandler;
import com.zhiya.ddd.domain.recommendation.RecommendationPolicy;
import com.zhiya.ddd.domain.recommendation.RecommendationResult;

import java.util.Map;
import java.util.Set;

/**
 * EX-04 一次推荐请求如何落到代码 —— 验证 ddd-04 全链路：
 *  1. 正常路径：特征 -> 策略视图 -> 召回 -> 排序 -> 领域策略过滤 -> 曝光
 *  2. 关闭个性化用户：领域策略剔除个性化商品（适配器/应用层不做业务判断）
 *  3. 排序不可用：领域兜底热门商品（fallback=true, 原因可观测）
 *  4. 特征不可用：降级为"零特征"，请求仍完成（不 500）
 *  5. 无策略视图：fallback 视图 + 正常决策（读路径不依赖写模型存在）
 */
public final class Ex04RecommendationChainDemo {

    public static void main(String[] args) {
        Checks c = new Checks();

        FakeFeatureProvider features = new FakeFeatureProvider(Map.of(
                "u-1001", Set.of("electronics", "photography")
        ), Set.of("u-9000"));
        FakeCandidateRecall recall = new FakeCandidateRecall();
        FakeRanker ranker = new FakeRanker();
        FakeExposurePublisher exposure = new FakeExposurePublisher();
        InMemoryViewStore views = new InMemoryViewStore();
        views.upsert("strategy-abc", "home-feed", 3);

        GetRecommendationsHandler handler = new GetRecommendationsHandler(
                features, InMemoryViewStore.asQuery(views), recall, ranker, exposure,
                new RecommendationPolicy()
        );

        // 1. 正常路径
        RecommendationResult r1 = handler.get("u-1001", "home-feed");
        c.check("正常路径非兜底", !r1.fallback());
        c.checkEq("正常路径返回个性化商品", "personalized:ipad", r1.items().get(0));
        c.checkEq("正常路径曝光已记录", 1, exposure.size());

        // 2. 关闭个性化用户（特征说允许=false）：领域策略剔除 personalized:*
        features.setAvailable(true);
        RecommendationResult r2 = handler.get("u-9000", "home-feed");
        c.check("关闭个性化 -> 无 personalized 商品",
                r2.items().stream().noneMatch(i -> i.startsWith("personalized:")));

        // 3. 排序不可用 -> 领域兜底热门
        ranker.setAvailable(false);
        RecommendationResult r3 = handler.get("u-1001", "home-feed");
        c.check("排序不可用 -> fallback=true", r3.fallback());
        c.checkEq("兜底原因可观测", "ranker-unavailable", r3.reason());
        c.checkEq("兜底是热门商品", "popular:1001", r3.items().get(0));
        ranker.setAvailable(true);

        // 4. 特征不可用 -> 零特征降级（仍走正常决策，非兜底）
        features.setAvailable(false);
        RecommendationResult r4 = handler.get("u-1001", "home-feed");
        c.check("特征不可用不抛异常", r4 != null);
        features.setAvailable(true);

        // 5. 无策略视图：fallback 视图 + 正常决策
        RecommendationResult r5 = handler.get("u-1001", "no-such-scene");
        c.check("无策略视图场景仍能出结果", !r5.items().isEmpty());

        c.summary("Ex04");
    }
}