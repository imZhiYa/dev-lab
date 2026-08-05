#!/usr/bin/env bash
# =============================================================================
# run-benchmarks.sh — JMH 微基准统一编排入口
#
# 设计目标：
#   1. 所有 JMH 调用参数收敛到下方"参数表"一处，作为唯一真源；
#   2. CI 与 README 不再各自抄一遍 java -jar 命令，只引用本脚本；
#   3. 结果落盘为 日志(.log) + 机器可读(.json)，便于回归对比。
#
# 用法：
#   bash scripts/run-benchmarks.sh [all|tree|sync|bio]
#   LONG=1 bash scripts/run-benchmarks.sh tree   # 长测档（足量预热/迭代）
# =============================================================================
set -euo pipefail

cd "$(dirname "$0")/.."

JAR="target/benchmarks.jar"
LOG_DIR="logs"
RESULT_DIR="results"

# -----------------------------------------------------------------------------
# 参数表 —— 唯一真源：target | JMH pattern | JMH 参数
# 其余统一参数（-wi/-i/-f/-foe/-rf/-rff）由 run_one() 收敛，禁止散落各处
# -----------------------------------------------------------------------------
BENCHES=(
  "tree|TreeBenchmark.*|-p n=10000 -t 1"
  "sync|SynchronizedMapVsConcurrentMapBenchmark.*|-p size=1000 -t 1"
  "bio|BioVsNioLoopbackBenchmark.*|-p payloadBytes=16,256,4096 -t 1"
)

# 预热/迭代档位：默认 CI 快速档；LONG=1 切足量档（本机深挖用）
WI=1
I=3
[ "${LONG:-0}" = "1" ] && WI=3 && I=5

mkdir -p "$LOG_DIR" "$RESULT_DIR"

run_one() {
  local target="$1" pattern="$2" params="$3"
  echo "===== JMH: $target ====="
  java -jar "$JAR" "$pattern" $params \
    -wi "$WI" -i "$I" -f 1 -foe true \
    -rf json -rff "$RESULT_DIR/$target.json" \
    2>&1 | tee "$LOG_DIR/$target.log"
}

run_all() {
  for entry in "${BENCHES[@]}"; do
    IFS='|' read -r target pattern params <<< "$entry"
    run_one "$target" "$pattern" "$params"
  done
}

ensure_jar() {
  if [ ! -f "$JAR" ]; then
    echo "❌ 缺少 $JAR，请先执行: mvn clean package -DskipTests"
    exit 1
  fi
}

TARGET="${1:-all}"
case "$TARGET" in
  all|tree|sync|bio) ;;
  *) echo "用法: $0 [all|tree|sync|bio]"; exit 1 ;;
esac

ensure_jar
if [ "$TARGET" = "all" ]; then
  run_all
else
  for entry in "${BENCHES[@]}"; do
    IFS='|' read -r target pattern params <<< "$entry"
    [ "$target" = "$TARGET" ] && run_one "$target" "$pattern" "$params"
  done
fi
