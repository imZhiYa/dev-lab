package com.zhiya.ddd.domain.recommendation;

/**
 * 值对象：兴趣标签。
 * of() 做格式校验 —— ACL 翻译非法标签时的"丢弃 + 计数"策略依赖它抛出异常（ddd-02 Level 3）。
 */
public record InterestTag(String value) {
    public InterestTag {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("interest tag is blank");
        }
        if (value.contains(":")) {
            // 约定：冒号是画像内部编码分隔符，不允许进入决策语义
            throw new IllegalArgumentException("interest tag contains illegal char ':' -> " + value);
        }
    }

    public static InterestTag of(String raw) {
        return new InterestTag(raw);
    }
}
