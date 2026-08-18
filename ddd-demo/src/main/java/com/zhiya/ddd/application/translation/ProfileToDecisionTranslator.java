package com.zhiya.ddd.application.translation;

import com.zhiya.ddd.contracts.FeatureSnapshotContract;
import com.zhiya.ddd.domain.recommendation.InterestTag;
import com.zhiya.ddd.domain.recommendation.RecommendationFeatures;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 防腐层（ACL）翻译：外部契约 -> 决策本地模型（ddd-02 Level 3）。
 * 职责：
 *  1. 只搬走决策需要的字段（忽略画像的批次号、追踪字段）
 *  2. 丢弃并计数非法标签 —— 外部脏数据不允许进入领域，但翻译器必须"消化"而不是崩溃
 * 内部契约演进（如 user_id 改名）只改这一个文件 —— 这是 ACL 的收益。
 */
public final class ProfileToDecisionTranslator {

    private final Counter droppedTags;

    public ProfileToDecisionTranslator(Counter droppedTags) {
        this.droppedTags = droppedTags;
    }

    public RecommendationFeatures translate(FeatureSnapshotContract contract) {
        Set<InterestTag> interests = new LinkedHashSet<>();
        for (String raw : contract.inferredInterests()) {
            try {
                interests.add(InterestTag.of(raw));
            } catch (IllegalArgumentException e) {
                droppedTags.increment(raw);
            }
        }
        return new RecommendationFeatures(
                contract.userId(),
                "home-feed",
                contract.personalizationAllowed(),
                interests
        );
    }

    /** 计数器端口：可观测性边界，翻译器只报告、不自行打日志（适配器实现负责输出）。 */
    public interface Counter {
        void increment(String reason);
    }
}
