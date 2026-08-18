package com.zhiya.ddd.demo;

import com.zhiya.ddd.adapters.memory.DropCounter;
import com.zhiya.ddd.application.translation.ProfileToDecisionTranslator;
import com.zhiya.ddd.contracts.FeatureSnapshotContract;
import com.zhiya.ddd.domain.recommendation.RecommendationFeatures;

import java.util.Set;

/**
 * EX-02 防腐层（ACL）契约翻译 —— 验证 ddd-02：
 *  1. 外部契约 -> 本地模型的字段搬移（只搬决策需要的）
 *  2. 非法标签被丢弃并计数，翻译器不崩溃（脏数据隔离在边界）
 *  3. 本地模型不可被外部类型污染（翻译后无人再引用 FeatureSnapshotContract）
 */
public final class Ex02AclTranslationDemo {

    public static void main(String[] args) {
        Checks c = new Checks();
        DropCounter counter = new DropCounter();
        ProfileToDecisionTranslator acl = new ProfileToDecisionTranslator(counter);

        // 1. 合法快照：字段搬移正确
        FeatureSnapshotContract snapshot = new FeatureSnapshotContract(
                "u-1001", 7L, true, Set.of("electronics", "photography", "illegal:tag")
        );
        RecommendationFeatures features = acl.translate(snapshot);

        c.checkEq("userId 搬移正确", "u-1001", features.userId());
        c.checkEq("个性化开关搬移正确", true, features.personalizationAllowed());
        c.checkEq("非法标签被丢弃", 2, features.interests().size());
        c.checkEq("丢弃计数=1", 1, counter.count());
        c.check("丢弃原因被记录", counter.reasons().get(0).contains("illegal:tag"));

        // 2. 快照版本号是画像自己的追踪字段，ACL 不搬（本地模型没有这个字段）
        //    —— 反射检查 record 组件集，证明本地模型根本没有 snapshotVersion 字段
        boolean noSnapshotField = java.util.Arrays.stream(RecommendationFeatures.class.getRecordComponents())
                .noneMatch(rc -> rc.getName().equals("snapshotVersion"));
        c.check("本地模型不包含 snapshotVersion 字段", noSnapshotField);
        //    不同 snapshotVersion 翻译结果必须完全等价
        FeatureSnapshotContract v8 = new FeatureSnapshotContract(
                "u-1001", 8L, true, Set.of("electronics", "photography")
        );
        RecommendationFeatures f8 = acl.translate(v8);
        c.checkEq("不同 snapshotVersion 翻译结果等价", features.interests(), f8.interests());

        // 3. 外部脏数据不崩溃：全量非法标签也只得到空兴趣集
        FeatureSnapshotContract dirty = new FeatureSnapshotContract(
                "u-2002", 1L, false, Set.of("a:b", "c:d", "e:f")
        );
        RecommendationFeatures fDirty = acl.translate(dirty);
        c.checkEq("脏快照 -> 空兴趣集（不抛异常）", 0, fDirty.interests().size());
        c.checkEq("丢弃计数累加=4", 4, counter.count());

        // 4. 关闭个性化的人：本地模型语义正确，后续决策可直接用
        c.checkEq("personalizationAllowed=false 原样保留", false, fDirty.personalizationAllowed());

        c.summary("Ex02");
    }
}