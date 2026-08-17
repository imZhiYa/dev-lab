package com.zhiya.es.experiment;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.zhiya.es.lab.EsLabBase;
import org.elasticsearch.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * EX-04 深分页：from+size vs search_after（es-03 检索与聚合，衔接知识库实验 008）。
 * <p>测法：12000 doc / 3 shards，按唯一 seq 排序。
 * A：from=0/1000/5000/9000（size=10）各 5 轮取中位——每个分片都要交 from+size 个候选，越深越贵；
 * B：from+size=10001 → 触发 index.max_result_window=10000 拒绝（400 实录）；
 * C：search_after 每页 1000 翻完 12000——每页候选恒为 shards×size，延迟应全程平稳。
 */
public class Ex04DeepPagination {

    private static final int DOCS = 12_000;
    private static final int SHARDS = 3;
    private static final int[] FROMS = {0, 1000, 5000, 9000};
    private static final int SIZE = 10;
    private static final int PAGE = 1000;

    public static void main(String[] args) throws Exception {
        try (RestClient restClient = EsLabBase.restClient()) {
            ElasticsearchClient client = EsLabBase.client(restClient);
            System.out.println("============ EX-04 深分页：from+size vs search_after ============");
            System.out.printf("测法：%d doc / %d shards，按唯一 seq 排序；from 档各 5 轮取中位；search_after 每页 %d 翻完%n%n",
                    DOCS, SHARDS, PAGE);

            String idx = "ex04-page";
            EsLabBase.recreateIndex(client, idx, SHARDS, 0, null);
            EsLabBase.bulkLoad(client, idx, DOCS, 1000, Ex04DeepPagination::doc);
            client.indices().refresh(r -> r.index(idx));

            // A. from+size 延迟曲线
            System.out.println("| from | 候选数/查询 (shards×(from+size)) | 延迟中位 |");
            System.out.println("| ---: | ---: | ---: |");
            for (int from : FROMS) {
                List<Double> took = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    took.add(searchFrom(client, idx, from, SIZE));
                }
                took.sort(Double::compareTo);
                double median = took.get(took.size() / 2);
                long candidates = (long) SHARDS * (from + SIZE);
                System.out.printf("| %d | %,d | %.1f ms |%n", from, candidates, median);
            }

            // B. 窗口拒绝
            System.out.println();
            try {
                searchFrom(client, idx, 9001, 1000);
                System.out.println("❌ from+size=10001 未被拒绝（与 max_result_window=10000 默认相悖，待查）");
            } catch (ElasticsearchException e) {
                System.out.println("[EX-04] from=9001&size=1000 → HTTP " + e.status()
                        + "：" + e.response().error().reason());
                System.out.println("        （index.max_result_window 默认 10000：from+size 必须不超过它——不是性能墙，是资源墙，直接拒绝）");
            }

            // C. search_after 全程翻页
            List<Double> pageTook = new ArrayList<>();
            List<FieldValue> after = null;
            long fetched = 0;
            while (true) {
                final List<FieldValue> cursor = after;
                SearchRequest.Builder sb = new SearchRequest.Builder()
                        .index(idx).size(PAGE)
                        .sort(so -> so.field(f -> f.field("seq").order(SortOrder.Asc)));
                if (cursor != null) {
                    sb.searchAfter(cursor);
                }
                long t0 = System.nanoTime();
                var resp = client.search(sb.build(), Map.class);
                pageTook.add((System.nanoTime() - t0) / 1_000_000.0);
                List<Hit<Map>> hits = resp.hits().hits();
                if (hits.isEmpty()) {
                    break;
                }
                fetched += hits.size();
                after = hits.get(hits.size() - 1).sort();
            }
            List<Double> sorted = new ArrayList<>(pageTook);
            sorted.sort(Double::compareTo);
            System.out.println();
            System.out.println("| 方式 | 页延迟 min | 中位 | max | 取回总数 |");
            System.out.println("| --- | ---: | ---: | ---: | ---: |");
            System.out.printf("| search_after 每页 %d | %.1f ms | %.1f ms | %.1f ms | %,d |%n",
                    PAGE, sorted.get(0), sorted.get(sorted.size() / 2), sorted.get(sorted.size() - 1), fetched);

            System.out.println();
            System.out.println("机制解读：from+size 要保证全局第 N 条之前有序，只能让每个分片都交出 from+size 个候选在协调端归并——"
                    + "深度进入成本（候选数）线性放大，且窗口 10000 直接拒绝；search_after 把'绝对深度'换成'游标'，"
                    + "每页候选恒为 shards×size，翻到底延迟都平稳（代价：只能顺序翻，不能跳页——es-03 深分页，实验 008）。");
        }
    }

    private static double searchFrom(ElasticsearchClient client, String idx, int from, int size) throws Exception {
        SearchRequest req = SearchRequest.of(s -> s.index(idx).from(from).size(size)
                .sort(so -> so.field(f -> f.field("seq").order(SortOrder.Asc))));
        return client.search(req, Map.class).took();
    }

    static Map<String, Object> doc(int i, Random rnd) {
        return Map.of("seq", i, "title", "page doc " + i, "seq2", rnd.nextInt(1000));
    }
}
