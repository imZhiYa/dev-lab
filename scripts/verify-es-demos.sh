#!/bin/bash
# =============================================================================
# dev-lab ES 专区公审引擎 (ES Verification Engine)
# 编译 + 纯逻辑冒烟（EsSmokeApp：bulk NDJSON 构造 / search_after 排序键 / 深分页候选公式，零中间件依赖，几秒完成）
# 依赖 docker 的实验（EX-01~06）不在 CI 运行，需本地环境（跑法见 es-demo/README.md）
# =============================================================================
set -e
cd "$(dirname "$0")/../es-demo"

assert_contains() {
  local haystack="$1" needle="$2" name="$3"
  if ! echo "$haystack" | grep -q "$needle"; then
    echo "❌ [$name] 断言失败：输出中未找到 [$needle]"
    echo "$haystack" | head -30
    exit 1
  fi
}

echo "🔨 [1/2] mvn compile（JDK 21）..."
mvn -q compile

echo "🧪 [2/2] EsSmokeApp 纯逻辑冒烟（NDJSON 配对 / search_after 排序键 / 深分页候选公式）..."
OUT=$(bash run.sh com.zhiya.es.core.EsSmokeApp 2>&1)
echo "$OUT"
assert_contains "$OUT" "通过 9 / 失败 0" "core 逻辑冒烟"
echo "✅ ES 专区公审通过（容器实验跳过，本地跑法见 es-demo/README.md）"
