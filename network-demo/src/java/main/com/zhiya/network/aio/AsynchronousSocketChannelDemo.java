package com.zhiya.network.aio;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 高性能网络编程演示。
 *
 * 对应层级：AIO（completion 风格 I/O）。
 * 演示主题：completion、应用 framing 与待写状态。
 * 验证目标：read completion 只交付一段字节；半包累计、完整帧判断和 write 残留仍由应用状态管理。
 *
 * <p>客户端将一个长度字段帧拆为两次发送。服务端使用 CompletionHandler 连续读，
 * 在 ConnectionState 中累计字节；仅当 decoder 产出完整帧后才异步写出响应。</p>
 */
public final class AsynchronousSocketChannelDemo {

    private static final int MAX_FRAME_BYTES = 1_024;
    private static final long TIMEOUT_SECONDS = 2L;

    private AsynchronousSocketChannelDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        System.out.println("\n===== AIO 演示：completion 不等于完整请求 =====");
        CountDownLatch responseWritten = new CountDownLatch(1);
        ConnectionState state = new ConnectionState(responseWritten);
        try (AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
             AsynchronousSocketChannel client = AsynchronousSocketChannel.open()) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            server.accept(state, new AcceptHandler());
            int port = ((InetSocketAddress) server.getLocalAddress()).getPort();
            client.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port)).get();

            byte[] request = frame("R-1001");
            client.write(ByteBuffer.wrap(request, 0, 2)).get();
            client.write(ByteBuffer.wrap(request, 2, request.length - 2)).get();

            ByteBuffer response = ByteBuffer.allocate(64);
            int responseBytes = client.read(response).get();
            response.flip();
            String payload = decodeOne(response);
            require(responseWritten.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "服务端没有完成响应写入");
            require("ACK:R-1001".equals(payload), "客户端响应内容不正确：" + payload);
            require(state.readCompletionCount >= 2, "拆包发送至少应产生两次 read completion");
            require(state.decodedFrameCount == 1, "服务端应只解码出一个完整请求");
            System.out.printf("read completion=%d，decoded frames=%d，write completion=%d，client response bytes=%d%n",
                    state.readCompletionCount, state.decodedFrameCount, state.writeCompletionCount, responseBytes);
            System.out.println("结论：AIO 改变的是通知方式；半包、frame decoder、待写 ByteBuffer 和请求状态仍由应用拥有。");
        }
    }

    private static final class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, ConnectionState> {
        @Override
        public void completed(AsynchronousSocketChannel channel, ConnectionState state) {
            state.channel = channel;
            beginRead(state);
        }

        @Override
        public void failed(Throwable exception, ConnectionState state) {
            state.fail(exception);
        }
    }

    private static void beginRead(ConnectionState state) {
        state.readBuffer.clear();
        state.channel.read(state.readBuffer, state, new CompletionHandler<>() {
            @Override
            public void completed(Integer readBytes, ConnectionState connection) {
                if (readBytes == -1) {
                    connection.fail(new IllegalStateException("客户端在完整请求前关闭"));
                    return;
                }
                connection.readCompletionCount++;
                connection.readBuffer.flip();
                byte[] bytes = new byte[connection.readBuffer.remaining()];
                connection.readBuffer.get(bytes);
                connection.cumulation.writeBytes(bytes);
                String requestId = decodeOne(connection.cumulation);
                System.out.printf("read completion：bytes=%d，累计字节=%d，完整帧=%d%n", readBytes, connection.cumulation.size(), connection.decodedFrameCount);
                if (requestId == null) {
                    beginRead(connection);
                    return;
                }
                connection.decodedFrameCount++;
                connection.pendingWrite = ByteBuffer.wrap(frame("ACK:" + requestId));
                beginWrite(connection);
            }

            @Override
            public void failed(Throwable exception, ConnectionState connection) {
                connection.fail(exception);
            }
        });
    }

    private static void beginWrite(ConnectionState state) {
        state.channel.write(state.pendingWrite, state, new CompletionHandler<>() {
            @Override
            public void completed(Integer writtenBytes, ConnectionState connection) {
                connection.writeCompletionCount++;
                System.out.printf("write completion：bytes=%d，剩余=%d%n", writtenBytes, connection.pendingWrite.remaining());
                if (connection.pendingWrite.hasRemaining()) {
                    beginWrite(connection);
                    return;
                }
                connection.responseWritten.countDown();
            }

            @Override
            public void failed(Throwable exception, ConnectionState connection) {
                connection.fail(exception);
            }
        });
    }

    private static byte[] frame(String payload) {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(Integer.BYTES + bytes.length).putInt(bytes.length).put(bytes).array();
    }

    private static String decodeOne(ByteArrayOutputStream cumulation) {
        byte[] bytes = cumulation.toByteArray();
        if (bytes.length < Integer.BYTES) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int length = buffer.getInt();
        require(length >= 0 && length <= MAX_FRAME_BYTES, "非法帧长度：" + length);
        if (buffer.remaining() < length) {
            return null;
        }
        byte[] payload = new byte[length];
        buffer.get(payload);
        byte[] remaining = Arrays.copyOfRange(bytes, buffer.position(), bytes.length);
        cumulation.reset();
        cumulation.writeBytes(remaining);
        return new String(payload, StandardCharsets.UTF_8);
    }

    private static String decodeOne(ByteBuffer buffer) {
        require(buffer.remaining() >= Integer.BYTES, "响应缺少长度字段");
        int length = buffer.getInt();
        require(buffer.remaining() == length, "响应长度不完整");
        byte[] payload = new byte[length];
        buffer.get(payload);
        return new String(payload, StandardCharsets.UTF_8);
    }

    private static final class ConnectionState {
        private final ByteBuffer readBuffer = ByteBuffer.allocate(8);
        private final ByteArrayOutputStream cumulation = new ByteArrayOutputStream();
        private final CountDownLatch responseWritten;
        private AsynchronousSocketChannel channel;
        private ByteBuffer pendingWrite;
        private int readCompletionCount;
        private int decodedFrameCount;
        private int writeCompletionCount;

        private ConnectionState(CountDownLatch responseWritten) {
            this.responseWritten = responseWritten;
        }

        private void fail(Throwable exception) {
            responseWritten.countDown();
            throw new AssertionError("异步 I/O 失败", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
