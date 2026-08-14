#!/bin/bash
# E04: 运行中 kill Nacos 注册中心，验证 consumer 本地缓存目录
# （RegistryDirectory 快照）在数据面继续派单；重启 Nacos 观察恢复；
# 再 kill -9 provider，测临时实例被剔除的时长（心跳语义）。
#
# 用法：./run-e04.sh [--no-cache-check] [--no-evict-check]
# 前置：Nacos 运行在 127.0.0.1:8848（docker 容器 nacos-e04，脚本会自动拉起）
# 多网卡环境（存在代理软件 TUN 虚拟网卡时）需先固定注册地址：
#   export DUBBO_IP_TO_BIND=<物理网卡IP> DUBBO_IP_TO_REGISTRY=<物理网卡IP>
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export DUBBO_IP_TO_BIND="${DUBBO_IP_TO_BIND:-}"
export DUBBO_IP_TO_REGISTRY="${DUBBO_IP_TO_REGISTRY:-}"

NACOS="http://127.0.0.1:8848"
NACOS_CONTAINER="nacos-e04"
PV_LOG="/tmp/e04-provider.log"
CV_LOG="/tmp/e04-consumer.log"
PV=com.zhiya.dubbo.demo.registry.ProviderApp
CV=com.zhiya.dubbo.demo.protocol.ConsumerLoopApp
MODE="${MODE:-all}"

OK=0; FAIL=0
ms() { python3 -c "import time;print(int(time.time()*1000))"; }
stamp() { python3 -c "import time;t=time.time();print(time.strftime('%H:%M:%S',time.localtime(t))+'.%03d'%(int(t*1000)%1000))"; }
step() { echo; echo "########## $1 ##########"; }
note() { echo "[$(stamp)] $1"; }
watch_consumer_ok() { grep -q "\[CONSUMER\] #.* OK" "$CV_LOG" 2>/dev/null; }

step "E04 ROUND: register-mode=$MODE (provider=$PV consumer=$CV)"
pkill -f "ConsumerLoopApp" 2>/dev/null; pkill -f "ProviderApp" 2>/dev/null; sleep 2

docker start "$NACOS_CONTAINER" >/dev/null 2>&1 || docker run -d --name "$NACOS_CONTAINER" \
  -p 8848:8848 -p 9848:9848 -e MODE=standalone -e NACOS_AUTH_ENABLE=false nacos/nacos-server:v2.4.3 >/dev/null
for i in $(seq 1 40); do curl -sf "$NACOS/nacos/v1/console/health/readiness" >/dev/null 2>&1 && break; sleep 1; done
note "Nacos ready"

step "[1/5] start provider (register-mode=$MODE)"
(cd "$ROOT" && nohup mvn -q -pl demo02-registry exec:java \
  -Dexec.mainClass="$PV" -Ddubbo.application.register-mode="$MODE" > "$PV_LOG" 2>&1 &)
for i in $(seq 1 60); do grep -q "waiting for orders" "$PV_LOG" 2>/dev/null && break; sleep 1; done
grep -q "waiting for orders" "$PV_LOG" || { note "PROVIDER FAILED"; tail -20 "$PV_LOG"; exit 1; }
note "provider up"

sleep 3
step "[2/5] start consumer loop (long-running)"
(cd "$ROOT" && nohup mvn -q -pl demo04-registry-protocol exec:java \
  -Dexec.mainClass="$CV" -Dexec.args="-i 1000" > "$CV_LOG" 2>&1 &)
for i in $(seq 1 30); do watch_consumer_ok && break; sleep 1; done
watch_consumer_ok || { note "CONSUMER FAILED TO START"; tail -20 "$CV_LOG"; exit 1; }
note "consumer loop serving; waiting 3 OK calls..."
sleep 3

step "[3/5] KILL Nacos (control plane down) at $(stamp)"
docker stop -t 1 "$NACOS_CONTAINER"
kill_at=$(ms)
note "Nacos stopped (t=$kill_at); polling consumer for failure..."
fail_at=""
for i in $(seq 1 60); do
  if grep -q "\[CONSUMER\] #.* FAIL" "$CV_LOG" 2>/dev/null; then
    fail_at=$(ms)
    note "first consumer FAIL at t+$((fail_at-kill_at)) ms after Nacos stopped"
    break
  fi
  sleep 1
done
[ -z "$fail_at" ] && note "no FAIL within 60s (local cache kept serving)"
ok_before_fail=$(grep -c " OK " "$CV_LOG")
note "total consumer OK calls before first FAIL: $ok_before_fail"

step "[4/5] restart Nacos, watch recovery"
docker start "$NACOS_CONTAINER" >/dev/null
for i in $(seq 1 40); do curl -sf --max-time 2 "$NACOS/nacos/v1/console/health/readiness" >/dev/null 2>&1 && break; sleep 1; done
note "Nacos back; waiting for consumer to recover (re-subscribe) ..."
recovered=""
for i in $(seq 1 60); do
  last=$(grep " OK " "$CV_LOG" | tail -1)
  if echo "$last" | grep -q " OK "; then
    t=$(echo "$last" | grep -oE "t=\+[0-9.]+s" | head -1)
    rec_at=$(ms)
    [ -z "$recovered" ] && { recovered="yes"; note "consumer OK again (last line: $t at t+$((rec_at-fail_at))ms after first FAIL)"; }
  fi
  sleep 1
done
note "consumer log tail after recovery:"; grep " OK \| FAIL " "$CV_LOG" | tail -6

step "[5/5] kill -9 provider, measure temporary-instance eviction delay"
svc="providers:com.zhiya.dubbo.demo.api.GreetingService:"
before=$(curl -s "$NACOS/nacos/v2/ns/instance/list?serviceName=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1],safe=''))" "$svc")&groupName=DEFAULT_GROUP" | python3 -c "import json,sys;print(len(json.load(sys.stdin)['data'].get('hosts',[])))")
note "instances before kill: $before"
pkill -9 -f "ProviderApp"
kill_at=$(ms)
note "provider killed at $(stamp), polling Nacos for eviction..."
evicted=""
for i in $(seq 1 60); do
  n=$(curl -s --max-time 2 "$NACOS/nacos/v2/ns/instance/list?serviceName=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1],safe=''))" "$svc")&groupName=DEFAULT_GROUP" | python3 -c "import json,sys;print(len(json.load(sys.stdin)['data'].get('hosts',[])))" 2>/dev/null)
  if [ "$n" = "0" ] 2>/dev/null; then evicted=$(ms); note "evicted after $((evicted-kill_at)) ms"; break; fi
  sleep 1
done
[ -z "$evicted" ] && note "NOT evicted within 60s (check Nacos config)"

note "consumer log tail:"
grep " OK \| FAIL " "$CV_LOG" | tail -12
step "DONE. consumer log: $CV_LOG | provider log: $PV_LOG"
