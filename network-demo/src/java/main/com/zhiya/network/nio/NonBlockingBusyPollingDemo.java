package com.zhiya.network.nio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * 高性能网络编程演示。
 * <p>
 * 对应层级：Level 3（NIO）。
 * 演示主题：非阻塞全连接扫描造成的忙轮询。
 * 验证目标：将 channel 配置为 non-blocking 只会让 read() 在无数据时立即返回 0；
 * 若应用持续扫描全部连接，等待从线程阻塞转移为 CPU 空转。
 *
 * <p>受控场景中，客户端建立连接后不发送任何字节。服务端循环扫描所有客户端对应的
 * SocketChannel，因此每次 read() 均返回 0。真实程序不应这样寻找活跃连接，而应使用
 * Selector 将“哪些连接值得尝试 I/O”的等待交给内核。</p>
 */
public final class NonBlockingBusyPollingDemo {

    private static final int CONNECTION_COUNT = 4;
    private static final int SCAN_ROUNDS = 2_000;

    private NonBlockingBusyPollingDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        System.out.println("\n===== Level 3 演示：非阻塞全连接扫描的忙轮询 =====");
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            int port = ((InetSocketAddress) server.getLocalAddress()).getPort();

            List<SocketChannel> clients = openIdleClients(port);
            List<SocketChannel> peers = acceptAll(server);
            try {
                for (SocketChannel peer : peers) {
                    peer.configureBlocking(false);
                }

                ScanMetrics metrics = scanWithoutSelector(peers);
                long expectedReadAttempts = (long) CONNECTION_COUNT * SCAN_ROUNDS;
                System.out.printf("连接数=%d，扫描轮数=%d，read 尝试=%d，read()==0 次数=%d，耗时=%d 微秒%n",
                        CONNECTION_COUNT, SCAN_ROUNDS, metrics.readAttempts, metrics.zeroReads, metrics.elapsedMicros);
                require(metrics.readAttempts == expectedReadAttempts, "扫描次数与连接数、轮数不匹配");
                require(metrics.zeroReads == expectedReadAttempts, "空闲连接应全部返回 read()==0");
                require(metrics.positiveReads == 0, "受控场景不应读取到业务字节");
                System.out.println("结论：非阻塞不等于没有等待；这里的等待被错误实现成 CPU 对全部连接的重复扫描。");
            } finally {
                closeAll(peers);
                closeAll(clients);
            }
        }
    }

    private static List<SocketChannel> openIdleClients(int port) throws IOException {
        List<SocketChannel> clients = new ArrayList<>();
        for (int index = 0; index < CONNECTION_COUNT; index++) {
            clients.add(SocketChannel.open(new InetSocketAddress(InetAddress.getLoopbackAddress(), port)));
        }
        return clients;
    }

    private static List<SocketChannel> acceptAll(ServerSocketChannel server) throws IOException {
        List<SocketChannel> peers = new ArrayList<>();
        while (peers.size() < CONNECTION_COUNT) {
            peers.add(server.accept());
        }
        return peers;
    }

    private static ScanMetrics scanWithoutSelector(List<SocketChannel> peers) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        ScanMetrics metrics = new ScanMetrics();
        long startedAt = System.nanoTime();
        for (int round = 0; round < SCAN_ROUNDS; round++) {
            for (SocketChannel peer : peers) {
                buffer.clear();
                int readBytes = peer.read(buffer);
                metrics.readAttempts++;
                if (readBytes == 0) {
                    metrics.zeroReads++;
                } else if (readBytes > 0) {
                    metrics.positiveReads++;
                }
            }
        }
        metrics.elapsedMicros = (System.nanoTime() - startedAt) / 1_000L;
        return metrics;
    }

    private static void closeAll(List<SocketChannel> channels) {
        for (SocketChannel channel : channels) {
            try {
                channel.close();
            } catch (IOException ignored) {
                // 演示收尾阶段只做尽力关闭；前面的断言负责验证实验结论。
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class ScanMetrics {
        private long readAttempts;
        private long zeroReads;
        private long positiveReads;
        private long elapsedMicros;
    }
}
