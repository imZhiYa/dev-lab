#!/bin/bash
# EX-05（实验说明见 es-demo/README.md 实验索引）
# FAST 档参数固化在对应 Java 类常量中（参数唯一真源）；SCALE=LONG 为将来深挖钩子
set -e
cd "$(dirname "$0")/.."
[ "$SCALE" = "LONG" ] && echo "LONG 档：可扩展更长窗口/更全参数矩阵（当前未实现）"
exec bash run.sh com.zhiya.es.experiment.Ex05FilterVsQuery
