package com.zhiya.es.core;

import java.util.List;

/**
 * 纯逻辑冒烟（零 ES 依赖，CI 同款，几秒完成）：
 * ① bulk NDJSON action/source 配对构造
 * ② search_after 复合排序键比较（(seq, id) 字典序）
 * ③ 深分页候选数公式 shards × (from + size) vs search_after 的 shards × size
 * 对应机制：es-01 写路径 / es-03 检索分页。
 */
public class EsSmokeApp {

    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {
        System.out.println("============ ES Lab 纯逻辑冒烟 ============");

        // ① bulk NDJSON：每个 doc 两行（action + source），action 行携带 _index/_id
        String ndjson = bulkNdjson("ex01", 3);
        String[] lines = ndjson.strip().split("\n");
        check("NDJSON 行数 = 2×doc 数", lines.length == 6, "实际 " + lines.length);
        boolean pairOk = true;
        for (int i = 0; i < lines.length; i += 2) {
            String action = lines[i];
            String source = lines[i + 1];
            pairOk &= action.contains("\"index\"") && action.contains("\"_index\":\"ex01\"")
                    && action.contains("\"_id\":\"d" + (i / 2) + "\"")
                    && source.startsWith("{\"title\"");
        }
        check("action/source 交替配对且携带 _index/_id", pairOk, ndjson);
        check("奇数行输入被校验拒绝", !validNdjson("{\"index\":{}}"), "奇数行不应通过");

        // ② search_after 复合排序键：先比 seq(long)，再比 id(string)
        check("(1,a) < (1,b)", cmp(List.of(1L, "a"), List.of(1L, "b")) < 0, "同 seq 比 id");
        check("(2,a) > (1,z)", cmp(List.of(2L, "a"), List.of(1L, "z")) > 0, "先比 seq");
        check("(5,x) == (5,x)", cmp(List.of(5L, "x"), List.of(5L, "x")) == 0, "完全相等");

        // ③ 深分页候选数：from+size 每分片拉 from+size 个候选；search_after 每页只拉 size
        check("from=9000,size=1000,shards=5 → 候选 5×10000=50000",
                candidates(5, 9000, 1000) == 50_000, String.valueOf(candidates(5, 9000, 1000)));
        check("search_after 每页候选 5×1000=5000（与翻到第几页无关）",
                candidates(5, 0, 1000) == 5_000, String.valueOf(candidates(5, 0, 1000)));
        check("from+size 越深候选越多（9000 页是第 0 页的 10 倍）",
                candidates(5, 9000, 1000) == 10 * candidates(5, 0, 1000), "放大比 10x");

        System.out.println();
        System.out.println("冒烟结果：通过 " + pass + " / 失败 " + fail);
        if (fail > 0) {
            System.exit(1);
        }
    }

    /** bulk NDJSON 构造（与 BulkRequest 的 wire 格式同构：action 行 + source 行交替） */
    static String bulkNdjson(String index, int docs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs; i++) {
            sb.append("{\"index\":{\"_index\":\"").append(index).append("\",\"_id\":\"d").append(i).append("\"}}\n");
            sb.append("{\"title\":\"doc-").append(i).append("\"}\n");
        }
        return sb.toString();
    }

    /** 行数必须为偶数且 action 行成对出现（模拟最小校验） */
    static boolean validNdjson(String s) {
        String[] lines = s.strip().split("\n");
        return lines.length % 2 == 0;
    }

    /** search_after 复合排序键比较：Long 优先，String 次之 */
    @SuppressWarnings("unchecked")
    static int cmp(List<Object> a, List<Object> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            Object x = a.get(i);
            Object y = b.get(i);
            if (x instanceof Long lx && y instanceof Long ly) {
                if (lx.compareTo(ly) != 0) {
                    return lx.compareTo(ly);
                }
            } else {
                int c = ((Comparable<Object>) x).compareTo(y);
                if (c != 0) {
                    return c;
                }
            }
        }
        return 0;
    }

    /** 深分页候选数：from+size 语义下每个分片都要交出 from+size 个候选 */
    static long candidates(int shards, int from, int size) {
        return (long) shards * (from + size);
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass++;
            System.out.println("  ✅ " + name);
        } else {
            fail++;
            System.out.println("  ❌ " + name + "（" + detail + "）");
        }
    }
}
