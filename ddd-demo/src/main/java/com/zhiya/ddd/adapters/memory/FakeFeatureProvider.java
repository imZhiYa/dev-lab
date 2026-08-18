package com.zhiya.ddd.adapters.memory;

import com.zhiya.ddd.domain.recommendation.InterestTag;
import com.zhiya.ddd.domain.recommendation.RecommendationFeatures;
import com.zhiya.ddd.ports.FeatureProviderPort;
import com.zhiya.ddd.ports.FeatureUnavailable;

import java.util.Map;
import java.util.Set;

/**
 * 特征提供适配器（模拟画像上下文）：用开关控制"不可用"，验证读路径降级。
 * optOutUsers 里的用户 personalizationAllowed=false（模拟用户主动关闭个性化）。
 */
public final class FakeFeatureProvider implements FeatureProviderPort {

    private final Map<String, Set<String>> interestsByUser;
    private final Set<String> optOutUsers;
    private volatile boolean available = true;

    public FakeFeatureProvider(Map<String, Set<String>> interestsByUser, Set<String> optOutUsers) {
        this.interestsByUser = interestsByUser;
        this.optOutUsers = optOutUsers;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public RecommendationFeatures load(String userId, String scene) {
        if (!available) {
            throw new FeatureUnavailable("feature service unavailable", null);
        }
        boolean personalizationAllowed = !optOutUsers.contains(userId);
        Set<String> raw = interestsByUser.getOrDefault(userId, Set.of());
        Set<InterestTag> tags = new java.util.LinkedHashSet<>();
        for (String r : raw) {
            tags.add(InterestTag.of(r));
        }
        return new RecommendationFeatures(userId, scene, personalizationAllowed, tags);
    }
}