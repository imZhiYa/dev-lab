package com.zhiya.dubbo.demo.protocol;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import com.zhiya.dubbo.demo.api.GreetingRequest;
import com.zhiya.dubbo.demo.api.GreetingService;

import java.util.Arrays;

/**
 * demo01 压测消费端：直连模式下按「信封（dubbo/triple）× 盒子（hessian2/kryo）」四象限对比。
 *
 * 用法：-Dproto=dubbo|triple -Dserialization=hessian2|kryo -Dhost=<host> [-Dpayload=small|medium|large]
 * 端口：dubbo+hessian2 20882、dubbo+kryo 20883、triple+hessian2 50052、triple+kryo 50053
 * 顺序：预热 200 次 → 实测 2000 次同步调用，输出 RT 分位 + QPS
 *
 * 边界：仅本机单机串行方向性参考，不可外推跨机/并发结论（E01 实测）
 */
public class ConsumerBench {

    public static void main(String[] args) throws Exception {
        System.setProperty("dubbo.network.interface.preferred", "lo0");
        System.setProperty("qos.enable", "false");

        String proto = System.getProperty("proto", "triple");
        String serialization = System.getProperty("serialization", "hessian2");
        String host = System.getProperty("host", "127.0.0.1");
        String payload = System.getProperty("payload", "small");

        String scheme = "dubbo".equals(proto) ? "dubbo" : "tri";
        int port = switch (proto + "/" + serialization) {
            case "dubbo/hessian2" -> 20882;
            case "dubbo/kryo" -> 20883;
            case "triple/hessian2" -> 50052;
            case "triple/kryo" -> 50053;
            default -> throw new IllegalArgumentException("unknown proto/serialization: " + proto + "/" + serialization);
        };

        ApplicationConfig application = new ApplicationConfig("demo01-consumer");
        ReferenceConfig<GreetingService> reference = new ReferenceConfig<>();
        reference.setApplication(application);
        reference.setInterface(GreetingService.class);
        reference.setUrl(scheme + "://" + host + ":" + port + "/com.zhiya.dubbo.demo.api.GreetingService?serialization=" + serialization);
        reference.setRetries(0);
        reference.setTimeout(10000);

        GreetingService service = reference.get();
        String name = switch (payload) {
            case "medium" -> "M".repeat(256);
            case "large" -> "L".repeat(1024);
            default -> "O";
        };

        int warmup = 200;
        int calls = 2000;
        for (int i = 0; i < warmup; i++) {
            service.greet(new GreetingRequest(name, i));
        }

        long[] latencies = new long[calls];
        long start = System.nanoTime();
        for (int i = 0; i < calls; i++) {
            long t0 = System.nanoTime();
            service.greet(new GreetingRequest(name, i));
            latencies[i] = (System.nanoTime() - t0) / 1000;
        }
        long totalUs = (System.nanoTime() - start) / 1000;

        Arrays.sort(latencies);
        double qps = calls * 1_000_000.0 / totalUs;
        System.out.printf("=== [BENCH] proto=%s serialization=%s payload=%s host=%s calls=%d%n",
                proto, serialization, payload, host, calls);
        System.out.printf("=== [BENCH] RT(us): p50=%d p90=%d p95=%d p99=%d max=%d mean=%.1f%n",
                latencies[calls / 2],
                latencies[(int) (calls * 0.90)],
                latencies[(int) (calls * 0.95)],
                latencies[(int) (calls * 0.99)],
                latencies[calls - 1],
                Arrays.stream(latencies).average().orElse(0));
        System.out.printf("=== [BENCH] total=%dms qps=%.1f%n", totalUs / 1000, qps);

        reference.destroy();
        System.exit(0);
    }
}
