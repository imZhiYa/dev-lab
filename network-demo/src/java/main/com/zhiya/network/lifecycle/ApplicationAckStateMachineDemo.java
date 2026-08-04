package com.zhiya.network.lifecycle;

/**
 * 高性能网络编程演示。
 * <p>
 * 对应层级：Level 5（生命周期）。
 * 演示主题：LOCAL_WRITE_DONE 与应用 ACK 的边界。
 * 验证目标：本地写完成后仍须等待协议 ACK，不能直接标记为业务完成。
 */
public final class ApplicationAckStateMachineDemo {
    private ApplicationAckStateMachineDemo() {
    }

    public static void main(String[] args) {
        run();
    }

    public static void run() {
        System.out.println("\n===== Level 5 演示：LOCAL_WRITE_DONE 不等于 ACKED =====");
        Request request = new Request("R-1001");
        request.move(State.DISPATCHED);
        request.move(State.RESPONSE_QUEUED);
        request.move(State.LOCAL_WRITE_DONE);
        request.move(State.WAITING_ACK);
        require(request.state == State.WAITING_ACK, "本地 write 完成不能直接成为 ACKED");
        System.out.println("本地发送路径完成后状态=" + request.state);
        request.move(State.ACKED);
        require(request.state == State.ACKED, "收到 ACK 后应进入 ACKED");
        System.out.println("结论：本地 write 只是发送路径进展；业务完成时点由协议 ACK 定义。");
    }

    private enum State {DISPATCHED, RESPONSE_QUEUED, LOCAL_WRITE_DONE, WAITING_ACK, ACKED}

    private static final class Request {
        private final String id;
        private State state;

        private Request(String id) {
            this.id = id;
        }

        private void move(State next) {
            state = next;
            System.out.println(id + " -> " + next);
        }
    }

    private static void require(boolean c, String m) {
        if (!c) throw new AssertionError(m);
    }
}
