package com.zhiya.network.nio;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * 高性能网络编程演示。
 *
 * 对应层级：Level 3。
 * 演示主题：Selector 就绪通知的语义边界。
 * 验证目标：READ-ready 只说明当前值得尝试 read，不代表一个完整应用请求已经到达。
 */
public final class SelectorReadinessDemo {

    private SelectorReadinessDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        System.out.println("\n===== Level 3 演示：readiness 不等于完整请求 =====");
        try (Selector selector = Selector.open();
             ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            server.configureBlocking(false);
            server.register(selector, SelectionKey.OP_ACCEPT);

            int port = ((InetSocketAddress) server.getLocalAddress()).getPort();
            try (SocketChannel client = SocketChannel.open(new InetSocketAddress(InetAddress.getLoopbackAddress(), port))) {
                client.write(StandardCharsets.UTF_8.encode("HEL"));
                System.out.println("客户端只发送 3 个字节：HEL；它不是完整长度字段帧。");

                int acceptReadyCount = selector.select(1_000);
                SocketChannel peer = acceptOne(selector, server);
                require(acceptReadyCount == 1 && peer != null, "服务端没有收到预期的 accept 就绪事件");

                int readReadyCount = selector.select(1_000);
                ByteBuffer buffer = ByteBuffer.allocate(16);
                int readBytes = readOne(selector, buffer);
                buffer.flip();
                String payload = StandardCharsets.UTF_8.decode(buffer).toString();

                System.out.printf("readiness 数量=%d，实际读取字节=%d，内容=%s%n", readReadyCount, readBytes, payload);
                require(readReadyCount == 1, "预期恰好一个 READ-ready 事件");
                require(readBytes == 3 && "HEL".equals(payload), "读取结果不符合受控输入");
                System.out.println("结论：Selector 报告的是 I/O 尝试资格；帧是否完整必须由应用 decoder 判断。");
                peer.close();
            }
        }
    }

    private static SocketChannel acceptOne(Selector selector, ServerSocketChannel server) throws Exception {
        SocketChannel peer = null;
        for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
            SelectionKey key = it.next();
            it.remove();
            if (key.isAcceptable()) {
                peer = server.accept();
                peer.configureBlocking(false);
                peer.register(selector, SelectionKey.OP_READ);
            }
        }
        return peer;
    }

    private static int readOne(Selector selector, ByteBuffer buffer) throws Exception {
        int readBytes = 0;
        for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
            SelectionKey key = it.next();
            it.remove();
            if (key.isReadable()) {
                readBytes = ((SocketChannel) key.channel()).read(buffer);
            }
        }
        return readBytes;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
