package com.zhiya.network.nio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 高性能网络编程演示。
 * <p>
 * 对应层级：Level 3（NIO / 多路复用之前的字节边界事实）。
 * 演示主题：TCP 半包、粘包与长度字段 decoder。
 * 验证目标：一次读取到的字节数不代表业务消息数；应用必须累计字节并只产出完整帧。
 *
 * <p>帧格式为 {@code [4 字节长度][UTF-8 payload]}。实验先将 hello 拆成两段，
 * 再将 world 与 reactor 合并为一次发送，稳定复现半包和一次读取多帧。</p>
 */
public final class HalfPacketAndStickyPacketDemo {

    private static final int MAX_FRAME_BYTES = 1_024;

    private HalfPacketAndStickyPacketDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        System.out.println("\n===== Level 3 演示：半包、粘包与增量解码 =====");
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
             Socket client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
             Socket peer = server.accept()) {
            OutputStream output = client.getOutputStream();
            InputStream input = peer.getInputStream();
            ByteArrayOutputStream cumulation = new ByteArrayOutputStream();

            byte[] hello = frame("hello");
            System.out.println("第一步：将 hello 帧拆成两段发送。");
            output.write(hello, 0, 3);
            output.flush();
            appendExactly(input, cumulation, 3);
            require(decode(cumulation).isEmpty(), "半个长度字段不能解码成完整帧");
            System.out.println("第一段后完整帧=0，累计字节=" + cumulation.size());

            output.write(hello, 3, hello.length - 3);
            output.flush();
            appendExactly(input, cumulation, hello.length - 3);
            require(decode(cumulation).equals(List.of("hello")), "补齐字节后应只得到 hello");
            System.out.println("第二段后解码=[hello]，累计字节=" + cumulation.size());

            byte[] joined = join(frame("world"), frame("reactor"));
            System.out.println("第二步：将 world 与 reactor 两帧合并为一次发送。");
            output.write(joined);
            output.flush();
            appendExactly(input, cumulation, joined.length);
            List<String> decoded = decode(cumulation);
            System.out.println("合并发送后解码=" + decoded + "，累计字节=" + cumulation.size());
            require(decoded.equals(List.of("world", "reactor")), "一次读到多个帧时 decoder 应逐帧产出");
            System.out.println("结论：TCP 只交付有序字节流；半包与粘包由应用 framing 负责消解。");
        }
    }

    private static byte[] frame(String payload) {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(Integer.BYTES + bytes.length).putInt(bytes.length).put(bytes).array();
    }

    private static void appendExactly(InputStream input, ByteArrayOutputStream cumulation, int expectedBytes) throws IOException {
        byte[] bytes = input.readNBytes(expectedBytes);
        require(bytes.length == expectedBytes, "对端在受控发送完成前关闭");
        cumulation.write(bytes);
    }

    private static List<String> decode(ByteArrayOutputStream cumulation) {
        byte[] allBytes = cumulation.toByteArray();
        ByteBuffer buffer = ByteBuffer.wrap(allBytes);
        List<String> frames = new ArrayList<>();
        int consumedBytes = 0;
        while (buffer.remaining() >= Integer.BYTES) {
            buffer.mark();
            int length = buffer.getInt();
            require(length >= 0 && length <= MAX_FRAME_BYTES, "非法帧长度：" + length);
            if (buffer.remaining() < length) {
                buffer.reset();
                break;
            }
            byte[] payload = new byte[length];
            buffer.get(payload);
            frames.add(new String(payload, StandardCharsets.UTF_8));
            consumedBytes = buffer.position();
        }
        if (consumedBytes > 0) {
            byte[] remaining = Arrays.copyOfRange(allBytes, consumedBytes, allBytes.length);
            cumulation.reset();
            try {
                cumulation.write(remaining);
            } catch (IOException exception) {
                throw new AssertionError("内存缓冲写入失败", exception);
            }
        }
        return frames;
    }

    private static byte[] join(byte[] first, byte[] second) {
        byte[] joined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
