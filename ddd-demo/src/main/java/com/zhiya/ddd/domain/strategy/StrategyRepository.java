package com.zhiya.ddd.domain.strategy;

import java.util.Optional;

/**
 * 仓储端口（领域层定义、基础设施实现）：以聚合为边界加载/保存。
 * 不是 DAO 改名 —— 每张表一个 Repository、塞所有查询，都是坑（ddd-03 坑 6）。
 */
public interface StrategyRepository {

    Optional<RecommendationStrategy> findById(StrategyId id);

    void save(RecommendationStrategy strategy);
}
