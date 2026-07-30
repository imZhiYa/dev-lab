#!/bin/bash
set -e

# =============================================================================
# dev-lab JDK 8 专区智能增量公审引擎 (JDK 8 Smart Verification Engine)
# =============================================================================

FORCE_ALL="${1:-false}"

echo "================================================================="
echo "🚀 启动 dev-lab JDK 8 智能 CI 引擎"
echo "================================================================="

TARGET_MODULES=("binary-demo" "tree-demo" "aqs-demo" "collection-demo")

if [ "$FORCE_ALL" = "true" ]; then
    echo "⚙️  [全量模式] 显式触发全量回归自检"
    FORCE_RUN=true
else
    FORCE_RUN=false
fi

if [ "$FORCE_RUN" = "false" ]; then
    if git rev-parse --verify origin/main >/dev/null 2>&1; then
        CHANGED_FILES=$(git diff --name-only origin/main...HEAD 2>/dev/null || git diff --name-only HEAD~1 HEAD 2>/dev/null || echo "")
    else
        CHANGED_FILES=$(git diff --name-only HEAD~1 HEAD 2>/dev/null || echo "")
    fi

    if [ -z "$CHANGED_FILES" ]; then
        echo "ℹ️  未检测到 Git 增量变更，默认开启全量安全自检。"
        FORCE_RUN=true
    else
        echo "🔍 检测到本次 Commit / PR 的变动文件列表:"
        echo "$CHANGED_FILES" | sed 's/^/  - /'
    fi
fi

RUN_COUNT=0
SKIP_COUNT=0
TOTAL_DEMOS_RUN=0

for module in "${TARGET_MODULES[@]}"; do
    if [ ! -d "$module" ]; then
        continue
    fi

    SHOULD_RUN=false
    if [ "$FORCE_RUN" = "true" ]; then
        SHOULD_RUN=true
    else
        if echo "$CHANGED_FILES" | grep -q "^${module}/"; then
            SHOULD_RUN=true
        fi
    fi

    if [ "$SHOULD_RUN" = "false" ]; then
        echo ""
        echo "⏭️  [跳过模块] $module 未检测到文件变更，自动跳过。"
        SKIP_COUNT=$((SKIP_COUNT + 1))
        continue
    fi

    RUN_COUNT=$((RUN_COUNT + 1))
    echo ""
    echo "================================================================="
    echo "🟢 [构建与公审] 正在处理模块: $module"
    echo "================================================================="

    TARGET_DIR="$module/target/classes"
    mkdir -p "$TARGET_DIR"

    JAVA_FILES=$(find "$module" -name "*.java")
    if [ -z "$JAVA_FILES" ]; then
        echo "⚠️ 模块 $module 未找到 Java 源文件，跳过。"
        continue
    fi

    # 编译
    echo "🔨 正在编译 $module 全量源码..."
    javac -encoding UTF-8 -d "$TARGET_DIR" $JAVA_FILES
    echo "✅ $module 编译成功！"

    # 自动寻址搜索包含 main 方法的类
    echo "🔍 自动感知 $module 中的 Demo 入口类..."
    MAIN_CLASSES=""
    for jf in $JAVA_FILES; do
        if grep -v '^[[:space:]]*//' "$jf" | grep -v '^[[:space:]]*/\*' | grep -v '^[[:space:]]*\*' | grep -q "public static void main"; then
            PKG=$(grep -E '^[[:space:]]*package[[:space:]]+' "$jf" | head -n 1 | sed -E 's/^[[:space:]]*package[[:space:]]+//;s/[;[:space:]].*//' | tr -d '\r')
            CLS=$(basename "$jf" .java)
            if [ -n "$PKG" ]; then
                FULL_CLS="${PKG}.${CLS}"
            else
                FULL_CLS="${CLS}"
            fi
            MAIN_CLASSES="$MAIN_CLASSES $FULL_CLS"
        fi
    done

    SORTED_MAIN_CLASSES=$(echo "$MAIN_CLASSES" | tr ' ' '\n' | grep -v '^$' | sort)

    if [ -z "$SORTED_MAIN_CLASSES" ]; then
        echo "ℹ️ $module 未找到包含 main 函数的入口类。"
    else
        echo "🚀 发现以下可执行 Demo 类:"
        echo "$SORTED_MAIN_CLASSES" | sed 's/^/   -> /'
        echo ""

        for cls in $SORTED_MAIN_CLASSES; do
            echo "-----------------------------------------------------------------"
            echo "▶️ [运行] $cls"
            echo "-----------------------------------------------------------------"
            java -cp "$TARGET_DIR" "$cls"
            echo "✅ $cls 校验通过！"
            TOTAL_DEMOS_RUN=$((TOTAL_DEMOS_RUN + 1))
        done
    fi
done

echo ""
echo "================================================================="
echo "🎉 JDK 8 增量公审流程顺利完成！"
echo "📊 汇总报告: 触发模块数: $RUN_COUNT | 跳过模块数: $SKIP_COUNT | 累计公审 Demo 数: $TOTAL_DEMOS_RUN"
echo "================================================================="
