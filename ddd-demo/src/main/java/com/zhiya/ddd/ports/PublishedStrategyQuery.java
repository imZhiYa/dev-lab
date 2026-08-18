package com.zhiya.ddd.ports;

import com.zhiya.ddd.contracts.PublishedStrategyView;

import java.util.Optional;

/**
 * 端口：在线读路径查询已发布策略视图（读模型），与聚合写路径的 StrategyRepository 分离（ddd-04 Level 3）。
 */
public interface PublishedStrategyQuery {

    Optional<PublishedStrategyView> findFor(String scene);
}
