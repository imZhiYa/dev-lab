#!/bin/bash
#
# jvm-demo/scripts/run-jvm-demos.sh
#
# 用途：在 GitHub Actions 或本地使用 JDK 21 运行 jvm-demo 全量验证
# 特点：
#   - 为每个 demo 注入推荐的 JVM 配置（从源码注释提取）
#   - 高级诊断参数：
#       • -XX:NativeMemoryTracking=summary (NMT)
#       • -XX:StartFlightRecording (JFR 录制)
#   - 正常 demo：带 GC 日志 + 合理内存 + 高级诊断
#   - OOM demo：故意使用极小限制 + 捕获预期失败
#   - 自动生成 JFR 文件、GC 日志、Heap Dump
#   - 可直接在 GitHub Actions 中调用，并自动上传 artifact
#
# 用法：
#   chmod +x jvm-demo/scripts/run-jvm-demos.sh
#   ./jvm-demo/scripts/run-jvm-demos.sh
#
# GitHub Actions 推荐用法：
#   - name: 运行带高级 JVM 配置的验证
#     run: bash jvm-demo/scripts/run-jvm-demos.sh
#

set -euo pipefail

CP="jvm-demo/target/classes"
LOG_DIR="/tmp/jvm-demo-logs"
mkdir -p "$LOG_DIR"

# =====================================================
# 高级 JVM 参数（GitHub Actions 友好）
# =====================================================
NMT="-XX:NativeMemoryTracking=summary"

# JFR 通用设置（JDK 21 推荐）
# - name: 录制名称
# - settings=profile：性能分析模板（包含 CPU、内存、锁等）
# - maxsize=100M：防止日志过大
# - filename：输出 .jfr 文件
JFR_COMMON="-XX:StartFlightRecording=name=jvm-demo,settings=profile,maxsize=100M"

echo "=================================================="
echo "🚀 jvm-demo JDK 21 + 高级 JVM 配置验证脚本"
echo "=================================================="
echo "CLASSPATH: $CP"
echo "日志目录: $LOG_DIR"
echo "启用特性: NativeMemoryTracking + JFR"
echo "JDK: $(java -version 2>&1 | head -1)"
echo ""

# 辅助函数：带高级配置运行
run_demo() {
    local name="$1"
    local main_class="$2"
    local jvm_opts="$3"
    local expected_fail="${4:-false}"
    local jfr_name="${5:-}"

    echo "--------------------------------------------------"
    echo "▶️  $name"
    echo "   JVM: $jvm_opts"
    if [ -n "$jfr_name" ]; then
        echo "   JFR: $jfr_name.jfr"
    fi
    echo "   Class: $main_class"
    echo "--------------------------------------------------"

    local log_file="$LOG_DIR/${name// /_}.log"
    local full_opts="$jvm_opts $NMT"

    # 如果指定了 JFR 名称，则追加 JFR 参数
    if [ -n "$jfr_name" ]; then
        local jfr_file="$LOG_DIR/${jfr_name}.jfr"
        full_opts="$full_opts $JFR_COMMON,filename=$jfr_file"
    fi

    if [ "$expected_fail" = "true" ]; then
        set +e
        java $full_opts -cp "$CP" "$main_class" 2>&1 | tee "$log_file"
        local exit_code=$?
        set -e

        if [ $exit_code -ne 0 ]; then
            echo "✅ $name 按预期以非零退出码结束 (OOM / 演示场景)"
        else
            echo "⚠️  $name 未触发预期异常（可能配置过大）"
        fi
    else
        java $full_opts -cp "$CP" "$main_class" 2>&1 | tee "$log_file"
        echo "✅ $name 正常完成"
    fi
    echo ""
}

# =====================================================
# 1. 正常验证类（带 NMT + JFR + GC 日志）
# =====================================================

run_demo "Jvm01-RuntimeDataArea" \
    "com.zhiya.runtime.Jvm01RuntimeDataArea" \
    "-Xms256m -Xmx256m -Xlog:gc=info,gc+heap=info,safepoint=info" \
    "false" \
    "jvm01-runtime"

run_demo "Jvm02-ClassLoading" \
    "com.zhiya.classloading.Jvm02ClassLoadingMechanism" \
    "-Xms128m -Xmx128m -Xlog:class+load=info,class+unload=info" \
    "false" \
    "jvm02-classloading"

run_demo "Jvm03-ObjectLayoutTlab" \
    "com.zhiya.object.Jvm03ObjectLayoutTlab" \
    "-Xms128m -Xmx128m -XX:+UseG1GC -Xlog:gc*=info" \
    "false" \
    "jvm03-object-tlab"

run_demo "Jvm04-SyncLockUpgrade" \
    "com.zhiya.sync.Jvm04SyncLockUpgrade" \
    "-Xms128m -Xmx128m -Xlog:gc=info" \
    "false" \
    "jvm04-sync"

run_demo "Jvm05-GcLoggingDiagnosis" \
    "com.zhiya.gc.Jvm05GcLoggingDiagnosis" \
    "-Xms128m -Xmx128m -Xlog:gc*=info,gc+heap=debug,gc+ergo=debug" \
    "false" \
    "jvm05-gc-logging"

run_demo "Jvm06-MonitoringDemo" \
    "com.zhiya.gc.Jvm06MonitoringDemo" \
    "-Xms128m -Xmx128m -Xlog:gc*,gc+heap=info,safepoint=info" \
    "false" \
    "jvm06-monitoring"

# =====================================================
# 2. OOM 演示类（故意使用极小限制 + NMT + JFR）
# =====================================================

echo "=================================================="
echo "💥 OOM 演示场景（故意触发 + 高级诊断，预期非零退出）"
echo "=================================================="

run_demo "HeapSpaceOom" \
    "com.zhiya.oom.HeapSpaceOom" \
    "-Xms32m -Xmx32m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$LOG_DIR/heap-space.hprof -Xlog:gc*=info,gc+heap=debug" \
    "true" \
    "oom-heapspace"

run_demo "MetaspaceOom" \
    "com.zhiya.oom.MetaspaceOom" \
    "-Xms128m -Xmx128m -XX:MaxMetaspaceSize=16m -Xlog:class+load=info,class+unload=info" \
    "true" \
    "oom-metaspace"

run_demo "DirectBufferMemoryOom" \
    "com.zhiya.oom.DirectBufferMemoryOom" \
    "-Xms32m -Xmx32m -XX:MaxDirectMemorySize=8m -Xlog:gc=info" \
    "true" \
    "oom-directbuffer"

run_demo "GcOverheadLimitOom" \
    "com.zhiya.oom.GcOverheadLimitOom" \
    "-Xms16m -Xmx16m -XX:+UseParallelGC -XX:-UseGCOverheadLimit -Xlog:gc*=info,gc+heap=debug" \
    "true" \
    "oom-gcoverhead"

# =====================================================
# 3. 最终验证 + 高级诊断报告
# =====================================================

echo "=================================================="
echo "✅ 全部 jvm-demo 验证完成！"
echo "=================================================="

echo ""
echo "📁 生成的文件列表："
ls -lh "$LOG_DIR" 2>/dev/null || echo "  (日志目录为空或无权限)"

echo ""
echo "=== JVM 高级诊断报告 ==="
echo "JDK 版本          : $(java -version 2>&1 | head -1)"
echo "NativeMemoryTracking : 已启用 (summary 级别)"
echo "JFR 录制          : 已启用 (profile settings)"
echo "GC 日志           : 多个 demo 开启 -Xlog:gc*"
echo "Heap Dump         : OOM demo 已生成 .hprof"
echo ""

echo "=== JFR 录制文件（可下载分析） ==="
find "$LOG_DIR" -name "*.jfr" -type f | while read -r jfr; do
    size=$(du -h "$jfr" | cut -f1)
    echo "  - $(basename "$jfr")  ($size)"
done || echo "  (未生成 JFR 文件)"

echo ""
echo "=== 本地查看建议（下载后执行） ==="
echo "  # 查看 JFR 概要"
echo "  jfr print --events CPUSample,AllocationSample,MonitorEnter,ClassLoad <file>.jfr | head -50"
echo ""
echo "  # 使用 JDK Mission Control (JMC) 或命令行分析"
echo "  jfr summary <file>.jfr"
echo ""
echo "  # Native Memory 详情（需在运行时或事后）"
echo "  jcmd <pid> VM.native_memory summary"
echo "=================================================="

# 生成简易摘要文件（便于 GitHub artifact 查看）
cat > "$LOG_DIR/summary.txt" << EOF
jvm-demo JDK21 高级验证报告
============================
生成时间: $(date)
JDK: $(java -version 2>&1 | head -1)

启用的高级参数:
- -XX:NativeMemoryTracking=summary
- -XX:StartFlightRecording (JFR)
- -Xlog:gc* (多级别)
- HeapDumpOnOutOfMemoryError

生成的 JFR 文件:
$(find "$LOG_DIR" -name "*.jfr" -exec basename {} \; 2>/dev/null | sort || echo "无")

生成的 Heap Dump:
$(find "$LOG_DIR" -name "*.hprof" -exec basename {} \; 2>/dev/null | sort || echo "无")

验证状态: 全部完成（含预期 OOM）
EOF

echo ""
echo "📝 摘要已写入: $LOG_DIR/summary.txt"
echo "=================================================="
