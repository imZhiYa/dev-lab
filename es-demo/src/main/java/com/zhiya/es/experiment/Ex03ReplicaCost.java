package com.zhiya.es.experiment;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.zhiya.es.lab.EsLabBase;
import org.elasticsearch.client.RestClient;

import java.util.List;

/**
 * EX-03 副本复制代价：replicas=0 vs 1（es-02 高可用与故障恢复）。
 * <p>测法：同一 bulk=500 × 40 批 = 20000 doc 负载，两档各用独立索引（1 shard），
 * 3 节点集群下副本自动落在另一节点——写 ACK 链路从"主分片 index + translog"变为
 * "主分片 → 副本同步转发 → 副本 index + translog → 双双完成才 ACK"（es-02 L2 复制）。
 * <p>FAST 档：20000 doc/档，~200B/条，durability=request 默认。
 */
public class Ex03ReplicaCost {

    private static final int DOCS = 20_000;
    private static final int BATCH = 500;

    public static void main(String[] args) throws Exception {
        try (RestClient restClient = EsLabBase.restClient()) {
            ElasticsearchClient client = EsLabBase.client(restClient);
            System.out.println("============ EX-03 副本复制代价：replicas=0 vs 1 ============");
            System.out.println("测法：3 节点集群，1 shard × " + DOCS + " doc（bulk " + BATCH + "），同步逐请求计延迟");
            System.out.println("链路差：0 副本 = 主分片落 translog 即 ACK；1 副本 = 还要等副本节点同步落盘（es-02 L2）\n");

            Result r0 = run(client, "ex03-r0", 0);
            Result r1 = run(client, "ex03-r1", 1);

            double drop = (r0.docsPerSec() - r1.docsPerSec()) * 100.0 / r0.docsPerSec();
            double p99Grow = (r1.p99Ms() - r0.p99Ms()) * 100.0 / r0.p99Ms();

            System.out.println("| 副本数 | 吞吐 (docs/s) | bulk 请求 P50 | P99 | max |");
            System.out.println("| --- | ---: | ---: | ---: | ---: |");
            System.out.printf("| 0 | %,d | %s | %s | %s |%n",
                    (long) r0.docsPerSec(), EsLabBase.ms(r0.p50Ms()), EsLabBase.ms(r0.p99Ms()), EsLabBase.ms(r0.maxMs()));
            System.out.printf("| 1 | %,d | %s | %s | %s |%n",
                    (long) r1.docsPerSec(), EsLabBase.ms(r1.p50Ms()), EsLabBase.ms(r1.p99Ms()), EsLabBase.ms(r1.maxMs()));
            System.out.println();
            System.out.println("机制解读：吞吐 -" + String.format("%.1f", drop) + "%、P99 +" + String.format("%.1f", p99Grow)
                    + "%——副本不是备份镜像的'零成本拷贝'，而是每次写都要走的第二条完整链路（网络转发 + 副本端 index + translog）。"
                    + "这是'高可用'用写吞吐买的：0 副本丢节点=丢数据，1 副本丢节点=副本顶上（es-02 的 trade-off）。"
                    + "教学量级绝对数不外推，代价随网络/盘延迟变化。");
        }
    }

    private record Result(double docsPerSec, double p50Ms, double p99Ms, double maxMs) {
    }

    private static Result run(ElasticsearchClient client, String idx, int replicas) throws Exception {
        EsLabBase.recreateIndex(client, idx, 1, replicas, null);
        long t0 = System.nanoTime();
        List<Long> lat = EsLabBase.bulkLoad(client, idx, DOCS, BATCH, Ex01BulkCurve::doc);
        long elapsed = System.nanoTime() - t0;
        EsLabBase.LatencyStats st = EsLabBase.LatencyStats.of(lat);
        double docsPerSec = DOCS * 1_000_000_000.0 / elapsed;
        System.out.printf("[EX-03] replicas=%d → %,10.0f docs/s，P50=%s P99=%s%n",
                replicas, docsPerSec, EsLabBase.ms(st.p50Ms()), EsLabBase.ms(st.p99Ms()));
        return new Result(docsPerSec, st.p50Ms(), st.p99Ms(), st.maxMs());
    }
}
