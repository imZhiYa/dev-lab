package com.zhiya.ddd.contracts;

import java.util.Set;

/**
 * 发布语言：画像上下文 -> 在线决策的特征快照契约（ddd-02 Level 3 主线）。
 * snapshotVersion 是数据快照版本，与 schemaVersion（契约结构版本）是两个字段（ddd-02 坑 7）。
 */
public record FeatureSnapshotContract(
        String userId,
        long snapshotVersion,
        boolean personalizationAllowed,
        Set<String> inferredInterests
) {
}
