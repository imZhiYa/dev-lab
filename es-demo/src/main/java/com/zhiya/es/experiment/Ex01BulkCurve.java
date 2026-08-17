package com.zhiya.es.experiment;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import com.zhiya.es.lab.EsLabBase;
import org.elasticsearch.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * EX-01 批量写曲线：单条 index vs bulk 100/500/1000/5000（es-01 存储内核与落盘）。
 * <p>测法：每个批档独立索引（1 shard 0 replicas，隔离副本与多分片干扰），同一文档生成器，
 * 同步发送并逐请求计延迟——批量摊薄的是"每请求固定成本"（网络往返 + 协调 + translog fsync 批处理），
 * durability 用索引默认(request)，即每请求都要等 translog 落盘，固定成本占比被放大、机制可见。
 * <p>FAST 档：单条档 10000 doc，bulk 档各 20000 doc，~200B/条。
 */
public class Ex01BulkCurve {

    private static final String[] SIZES = {"single", "100", "500", "1000", "5000"};
    private static final int SINGLE_DOCS = 10_000;
    private static final int BULK_DOCS = 20_000;

    public static void main(String[] args) throws Exception {
        try (RestClient restClient = EsLabBase.restClient()) {
            ElasticsearchClient client = EsLabBase.client(restClient);
            System.out.println("============ EX-01 批量写曲线：单条 vs bulk 100/500/1000/5000 ============");
            System.out.println("测法：每档独立索引(1s0r) + 同步发送逐请求计延迟；durability=request(默认) → 每请求等 translog fsync");
            System.out.printf("FAST 档：单条 %d doc，bulk 档各 %d doc，~200B/条%n%n", SINGLE_DOCS, BULK_DOCS);

            List<String[]> rows = new ArrayList<>();
            for (String size : SIZES) {
                String idx = "ex01-" + (size.equals("single") ? "single" : "b" + size);
                EsLabBase.recreateIndex(client, idx, 1, 0, null);
                long elapsed;
                List<Long> latencies;
                long docs;
                if (size.equals("single")) {
                    docs = SINGLE_DOCS;
                    long t0 = System.nanoTime();
                    latencies = runSingle(client, idx, SINGLE_DOCS);
                    elapsed = System.nanoTime() - t0;
                } else {
                    docs = BULK_DOCS;
                    int batch = Integer.parseInt(size);
                    long t0 = System.nanoTime();
                    latencies = EsLabBase.bulkLoad(client, idx, BULK_DOCS, batch, Ex01BulkCurve::doc);
                    elapsed = System.nanoTime() - t0;
                }
                EsLabBase.LatencyStats st = EsLabBase.LatencyStats.of(latencies);
                double docsPerSec = docs * 1_000_000_000.0 / elapsed;
                rows.add(new String[]{size, String.format("%,d", (long) docsPerSec),
                        EsLabBase.ms(st.p50Ms()), EsLabBase.ms(st.p99Ms()), EsLabBase.ms(st.maxMs())});
                System.out.printf("[EX-01] bulk=%-6s → %,10.0f docs/s，bulk 请求 P50=%s P99=%s max=%s%n",
                        size, docsPerSec, EsLabBase.ms(st.p50Ms()), EsLabBase.ms(st.p99Ms()), EsLabBase.ms(st.maxMs()));
            }

            System.out.println();
            System.out.println("| bulk 批大小 | 吞吐 (docs/s) | 请求延迟 P50 | P99 | max |");
            System.out.println("| --- | ---: | ---: | ---: | ---: |");
            for (String[] r : rows) {
                System.out.printf("| %s | %s | %s | %s | %s |%n", r[0], r[1], r[2], r[3], r[4]);
            }
            System.out.println();
            System.out.println("机制解读：吞吐随批大小上升不是'ES 更快了'，而是每请求固定成本（HTTP 往返 + 协调节点解析 + "
                    + "translog fsync 的批处理）被一批文档摊薄；durability=request 下单条写的 fsync 无法摊薄，"
                    + "差距即摊薄收益（es-01：写路径固定成本清单）。绝对数是跨 VM 教学量级，只看相对差异方向。");
        }
    }

    private static List<Long> runSingle(ElasticsearchClient client, String idx, int total) throws Exception {
        List<Long> latencies = new ArrayList<>(total);
        Random rnd = new Random(42);
        for (int i = 0; i < total; i++) {
            Map<String, Object> doc = doc(i, rnd);
            int id = i;
            long t0 = System.nanoTime();
            IndexResponse resp = client.index(ir -> ir.index(idx).id("d" + id).document(doc));
            long dt = System.nanoTime() - t0;
            if (resp.result() == null) {
                throw new IllegalStateException("index 失败 at " + i);
            }
            latencies.add(dt);
        }
        return latencies;
    }

    static Map<String, Object> doc(int i, Random rnd) {
        return Map.of(
                "seq", i,
                "title", "elasticsearch lab doc " + i + " alpha beta gamma delta epsilon",
                "price", Math.round(rnd.nextDouble() * 10000) / 100.0,
                "status", rnd.nextBoolean() ? "A" : "B");
    }
}
