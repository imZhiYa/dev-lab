package com.zhiya.ddd.domain.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聚合根：一致性边界的外部唯一入口（ddd-03 Level 4）。
 *
 * 不变量：
 *  - 没有规则的策略不能发布
 *  - 只有 DRAFT 能发布；只有 PUBLISHED 能下线
 *  - 已发布策略不能再添加规则（防止绕过校验改线上行为）
 *  - 内部集合不把修改权交给外部
 *
 * 本类不依赖 Spring/JPA/Redis/Kafka —— 由 EX-03 的 ArchUnit 规则守护。
 */
public final class RecommendationStrategy {

    private final StrategyId id;
    private final StrategyCode code;
    private final StrategyScene scene;
    private final TrafficRatio traffic;
    private StrategyVersion version;
    private LifecycleStatus status;
    private final List<StrategyRule> rules = new ArrayList<>();
    private long expectedPersistenceRevision;

    private RecommendationStrategy(
            StrategyId id, StrategyCode code, StrategyScene scene,
            TrafficRatio traffic, StrategyVersion version,
            LifecycleStatus status, List<StrategyRule> rules,
            long expectedPersistenceRevision
    ) {
        this.id = id;
        this.code = code;
        this.scene = scene;
        this.traffic = traffic;
        this.version = version;
        this.status = status;
        this.rules.addAll(rules);
        this.expectedPersistenceRevision = expectedPersistenceRevision;
    }

    /** 新建草稿（业务入口）。 */
    public static RecommendationStrategy draft(StrategyId id, StrategyCode code, StrategyScene scene, TrafficRatio traffic) {
        return new RecommendationStrategy(id, code, scene, traffic,
                new StrategyVersion(1), LifecycleStatus.DRAFT, List.of(), 0L);
    }

    /** 仅供仓储重建聚合（持久化对象 -> 领域对象，见 ddd-03 Level 5）。 */
    static RecommendationStrategy reconstitute(
            StrategyId id, StrategyCode code, StrategyScene scene,
            TrafficRatio traffic, StrategyVersion version,
            LifecycleStatus status, List<StrategyRule> rules,
            long expectedPersistenceRevision
    ) {
        return new RecommendationStrategy(id, code, scene, traffic, version, status, rules, expectedPersistenceRevision);
    }

    public StrategyId id() {
        return id;
    }

    public StrategyCode code() {
        return code;
    }

    public StrategyScene scene() {
        return scene;
    }

    public TrafficRatio traffic() {
        return traffic;
    }

    public StrategyVersion version() {
        return version;
    }

    public LifecycleStatus status() {
        return status;
    }

    public long expectedPersistenceRevision() {
        return expectedPersistenceRevision;
    }

    /**
     * 仅供仓储实现（Repository 属于领域契约，是聚合的合法持有着）在保存成功后回写并发修订号。
     * 不是公开业务 API：应用层不得调用。
     */
    public void markPersisted(long revision) {
        this.expectedPersistenceRevision = revision;
    }

    /** 只读视图：外部拿到集合也不能改内部状态（ddd-03 Level 4 集合封装）。 */
    public List<StrategyRule> rules() {
        return Collections.unmodifiableList(rules);
    }

    /** 添加规则的业务入口：只有草稿期允许。 */
    public void addRule(StrategyRule rule) {
        if (status != LifecycleStatus.DRAFT) {
            throw new IllegalStateException("can only add rules to draft, current=" + status);
        }
        rules.add(StrategyRule.require(rule));
    }

    /** 发布前校验：把"能不能发布"从 Service/if-else 收回聚合。 */
    public void validateCanPublish() {
        if (rules.isEmpty()) {
            throw new IllegalStateException("strategy rules are empty");
        }
        if (status != LifecycleStatus.DRAFT) {
            throw new IllegalStateException("only draft strategy can be published, current=" + status);
        }
    }

    /** 发布：状态迁移 + 返回领域事实。 */
    public StrategyPublished publish() {
        validateCanPublish();
        this.status = LifecycleStatus.PUBLISHED;
        return StrategyPublished.of(id, scene, version);
    }

    /** 下线：只有已发布状态允许。 */
    public void retire() {
        if (status != LifecycleStatus.PUBLISHED) {
            throw new IllegalStateException("only published strategy can retire, current=" + status);
        }
        this.status = LifecycleStatus.RETIRED;
    }
}
