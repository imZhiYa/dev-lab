package com.zhiya.ddd.ports;

import com.zhiya.ddd.domain.recommendation.RecommendationFeatures;

/**
 * 端口：领域需要"用户允许使用的特征"这一能力（ddd-04 Level 3）。
 * 返回本地模型 RecommendationFeatures —— Redis/特征平台的 Key、Hash 结构留在适配器。
 */
public interface FeatureProviderPort {

    RecommendationFeatures load(String userId, String scene);
}
