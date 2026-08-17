package com.zhiya.es.experiment;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.zhiya.es.lab.EsLabBase;
import org.elasticsearch.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * EX-02 refresh 可见性窗口：写 → 搜之间隔着 refresh（es-01 存储内核与落盘，衔接知识库实验 001）。
 * <p>测法 A：默认 refresh_interval=1s，写一条 → 每 10ms 轮询 term 查询直到可见，得可见性延迟分布（15 轮）。
 * <p>测法 B：refresh_interval=-1（关闭自动 refresh），写 5 条 → 搜不到；sleep 1.5s 仍搜不到；
 * 手动 refresh 后立即可见——证明"搜不到"不是没写进去，而是还在内存 buffer，未生成可搜索 segment。
 */
public class Ex02RefreshVisibility {

    private static final int ROUNDS = 15;
    private static final long POLL_INTERVAL_MS = 10;
    private static final long TIMEOUT_MS = 5_000;

    public static void main(String[] args) throws Exception {
        try (RestClient restClient = EsLabBase.restClient()) {
            ElasticsearchClient client = EsLabBase.client(restClient);
            System.out.println("============ EX-02 refresh 可见性窗口 ============");
            System.out.println("测法 A：默认 refresh_interval=1s，写入后 10ms 轮询到可见，" + ROUNDS + " 轮取分布");
            System.out.println("测法 B：refresh_interval=-1 + 手动 refresh，验证'不可见 ≠ 没写入'\n");

            // A. 默认 1s：可见性延迟分布
            String idxA = "ex02-default";
            EsLabBase.recreateIndex(client, idxA, 1, 0, "1s");
            List<Long> vis = new java.util.ArrayList<>();
            for (int r = 0; r < ROUNDS; r++) {
                String id = "v" + r;
                int round = r;
                client.index(i -> i.index(idxA).id(id).document(Map.of("tag", "vis2", "round", round)));
                long t0 = System.nanoTime();
                long waited = 0;
                while (waited < TIMEOUT_MS) {
                    long hits = count(client, idxA, r);
                    if (hits > 0) {
                        vis.add(System.nanoTime() - t0);
                        break;
                    }
                    Thread.sleep(POLL_INTERVAL_MS);
                    waited += POLL_INTERVAL_MS;
                }
                if (waited >= TIMEOUT_MS) {
                    throw new IllegalStateException("round " + r + " 超时未见，机制异常");
                }
            }
            List<Long> sorted = new java.util.ArrayList<>(vis);
            sorted.sort(Long::compareTo);
            double min = sorted.get(0) / 1_000_000.0;
            double p50 = sorted.get(sorted.size() / 2) / 1_000_000.0;
            double max = sorted.get(sorted.size() - 1) / 1_000_000.0;
            System.out.println("| 档 | 可见性延迟 min | P50 | max |");
            System.out.println("| --- | ---: | ---: | ---: |");
            System.out.printf("| 默认 refresh=1s | %.0f ms | %.0f ms | %.0f ms |%n", min, p50, max);
            System.out.printf("[EX-02] 默认档：%d 轮可见性延迟 min=%.0fms P50=%.0fms max=%.0fms%n%n",
                    ROUNDS, min, p50, max);

            // B. 关闭自动 refresh
            String idxB = "ex02-manual";
            EsLabBase.recreateIndex(client, idxB, 1, 0, "-1");
            for (int i = 0; i < 5; i++) {
                int n = i;
                client.index(ir -> ir.index(idxB).id("m" + n).document(Map.of("tag", "man5", "seq", n)));
            }
            long before = countAll(client, idxB);
            Thread.sleep(1_500);
            long afterSleep = countAll(client, idxB);
            client.indices().refresh(r -> r.index(idxB));
            long afterRefresh = countAll(client, idxB);

            System.out.println("| refresh_interval=-1 | 可见命中数 |");
            System.out.println("| --- | ---: |");
            System.out.printf("| 写入 5 条后立即搜 | %d |%n", before);
            System.out.printf("| sleep 1.5s 后搜 | %d |%n", afterSleep);
            System.out.printf("| 手动 refresh 后搜 | %d |%n", afterRefresh);

            System.out.println();
            System.out.println("机制解读：写入 ACK 只保证进了内存 buffer + translog（可恢复），不保证可搜——可搜要等 refresh "
                    + "把 buffer 变成新 segment（默认 1s 一拍，P50 可见延迟接近但小于 1s：写入落在拍内随机位置）。"
                    + "关掉自动 refresh 后'搜不到'能一直保持，直到手动触发——NRT 的'近'实时就是这一拍（es-01 L2，实验 001）。");
        }
    }

    private static long count(ElasticsearchClient client, String idx, int round) throws Exception {
        SearchRequest req = SearchRequest.of(s -> s.index(idx).size(0)
                .query(q -> q.bool(b -> b
                        .must(m -> m.term(t -> t.field("tag").value("vis2")))
                        .must(m -> m.term(t -> t.field("round").value(round))))));
        return client.search(req, Map.class).hits().total().value();
    }

    private static long countAll(ElasticsearchClient client, String idx) throws Exception {
        return client.search(s -> s.index(idx).size(0), Map.class).hits().total().value();
    }
}
