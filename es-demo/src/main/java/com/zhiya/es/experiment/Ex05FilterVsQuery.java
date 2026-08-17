package com.zhiya.es.experiment;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiya.es.lab.EsLabBase;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * EX-05 filter vs query：算分上下文的代价 + query cache 观测（es-03 检索与聚合）。
 * <p>测法：20000 doc / 1 shard，同一条件 match(title: elasticsearch) 分别放进
 * bool.must（query 上下文：要算 BM25 分）与 bool.filter（filter 上下文：只判 yes/no，不算分、可缓存）。
 * 首次调用 + 300 次重复取中位；再读 _stats/query_cache 观测缓存命中——
 * query cache 是段级缓存，段要足够大才进入候选（官方阈值量大），FAST 档 2 万文档可能为 0，如实记录。
 */
public class Ex05FilterVsQuery {

    private static final int DOCS = 20_000;
    private static final int REPEATS = 300;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        try (RestClient restClient = EsLabBase.restClient()) {
            ElasticsearchClient client = EsLabBase.client(restClient);
            System.out.println("============ EX-05 filter vs query（同一 match 条件的两种上下文） ============");
            System.out.printf("测法：%d doc / 1 shard，must(算 BM25) vs filter(不算分)；首次 + %d 次重复取中位%n%n", DOCS, REPEATS);

            String idx = "ex05-ctx";
            EsLabBase.recreateIndex(client, idx, 1, 0, null);
            EsLabBase.bulkLoad(client, idx, DOCS, 1000, Ex05FilterVsQuery::doc);
            client.indices().refresh(r -> r.index(idx));

            Result must = run(client, idx, true);
            Result filter = run(client, idx, false);

            System.out.println("| 上下文 | 首次 | 重复中位 | P99 | 命中数 |");
            System.out.println("| --- | ---: | ---: | ---: | ---: |");
            System.out.printf("| must(query,算分) | %.2f ms | %.2f ms | %.2f ms | - |%n",
                    must.firstMs(), must.medianMs(), must.p99Ms());
            System.out.printf("| filter(不算分) | %.2f ms | %.2f ms | %.2f ms | %s |%n",
                    filter.firstMs(), filter.medianMs(), filter.p99Ms(), must.cacheLabel());

            // query cache 观测（filter 跑完后）
            Request stat = new Request("GET", "/" + idx + "/_stats/query_cache");
            Response raw = restClient.performRequest(stat);
            JsonNode qc = MAPPER.readTree(raw.getEntity().getContent())
                    .path("indices").path(idx).path("total").path("query_cache");
            long hit = qc.path("hit_count").asLong();
            long miss = qc.path("miss_count").asLong();
            long cached = qc.path("cache_count").asLong();
            System.out.println();
            System.out.printf("[EX-05] query_cache 观测：hit=%d miss=%d 已缓存条目=%d%n", hit, miss, cached);
            if (cached == 0) {
                System.out.println("        机制注记：FAST 档 2 万文档未触发缓存——query cache 是段级缓存，"
                        + "官方以'段足够大'为进入条件（量级远大于本档），未触发属机制边界而非异常。");
            }

            System.out.println();
            System.out.println("机制解读：filter 上下文跳过 BM25 算分与评分归一，重复执行还可能命中 query cache——"
                    + "'是否需要相关度排序'应成为写查询的第一问：要排序用 query，只要 yes/no（时间范围、状态、权限）一律 filter（es-03）。"
                    + "教学量级：两上下文差异受段数与倒排长度影响，绝对数不外推。");
        }
    }

    private record Result(double firstMs, double medianMs, double p99Ms, String cacheLabel) {
    }

    private static Result run(ElasticsearchClient client, String idx, boolean mustContext) throws Exception {
        FieldValue term = FieldValue.of("elasticsearch");
        SearchRequest req = SearchRequest.of(s -> s.index(idx).size(5).query(q -> q.bool(b -> {
            if (mustContext) {
                b.must(m -> m.match(mm -> mm.field("title").query(term)));
            } else {
                b.filter(f -> f.match(mm -> mm.field("title").query(term)));
            }
            return b;
        })));

        long t0 = System.nanoTime();
        client.search(req, Map.class);
        double first = (System.nanoTime() - t0) / 1_000_000.0;

        List<Double> took = new ArrayList<>(REPEATS);
        for (int i = 0; i < REPEATS; i++) {
            took.add((double) client.search(req, Map.class).took());
        }
        took.sort(Double::compareTo);
        double median = took.get(took.size() / 2);
        double p99 = took.get((int) Math.min(took.size() - 1, took.size() * 99 / 100));
        System.out.printf("[EX-05] %-22s 首次 %.2f ms → 重复中位 %.2f ms（P99 %.2f ms）%n",
                mustContext ? "must(query,算分)" : "filter(不算分)", first, median, p99);
        return new Result(first, median, p99, "");
    }

    static Map<String, Object> doc(int i, Random rnd) {
        String title = (i % 2 == 0)
                ? "elasticsearch in action chapter " + i
                : "ordinary book title number " + i;
        return Map.of("seq", i, "title", title, "status", rnd.nextBoolean() ? "A" : "B");
    }
}
