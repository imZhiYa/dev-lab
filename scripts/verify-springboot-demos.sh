#!/bin/bash
# =============================================================================
# dev-lab SpringBoot 专区公审引擎 (SpringBoot 3.3.5 Verification Engine)
# 编译 + 冒烟：IoC / 数据层(H2) / Web 双跑法 / 启动慢排查端点
# 每个 demo 断言关键输出，失败即退出非零
# =============================================================================
set -e
cd "$(dirname "$0")/../springboot-demo"

assert_contains() {
  local haystack="$1" needle="$2" name="$3"
  if ! echo "$haystack" | grep -q "$needle"; then
    echo "❌ [$name] 断言失败：输出中未找到 [$needle]"
    echo "$haystack" | head -20
    exit 1
  fi
}

echo "🔨 [1/5] mvn compile..."
mvn -q compile
echo "✅ 编译成功"

echo "🔨 [2/5] build-classpath..."
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
CP="target/classes:$(cat target/cp.txt)"
echo "✅ classpath 就绪"

echo "🧪 [3/5] demo01 手写 IoC..."
OUT=$(java -cp "$CP" demo01.MyMiniIoCApp 2>&1)
assert_contains "$OUT" "同一实例" "demo01 IoC"
echo "✅ IoC 单例池验证通过"

echo "🧪 [4/5] demo12 H2 + Hikari 数据源..."
OUT=$(java -cp "$CP" demo12.ds.DataSourceApp 2>&1)
assert_contains "$OUT" "HikariDataSource" "demo12 数据源"
echo "✅ 数据层验证通过"

echo "🧪 [5/5] demo11 Web 双跑法..."
OUT=$(java -cp "$CP" demo11.webflux.WebFluxApp --server.port=18081 2>&1)
assert_contains "$OUT" "AnnotationConfigServletWebServerApplicationContext" "SERVLET 分支"
echo "✅ SERVLET 分支通过 (Tomcat)"

RC=$(echo "$CP" | tr ':' '\n' | grep -v 'spring-webmvc' | grep -v 'tomcat-embed' | tr '\n' ':')
OUT=$(java -cp "target/classes:$RC" demo11.webflux.WebFluxApp --server.port=18082 2>&1)
assert_contains "$OUT" "AnnotationConfigReactiveWebServerApplicationContext" "REACTIVE 分支"
echo "✅ REACTIVE 分支通过 (Netty)"

echo "🎉 SpringBoot 专区公审全部通过"
