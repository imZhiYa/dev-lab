#!/bin/bash
# =============================================================================
# dev-lab sharding 专区公审引擎 (Sharding Verification Engine)
# 编译 + 纯逻辑冒烟（ShardingSmokeApp：gcd 搬迁公式 / 同余判据 / 路由片数 / 翻页下推形态，零中间件依赖，几秒完成）
# 依赖 docker 的实验（EX-01~07）不在 CI 运行，需本地环境（跑法见 sharding-demo/README.md）
# =============================================================================
set -e
cd "$(dirname "$0")/../sharding-demo"

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

echo "🧪 [2/2] ShardingSmokeApp 纯逻辑冒烟（gcd 搬迁公式 / 同余判据 / 路由片数 / 翻页下推）..."
OUT=$(bash run.sh com.zhiya.sharding.core.ShardingSmokeApp 2>&1)
echo "$OUT"
assert_contains "$OUT" "通过 11 / 失败 0" "core 逻辑冒烟"
echo "✅ Sharding 专区公审通过（容器实验跳过，本地跑法见 sharding-demo/README.md）"