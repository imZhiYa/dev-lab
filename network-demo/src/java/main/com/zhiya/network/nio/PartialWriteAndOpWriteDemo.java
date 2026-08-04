package com.zhiya.network.nio;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 高性能网络编程演示。
 *
 * 对应层级：Level 3。
 * 演示主题：非阻塞部分写与 OP_WRITE 关注条件。
 * 验证目标：未写完的 ByteBuffer 必须保留；只有存在残留数据时才订阅 OP_WRITE。
 */
public final class PartialWriteAndOpWriteDemo {

    private PartialWriteAndOpWriteDemo() {
    }

    public static void main(String[] args) {
        run();
    }

    public static void run() {
        System.out.println("\n===== Level 3 演示：部分写与按需 OP_WRITE =====");
        ByteBuffer response = ByteBuffer.wrap("response-payload".getBytes(StandardCharsets.UTF_8));
        ControlledNonBlockingChannel channel = new ControlledNonBlockingChannel(4, 0, 5, 99);
        boolean writeInterested = false;
        int zeroWrites = 0;
        int enableCount = 0;

        while (response.hasRemaining()) {
            int written = channel.write(response);
            if (written == 0) {
                zeroWrites++;
            }
            System.out.printf("本次 write=%d，剩余=%d%n", written, response.remaining());
            if (response.hasRemaining() && !writeInterested) {
                writeInterested = true;
                enableCount++;
                System.out.println("存在残留字节：订阅 OP_WRITE。" );
            }
        }
        writeInterested = false;
        System.out.println("待写队列清空：取消 OP_WRITE。" );

        require(channel.writtenBytes == 16, "响应字节没有完整写出");
        require(zeroWrites == 1, "受控场景应恰好出现一次 write()==0");
        require(enableCount == 1 && !writeInterested, "OP_WRITE 的订阅生命周期不正确");
        System.out.println("结论：一次 write 不保证写完；保存 position 后续写，清空后立即取消 OP_WRITE。");
    }

    private static final class ControlledNonBlockingChannel {
        private final int[] quotas;
        private int quotaIndex;
        private int writtenBytes;

        private ControlledNonBlockingChannel(int... quotas) {
            this.quotas = quotas;
        }

        private int write(ByteBuffer buffer) {
            int quota = quotas[Math.min(quotaIndex++, quotas.length - 1)];
            int written = Math.min(buffer.remaining(), quota);
            buffer.position(buffer.position() + written);
            writtenBytes += written;
            return written;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
