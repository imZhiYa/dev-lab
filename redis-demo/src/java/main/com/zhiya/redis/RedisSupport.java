package com.zhiya.redis;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Redis 演示统一支持库：打印 / 断言与并发编排 / 哈希，三合一。
 * <p>
 * 由原 RedisConsole + RedisDemoSupport + RedisHashes 合并而来，职责划分：
 * §1 打印    —— 分节横幅、中文对齐表格、口诀，颜色仅在交互终端开启。
 * §2 断言并发 —— require 验证目标、统一并发放行、超时等待与失败传播。
 * §3 哈希    —— CRC16(与 Redis 同算法)、{hashtag}、槽位、HLL 64 位哈希。
 * <p>
 * 使用边界：仅供本仓库演示类复用，不承载任何 Redis 业务语义。
 */
public final class RedisSupport {
    private RedisSupport() {
    }

    // ====================================================================
    // §1 打印
    // ====================================================================

    public static final boolean COLOR = System.console() != null && !"true".equals(System.getProperty("nocolor"));

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";

    private static String c(String code, String s) {
        return COLOR ? code + s + RESET : s;
    }

    public static String bold(String s) {
        return c(BOLD, s);
    }

    public static String dim(String s) {
        return c(DIM, s);
    }

    public static String red(String s) {
        return c(RED, s);
    }

    public static String green(String s) {
        return c(GREEN, s);
    }

    public static String yellow(String s) {
        return c(YELLOW, s);
    }

    public static String cyan(String s) {
        return c(CYAN, s);
    }

    public static void print() {
        System.out.println();
    }

    public static void print(String s) {
        System.out.println(s);
    }

    public static void ok(String s) {
        System.out.println(c(GREEN, s));
    }

    public static void warn(String s) {
        System.out.println(c(YELLOW, s));
    }

    public static void err(String s) {
        System.out.println(c(RED, s));
    }

    public static void dimln(String s) {
        System.out.println(c(DIM, s));
    }

    /**
     * 演示横幅：每层一个
     */
    public static void banner(String title, String subtitle) {
        System.out.println();
        System.out.println(c(BOLD + BLUE, "===== " + title + " ====="));
        if (subtitle != null && !subtitle.isEmpty()) {
            System.out.println(c(DIM, subtitle));
        }
        System.out.println();
    }

    /**
     * 小节标题
     */
    public static void sec(String s) {
        System.out.println();
        System.out.println(c(BOLD, "===== " + s + " ====="));
    }

    /**
     * 口诀：红色大字
     */
    public static void mantra(String s) {
        System.out.println();
        System.out.println(c(BOLD + RED, "🔴 " + s));
        System.out.println();
    }

    public static void hr() {
        System.out.println(c(DIM, "-".repeat(74)));
    }

    /**
     * 显示宽度：CJK/全角字符按 2 列计（用于对齐）。
     */
    public static int dw(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (isWide(cp)) n += 2;
            else n += 1;
            i += Character.charCount(cp);
        }
        return n;
    }

    private static boolean isWide(int cp) {
        return cp >= 0x1100 && (cp <= 0x115F || cp == 0x2329 || cp == 0x232A
                || (cp >= 0x2E80 && cp <= 0xA4CF && cp != 0x303F)
                || (cp >= 0xAC00 && cp <= 0xD7A3)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0xFE30 && cp <= 0xFE4F)
                || (cp >= 0xFF00 && cp <= 0xFF60)
                || (cp >= 0xFFE0 && cp <= 0xFFE6)
                || (cp >= 0x20000 && cp <= 0x3FFFD));
    }

    public static String pad(String s, int width) {
        int extra = width - dw(s);
        if (extra <= 0) return s;
        return s + " ".repeat(extra);
    }

    /**
     * 打印对齐表格。
     */
    public static void table(String[] header, List<String[]> rows) {
        table(header, rows, null);
    }

    public static void table(String[] header, List<String[]> rows, int[] align) {
        int cols = header.length;
        int[] w = new int[cols];
        for (int j = 0; j < cols; j++) w[j] = dw(header[j]);
        for (String[] r : rows) {
            for (int j = 0; j < cols; j++) {
                if (r[j] != null) w[j] = Math.max(w[j], dw(r[j]));
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < cols; j++) {
            sb.append("+").append("-".repeat(w[j] + 2));
        }
        sb.append("+");
        String sep = sb.toString();
        System.out.println(sep);
        StringBuilder h = new StringBuilder();
        for (int j = 0; j < cols; j++) h.append("| ").append(pad(header[j], w[j])).append(' ');
        h.append('|');
        System.out.println(c(BOLD, h.toString()));
        System.out.println(sep);
        for (String[] r : rows) {
            StringBuilder line = new StringBuilder();
            for (int j = 0; j < cols; j++) {
                String cell = r[j] == null ? "" : r[j];
                String padded;
                if (align != null && align[j] > 0) {
                    int extra = w[j] - dw(cell);
                    padded = " ".repeat(Math.max(0, extra)) + cell;
                } else {
                    padded = pad(cell, w[j]);
                }
                line.append("| ").append(padded).append(' ');
            }
            line.append('|');
            System.out.println(line);
        }
        System.out.println(sep);
    }

    // ====================================================================
    // §2 断言与并发编排
    // ====================================================================

    /**
     * 演示断言：验证目标不成立时快速失败，而不是把错误结论打印出去。
     */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("验证失败: " + message);
        }
    }

    /**
     * 带描述的可容忍睡眠。
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 启动一个平台线程，并把线程内部异常保存到调用方可见的位置。
     * 不直接在线程内部抛出异常，是因为未捕获异常只会打印到 stderr，主线程无法可靠失败。
     */
    public static Thread start(String name, ThrowingRunnable action, AtomicReference<Throwable> failure) {
        Thread thread = new Thread(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }, name);
        thread.start();
        return thread;
    }

    /**
     * 等待一次确定的协调事件。超时将失败而不是无限挂起。
     */
    public static void await(CountDownLatch latch, String description) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out: " + description);
        }
    }

    /**
     * 将一组平台线程先全部创建并停在 start 闸门，再统一放行。
     * 这样测量的是同一时刻争用资源的结果，而不是线程创建先后造成的串行假象。
     */
    public static void runConcurrently(int workerCount, String threadNamePrefix, ThrowingRunnable action)
            throws Exception {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workerCount);
        for (int index = 0; index < workerCount; index++) {
            start(threadNamePrefix + "-" + index, () -> {
                ready.countDown();
                await(start, "统一放行 " + threadNamePrefix + " 线程");
                try {
                    action.run();
                } finally {
                    done.countDown();
                }
            }, new AtomicReference<>());
        }
        await(ready, threadNamePrefix + " 线程就绪");
        start.countDown();
        await(done, threadNamePrefix + " 线程完成");
    }

    /**
     * 等待可观察状态成立。此轮询只用于演示观察。
     */
    public static void awaitTrue(BooleanSupplier condition, String description) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out: " + description);
            }
            Thread.onSpinWait();
        }
    }

    /**
     * 可抛受检异常的 Runnable，用于演示线程动作。
     */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    // ====================================================================
    // §3 哈希
    // ====================================================================

    private static final int[] CRC16_TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int crc = i << 8;
            for (int j = 0; j < 8; j++) {
                crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) : (crc << 1);
            }
            CRC16_TABLE[i] = crc & 0xFFFF;
        }
    }

    /**
     * 与 Redis crc16.c 同算法的 16 位 CRC。
     */
    public static int crc16(byte[] data) {
        int crc = 0;
        for (byte b : data) {
            crc = ((crc << 8) ^ CRC16_TABLE[((crc >> 8) ^ (b & 0xFF)) & 0xFF]) & 0xFFFF;
        }
        return crc;
    }

    /**
     * 提取 {hashtag}：只对花括号内内容做哈希。
     */
    public static String hashtag(String key) {
        int open = key.indexOf('{');
        if (open >= 0) {
            int close = key.indexOf('}', open + 1);
            if (close > open + 1) {
                return key.substring(open + 1, close);
            }
        }
        return key;
    }

    /**
     * key -> 槽位（0..16383），与 Redis 完全一致。
     */
    public static int slot(String key) {
        String tag = hashtag(key);
        return crc16(tag.getBytes(StandardCharsets.UTF_8)) & 16383;
    }

    /**
     * FNV-1a 64 位，用于把任意字符串摊平成 64 位。
     */
    public static long fnv1a64(String s) {
        long h = 0xcbf29ce484222325L;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xFFL);
            h *= 0x100000001b3L;
        }
        return h;
    }

    /**
     * SplitMix64：把 64 位种子再打散成均匀 64 位。
     */
    public static long splitmix64(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    /**
     * HLL 专用：字符串 -> 均匀 64 位哈希。
     */
    public static long hllHash(String element) {
        return splitmix64(fnv1a64(element) ^ 0xD1B54A32D192ED03L);
    }
}
