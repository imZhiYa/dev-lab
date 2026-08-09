#!/bin/bash
# 优雅停机对照实验：immediate vs graceful（3.3.5 实测，demo16）
# 用法：./graceful-shutdown-test.sh immediate|graceful|graceful-timeout
set -e
cd "$(dirname "$0")/.."

MODE="${1:-immediate}"
PORT=18080
WORK=$(mktemp -d -t graceful-test)
trap 'rm -rf "$WORK"' EXIT

ARGS="--server.port=$PORT"
case "$MODE" in
  graceful)        ARGS="$ARGS --server.shutdown=graceful" ;;
  graceful-timeout) ARGS="$ARGS --server.shutdown=graceful --server.shutdown-timeout=2s" ;;
esac

echo "== 模式: $MODE  (args: $ARGS)"

# 依赖 classpath 复用 run.sh 的生成逻辑
if [ ! -f target/cp.txt ]; then
  mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
fi
CP="target/classes:$(cat target/cp.txt)"

java -cp "$CP" demo16.GracefulShutdownApp $ARGS > "$WORK/app.log" 2>&1 &
APP_PID=$!
echo "app pid=$APP_PID"

# 等 /ping 就绪
for i in $(seq 1 30); do
  if curl -s -m 1 "http://127.0.0.1:$PORT/ping" > /dev/null 2>&1; then break; fi
  sleep 0.5
done
echo "[t=0] 应用就绪，发起 6s 慢请求"
curl -s -m 15 "http://127.0.0.1:$PORT/slow" > "$WORK/slow.out" &
SLOW_PID=$!
sleep 1
echo "[t=1s] SIGTERM 发出（慢请求在途）"
START=$(date +%s%N)
kill -TERM $APP_PID

# SIGTERM 后再发新请求：观察被拒还是放行
sleep 1
NEW_CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 2 "http://127.0.0.1:$PORT/ping" 2>&1 || echo "conn-fail")
echo "[t=2s] SIGTERM 后新请求结果: $NEW_CODE"

# 等进程退出（wait 返回 143 = 被 SIGTERM 杀；set -e 下先临时关闭）
set +e
wait $APP_PID
APP_EXIT=$?
set -e
END=$(date +%s%N)
ELAPSED=$(( (END - START) / 1000000 ))
echo "== 进程退出码=[$APP_EXIT] SIGTERM 到退出耗时=${ELAPSED}ms"

# 慢请求结果
if wait $SLOW_PID 2>/dev/null; then
  echo "== 慢请求(6s)完成: $(cat "$WORK/slow.out")"
else
  echo "== 慢请求(6s)被中断"
fi

echo "== 应用日志 =="
cat "$WORK/app.log"
