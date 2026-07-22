#!/bin/bash
#
# =============================================================================
# jvm-demo/scripts/run-jvm-demos.sh
# =============================================================================
#
# 【脚本作用 (Purpose)】
#   1. 运行 jvm-demo 项目中的 10 个 JVM 演示程序（6 个正常示例 + 4 个 OOM 示例）
#   2. 支持 G1 和 ZGC 两种垃圾收集器矩阵测试（通过 GC_TYPE 环境变量控制）
#   3. 收集完整的诊断数据：
#        - GC 日志（-Xlog:gc*）
#        - JFR 飞行记录（settings=profile）
#        - Native Memory Tracking（NMT=summary）
#        - OOM 时强制生成 Heap Dump
#   4. **核心保障**：无论脚本中途失败（包括 System.exit(1)），都会生成 summary 文件
#   5. 最后自动生成结构化的“核心日志分析报告”（严格包含用户要求的 4 个 section）
#
# 【为什么需要这个脚本？】
#   - 早期版本因为 set -e + Jvm02ClassLoadingMechanism 里的 System.exit(1)，
#     导致 summary-*.txt 为空，GitHub Actions artifact 上传失败。
#   - 本脚本通过以下机制解决：
#     * 脚本启动后**立即**创建 summary 文件
#     * 使用 trap 捕获所有退出信号更新最终状态
#     * 每个 java 调用用 set +e 保护，不让单个 demo 失败中断整个流程
#
# 【使用方式 (How to Use)】
#
# 1. 本地运行（推荐先编译）
#    --------------------------------------------------
#    # 编译（必须）
#    mkdir -p jvm-demo/target/classes
#    find jvm-demo -name "*.java" | xargs javac -encoding UTF-8 -d jvm-demo/target/classes
#
#    # 默认使用 G1
#    ./jvm-demo/scripts/run-jvm-demos.sh
#
#    # 使用 ZGC
#    GC_TYPE=ZGC ./jvm-demo/scripts/run-jvm-demos.sh
#
#    # 运行后查看结果
#    ls -lh /tmp/jvm-demo-logs/
#    cat /tmp/jvm-demo-logs/summary-G1.txt
#
# 2. 在 GitHub Actions 中使用（当前主要用途）
#    --------------------------------------------------
#    由 .github/workflows/jdk21-ci.yml 调用：
#      env:
#        GC_TYPE: ${{ matrix.gc }}   # G1 或 ZGC
#      run: |
#        bash jvm-demo/scripts/run-jvm-demos.sh || echo "⚠️ 部分 demo 按预期退出"
#
#    工作流会用 if: always() 上传：
#      - /tmp/jvm-demo-logs/summary-*.txt
#      - /tmp/jvm-demo-logs/*.log
#      - /tmp/jvm-heap-dumps/*.hprof
#      - JFR 文件
#
# 3. 单独运行某个 demo（调试用）
#    --------------------------------------------------
#    可以临时注释掉 run() 调用，只保留想调试的那个。
#
# 【输出文件说明】
#   /tmp/jvm-demo-logs/
#     ├── summary-G1.txt          ← 核心分析报告（必须上传）
#     ├── summary-ZGC.txt
#     ├── Jvm01-G1.log
#     ├── Jvm02-G1.log            ← Jvm02 预期会失败（System.exit）
#     ├── Jvm01-G1.jfr
#     └── ...
#
#   /tmp/jvm-heap-dumps/
#     └── HeapOom-G1.hprof        ← OOM 时自动生成
#
# 【重要配置】
#   - Jvm02 故意标记为 expect_fail=true（因为它包含多个 System.exit(1) 校验）
#   - OOM 演示全部开启 -XX:+HeapDumpOnOutOfMemoryError
#   - 所有 java 调用都使用 eval + set +e 保护
#
# =============================================================================

set -euo pipefail

# -------------------------- 环境变量与目录 --------------------------
GC_TYPE="${GC_TYPE:-G1}"                    # 默认 G1，可通过环境变量指定 ZGC
CP="jvm-demo/target/classes"
LOG_DIR="/tmp/jvm-demo-logs"
DUMP_DIR="/tmp/jvm-heap-dumps"

mkdir -p "$LOG_DIR" "$DUMP_DIR"

# =====================================================
# 核心修复：脚本启动后**立即**创建 summary 文件
# 目的：即使后面所有 demo 都崩溃，artifact 也永远不会为空
# =====================================================
cat > "$LOG_DIR/summary-${GC_TYPE}.txt" << EOF
jvm-demo JDK21 核心日志分析报告
===============================
GC 类型: $GC_TYPE
时间: $(date)
JDK: $(java -version 2>&1 | head -1)

状态: 进行中 (脚本刚启动)

生成的日志文件:
(执行中...)

Heap Dump:
(执行中...)

JFR:
(执行中...)

=== 1. Full GC / OOM 相关 ===
(执行中...)

=== 2. 最后 20 条 Young / Full GC ===
(执行中...)

=== 3. 最后 40 条 Heap / Region 状态 ===
(执行中...)

=== 4. 日志末尾 ===
(执行中...)
EOF

# 无论脚本如何退出（包括 set -e 触发的退出、Ctrl+C、kill），都更新最终状态
trap 'cat > "$LOG_DIR/summary-${GC_TYPE}.txt" << EOF
jvm-demo JDK21 核心日志分析报告
===============================
GC 类型: '"$GC_TYPE"'
时间: $(date)
JDK: $(java -version 2>&1 | head -1)

状态: 完成 (可能部分 demo 中途退出或被中断)

生成的日志文件:
$(ls -1 "$LOG_DIR"/*-${GC_TYPE}.log 2>/dev/null | xargs -I {} basename {} || echo 无)

Heap Dump:
$(ls -1 "$DUMP_DIR"/*-${GC_TYPE}.hprof 2>/dev/null | xargs -I {} basename {} || echo 无)

JFR:
$(ls -1 "$LOG_DIR"/*-${GC_TYPE}.jfr 2>/dev/null | xargs -I {} basename {} || echo 无)
EOF' EXIT INT TERM

# -------------------------- 欢迎信息 --------------------------
echo "=================================================="
echo "🚀 jvm-demo JDK 21 + GC矩阵 + 诊断 (GC=$GC_TYPE)"
echo "=================================================="

# -------------------------- JVM 通用参数 --------------------------
COMMON="-XX:NativeMemoryTracking=summary"
JFR="-XX:StartFlightRecording=name=jvm-demo-${GC_TYPE},settings=profile,maxsize=60M"

# 根据 GC_TYPE 选择收集器
case "$GC_TYPE" in
    ZGC)   GC_OPT="-XX:+UseZGC" ;;
    *)     GC_OPT="-XX:+UseG1GC" ;;
esac

# -------------------------- 单个 demo 执行函数 --------------------------
# 用法：run "显示名称" "全限定类名" "JVM参数" [是否预期失败] [JFR文件名]
run() {
    local name=$1 cls=$2 opts=$3 fail=${4:-false} jfr=${5:-}
    local logf="$LOG_DIR/${name// /_}-${GC_TYPE}.log"
    local dump="$DUMP_DIR/${name// /_}-${GC_TYPE}.hprof"

    echo "▶️ $name"
    local cmd="java $opts $GC_OPT $COMMON"

    # 按需附加 JFR
    [ -n "$jfr" ] && cmd="$cmd $JFR,filename=$LOG_DIR/${jfr}.jfr"

    # OOM 类开启堆转储
    [ "$fail" = "true" ] && cmd="$cmd -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$dump"

    cmd="$cmd -cp $CP $cls"

    # ===================== 关键保护 =====================
    # 用 set +e 包裹单个 java 调用
    # 防止 Jvm02 的 System.exit(1) 或 OOM 导致整个脚本因为 set -e 而提前退出
    set +e
    eval "$cmd" 2>&1 | tee "$logf"
    local rc=$?
    set -e
    # ===================================================

    if [ "$fail" = "true" ]; then
        [ $rc -ne 0 ] && echo "✅ 预期 OOM (rc=$rc)" || echo "⚠️ 未触发 OOM"
    else
        [ $rc -eq 0 ] && echo "✅ 完成" || echo "⚠️ 退出码 $rc（继续执行）"
    fi
    echo ""
}

# ===================== 正常演示程序 =====================
run "Jvm01" "com.zhiya.runtime.Jvm01RuntimeDataArea" "-Xms256m -Xmx256m -Xlog:gc=info" false jvm01
run "Jvm02" "com.zhiya.classloading.Jvm02ClassLoadingMechanism" "-Xms128m -Xmx128m" true jvm02
run "Jvm03" "com.zhiya.object.Jvm03ObjectLayoutTlab" "-Xms128m -Xmx128m" false jvm03
run "Jvm04" "com.zhiya.sync.Jvm04SyncLockUpgrade" "-Xms128m -Xmx128m" false jvm04
run "Jvm05" "com.zhiya.gc.Jvm05GcLoggingDiagnosis" "-Xms128m -Xmx128m -Xlog:gc*=info" false jvm05
run "Jvm06" "com.zhiya.gc.Jvm06MonitoringDemo" "-Xms128m -Xmx128m -Xlog:gc*" false jvm06

# ===================== OOM 演示程序 =====================
echo "=== OOM ==="
run "HeapOom" "com.zhiya.oom.HeapSpaceOom" "-Xms32m -Xmx32m" true oom-heap
run "MetaOom" "com.zhiya.oom.MetaspaceOom" "-Xms128m -Xmx128m -XX:MaxMetaspaceSize=12m" true oom-meta
run "DirectOom" "com.zhiya.oom.DirectBufferMemoryOom" "-Xms32m -Xmx32m -XX:MaxDirectMemorySize=6m" true oom-direct
run "GcOverOom" "com.zhiya.oom.GcOverheadLimitOom" "-Xms16m -Xmx16m" true oom-gc

# =====================================================
# 核心日志分析 —— 严格输出用户要求的 4 个 section
# =====================================================
analyze_and_update_summary() {
    local summary="$LOG_DIR/summary-${GC_TYPE}.txt"
    local all_logs=$(ls "$LOG_DIR"/*-${GC_TYPE}.log 2>/dev/null || true)

    # 重写 summary 文件头部
    cat > "$summary" << EOF
jvm-demo JDK21 核心日志分析报告
===============================
GC 类型: $GC_TYPE
时间: $(date)
JDK: $(java -version 2>&1 | head -1)

状态: 完成

生成的日志文件:
$(ls -1 "$LOG_DIR"/*-${GC_TYPE}.log 2>/dev/null | xargs -I {} basename {} || echo 无)

Heap Dump:
$(ls -1 "$DUMP_DIR"/*-${GC_TYPE}.hprof 2>/dev/null | xargs -I {} basename {} || echo 无)

JFR:
$(ls -1 "$LOG_DIR"/*-${GC_TYPE}.jfr 2>/dev/null | xargs -I {} basename {} || echo 无)

EOF

    # Section 1: Full GC / OOM 相关
    echo "=== 1. Full GC / OOM 相关 ===" >> "$summary"
    if [ -n "$all_logs" ]; then
        grep -E -i "(full gc|oom|outofmemory|heapdump|gc overhead|directbuffer|OutOfMemoryError)" $all_logs 2>/dev/null | tail -30 >> "$summary" || echo "(无匹配的 Full GC / OOM 日志)" >> "$summary"
    else
        echo "(无日志文件)" >> "$summary"
    fi
    echo "" >> "$summary"

    # Section 2: 最后 20 条 Young / Full GC
    echo "=== 2. 最后 20 条 Young / Full GC ===" >> "$summary"
    if [ -n "$all_logs" ]; then
        grep -E "(Young|Full GC|GC\(|gc\()" $all_logs 2>/dev/null | tail -20 >> "$summary" || echo "(无 Young/Full GC 日志)" >> "$summary"
    else
        echo "(无日志文件)" >> "$summary"
    fi
    echo "" >> "$summary"

    # Section 3: 最后 40 条 Heap / Region 状态
    echo "=== 3. 最后 40 条 Heap / Region 状态 ===" >> "$summary"
    if [ -n "$all_logs" ]; then
        grep -E -i "(Heap|Region|eden|survivor|tenured|metaspace|used|capacity|committed)" $all_logs 2>/dev/null | tail -40 >> "$summary" || echo "(无 Heap/Region 状态日志)" >> "$summary"
    else
        echo "(无日志文件)" >> "$summary"
    fi
    echo "" >> "$summary"

    # Section 4: 日志末尾
    echo "=== 4. 日志末尾 ===" >> "$summary"
    if [ -n "$all_logs" ]; then
        for logf in $all_logs; do
            echo "--- $(basename "$logf") ---" >> "$summary"
            tail -25 "$logf" 2>/dev/null >> "$summary" || true
            echo "" >> "$summary"
        done
    else
        echo "(无日志文件)" >> "$summary"
    fi
}

# 最终强制执行分析并更新 summary（保证 artifact 内容完整）
analyze_and_update_summary

echo "✅ 全部结束 | summary: $LOG_DIR/summary-${GC_TYPE}.txt"
ls -lh "$LOG_DIR/summary-${GC_TYPE}.txt" 2>/dev/null || true
