package com.zhiya.ddd.demo;

import com.zhiya.ddd.domain.strategy.RecommendationStrategy;
import com.zhiya.ddd.domain.strategy.StrategyCode;
import com.zhiya.ddd.domain.strategy.StrategyId;
import com.zhiya.ddd.domain.strategy.StrategyRule;
import com.zhiya.ddd.domain.strategy.StrategyScene;
import com.zhiya.ddd.domain.strategy.StrategyVersion;
import com.zhiya.ddd.domain.strategy.TrafficRatio;

import java.util.List;

/**
 * EX-01 策略聚合与生命周期 —— 验证 ddd-03 的聚合不变量：
 *  1. 构造即校验（Value Object 自守卫）：流量比例 101、空编码、版本 0 必须抛异常
 *  2. 发布前置校验：无规则 -> 拒绝发布；规则必须加到 DRAFT
 *  3. 已发布策略禁止改规则（防止绕过校验改线上行为）
 *  4. 状态机：只有 DRAFT 能发布、只有 PUBLISHED 能下线
 *  5. 集合封装：外部拿到 rules() 后无法修改内部集合
 */
public final class Ex01StrategyAggregateDemo {

    public static void main(String[] args) {
        Checks c = new Checks();

        // 1. 值对象构造即校验
        boolean ratioRejected = false;
        try {
            new TrafficRatio(101);
        } catch (IllegalArgumentException e) {
            ratioRejected = true;
        }
        c.check("TrafficRatio(101) 被拒绝", ratioRejected);

        boolean codeRejected = false;
        try {
            new StrategyCode("  ");
        } catch (IllegalArgumentException e) {
            codeRejected = true;
        }
        c.check("StrategyCode('  ') 被拒绝", codeRejected);

        boolean versionRejected = false;
        try {
            new StrategyVersion(0);
        } catch (IllegalArgumentException e) {
            versionRejected = true;
        }
        c.check("StrategyVersion(0) 被拒绝", versionRejected);

        // 2. 无规则不能发布
        RecommendationStrategy s = RecommendationStrategy.draft(
                StrategyId.newId(),
                new StrategyCode("newuser-2026"),
                new StrategyScene("home-feed"),
                new TrafficRatio(50)
        );
        boolean publishRejected = false;
        try {
            s.publish();
        } catch (IllegalStateException e) {
            publishRejected = true;
        }
        c.check("空规则拒绝发布", publishRejected);

        // 3. 发布流程：加规则 -> 发布 -> 状态/事件正确
        s.addRule(StrategyRule.of("recall-newuser-pool"));
        s.addRule(StrategyRule.of("rank-by-register-time"));
        var domainEvent = s.publish();
        c.checkEq("发布后状态=PUBLISHED", "PUBLISHED", s.status().name());
        c.checkEq("领域事件携带版本", new StrategyVersion(1), domainEvent.version());

        // 4. 已发布禁止改规则
        boolean mutateRejected = false;
        try {
            s.addRule(StrategyRule.of("late-rule"));
        } catch (IllegalStateException e) {
            mutateRejected = true;
        }
        c.check("已发布禁止加规则", mutateRejected);

        // 5. 重复发布被拒绝（状态机约束）
        boolean republishRejected = false;
        try {
            s.publish();
        } catch (IllegalStateException e) {
            republishRejected = true;
        }
        c.check("重复发布被拒绝", republishRejected);

        // 6. 集合封装：外部修改抛 UnsupportedOperationException
        boolean collectionGuarded = false;
        try {
            s.rules().clear();
        } catch (UnsupportedOperationException e) {
            collectionGuarded = true;
        }
        c.check("rules() 外部不可修改", collectionGuarded);
        c.checkEq("规则数保持 2", 2, s.rules().size());

        // 7. 下线：只有 PUBLISHED 能下线；下线后不能再下线
        s.retire();
        c.checkEq("下线后状态=RETIRED", "RETIRED", s.status().name());
        boolean retireRejected = false;
        try {
            s.retire();
        } catch (IllegalStateException e) {
            retireRejected = true;
        }
        c.check("RETIRED 不能再次下线", retireRejected);

        // 8. 只读视图：外部拿到的是副本（List 内容不可改，引用数组也不可改）
        List<StrategyRule> view = s.rules();
        c.checkEq("视图只读且内容完整", 2, view.size());

        c.summary("Ex01");
    }
}