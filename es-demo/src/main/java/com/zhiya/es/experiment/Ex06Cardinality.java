package com.zhiya.es.experiment;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.zhiya.es.lab.EsLabBase;
import org.elasticsearch.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * EX-06 cardinality 精度与代价：terms 精确基数 vs HLL++ 估计（es-03 检索与聚合）。
 * <p>测法：30000 doc / 1 shard，user_id 真实基数 5000（keyword 显式 mapping）。
 * A：terms size=10000 全量数桶 = 精确基数（校验 sum_other=0 确认无截断）；
 * B：cardinality(precision_threshold=100/3000/40000) —— 阈值低于真实基数时按 ~失真率换内存，
 * 高于真实基数时近精确；各 5 轮取服务端 took 中位。
 * <p>FAST 档：30000 doc，distinct=5000。
 */
public class Ex06Cardinality {

    private static final int DOCS = 30_000;
    private static final int DISTINCT = 5_000;
    private static final long[] THRESHOLDS = {100, 3_000, 40_000};

    public static void main(String[] args) throws Exception {
        try (RestClient restClient = EsLabBase.restClient()) {
            ElasticsearchClient client = EsLabBase.client(restClient);
            System.out.println("============ EX-06 cardinality 精度与代价 ============");
            System.out.printf("测法：%d doc / 1 shard，user_id 真实基数 %d（keyword mapping）；terms 数桶 vs HLL++ 估计%n%n",
                    DOCS, DISTINCT);

            String idx = "ex06-card";
            EsLabBase.recreateIndex(client, idx, 1, 0, null);
            client.indices().putMapping(m -> m.index(idx)
                    .properties("user_id", p -> p.keyword(k -> k)));
            EsLabBase.bulkLoad(client, idx, DOCS, 1000, Ex06Cardinality::doc);
            client.indices().refresh(r -> r.index(idx));

            // A. terms 精确基数
            double exact = 0;
            {
                List<Double> took = new ArrayList<>();
                long buckets = -1;
                long sumOther = -1;
                for (int i = 0; i < 5; i++) {
                    SearchRequest req = SearchRequest.of(s -> s.index(idx).size(0)
                            .aggregations("t", a -> a.terms(t -> t.field("user_id").size(10_000))));
                    var resp = client.search(req, Map.class);
                    took.add((double) resp.took());
                    var terms = resp.aggregations().get("t").sterms();
                    buckets = terms.buckets().array().size();
                    sumOther = terms.sumOtherDocCount();
                }
                if (sumOther != 0) {
                    throw new IllegalStateException("terms 截断(sum_other=" + sumOther + ")，精确基准失真");
                }
                exact = buckets;
                took.sort(Double::compareTo);
                System.out.printf("[EX-06] terms 精确基数 = %d（took 中位 %.1f ms）%n",
                        buckets, took.get(took.size() / 2));
            }

            // B. cardinality 三档
            System.out.println();
            System.out.println("| 聚合 | 估值 | 误差 | took 中位 |");
            System.out.println("| --- | ---: | ---: | ---: |");
            System.out.printf("| terms（精确基准） | %.0f | 0%% | （见上） |%n", exact);
            for (long threshold : THRESHOLDS) {
                List<Double> took = new ArrayList<>();
                long estimate = -1;
                for (int i = 0; i < 5; i++) {
                    SearchRequest req = SearchRequest.of(s -> s.index(idx).size(0)
                            .aggregations("c", a -> a.cardinality(
                                    c -> c.field("user_id").precisionThreshold((int) threshold))));
                    var resp = client.search(req, Map.class);
                    took.add((double) resp.took());
                    estimate = resp.aggregations().get("c").cardinality().value();
                }
                took.sort(Double::compareTo);
                double err = Math.abs(estimate - exact) * 100.0 / exact;
                System.out.printf("| cardinality(threshold=%,d) | %d | %.2f%% | %.1f ms |%n",
                        threshold, estimate, err, took.get(took.size() / 2));
                System.out.printf("[EX-06] threshold=%,d → 估值 %d（误差 %.2f%%，took 中位 %.1f ms）%n",
                        threshold, estimate, err, took.get(took.size() / 2));
            }

            System.out.println();
            System.out.println("机制解读：HLL++ 用固定内存的寄存器换基数估计——threshold 低于真实基数时给出的是带失真率的估计"
                    + "（失真率由算法保证，官方口径约百分之几量级），高于真实基数时近精确；terms 精确数桶则要物化全部桶，"
                    + "基数越大内存/耗时越线性。'量级 vs 精确对账'的选型（es-03 cardinality / es-07 日志场景）。"
                    + "FAST 档绝对误差不外推：误差随真实基数/阈值/数据分布变化。");
        }
    }

    static Map<String, Object> doc(int i, Random rnd) {
        return Map.of("seq", i, "user_id", "u" + rnd.nextInt(DISTINCT));
    }
}
