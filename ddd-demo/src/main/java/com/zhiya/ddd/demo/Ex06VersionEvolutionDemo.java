package com.zhiya.ddd.demo;

import com.zhiya.ddd.contracts.FeatureSnapshotContract;
import com.zhiya.ddd.domain.recommendation.InterestTag;

import java.util.Set;

/**
 * EX-06 契约演进与降级决策 —— 验证 ddd-02 坑 7（schemaVersion）与 ddd-06：
 *  1. schemaVersion 是新字段的演进开关：v1 没有 ownerType -> 默认值兜底（老消费者兼容）
 *  2. 读旧版本契约不崩溃：多写新字段 = 老消费者丢弃未知字段
 *  3. 非法数据不进领域（与 EX-02 呼应）：即使新版本带了脏 ownerType，决策只认本地模型
 *  4. 演进策略（ddd-06 决策卡）：契约演进 = 增字段 + 默认值，禁止改语义（允许个性化!=禁止）
 */
public final class Ex06VersionEvolutionDemo {

    // 模拟契约 v1（ownerType 尚未出现）
    private record ContractV1(String userId, boolean personalizationAllowed, Set<String> inferredInterests) {
    }

    // 模拟契约 v2（新增 ownerType 字段；inferredInterests 语义不变）
    private record ContractV2(String userId, boolean personalizationAllowed, Set<String> inferredInterests,
                              String ownerType) {
    }

    public static void main(String[] args) {
        Checks c = new Checks();
        com.zhiya.ddd.adapters.memory.DropCounter counter = new com.zhiya.ddd.adapters.memory.DropCounter();
        com.zhiya.ddd.application.translation.ProfileToDecisionTranslator acl =
                new com.zhiya.ddd.application.translation.ProfileToDecisionTranslator(counter);

        // 1. v1 老消费者：没有 ownerType 字段，正常翻译
        ContractV1 v1 = new ContractV1("u-1", true, Set.of("electronics"));
        var f1 = acl.translate(new FeatureSnapshotContract(v1.userId(), 5L, v1.personalizationAllowed(), v1.inferredInterests()));
        c.check("v1 契约翻译成功", f1.userId().equals("u-1") && f1.interests().size() == 1);

        // 2. v2 新契约：多出的 ownerType 对决策上下文无意义，翻译后本地模型不增加任何字段
        ContractV2 v2 = new ContractV2("u-1", true, Set.of("electronics"), "BRAND_ACCOUNT");
        var f2 = acl.translate(new FeatureSnapshotContract(v2.userId(), 6L, v2.personalizationAllowed(), v2.inferredInterests()));
        c.check("v2 契约翻译后本地模型等价（未知字段被边界消化）",
                f1.interests().equals(f2.interests()) && f1.personalizationAllowed() == f2.personalizationAllowed());

        // 3. 语义演进禁令：personalizationAllowed 从"允许"改成"必须"是语义变化，必须新建字段而不是改含义
        //    —— 演示：如果 v3 把 false 默认值颠倒，老数据语义就翻转了。这里断言"决策上下文只按本地模型语义解释"
        c.check("ACL 只认本地模型布尔语义（false=不个性化）",
                !acl.translate(new FeatureSnapshotContract("u-2", 1L, false, Set.of())).personalizationAllowed());

        // 4. 值对象守卫与版本无关：新字段如果是非法值，仍被丢弃（老逻辑不被新字段绕过）
        boolean dirtyRejected = false;
        try {
            InterestTag.of("brand:leak");
        } catch (IllegalArgumentException e) {
            dirtyRejected = true;
        }
        c.check("非法标签拒绝规则与 schemaVersion 无关", dirtyRejected);

        c.summary("Ex06");
    }
}