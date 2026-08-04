package com.zhiya.network.reactor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 高性能网络编程演示。
 * <p>
 * 对应层级：Level 4（Reactor）。
 * 演示主题：连接状态的唯一串行 owner。
 * 验证目标：连接状态只能由所属 Event Loop 修改；其他线程只能投递意图。
 */
public final class SingleReactorOwnershipDemo {
    private SingleReactorOwnershipDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        System.out.println("\n===== Level 4 演示：Reactor 的状态所有权 =====");
        EventLoop loop = new EventLoop();
        Thread loopThread = new Thread(loop, "event-loop-0");
        loopThread.start();
        loop.submit(() -> loop.connection.append("READING_FRAME"));
        loop.submit(() -> loop.connection.append("RESPONSE_QUEUED"));
        loop.submit(() -> loop.connection.append("LOCAL_WRITE_DONE"));
        loop.submit(() -> loop.stop());
        loopThread.join(1_000L);
        System.out.println("状态流转=" + loop.connection.history);
        require("event-loop-0".equals(loop.connection.owner.get()), "连接状态不是由 Event Loop 拥有");
        require("READING_FRAME -> RESPONSE_QUEUED -> LOCAL_WRITE_DONE".equals(loop.connection.history.toString()), "状态顺序错误");
        System.out.println("结论：Reactor 的关键不是线程数量，而是每份可变连接状态有唯一串行 owner。");
    }

    private static final class EventLoop implements Runnable {
        private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
        private final ConnectionState connection = new ConnectionState();
        private volatile boolean running = true;

        private void submit(Runnable task) {
            tasks.add(task);
        }

        private void stop() {
            running = false;
        }

        @Override
        public void run() {
            while (running) {
                try {
                    tasks.take().run();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static final class ConnectionState {
        private final AtomicReference<String> owner = new AtomicReference<>();
        private final StringBuilder history = new StringBuilder();

        private void append(String state) {
            String currentThread = Thread.currentThread().getName();
            owner.compareAndSet(null, currentThread);
            require(owner.get().equals(currentThread), "非 owner 线程修改连接状态：" + currentThread);
            if (history.length() > 0) {
                history.append(" -> ");
            }
            history.append(state);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
