#!/bin/bash
# =============================================================================
# ES 机制验证实验室 - 一把跑完 6 实验（单会话 ~10min，FAST 档）
# 流程：环境快照 → vm.max_map_count 尽力设置 → 起齐 3 节点 ES → 各 EX 串行 → down -v 销毁
# 首次会话需拉镜像（ES 8.15.3 ~1.6GB ×1，三节点共用），此后缓存命中
# =============================================================================
set -e
cd "$(dirname "$0")/.."

echo "=========================================="
echo "🔎 ES 机制验证实验室 · run-all（FAST 档）"
echo "=========================================="

bash scripts/env-snapshot.sh

echo ""
echo "🛠️ [2/10] 尽力设置 colima VM vm.max_map_count=262144（重启即丢，幂等）..."
colima ssh -- sudo sysctl -w vm.max_map_count=262144 2>/dev/null || echo "⚠️ 设置失败（若容器能起可忽略；失败则手动: colima ssh -- sudo sysctl -w vm.max_map_count=262144）"

echo ""
echo "🚀 [3/10] 起齐 ES 8.15.3 × 3 节点（512m 堆/节点，security off）..."
docker compose -f scripts/compose-es.yml up -d --wait 2>&1 | tail -3
echo "✅ 集群就绪"

echo ""
echo "🧪 [4/10] EX-01 批量写曲线：单条 vs bulk 100/500/1000/5000"
bash scripts/run-ex01.sh

echo ""
echo "🧪 [5/10] EX-02 refresh 可见性窗口"
bash scripts/run-ex02.sh

echo ""
echo "🧪 [6/10] EX-03 副本复制代价：replicas=0 vs 1"
bash scripts/run-ex03.sh

echo ""
echo "🧪 [7/10] EX-04 深分页：from+size vs search_after"
bash scripts/run-ex04.sh

echo ""
echo "🧪 [8/10] EX-05 filter vs query（含 query cache 观测）"
bash scripts/run-ex05.sh

echo ""
echo "🧪 [9/10] EX-06 cardinality 精度与代价"
bash scripts/run-ex06.sh

echo ""
echo "🧹 [10/10] 清理：down -v 销毁全部容器与测试数据（零残留）..."
docker compose -f scripts/compose-es.yml down -v 2>&1 | tail -3
echo "✅ 全部实验完成，测试数据已随容器销毁"
echo ""
echo "说明：FAST 档 = 机制验证级（教学量级），非 benchmark 级；"
echo "      跨 VM（macOS→colima）只产相对结论，绝对数不外推。"
