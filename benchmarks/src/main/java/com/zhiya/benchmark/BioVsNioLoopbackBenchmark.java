package com.zhiya.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 🌐 BIO vs NIO 本机 Loopback 请求-响应基准测试套件。
 *
 * 知识库对应：
 * - 高性能网络编程原理：BIO、NIO、多路复用与 readiness；
 * - BIO：工作线程在阻塞 read/write 中承担等待；
 * - NIO：Selector 集中领取 OP_ACCEPT、OP_READ 和按需 OP_WRITE。
 *
 * 【测试边界】
 * - 使用 127.0.0.1 的固定长连接与长度字段 Echo 协议；
 * - 每个 benchmark 操作是一轮“完整请求写出 + 完整响应读回”；
 * - 不在 benchmark 方法中创建连接、启动线程或创建 Selector；
 * - 仅比较低并发、单连接、本机 loopback 下的局部 request-response 成本。
 *
 * 【不能由本基准断言】
 * - 不能据此宣称 NIO 在所有场景都比 BIO 快；
 * - 不代表公网延迟、海量空闲连接容量、慢客户端背压或下游 DB/RPC 性能。
 *
 * 运行方式：
 * cd benchmarks
 * mvn clean package -DskipTests
 * java -jar target/benchmarks.jar "BioVsNioLoopbackBenchmark.*" -p payloadBytes=16,256,4096 -t 1 -wi 3 -i 5 -f 1
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BioVsNioLoopbackBenchmark {

    private static final int LENGTH_FIELD_BYTES = Integer.BYTES;
    private static final long START_TIMEOUT_SECONDS = 3L;

    @Param({"16", "256", "4096"})
    private int payloadBytes;

    private byte[] requestFrame;
    private byte[] responseFrame;

    private BioEchoServer bioServer;
    private Socket bioClient;
    private DataInputStream bioInput;
    private DataOutputStream bioOutput;

    private NioEchoServer nioServer;
    private Socket nioClient;
    private DataInputStream nioInput;
    private DataOutputStream nioOutput;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        requestFrame = createFrame(payloadBytes);
        responseFrame = new byte[requestFrame.length];
        setUpBioRoundTrip();
        setUpNioRoundTrip();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        closeQuietly(bioClient);
        closeQuietly(nioClient);
        if (bioServer != null) {
            bioServer.close();
        }
        if (nioServer != null) {
            nioServer.close();
        }
    }

    // =========================================================================
    // 模块一：BIO 固定连接 Echo —— 阻塞 read/write 由连接 worker 陪等
    // =========================================================================

    /**
     * BIO 往返：客户端阻塞写出完整 frame，随后阻塞读取完整 Echo response。
     * 服务端每条连接由一个 worker 顺序执行 readFully / write / flush。
     */
    @Benchmark
    @Threads(1)
    public int bio_BlockingRoundTrip(Blackhole blackhole) throws IOException {
        bioOutput.write(requestFrame);
        bioOutput.flush();
        bioInput.readFully(responseFrame);
        blackhole.consume(responseFrame[responseFrame.length - 1]);
        return responseFrame.length;
    }

    // =========================================================================
    // 模块二：NIO Selector 固定连接 Echo —— readiness 驱动的非阻塞 read/write
    // =========================================================================

    /**
     * NIO 往返：客户端仍使用阻塞读取保证单次 JMH 操作有明确完成边界；
     * 服务端使用非阻塞 SocketChannel + Selector 处理 OP_ACCEPT、OP_READ 和 OP_WRITE。
     */
    @Benchmark
    @Threads(1)
    public int nio_SelectorRoundTrip(Blackhole blackhole) throws IOException {
        nioOutput.write(requestFrame);
        nioOutput.flush();
        nioInput.readFully(responseFrame);
        blackhole.consume(responseFrame[responseFrame.length - 1]);
        return responseFrame.length;
    }

    private void setUpBioRoundTrip() throws Exception {
        bioServer = new BioEchoServer(requestFrame.length);
        bioServer.start();
        bioClient = new Socket(InetAddress.getLoopbackAddress(), bioServer.port());
        bioInput = new DataInputStream(bioClient.getInputStream());
        bioOutput = new DataOutputStream(bioClient.getOutputStream());
    }

    private void setUpNioRoundTrip() throws Exception {
        nioServer = new NioEchoServer(requestFrame.length);
        nioServer.start();
        nioClient = new Socket(InetAddress.getLoopbackAddress(), nioServer.port());
        nioInput = new DataInputStream(nioClient.getInputStream());
        nioOutput = new DataOutputStream(nioClient.getOutputStream());
    }

    private static byte[] createFrame(int payloadBytes) {
        ByteBuffer frame = ByteBuffer.allocate(LENGTH_FIELD_BYTES + payloadBytes);
        frame.putInt(payloadBytes);
        for (int index = 0; index < payloadBytes; index++) {
            frame.put((byte) (index & 0x7F));
        }
        return frame.array();
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) return;
        try { socket.close(); } catch (IOException ignored) { }
    }

    private static final class BioEchoServer implements AutoCloseable {
        private final int frameBytes;
        private final ServerSocket server;
        private final CountDownLatch started = new CountDownLatch(1);
        private volatile boolean running = true;
        private Thread thread;

        private BioEchoServer(int frameBytes) throws IOException {
            this.frameBytes = frameBytes;
            this.server = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
        }
        private void start() throws InterruptedException {
            thread = new Thread(this::run, "jmh-bio-echo-server");
            thread.setDaemon(true);
            thread.start();
            if (!started.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) throw new IllegalStateException("BIO server 启动超时");
        }
        private int port() { return server.getLocalPort(); }
        private void run() {
            started.countDown();
            try (Socket peer = server.accept(); DataInputStream input = new DataInputStream(peer.getInputStream()); DataOutputStream output = new DataOutputStream(peer.getOutputStream())) {
                byte[] frame = new byte[frameBytes];
                while (running) { input.readFully(frame); output.write(frame); output.flush(); }
            } catch (IOException ignored) { }
        }
        @Override public void close() throws IOException { running = false; server.close(); if (thread != null) thread.interrupt(); }
    }

    private static final class NioEchoServer implements AutoCloseable {
        private final int frameBytes;
        private final ServerSocketChannel server;
        private final Selector selector;
        private final CountDownLatch started = new CountDownLatch(1);
        private volatile boolean running = true;
        private Thread thread;

        private NioEchoServer(int frameBytes) throws IOException {
            this.frameBytes = frameBytes;
            this.selector = Selector.open();
            this.server = ServerSocketChannel.open();
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            server.configureBlocking(false);
            server.register(selector, SelectionKey.OP_ACCEPT);
        }
        private void start() throws InterruptedException {
            thread = new Thread(this::run, "jmh-nio-selector-server");
            thread.setDaemon(true);
            thread.start();
            if (!started.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) throw new IllegalStateException("NIO server 启动超时");
        }
        private int port() throws IOException { return ((InetSocketAddress) server.getLocalAddress()).getPort(); }
        private void run() {
            started.countDown();
            try {
                while (running) {
                    selector.select();
                    for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
                        SelectionKey key = it.next(); it.remove();
                        if (key.isAcceptable()) accept();
                        if (key.isReadable()) read(key);
                        if (key.isWritable()) write(key);
                    }
                }
            } catch (IOException ignored) { }
        }
        private void accept() throws IOException {
            SocketChannel peer = server.accept();
            if (peer == null) return;
            peer.configureBlocking(false);
            peer.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(frameBytes));
        }
        private void read(SelectionKey key) throws IOException {
            SocketChannel peer = (SocketChannel) key.channel();
            ByteBuffer request = (ByteBuffer) key.attachment();
            if (peer.read(request) == -1) { key.cancel(); peer.close(); return; }
            if (!request.hasRemaining()) { request.flip(); key.attach(request); key.interestOps(SelectionKey.OP_WRITE); }
        }
        private void write(SelectionKey key) throws IOException {
            SocketChannel peer = (SocketChannel) key.channel();
            ByteBuffer response = (ByteBuffer) key.attachment();
            peer.write(response);
            if (!response.hasRemaining()) { key.attach(ByteBuffer.allocate(frameBytes)); key.interestOps(SelectionKey.OP_READ); }
        }
        @Override public void close() throws IOException { running = false; selector.wakeup(); server.close(); selector.close(); if (thread != null) thread.interrupt(); }
    }
}
