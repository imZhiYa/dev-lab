#!/bin/bash
# 环境快照：记录本次会话的 JVM / Docker / 容器版本与内核参数，作为结果证据边界的一部分
set -e
cd "$(dirname "$0")/.."

echo "─── 环境快照 ───"
JAVA_BIN="java"
if /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
  JAVA_BIN="$(/usr/libexec/java_home -v 21)/bin/java"
fi
echo "JVM      : $($JAVA_BIN -version 2>&1 | head -1)"
echo "Docker   : $(docker version --format '{{.Server.Version}}' 2>/dev/null)"
echo "colima   : $(colima list 2>/dev/null | tail -1 | awk '{print $2, $3, $4, $5}')"
# ES mmap 需要 vm.max_map_count ≥ 262144（colima VM 重启会丢 sysctl，尽力读取当前值）
MMC=$(colima ssh -- sysctl -n vm.max_map_count 2>/dev/null | tr -d '\r' || true)
echo "vm.max_map_count(colima VM): ${MMC:-未知}"
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q eslab; then
  echo "容器镜像 :"
  docker inspect $(docker ps --format '{{.Names}}' | grep eslab | tr '\n' ' ') \
    --format '  {{.Name}} → {{.Config.Image}}' 2>/dev/null
fi
echo "─────────────"
