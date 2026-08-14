#!/usr/bin/env bash
# E05: LoadBalance 派单分布 + failover 重试行为（Dubbo 3.3.4 + Nacos 2.4.3）
#
# 用法：./run-e05.sh [dist-random | dist-roundrobin | kill-instance | retries-compare | all]
# 前置：Nacos 运行在 127.0.0.1:8848（docker 容器 nacos-e04，脚本会自动拉起）
# 多网卡环境（存在代理软件 TUN 虚拟网卡时）需先固定注册地址：
#   export DUBBO_IP_TO_BIND=<物理网卡IP> DUBBO_IP_TO_REGISTRY=<物理网卡IP>
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

NACOS="http://127.0.0.1:8848"
PV=com.zhiya.dubbo.demo.registry.ProviderApp
CV=com.zhiya.dubbo.demo.protocol.ConsumerLoopApp
BASE_PORTS=(50052 50053 50054)
PV_LOG=/tmp/e05-provider.log
CV_LOG=/tmp/e05-consumer.log
ms() { python3 -c "import time;print(int(time.time()*1000))"; }

step() { echo "########## $* ##########"; }
note() { echo "[$(ms)] $*"; }

cleanup() {
  pkill -f "ProviderApp" 2>/dev/null
  pkill -f "ConsumerLoopApp" 2>/dev/null
  sleep 1
}

start_nacos() {
  docker start nacos-e04 >/dev/null 2>&1
  for i in $(seq 1 40); do curl -sf --max-time 2 "$NACOS/nacos/v1/console/health/readiness" >/dev/null 2>&1 && return 0; sleep 1; done
  echo "Nacos not ready"; exit 1
}

start_providers() {
  local n=${1:-3}
  for i in $(seq 0 $((n-1))); do
    local port=${BASE_PORTS[$i]}
    (cd "$ROOT" && DUBBO_IP_TO_BIND="${DUBBO_IP_TO_BIND:-}" DUBBO_IP_TO_REGISTRY="${DUBBO_IP_TO_REGISTRY:-}" \
      mvn -q -pl demo02-registry exec:java -Dexec.mainClass="$PV" -Dexec.args="" \
      -Ddubbo.application.register-mode=all -Ddubbo.protocol.port="$port" \
      >> "$PV_LOG" 2>&1 &)
  done
  for i in $(seq 1 60); do
    local up=0
    for port in "${BASE_PORTS[@]:0:$n}"; do
      grep -q "waiting for orders" "$PV_LOG" 2>/dev/null && up=$((up+1))
    done
    [ "$up" -ge "$n" ] && break
    sleep 1
  done
  sleep 5
  local svc='providers:com.zhiya.dubbo.demo.api.GreetingService::'
  local enc=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1],safe=''))" "$svc")
  local hosts=$(curl -s --max-time 2 "$NACOS/nacos/v2/ns/instance/list?serviceName=$enc&groupName=DEFAULT_GROUP" | python3 -c "import json,sys;print(len(json.load(sys.stdin)['data'].get('hosts',[])))" 2>/dev/null)
  note "providers up, registered hosts=$hosts"
}

start_consumer() {
  local extra=${1:-}
  (cd "$ROOT" && mvn -q -pl demo04-registry-protocol exec:java \
    -Dexec.mainClass="$CV" -Dexec.args="-n 300 -i 100" $extra > "$CV_LOG" 2>&1 &)
  for i in $(seq 1 40); do grep -q "references built" "$CV_LOG" 2>/dev/null && break; sleep 1; done
  grep -q "references built" "$CV_LOG" || { echo "consumer failed"; tail -20 "$CV_LOG"; exit 1; }
  note "consumer started"
}

dist_summary() {
  echo "=== 派单分布（响应中的端口统计）==="
  grep -oE "demo02-provider:[0-9]+" "$CV_LOG" | sort | uniq -c | sort -rn
  echo "=== OK/FAIL 计数 ==="
  grep -cE "#[0-9]+ OK" "$CV_LOG"; grep -cE "#[0-9]+ FAIL" "$CV_LOG"
}

PART="${1:-all}"

if [ "$PART" = "dist-random" ] || [ "$PART" = "all" ]; then
step "E05-1: Random 分布（3 实例，300 次，100ms 间隔）"
  cleanup; : > "$PV_LOG"; : > "$CV_LOG"; start_nacos; start_providers 3
  start_consumer "-Ddubbo.consumer.retries=0"
  for i in $(seq 1 60); do grep -q "done ok=" "$CV_LOG" 2>/dev/null && break; sleep 2; done
  dist_summary
fi

if [ "$PART" = "dist-roundrobin" ] || [ "$PART" = "all" ]; then
step "E05-1b: RoundRobin 分布（3 实例，300 次，100ms 间隔）"
  cleanup; : > "$PV_LOG"; : > "$CV_LOG"; start_nacos; start_providers 3
  start_consumer "-Ddubbo.consumer.retries=0 -Ddubbo.consumer.loadbalance=roundrobin"
  for i in $(seq 1 60); do grep -q "done ok=" "$CV_LOG" 2>/dev/null && break; sleep 2; done
  dist_summary
fi

kill_instance() {
  local port=$1
  local pid=$(lsof -ti tcp:"$port" 2>/dev/null | head -1)
  if [ -n "$pid" ]; then
    kill -9 "$pid" 2>/dev/null
    note "killed pid $pid (port $port)"
  else
    note "no listener on $port"
  fi
}

if [ "$PART" = "kill-instance" ] || [ "$PART" = "all" ]; then
step "E05-2: kill -9 一个实例 → failover 重试观察（retries=2）"
  cleanup; : > "$PV_LOG"; : > "$CV_LOG"; start_nacos; start_providers 3
  start_consumer "-Ddubbo.consumer.retries=2"
  sleep 5
  kill_instance 50053
  note "killed; waiting consumer 60s ..."
  for i in $(seq 1 30); do grep -q "done ok=" "$CV_LOG" 2>/dev/null && break; sleep 2; done
  echo "=== FAIL 计数 ==="; grep -cE "#[0-9]+ FAIL" "$CV_LOG"
  echo "=== 失败与重试相关日志 ==="; grep -iE "fail|retry|no provider" "$CV_LOG" | head -8
  dist_summary
fi

if [ "$PART" = "retries-compare" ] || [ "$PART" = "all" ]; then
step "E05-3: retries=0 vs 2（kill 实例后的失败数/耗时对比）"
  for R in 0 2; do
    cleanup; : > "$PV_LOG"; : > "$CV_LOG"; start_nacos; start_providers 3
    start_consumer "-Ddubbo.consumer.retries=$R"
    sleep 3
    note "retries=$R: killing provider 50053"
    kill_instance 50053
    for i in $(seq 1 30); do grep -q "done ok=" "$CV_LOG" 2>/dev/null && break; sleep 2; done
    ok=$(grep -cE "#[0-9]+ OK" "$CV_LOG")
    fail=$(grep -cE "#[0-9]+ FAIL" "$CV_LOG")
    echo "=== retries=$R: ok=$ok fail=$fail ==="
  done
fi

cleanup
note "E05 done"
