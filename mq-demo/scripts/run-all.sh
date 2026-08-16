#!/bin/bash
# =============================================================================
# MQ 机制验证实验室 - 一把跑完 6 实验（单会话 ~10min，FAST 档）
# 流程：环境快照 → 起齐 4 容器 → 各 EX 串行 → 汇总 → down -v 销毁全部测试数据
# 首次会话需拉镜像（Kafka/MySQL/RabbitMQ ~1.2GB），此后缓存命中
# =============================================================================
set -e
cd "$(dirname "$0")/.."

echo "=========================================="
echo "📨 MQ 机制验证实验室 · run-all（FAST 档）"
echo "=========================================="

bash scripts/env-snapshot.sh

echo ""
echo "🚀 [1/8] 起齐中间件容器（Kafka 3.5 / MySQL 8 / Redis 7 / RabbitMQ 3.13）..."
docker compose -f scripts/compose-mq.yml up -d --wait 2>&1 | tail -3
echo "✅ 容器就绪"

echo ""
echo "🧪 [2/8] EX-01 单分区吞吐：acks=1 vs acks=all"
bash scripts/run-ex01.sh

echo ""
echo "🧪 [3/8] EX-02 批量消费与踢边界"
bash scripts/run-ex02.sh

echo ""
echo "🧪 [4/8] EX-04 积压恢复校准"
bash scripts/run-ex04.sh

echo ""
echo "🧪 [5/8] EX-06 乱序与补偿注入"
bash scripts/run-ex06.sh

echo ""
echo "🧪 [6/8] EX-03 幂等三方案对比（含 Redis 淘汰注入）"
bash scripts/run-ex03.sh

echo ""
echo "🧪 [7/8] EX-05 经典队列(CQv1) vs quorum 队列(CQv2)"
bash scripts/run-ex05.sh

echo ""
echo "🧹 [8/8] 清理：down -v 销毁全部容器与测试数据（零残留）..."
docker compose -f scripts/compose-mq.yml down -v 2>&1 | tail -3
echo "✅ 全部实验完成，测试数据已随容器销毁"
echo ""
echo "说明：FAST 档 = 机制验证级（教学量级），非 benchmark 级；"
echo "      跨 VM（macOS→colima）只产相对结论，绝对数不外推。"
