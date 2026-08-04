package com.zhiya.network.bio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 高性能网络编程演示。
 * <p>
 * 对应层级：Level 2（BIO）。
 * 演示主题：一连接一线程模型中的“线程陪等”。
 * 验证目标：多个空闲连接没有发送字节时，每条连接仍占用一个阻塞在 read() 的工作线程。
 *
 * <p>本实验使用受控的本地客户端：先建立连接但不发送请求，确认 worker 已经进入 read()，
 * 再统一发送一行请求，让所有 worker 返回。这样不依赖任意 sleep 来推断线程状态。</p>
 */
public final class BioThreadPerConnectionDemo {

    private static final int CLIENT_COUNT = 3;
    private static final long AWAIT_TIMEOUT_SECONDS = 2L;

    private BioThreadPerConnectionDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        System.out.println("\n===== Level 2 演示：BIO 的线程陪等 =====");
        AtomicInteger acceptedCount = new AtomicInteger();
        AtomicInteger completedCount = new AtomicInteger();
        CountDownLatch accepted = new CountDownLatch(CLIENT_COUNT);
        CountDownLatch workersReadyToRead = new CountDownLatch(CLIENT_COUNT);
        List<Thread> workers = new ArrayList<>();

        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Thread acceptor = startAcceptor(server, acceptedCount, completedCount, accepted, workersReadyToRead, workers);
            List<Socket> clients = openIdleClients(server.getLocalPort());

            require(accepted.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS), "服务端未在规定时间内 accept 全部连接");
            require(workersReadyToRead.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS), "worker 未全部进入 read 等待点");
            System.out.printf("连接数=%d，阻塞 worker 数=%d，已完成请求=%d%n", acceptedCount.get(), workers.size(), completedCount.get());
            require(workers.size() == CLIENT_COUNT && completedCount.get() == 0, "空闲连接没有对应到阻塞 worker");
            System.out.println("结论：BIO 并非不能使用；问题是沉默连接会让昂贵线程一对一陪等。");

            for (int index = 0; index < clients.size(); index++) {
                clients.get(index).getOutputStream().write(("request-" + index + "\n").getBytes(StandardCharsets.UTF_8));
            }
            for (Socket client : clients) {
                client.close();
            }
            for (Thread worker : workers) {
                worker.join(1_000L);
            }
            acceptor.join(1_000L);
            System.out.printf("发送请求后完成数=%d%n", completedCount.get());
            require(completedCount.get() == CLIENT_COUNT, "worker 未全部完成读取");
        }
    }

    private static Thread startAcceptor(ServerSocket server, AtomicInteger acceptedCount, AtomicInteger completedCount,
                                        CountDownLatch accepted, CountDownLatch workersReadyToRead, List<Thread> workers) {
        Thread acceptor = new Thread(() -> {
            try {
                while (acceptedCount.get() < CLIENT_COUNT) {
                    Socket socket = server.accept();
                    int workerId = acceptedCount.incrementAndGet();
                    accepted.countDown();
                    Thread worker = new Thread(() -> runWorker(socket, workerId, completedCount, workersReadyToRead), "bio-worker-" + workerId);
                    workers.add(worker);
                    worker.start();
                }
            } catch (IOException exception) {
                throw new RuntimeException("accept 失败", exception);
            }
        }, "bio-acceptor");
        acceptor.start();
        return acceptor;
    }

    private static void runWorker(Socket socket, int workerId, AtomicInteger completedCount, CountDownLatch workersReadyToRead) {
        try (socket; BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            System.out.println("worker-" + workerId + " 已进入阻塞 read()。");
            workersReadyToRead.countDown();
            String request = reader.readLine();
            if (request != null) {
                completedCount.incrementAndGet();
                System.out.println("worker-" + workerId + " 收到：" + request);
            }
        } catch (IOException exception) {
            throw new RuntimeException("worker 读取失败", exception);
        }
    }

    private static List<Socket> openIdleClients(int port) throws IOException {
        List<Socket> clients = new ArrayList<>();
        for (int index = 0; index < CLIENT_COUNT; index++) {
            clients.add(new Socket(InetAddress.getLoopbackAddress(), port));
        }
        return clients;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
