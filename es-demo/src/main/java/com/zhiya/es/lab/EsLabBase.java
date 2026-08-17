package com.zhiya.es.lab;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * ES 实验共享工具：连接地址（env ES_URL，默认 http://localhost:9200，指向 eslab-es01 协调节点）、
 * 建索引（先删后建）、bulk 灌库、文档生成、分位统计。
 */
public final class EsLabBase {

    public static final String ES_URL = System.getenv().getOrDefault("ES_URL", "http://localhost:9200");

    private EsLabBase() {
    }

    /** 连接客户端（Jackson mapper；实验结束 close restClient） */
    public static RestClient restClient() {
        return RestClient.builder(HttpHost.create(ES_URL)).build();
    }

    public static ElasticsearchClient client(RestClient restClient) {
        return new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    /** 先删后建索引（每轮实验数据干净）；settings 由调用方追加 */
    public static void recreateIndex(ElasticsearchClient client, String name,
                                     int shards, int replicas, String refreshInterval) throws Exception {
        if (client.indices().exists(e -> e.index(name)).value()) {
            client.indices().delete(d -> d.index(name));
        }
        client.indices().create(c -> c.index(name).settings(s -> {
            s.numberOfShards(String.valueOf(shards)).numberOfReplicas(String.valueOf(replicas));
            if (refreshInterval != null) {
                String v = refreshInterval;
                s.refreshInterval(t -> t.time(v));
            }
            return s;
        }));
        waitForGreen(client, name);
    }

    /** 等索引级 green（3 节点 0/1 副本均可达 green） */
    public static void waitForGreen(ElasticsearchClient client, String index) throws Exception {
        client.cluster().health(h -> h.index(index)
                .waitForStatus(HealthStatus.Green).timeout(t -> t.time("120s")));
    }

    /** 改副本数后等分片落位 */
    public static void setReplicas(ElasticsearchClient client, String index, int replicas) throws Exception {
        client.indices().putSettings(p -> p.index(index).settings(
                s -> s.numberOfReplicas(String.valueOf(replicas))));
        waitForGreen(client, index);
    }

    /**
     * bulk 灌库：gen 生成第 i 号文档，返回请求延迟样本（ns）。
     * 灌完不 refresh（由调用方决定可见性语义），translog durability 用索引默认(request)。
     */
    public static List<Long> bulkLoad(ElasticsearchClient client, String index, int totalDocs, int batchSize,
                                      DocGenerator gen) throws Exception {
        List<Long> requestLatencies = new ArrayList<>();
        Random rnd = new Random(42);
        for (int start = 0; start < totalDocs; start += batchSize) {
            BulkRequest.Builder br = new BulkRequest.Builder();
            int end = Math.min(start + batchSize, totalDocs);
            for (int i = start; i < end; i++) {
                Map<String, Object> doc = gen.doc(i, rnd);
                int id = i;
                br.operations(op -> op.index(io -> io.index(index).id("d" + id).document(doc)));
            }
            long t0 = System.nanoTime();
            BulkResponse resp = client.bulk(br.build());
            long dt = System.nanoTime() - t0;
            if (resp.errors()) {
                BulkResponseItem item = resp.items().stream()
                        .filter(x -> x.error() != null).findFirst().orElse(null);
                throw new IllegalStateException("bulk 部分失败: " + (item != null ? item.error().reason() : "?"));
            }
            requestLatencies.add(dt);
        }
        return requestLatencies;
    }

    @FunctionalInterface
    public interface DocGenerator {
        Map<String, Object> doc(int i, Random rnd);
    }

    /** 分位统计（P50/P99，输入 ns） */
    public record LatencyStats(double p50Ms, double p99Ms, double maxMs) {
        public static LatencyStats of(List<Long> samplesNs) {
            if (samplesNs == null || samplesNs.isEmpty()) {
                return new LatencyStats(0, 0, 0);
            }
            List<Long> sorted = new ArrayList<>(samplesNs);
            sorted.sort(Long::compareTo);
            double p50 = sorted.get(sorted.size() / 2) / 1_000_000.0;
            double p99 = sorted.get((int) Math.min(sorted.size() - 1, sorted.size() * 99 / 100)) / 1_000_000.0;
            double max = sorted.get(sorted.size() - 1) / 1_000_000.0;
            return new LatencyStats(p50, p99, max);
        }
    }

    public static String ms(double v) {
        return String.format("%.2f ms", v);
    }
}
