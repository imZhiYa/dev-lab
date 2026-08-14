#!/bin/bash
# =============================================================================
# dev-lab Dubbo 专区公审引擎 (Dubbo 3.3.4 Verification Engine)
# 编译 + 冒烟：E00 直连 round-trip / E01 序列化产物 / E09 SPI 四连
# 每个 demo 断言关键输出，失败即退出非零
# 依赖 docker 的实验（E03/E04/E05 注册中心、E04b ZK 锁）不在 CI 运行，需本地环境
# =============================================================================
set -e
cd "$(dirname "$0")/../dubbo-demo"

assert_contains() {
  local haystack="$1" needle="$2" name="$3"
  if ! echo "$haystack" | grep -q "$needle"; then
    echo "❌ [$name] 断言失败：输出中未找到 [$needle]"
    echo "$haystack" | head -30
    exit 1
  fi
}

PV_PID=""
cleanup() {
  [ -n "$PV_PID" ] && kill "$PV_PID" 2>/dev/null || true
}
trap cleanup EXIT

echo "🔨 [1/5] mvn install 全模块（dubbo-api 需进本地仓库供 -pl 单模块 exec）..."
mvn -q install -DskipTests
echo "✅ 编译成功"

echo "🧪 [2/5] demo00 直连 round-trip（后台 provider + consumer）..."
PV_LOG=$(mktemp)
mvn -q -pl demo00-callchain exec:java \
  -Dexec.mainClass=com.zhiya.dubbo.demo.callchain.ProviderApp > "$PV_LOG" 2>&1 &
PV_PID=$!
for i in $(seq 1 60); do grep -q "waiting for orders" "$PV_LOG" 2>/dev/null && break; sleep 1; done
grep -q "waiting for orders" "$PV_LOG" || { echo "❌ provider 启动失败"; cat "$PV_LOG"; exit 1; }
OUT=$(mvn -q -pl demo00-callchain exec:java \
  -Dexec.mainClass=com.zhiya.dubbo.demo.callchain.ConsumerApp 2>&1)
assert_contains "$OUT" "Hello O" "demo00 round-trip"
kill "$PV_PID" 2>/dev/null; PV_PID=""
echo "✅ E00 直连全链路通过"

echo "🧪 [3/5] demo01 SerializeDump 字节产物..."
OUT=$(mvn -q -pl demo01-protocol exec:java \
  -Dexec.mainClass=com.zhiya.dubbo.demo.protocol.SerializeDump 2>&1)
assert_contains "$OUT" "contentTypeId=2" "hessian2 ID"
assert_contains "$OUT" "contentTypeId=8" "kryo ID"
assert_contains "$OUT" "contentTypeId=23" "fastjson2 ID"
assert_contains "$OUT" "roundtrip=OK" "序列化往返一致"
echo "✅ E01 三种盒子字节产物通过"

echo "🧪 [4/5] demo08 SPI 四连..."
OUT=$(mvn -q -pl demo08-spi exec:java \
  -Dexec.mainClass=com.zhiya.dubbo.demo.spi.SpiBasicsApp 2>&1)
assert_contains "$OUT" "default extension name = random" "E09-1 默认扩展名"
assert_contains "$OUT" "same instance cached? true" "E09-1 单例缓存"
assert_contains "$OUT" "No such extension" "E09-1 报错格式"

OUT=$(mvn -q -pl demo08-spi exec:java \
  -Dexec.mainClass=com.zhiya.dubbo.demo.spi.AdaptiveApp 2>&1)
assert_contains "$OUT" "adaptive class: com.zhiya.dubbo.demo.spi.extension.DemoBalance\$Adaptive" "E09-2 自适应类"

OUT=$(mvn -q -pl demo08-spi exec:java \
  -Dexec.mainClass=com.zhiya.dubbo.demo.spi.WrapperApp 2>&1)
assert_contains "$OUT" "is wrapped by DemoBalanceWrapper? true" "E09-4 Wrapper 包装"

OUT=$(mvn -q -pl demo08-spi exec:java \
  -Dexec.mainClass=com.zhiya.dubbo.demo.spi.ActivateApp 2>&1)
assert_contains "$OUT" "contains DemoProviderFilter? true" "E09-3 @Activate 激活"
echo "✅ E09 SPI 四连通过"

echo "📝 [5/5] 注册中心/ZK 实验为 docker 依赖，CI 跳过（本地跑法见 README）"
echo "🎉 Dubbo 专区公审全部通过"
