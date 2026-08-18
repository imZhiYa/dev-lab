#!/bin/bash
# DDD 机制验证实验室 - 一把跑完 EX-01~07（纯 JVM 逻辑实验，零中间件，秒级完成）
set -e
cd "$(dirname "$0")/.."

echo "=========================================="
echo "🏛️  DDD 机制验证实验室 · run-all"
echo "=========================================="

for n in 01 02 03 04 05 06 07; do
  echo ""
  echo "🧪 EX-$n ..."
  bash scripts/run-ex.sh "$n"
done

echo ""
echo "✅ 全部实验通过（机制验证级 = 教学可复现，非 benchmark 级；内存适配器模拟的边界见 README）"
