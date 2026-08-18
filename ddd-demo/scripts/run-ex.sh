#!/bin/bash
# 运行单个 DDD 实验：./scripts/run-ex.sh <01..07>
set -e
cd "$(dirname "$0")/.."

case "$1" in
  01) MAIN=com.zhiya.ddd.demo.Ex01StrategyAggregateDemo ;;
  02) MAIN=com.zhiya.ddd.demo.Ex02AclTranslationDemo ;;
  03) MAIN=com.zhiya.ddd.demo.Ex03ArchitectureGuardDemo ;;
  04) MAIN=com.zhiya.ddd.demo.Ex04RecommendationChainDemo ;;
  05) MAIN=com.zhiya.ddd.demo.Ex05OutboxDemo ;;
  06) MAIN=com.zhiya.ddd.demo.Ex06VersionEvolutionDemo ;;
  07) MAIN=com.zhiya.ddd.demo.Ex07DddDecisionMatrixDemo ;;
  *) echo "用法: ./scripts/run-ex.sh <01..07>"; exit 1 ;;
esac

exec bash run.sh "$MAIN"
