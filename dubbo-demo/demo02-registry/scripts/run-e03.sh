#!/bin/bash
# E03: 接口级 vs 应用级注册三态对比（Dubbo 3.3.4 + Nacos 2.4.3）
# 用法：./run-e03.sh all|interface|instance
# 前置：Nacos 运行在 127.0.0.1:8848（standalone、鉴权关闭，见 README）
# 多网卡环境（存在代理软件 TUN 虚拟网卡时）需先固定注册地址：
#   export DUBBO_IP_TO_BIND=<物理网卡IP> DUBBO_IP_TO_REGISTRY=<物理网卡IP>
set -u
MODE="${1:?usage: run-e03.sh all|interface|instance}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# 不硬编码注册地址：默认交给调用方环境；多网卡探测不稳定时由上方环境变量显式固定
export DUBBO_IP_TO_BIND="${DUBBO_IP_TO_BIND:-}"
export DUBBO_IP_TO_REGISTRY="${DUBBO_IP_TO_REGISTRY:-}"

NACOS="http://127.0.0.1:8848"
PV_LOG="/tmp/e03-provider-$MODE.log"

echo "########## E03 ROUND: register-mode=$MODE ##########"

pkill -f "ProviderApp" 2>/dev/null; sleep 2

echo "--- [1/5] starting provider (mode=$MODE) ---"
(cd "$ROOT" && nohup mvn -q -pl demo02-registry exec:java \
  -Dexec.mainClass=com.zhiya.dubbo.demo.registry.ProviderApp \
  -Ddubbo.application.register-mode="$MODE" > "$PV_LOG" 2>&1 &)
for i in $(seq 1 60); do grep -q "waiting for orders" "$PV_LOG" 2>/dev/null && break; sleep 1; done
grep -q "waiting for orders" "$PV_LOG" || { echo "PROVIDER FAILED"; tail -20 "$PV_LOG"; exit 1; }
echo "provider up"

sleep 3
echo "--- [2/5] service list in Nacos ---"
curl -s "$NACOS/nacos/v2/ns/service/list?pageNo=1&pageSize=100" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
print('service count =', d['count'])
for s in d['services']: print('  -', s)
"

echo "--- [3/5] instance payload per service ---"
for s in $(curl -s "$NACOS/nacos/v2/ns/service/list?pageNo=1&pageSize=100" | python3 -c "
import json,sys
for s in json.load(sys.stdin)['data']['services']: print(s)
"); do
  curl -s "$NACOS/nacos/v2/ns/instance/list?serviceName=$(python3 -c "
import urllib.parse,sys
print(urllib.parse.quote(sys.argv[1], safe=''))" "$s")&groupName=DEFAULT_GROUP" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
hosts=d.get('hosts',[])
print(f\"  service={d['name']!r} instances={len(hosts)}\")
for h in hosts:
    meta=h.get('metadata',{})
    keys=sorted(meta.keys())
    size=sum(len(k)+len(str(v)) for k,v in meta.items())
    print(f\"    ip={h['ip']}:{h['port']} healthy={h['healthy']} meta_keys={len(keys)} meta_bytes={size}\")
    print('    meta:', json.dumps(meta, ensure_ascii=False)[:300])
"
done

echo "--- [4/5] consumer call round-trip ---"
(cd "$ROOT" && timeout 120 mvn -q -pl demo02-registry exec:java \
  -Dexec.mainClass=com.zhiya.dubbo.demo.registry.ConsumerApp \
  -Ddubbo.application.register-mode="$MODE" 2>&1 | grep -E "CONSUMER.*(->|proxy)")

echo "--- [5/5] stopping provider ---"
pkill -f "ProviderApp" 2>/dev/null; sleep 3
echo "########## END ROUND $MODE ##########"
