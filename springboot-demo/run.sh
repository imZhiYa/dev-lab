#!/bin/bash
# 运行指定 demo：./run.sh [-reactive] demo01.MyMiniIoCApp [args...]
# 依赖由 Maven 管理（pom.xml），首次运行自动编译并生成 classpath 文件
set -e
cd "$(dirname "$0")"

REACTIVE=0
if [ "$1" = "-reactive" ]; then
  REACTIVE=1
  shift
fi
[ $# -ge 1 ] || { echo "用法: ./run.sh [-reactive] <主类> [args...]"; exit 1; }

if [ ! -f target/classes/application.properties ]; then
  mvn -q compile
fi
if [ ! -f target/cp.txt ]; then
  mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
fi

CP="target/classes:$(cat target/cp.txt)"
if [ "$REACTIVE" = 1 ]; then
  # WebFlux 双跑法：去掉 webmvc + tomcat-embed → Boot 走 REACTIVE 分支（demo11.webflux.WebFluxApp）
  CP=$(echo "$CP" | tr ':' '\n' | grep -v 'spring-webmvc' | grep -v 'tomcat-embed' | tr '\n' ':')
fi

exec java -cp "$CP" "$@"
