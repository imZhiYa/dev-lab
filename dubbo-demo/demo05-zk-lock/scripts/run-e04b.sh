#!/bin/bash
# E04b: ZooKeeper 分布式锁 + ZK vs Nacos 故障发现窗口对照
#
# Part 1: 互斥验证（LockDemo）——N 线程同时抢锁，任意时刻只有一个持有者
# Part 2: ZK session 语义——kill -9 持锁进程，测竞争者的抢锁延迟
# Part 3: Nacos 剔除窗口——kill -9 provider，测临时实例摘除延迟
#
# 用法：./run-e04b.sh [part]
#   part = lock | zk-session | nacos-evict | all   （默认 all）
# 前置：ZK 运行在 127.0.0.1:2181（docker 容器 zk-e04，见 README）；
#       Nacos 运行在 127.0.0.1:8848
# 多网卡环境（存在代理软件 TUN 虚拟网卡时）需先固定注册地址：
#   export DUBBO_IP_TO_BIND=<物理网卡IP> DUBBO_IP_TO_REGISTRY=<物理网卡IP>
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ZK="127.0.0.1:2181"
LOCK="order-stock"
LOCK_LOG="/tmp/e04b-lock.log"
HOLDER_LOG="/tmp/e04b-holder.log"
CONT_LOG="/tmp/e04b-contender.log"
HOLDER=com.zhiya.dubbo.demo.zklock.CrashHolder
CONT=com.zhiya.dubbo.demo.zklock.WaitingContender
LOCKDEMO=com.zhiya.dubbo.demo.zklock.LockDemo
PART="${1:-all}"

ms() { python3 -c "import time;print(int(time.time()*1000))"; }
stamp() { python3 -c "import time;t=time.time();print(time.strftime('%H:%M:%S',time.localtime(t))+'.%03d'%(int(t*1000)%1000))"; }
step() { echo; echo "########## $1 ##########"; }
note() { echo "[$(stamp)] $1"; }

cd "$ROOT"

if [ "$PART" = "all" ] || [ "$PART" = "lock" ]; then
step "PART 1: LockDemo - mutual exclusion (5 threads, hold 300ms)"
  mvn -q -pl demo05-zk-lock exec:java -Dexec.mainClass="$LOCKDEMO" -Dexec.args="5 300 $ZK $LOCK" > "$LOCK_LOG" 2>&1
  grep -E "=== |LOCKED|unlocked|MUTUAL" "$LOCK_LOG" | head -25
  grep -q "MUTUAL EXCLUSION OK" "$LOCK_LOG" && note "MUTUAL EXCLUSION OK" || { note "LOCK TEST FAILED"; tail -20 "$LOCK_LOG"; exit 1; }
fi

if [ "$PART" = "all" ] || [ "$PART" = "zk-session" ]; then
step "PART 2: ZK session semantics - kill -9 holder, measure contender acquire delay"
  pkill -f "CrashHolder" 2>/dev/null; pkill -f "WaitingContender" 2>/dev/null; sleep 2
  # clean any leftover ephemeral nodes from a previous run
  docker exec zk-e04 sh -c 'rmr /e04-locks 2>/dev/null || true'

  (mvn -q -pl demo05-zk-lock exec:java -Dexec.mainClass="$HOLDER" -Dexec.args="$ZK $LOCK 120" > "$HOLDER_LOG" 2>&1 &)
  for i in $(seq 1 40); do grep -q "HOLDER_READY" "$HOLDER_LOG" 2>/dev/null && break; sleep 1; done
  grep -q "HOLDER_READY" "$HOLDER_LOG" || { note "HOLDER FAILED"; tail -20 "$HOLDER_LOG"; exit 1; }
  grep "HOLDER:" "$HOLDER_LOG"
  note "holder holds lock; starting contender ..."
  t_wait0=$(ms)
  (mvn -q -pl demo05-zk-lock exec:java -Dexec.mainClass="$CONT" -Dexec.args="$ZK $LOCK" > "$CONT_LOG" 2>&1 &)
  sleep 4
  note "killing holder with kill -9 ..."
  pkill -9 -f "CrashHolder"
  for i in $(seq 1 30); do grep -q "ACQUIRED" "$CONT_LOG" 2>/dev/null && break; sleep 1; done
  if grep -q "ACQUIRED" "$CONT_LOG" 2>/dev/null; then
    grep "CONTENDER:" "$CONT_LOG"
  else
    note "contender did NOT acquire within 30s"; tail -10 "$CONT_LOG"
  fi
  pkill -f "WaitingContender" 2>/dev/null
fi

if [ "$PART" = "all" ] || [ "$PART" = "nacos-evict" ]; then
step "PART 3: Nacos eviction window - kill -9 provider, measure instance removal"
  NACOS="http://127.0.0.1:8848"
  PV_LOG="/tmp/e04-provider.log"
  PV=com.zhiya.dubbo.demo.registry.ProviderApp
  export DUBBO_IP_TO_BIND="${DUBBO_IP_TO_BIND:-}"
  export DUBBO_IP_TO_REGISTRY="${DUBBO_IP_TO_REGISTRY:-}"
  pkill -f "ProviderApp" 2>/dev/null; sleep 2
  docker start nacos-e04 >/dev/null 2>&1
  for i in $(seq 1 40); do curl -sf --max-time 2 "$NACOS/nacos/v1/console/health/readiness" >/dev/null 2>&1 && break; sleep 1; done
  (mvn -q -pl demo02-registry exec:java -Dexec.mainClass="$PV" -Dexec.args="" \
    -Ddubbo.application.register-mode=all > "$PV_LOG" 2>&1 &)
  for i in $(seq 1 60); do grep -q "waiting for orders" "$PV_LOG" 2>/dev/null && break; sleep 1; done
  sleep 5
  svc="providers:com.zhiya.dubbo.demo.api.GreetingService::"
  encoded=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1],safe=''))" "$svc")
  n0=$(curl -s --max-time 2 "$NACOS/nacos/v2/ns/instance/list?serviceName=$encoded&groupName=DEFAULT_GROUP" | python3 -c "import json,sys;print(len(json.load(sys.stdin)['data'].get('hosts',[])))")
  note "instances before kill: $n0"
  pkill -9 -f "ProviderApp"
  kill_at=$(ms)
  note "provider killed (kill -9); polling Nacos for eviction ..."
  evicted=""
  for i in $(seq 1 90); do
    n=$(curl -s --max-time 2 "$NACOS/nacos/v2/ns/instance/list?serviceName=$encoded&groupName=DEFAULT_GROUP" | python3 -c "import json,sys;print(len(json.load(sys.stdin)['data'].get('hosts',[])))" 2>/dev/null)
    if [ "${n:-1}" = "0" ] 2>/dev/null; then evicted=$(ms); note "Nacos evicted instance after $((evicted-kill_at)) ms"; break; fi
    sleep 1
  done
  [ -z "$evicted" ] && note "NOT evicted within 90s"
fi

step "DONE"
