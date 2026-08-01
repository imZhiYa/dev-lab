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
java -Dfile.encoding=UTF-8 -cp innodb-demo/target/classes com.imzhiya.devlab.innodb.BufferPoolLRU

echo "🧪 运行 BPlusTreeRoutingDemo..."
java -Dfile.encoding=UTF-8 -cp innodb-demo/target/classes com.imzhiya.devlab.innodb.BPlusTreeRoutingDemo

echo "🧪 运行 PageDirectoryDemo..."
java -Dfile.encoding=UTF-8 -cp innodb-demo/target/classes com.imzhiya.devlab.innodb.PageDirectoryDemo

echo "🧪 运行 RedoLogRingBufferDemo..."
java -Dfile.encoding=UTF-8 -cp innodb-demo/target/classes com.imzhiya.devlab.innodb.RedoLogRingBufferDemo

echo "🧪 运行 MVCCDemo..."
java -Dfile.encoding=UTF-8 -cp innodb-demo/target/classes com.imzhiya.devlab.innodb.MVCCDemo

echo "🧪 运行 NextKeyLockDemo..."
java -Dfile.encoding=UTF-8 -cp innodb-demo/target/classes com.imzhiya.devlab.innodb.NextKeyLockDemo

echo "🧪 运行 DoublewriteBufferDemo..."
java -Dfile.encoding=UTF-8 -cp innodb-demo/target/classes com.imzhiya.devlab.innodb.DoublewriteBufferDemo

echo "✅ innodb-demo 全部底层模型运行验证完毕！"

