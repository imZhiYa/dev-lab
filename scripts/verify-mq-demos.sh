#!/bin/bash
# =============================================================================
# dev-lab MQ 专区公审引擎 (MQ Verification Engine)
# 编译 + 纯逻辑冒烟（CoreSmokeApp：状态机/幂等键/重试策略，零中间件依赖，几秒完成）
# 依赖 docker 的实验（EX-01~06）不在 CI 运行，需本地环境（跑法见 mq-demo/README.md）
# =============================================================================
set -e
cd "$(dirname "$0")/../mq-demo"

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

echo "🧪 [2/2] CoreSmokeApp 纯逻辑冒烟（状态机乱序拒绝 / 幂等键确定性 / 重试退避）..."
OUT=$(bash run.sh com.zhiya.mq.core.CoreSmokeApp 2>&1)
echo "$OUT"
assert_contains "$OUT" "通过 5 / 失败 0" "core 逻辑冒烟"
echo "✅ MQ 专区公审通过（容器实验跳过，本地跑法见 mq-demo/README.md）"
