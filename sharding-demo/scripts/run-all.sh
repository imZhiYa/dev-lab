#!/bin/bash
# =============================================================================
# sharding-demo 一键复跑：EX-01~07（依赖 docker 容器 sharding-mysql 已启动）
# 用法：bash scripts/run-all.sh
# =============================================================================
set -e
cd "$(dirname "$0")/.."

echo "🌐 检查 MySQL 容器（sharding-mysql）..."
if ! docker exec sharding-mysql mysqladmin ping -h127.0.0.1 -proot >/dev/null 2>&1; then
  echo "❌ 容器未启动，请先执行：docker compose -f scripts/compose-mysql.yml up -d"
  exit 1
fi

echo "🧪 [0/7] ShardingSmokeApp 纯逻辑冒烟..."
./run.sh com.zhiya.sharding.core.ShardingSmokeApp

echo "🧪 [1/7] EX-01 分片键分布（连续键 vs 雪花低速率键）..."
./run.sh com.zhiya.sharding.experiment.Ex01KeyDistribution

echo "🧪 [2/7] EX-02 路由与广播（带键单片 vs 无键广播）..."
./run.sh com.zhiya.sharding.experiment.Ex02RouteAndBroadcast

echo "🧪 [3/7] EX-03 改写引擎（AVG→SUM+COUNT、IN 合并/拆分）..."
./run.sh com.zhiya.sharding.experiment.Ex03Rewrite

echo "🧪 [4/7] EX-04 翻页代价（offset vs keyset）..."
./run.sh com.zhiya.sharding.experiment.Ex04PaginationCost

echo "🧪 [5/7] EX-05 跨片 JOIN（静默丢数据实录）..."
./run.sh com.zhiya.sharding.experiment.Ex05CrossShardJoin

echo "🧪 [6/7] EX-06 扩容搬迁比例（gcd 公式实证）..."
./run.sh com.zhiya.sharding.experiment.Ex06ReshardRatio

echo "🧪 [7/7] EX-07 连接预算（按库不按片）..."
./run.sh com.zhiya.sharding.experiment.Ex07Connections

echo ""
echo "✅ 全部实验通过"