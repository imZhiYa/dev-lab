package com.zhiya.redis;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * 迷你数据结构合集：Redis 三种紧凑/索引结构的教学复刻，合并自原三个独立文件。
 *
 *   Listpack —— 一块连续字节、无指针、一次分配（小 hash/zset/set/quicklist 节点）。
 *   Intset   —— 有序整数数组 + 二分查找（全整数小集合）。
 *   Skiplist —— 概率跳表，层高随机（zset 大集合的后端）。
 *
 * 使用边界：教学复刻，目标是让“连续内存 vs 指针结构”“O(log N) 范围查询”看得见摸得着，
 *           不是生产级等价物；真实 Redis 的编码细节（backlen、int16/32/64 三档等）属于私有实现。
 */
final class MiniStructures {
    private MiniStructures() {
    }

    // ====================================================================
    // Listpack
    // ====================================================================

    /**
     * 迷你 Listpack：Redis 7.x 里小 hash / 小 zset / 小 set / quicklist 节点的紧凑编码。
     * 用 byte[] 复刻“连续、无指针”的本质：每个元素 = [1B 长度][数据字节]。
     */
    static final class Listpack {
        private byte[] data = new byte[64];
        private int size = 0;
        private int count = 0;

        void append(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            ensure(size + 1 + b.length);
            data[size++] = (byte) b.length;
            System.arraycopy(b, 0, data, size, b.length);
            size += b.length;
            count++;
        }

        private void ensure(int need) {
            if (need <= data.length) return;
            byte[] nd = new byte[Math.max(need, data.length * 2)];
            System.arraycopy(data, 0, nd, 0, size);
            data = nd;
        }

        String get(int idx) {
            int p = 0;
            for (int i = 0; i < idx; i++) {
                int len = data[p] & 0xFF;
                p += 1 + len;
            }
            int len = data[p] & 0xFF;
            return new String(data, p + 1, len, StandardCharsets.UTF_8);
        }

        int count() { return count; }
        int byteSize() { return size; }

        /** 把所有元素展开成一列（教学输出用） */
        List<String> dump() {
            List<String> out = new ArrayList<>();
            int p = 0;
            while (p < size) {
                int len = data[p] & 0xFF;
                out.add(new String(data, p + 1, len, StandardCharsets.UTF_8));
                p += 1 + len;
            }
            return out;
        }

        /** 描述这个分配：一块连续 buffer，一个分配单元 */
        String describe() {
            return "byte[capacity=" + data.length + ", used=" + size + "]  ← 单块连续内存";
        }
    }

    // ====================================================================
    // Intset
    // ====================================================================

    /**
     * 迷你 Intset：Redis set 的“全整数小集合”编码 —— 有序整数数组 + 二分查找。
     * 内存 = 4×N 字节（真实 Redis 按 int16/int32/int64 三档伸缩，教学用 int32 档）。
     */
    static final class Intset {
        private int[] a = new int[4];
        private int n = 0;

        /** 插入（保持有序、去重），返回是否新增 */
        boolean add(int v) {
            int i = Arrays.binarySearch(a, 0, n, v);
            if (i >= 0) return false;                 // 已存在
            int ins = -(i + 1);
            if (n == a.length) a = Arrays.copyOf(a, a.length * 2);
            System.arraycopy(a, ins, a, ins + 1, n - ins);
            a[ins] = v;
            n++;
            return true;
        }

        boolean contains(int v) {
            return Arrays.binarySearch(a, 0, n, v) >= 0;
        }

        int size() { return n; }
        int byteSize() { return 4 * n; }
    }

    // ====================================================================
    // Skiplist
    // ====================================================================

    /**
     * 迷你跳表：Redis ZSet 大集合的后端（skiplist + dict 的 skiplist 部分）。
     * 每个节点持有 member/score，以及若干层 forward 指针；层高随机（1/2 概率升层）。
     * O(log N) 查找/插入/范围查询。
     */
    static final class Skiplist {
        static final int MAX_LEVEL = 16;
        private static final Random R = new Random(42);

        private static class Node {
            final String member;
            double score;
            final Node[] next = new Node[MAX_LEVEL];
            Node(String m, double s) { member = m; score = s; }
        }

        private final Node head = new Node(null, Double.NEGATIVE_INFINITY);
        private int level = 1;
        private int size = 0;
        int searchSteps = 0;     // 教学：最近一次搜索的步数

        void add(String member, double score) {
            Node[] update = new Node[MAX_LEVEL];
            Node x = head;
            for (int i = level - 1; i >= 0; i--) {
                while (x.next[i] != null && (x.next[i].score < score
                        || (x.next[i].score == score && x.next[i].member.compareTo(member) < 0))) {
                    x = x.next[i];
                }
                update[i] = x;
            }
            x = x.next[0];
            if (x != null && x.member.equals(member)) {   // 已存在：改分
                x.score = score;
                return;
            }
            int lvl = randomLevel();
            if (lvl > level) {
                for (int i = level; i < lvl; i++) update[i] = head;
                level = lvl;
            }
            Node nn = new Node(member, score);
            for (int i = 0; i < lvl; i++) {
                nn.next[i] = update[i].next[i];
                update[i].next[i] = nn;
            }
            size++;
        }

        private int randomLevel() {
            int l = 1;
            while (l < MAX_LEVEL && R.nextInt(2) == 0) l++;
            return l;
        }

        /** 按分数范围取成员（升序），顺带统计步数 */
        List<String> range(double lo, double hi) {
            List<String> out = new ArrayList<>();
            Node x = head;
            searchSteps = 0;
            for (int i = level - 1; i >= 0; i--) {
                while (x.next[i] != null && x.next[i].score < lo) { x = x.next[i]; searchSteps++; }
            }
            x = x.next[0];
            searchSteps++;
            while (x != null && x.score <= hi) {
                out.add(x.member + ":" + x.score);
                x = x.next[0];
                searchSteps++;
            }
            return out;
        }

        int size() { return size; }
        int level() { return level; }
    }
}
