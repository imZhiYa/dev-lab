#!/bin/bash
# 运行指定实验主类：./run.sh <全限定类名> [args...]
# JDK 21 自动探测（/usr/libexec/java_home），依赖由 Maven 管理，首次运行自动编译 + 生成 classpath
set -e
cd "$(dirname "$0")"

# 探测 JDK 21（零硬编码个人路径）
JAVA_BIN="java"
if /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
  JAVA_BIN="$(/usr/libexec/java_home -v 21)/bin/java"
else
  echo "⚠️ 未找到 JDK 21，回退 PATH 中的 java（当前: $($JAVA_BIN -version 2>&1 | head -1)）"
fi

[ $# -ge 1 ] || { echo "用法: ./run.sh <全限定类名> [args...]"; exit 1; }

if [ ! -f target/classes/com/zhiya/mq/core/CoreSmokeApp.class ]; then
  echo "🔨 首次运行，执行 mvn compile..."
  mvn -q compile
fi
if [ ! -f target/cp.txt ]; then
  mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
fi

exec "$JAVA_BIN" -cp "target/classes:$(cat target/cp.txt)" "$@"
