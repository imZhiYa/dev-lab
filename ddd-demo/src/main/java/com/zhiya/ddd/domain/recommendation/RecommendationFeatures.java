package com.zhiya.ddd.domain.recommendation;

import java.util.Objects;
import java.util.Set;

/**
 * 在线决策本地模型：ACL 把 FeatureSnapshotContract 翻译后的产物。
 * 领域规则只依赖它，不依赖外部 DTO（ddd-02 Level 3 / ddd-04 Level 3）。
 */
public record RecommendationFeatures(
        String userId,
        String scene,
        boolean personalizationAllowed,
        Set<InterestTag> interests
) {
    public RecommendationFeatures {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(scene, "scene");
        interests = Set.copyOf(interests);
    }

    public boolean hasHighInterestTag() {
        return !interests.isEmpty();
    }
}
