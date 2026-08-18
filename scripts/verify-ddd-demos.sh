# =============================================================================
# dev-lab DDD 专区公审引擎 (DDD Verification Engine)
# 编译 + EX-01~07 全部纯逻辑实验（聚合不变量 / ACL 翻译 / ArchUnit 边界 /
# 全链路降级 / Outbox 幂等重试 / 契约演进 / 选型矩阵），零中间件依赖，秒级完成
# =============================================================================
set -e
cd "$(dirname "$0")/../ddd-demo"

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

echo "🧪 [2/2] EX-01~07 全链路冒烟（聚合规则 / ACL / 架构边界 / 降级 / Outbox 幂等）..."
for n in 01 02 03 04 05 06 07; do
  OUT=$(bash scripts/run-ex.sh "$n" 2>&1)
  assert_contains "$OUT" "失败 0" "EX-$n"
done
echo "$OUT"
echo "✅ DDD 专区公审通过（EX-01~07 全部 失败 0）"
