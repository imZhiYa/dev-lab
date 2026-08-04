#!/bin/bash
set -e

# =============================================================================
# dev-lab JDK 21 专区公审引擎 (JDK 21 JVM Demo Verification Engine)
# =============================================================================

echo "================================================================="
echo "🚀 启动 dev-lab JDK 21 JVM 特性与诊断实战公审"
echo "================================================================="

# 1. 编译 jvm-demo 全量源码 (JDK 21)
echo "🔨 正在编译 jvm-demo 全量源码..."
mkdir -p jvm-demo/target/classes
find jvm-demo -name "*.java" | xargs javac -encoding UTF-8 -d jvm-demo/target/classes
echo "✅ jvm-demo 全量源码编译成功！"

# 2. 执行核心诊断与演示脚本
if [ -f "jvm-demo/scripts/run-jvm-demos.sh" ]; then
    chmod +x jvm-demo/scripts/run-jvm-demos.sh
    GC_TYPE=G1 ./jvm-demo/scripts/run-jvm-demos.sh
else
    echo "❌ 找不到 jvm-demo/scripts/run-jvm-demos.sh 脚本！"
    exit 1
fi

# 3. 编译 thread-demo 全量源码 (JDK 21 - 虚拟线程 / 线程池 / 响应式)
echo ""
echo "================================================================="
echo "🧵 启动 thread-demo 线程池与虚拟线程实验公审"
echo "================================================================="
echo "🔨 正在编译 thread-demo 全量源码..."
mkdir -p thread-demo/target/classes
find thread-demo -name "*.java" | xargs javac -encoding UTF-8 -d thread-demo/target/classes
echo "✅ thread-demo 全量源码编译成功！"

# 4. 运行 thread-demo 各实验
echo "🧪 运行 VirtualThreadDemo..."
java -cp thread-demo/target/classes com.zhiya.VirtualThreadDemo

echo ""
echo "🧪 运行 ThreadPoolLabs..."
java -cp thread-demo/target/classes com.zhiya.ThreadPoolLabs

echo ""
echo "🧪 运行 ReactiveNoDepsDemo (背压 + IO隔离实验)..."
java -cp thread-demo/target/classes com.zhiya.ReactiveNoDepsDemo

echo ""
echo "✅ thread-demo 全部实验执行完毕！"

# 5. 编译 innodb-demo 全量源码 (JDK 21)
echo ""
echo "================================================================="
echo "🐬 启动 innodb-demo 数据库底层机制实验公审"
echo "================================================================="
echo "🔨 正在编译 innodb-demo 全量源码..."
mkdir -p innodb-demo/target/classes
find innodb-demo -name "*.java" | xargs javac -encoding UTF-8 -d innodb-demo/target/classes
echo "✅ innodb-demo 全量源码编译成功！"

echo "🧪 运行 BufferPoolLRU..."
java -Dfile.encoding=UTF-8 -cp innodb-demo/target/classes com.zhiya.innodb.BufferPoolLRU

echo "🧪 运行 BPlusTreeRoutingDemo..."
java -Dfile.encoding=UTF-8 -cp innodb-demo/target/classes com.zhiya.innodb.BPlusTreeRoutingDemo

echo "🧪 运行 PageDirectoryDemo..."
java -Dfile.encoding=UTF-8 -cp innodb-demo/target/classes com.zhiya.innodb.PageDirectoryDemo

echo "🧪 运行 RedoLogRingBufferDemo..."
java -Dfile.encoding=UTF-8 -cp innodb-demo/target/classes com.zhiya.innodb.RedoLogRingBufferDemo

echo "🧪 运行 MVCCDemo..."
java -Dfile.encoding=UTF-8 -cp innodb-demo/target/classes com.zhiya.innodb.MVCCDemo

echo "🧪 运行 NextKeyLockDemo..."
java -Dfile.encoding=UTF-8 -cp innodb-demo/target/classes com.zhiya.innodb.NextKeyLockDemo

echo "🧪 运行 DoublewriteBufferDemo..."
java -Dfile.encoding=UTF-8 -cp innodb-demo/target/classes com.zhiya.innodb.DoublewriteBufferDemo

echo "✅ innodb-demo 全部底层模型运行验证完毕！"

# 8. 编译 redis-demo 全量源码 (JDK 21 - Redis 深度解析: 9 层认知墙 + 坑 / 自测 / 决策卡)
echo ""
echo "================================================================="
echo "⚡ 启动 redis-demo Redis 深度解析实验公审"
echo "================================================================="
echo "🔨 正在编译 redis-demo 全量源码..."
mkdir -p redis-demo/target/classes
find redis-demo -name "*.java" | xargs javac -encoding UTF-8 -d redis-demo/target/classes
echo "✅ redis-demo 全量源码编译成功！"

# 若存在 RunAllDemos 跑批入口则一次跑批，否则自动扫描所有含 main 的类逐个运行
if find redis-demo -name "RunAllDemos.java" | grep -q .; then
    echo "🚀 检测到 RunAllDemos 跑批入口，执行全量 21 项演示..."
    java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp redis-demo/target/classes com.zhiya.redis.RunAllDemos all
else
    echo "🚀 自动扫描 redis-demo 中的 Demo 入口类..."
    for jf in $(find redis-demo -name "*.java"); do
        if grep -q "public static void main" "$jf"; then
            PKG=$(grep -E '^[[:space:]]*package[[:space:]]+' "$jf" | head -n 1 | sed -E 's/^[[:space:]]*package[[:space:]]+//;s/[;[:space:]].*//' | tr -d '\r')
            CLS=$(basename "$jf" .java)
            if [ -n "$PKG" ]; then FULL_CLS="${PKG}.${CLS}"; else FULL_CLS="${CLS}"; fi
            echo "▶️  [运行] $FULL_CLS"
            java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp redis-demo/target/classes "$FULL_CLS"
            echo "✅ $FULL_CLS 校验通过！"
        fi
    done
fi
echo "✅ redis-demo 全部实验运行验证完毕！"

# 9. 编译并运行 network-demo 全量源码（JDK 21：BIO / NIO / Reactor / AIO / 生命周期）
echo ""
echo "================================================================="
echo "🌐 启动 network-demo 高性能网络编程实验公审"
echo "================================================================="

echo "🔨 正在编译 network-demo 全量源码..."
mkdir -p network-demo/target/classes

find network-demo/src/java/main -name "*.java" \
  | xargs javac -encoding UTF-8 -d network-demo/target/classes

echo "✅ network-demo 全量源码编译成功！"

echo "🚀 自动扫描 network-demo 中的 Demo 入口类..."

while IFS= read -r java_file; do
  if grep -q "public static void main" "$java_file"; then
    package_name=$(
      grep -E '^[[:space:]]*package[[:space:]]+' "$java_file" \
        | head -n 1 \
        | sed -E 's/^[[:space:]]*package[[:space:]]+//;s/[;[:space:]].*//' \
        | tr -d '\r'
    )

    class_name=$(basename "$java_file" .java)
    full_class="${package_name}.${class_name}"

    echo "▶️ [运行] $full_class"

    java \
      -Dfile.encoding=UTF-8 \
      -Dstdout.encoding=UTF-8 \
      -cp network-demo/target/classes \
      "$full_class"

    echo "✅ $full_class 校验通过！"
  fi
done < <(find network-demo/src/java/main -name "*.java" | sort)

echo "✅ network-demo 全部实验运行验证完毕！"

