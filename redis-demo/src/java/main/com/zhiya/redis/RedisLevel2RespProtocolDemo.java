package com.zhiya.redis;

import com.zhiya.redis.RedisSupport;


import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Level 2 补充：RESP 协议——命令在网线上的样子。
 * <p>
 * 对应层级：Level 2。
 * 演示主题：RESP3 帧编码与解码。
 * 验证目标：把 C = SET sku:1001:stock 42 编码成字节流再按 \\r\\n 切帧解析回显；
 *           理解半包/粘包由服务端缓冲吸收，以及“超时 ≠ 失败，重试写命令必须幂等”。
 */
public final class RedisLevel2RespProtocolDemo {

    private RedisLevel2RespProtocolDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("Level 2 补充 · RESP 协议：命令在网线上的样子",
                "“客户端库把 C 序列化为 RESP3 字节流” —— 到底是什么字节？");

        String[] cmd = {"SET", "sku:1001:stock", "42"};
        byte[] frame = encodeArray(cmd);
        System.out.println("  C = SET sku:1001:stock 42");
        System.out.println("  RESP3 数组帧（3 个元素，第 1 个是简单串 +SET，后两个是批量串）:");
        System.out.println("  " + new String(frame, StandardCharsets.ISO_8859_1)
                .replace("\r", "\\r").replace("\n", "\\n"));
        System.out.println("  HEX: " + hex(frame));
        System.out.println();

        List<String[]> decoded = decodeArray(frame);
        System.out.println("  服务端 readQueryFromClient 按 \\r\\n 切帧，解析结果：");
        for (String[] arr : decoded) {
            System.out.println("    " + java.util.Arrays.toString(arr));
        }

        RedisSupport.sec("帧格式速记");
        RedisSupport.table(
                new String[]{"标记", "含义", "例子"},
                List.of(new String[][]{
                        {"+", "简单串", "+OK\\r\\n"},
                        {"-", "错误", "-ERR unknown command\\r\\n"},
                        {"$", "批量串(带长度)", "$3\\r\\nfoo\\r\\n"},
                        {"*", "数组(带长度)", "*3\\r\\n…"},
                        {"%", "Map(RESP3)", "%2\\r\\n…"},
                        {"_", "Null(RESP3)", "_\r\n"},
                }));
        System.out.println();
        System.out.println("  半包/粘包：一次 read() 可能只读到半个帧，或读到多个帧——");
        System.out.println("  服务端把字节存进查询缓冲，按帧边界切，切不出来就等下一次 read()。");
        System.out.println();
        System.out.println("  超时 ≠ 失败：应答可能在网络上丢失，但命令已执行。重试写命令必须幂等，");
        System.out.println("  否则“重发一次 SET”没事，“重发一次 INCR”就是双加（自测 #2 / T0 列）。");
    }

    /** RESP 数组编码：*N\r\n 每元素 $len\r\nbytes\r\n；首元素用简单串 +CMD */
    static byte[] encodeArray(String[] cmd) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, ("*" + cmd.length + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < cmd.length; i++) {
            byte[] b = cmd[i].getBytes(StandardCharsets.UTF_8);
            if (i == 0) {
                write(out, ("+" + cmd[0] + "\r\n").getBytes(StandardCharsets.UTF_8));
            } else {
                write(out, ("$" + b.length + "\r\n").getBytes(StandardCharsets.UTF_8));
                write(out, b);
                write(out, "\r\n".getBytes(StandardCharsets.UTF_8));
            }
        }
        return out.toByteArray();
    }

    static List<String[]> decodeArray(byte[] frame) {
        String s = new String(frame, StandardCharsets.UTF_8);
        List<String[]> result = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char t = s.charAt(i);
            if (t == '*') {                      // 数组：跳过
                i = skipLine(s, i);
            } else if (t == '+') {               // 简单串
                int e = s.indexOf("\r\n", i);
                result.add(new String[]{s.substring(i + 1, e)});
                i = e + 2;
            } else if (t == '$') {               // 批量串
                int e = s.indexOf("\r\n", i);
                int len = Integer.parseInt(s.substring(i + 1, e));
                i = e + 2;
                result.add(new String[]{s.substring(i, i + len)});
                i += len + 2;
            } else {
                throw new IllegalStateException("bad RESP: " + t);
            }
        }
        return result;
    }

    private static int skipLine(String s, int i) {
        int e = s.indexOf("\r\n", i);
        return e + 2;
    }

    private static void write(ByteArrayOutputStream out, byte[] b) {
        out.write(b, 0, b.length);
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X ", x));
        return sb.toString().trim();
    }
}
