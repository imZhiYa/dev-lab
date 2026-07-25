#!/bin/bash
set -e

# =============================================================================
# dev-lab JDK 21 专区公审引擎 (JDK 21 JVM Demo Verification Engine)
# =============================================================================

echo "================================================================="
echo "🚀 启动 dev-lab JDK 21 JVM 特性与诊断实战公审"
echo "================================================================="

# 1. 编译 jvm-demo 全量源码 (JDK 21)
echo "🔨 正在编译 jvm-demo 全量源码..."
mkdir -p jvm-demo/target/classes
find jvm-demo -name "*.java" | xargs javac -encoding UTF-8 -d jvm-demo/target/classes
echo "✅ jvm-demo 全量源码编译成功！"

# 2. 执行核心诊断与演示脚本
if [ -f "jvm-demo/scripts/run-jvm-demos.sh" ]; then
    chmod +x jvm-demo/scripts/run-jvm-demos.sh
    GC_TYPE=G1 ./jvm-demo/scripts/run-jvm-demos.sh
else
    echo "❌ 找不到 jvm-demo/scripts/run-jvm-demos.sh 脚本！"
    exit 1
fi
