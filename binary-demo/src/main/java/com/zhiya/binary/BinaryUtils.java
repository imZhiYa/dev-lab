package com.zhiya.binary;

/**
 * 纯手工二进制位移算法解析引擎 (零依赖)
 * 供 dev-lab 独立本地与 CI 运行验证
 */
public class BinaryUtils {

    /**
     * 🟢 纯手工实现 32 位整型 -> 纯正完整补码字符串 (拒绝调用现成 API)
     */
    public static String intToBinary(int num) {
        char[] bits = new char[32];
        for (int i = 31; i >= 0; i--) {
            int bit = (num >>> i) & 1;
            bits[31 - i] = (char) ('0' + bit);
        }
        return new String(bits);
    }

    /**
     * 🔵 格式化肉眼直观对齐版 (每 8 位分块)
     */
    public static String toPrettyBinary(int num) {
        String full = intToBinary(num);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < full.length(); i++) {
            if (i > 0 && i % 8 == 0) sb.append(' ');
            sb.append(full.charAt(i));
        }
        return sb.toString();
    }

    /**
     * 🟣 二进制字符串 -> int (完美支持补码负数越界解读)
     */
    public static int parseBinaryToInt(String binStr) {
        String cleaned = binStr.replaceAll("\\s+", "");
        int result = 0;
        int len = Math.min(cleaned.length(), 32);
        int start = cleaned.length() - len;
        for (int i = start; i < cleaned.length(); i++) {
            result <<= 1;
            if (cleaned.charAt(i) == '1') {
                result |= 1;
            }
        }
        return result;
    }

    public static void main(String[] args) {
        System.out.println("42 的内存实态:   " + toPrettyBinary(42));       // 00000000 00000000 00000000 00101010
        System.out.println("-1 的内存实态:   " + toPrettyBinary(-1));       // 11111111 11111111 11111111 11111111
        System.out.println(">>> 1 斩击结果:  " + toPrettyBinary(-1 >>> 1)); // 01111111 11111111 11111111 11111111
    }
}
