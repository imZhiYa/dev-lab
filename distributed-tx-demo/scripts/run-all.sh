#!/bin/bash
# =============================================================================
# distributed-tx-demo 一键复跑：冒烟 + EX-01~06（依赖 docker 容器 dtx-mysql 已启动）
# 用法：docker compose -f scripts/compose-mysql.yml up -d --wait && bash scripts/run-all.sh
# 流程：检查容器 → 纯逻辑冒烟 → 各 EX 串行 → 全部断言通过
# =============================================================================
set -e
cd "$(dirname "$0")/.."

echo "🌐 检查 MySQL 容器（dtx-mysql）..."
if ! docker exec dtx-mysql mysqladmin ping -h127.0.0.1 -proot >/dev/null 2>&1; then
  echo "❌ 容器未启动，请先执行：docker compose -f scripts/compose-mysql.yml up -d --wait"
  exit 1
fi

echo ""
echo "🧪 [0/6] DtxSmokeApp 纯逻辑冒烟..."
./run.sh com.zhiya.dtx.core.DtxSmokeApp

echo ""
echo "🧪 [1/6] EX-01 XA prepare 后悬挂与 in-doubt（人工裁决）..."
./run.sh com.zhiya.dtx.experiment.Ex01XaHanging

echo ""
echo "🧪 [2/6] EX-02 TCC 三难题（空回滚 / 悬挂 / 幂等）..."
./run.sh com.zhiya.dtx.experiment.Ex02TccThreeProblems

echo ""
echo "🧪 [3/6] EX-03 TCC 悬挂检测与阈值校准..."
./run.sh com.zhiya.dtx.experiment.Ex03TccHangingScan

echo ""
echo "🧪 [4/6] EX-04 Saga 编排器倒序补偿..."
./run.sh com.zhiya.dtx.experiment.Ex04SagaCompensation

echo ""
echo "🧪 [5/6] EX-05 Saga 超时结果未知（查询确认）..."
./run.sh com.zhiya.dtx.experiment.Ex05SagaTimeoutUnknown

echo ""
echo "🧪 [6/6] EX-06 Outbox 原子性（状态与事件同事务）..."
./run.sh com.zhiya.dtx.experiment.Ex06OutboxAtomicity

echo ""
echo "✅ 全部实验通过（机制验证级，FAST 档）"
