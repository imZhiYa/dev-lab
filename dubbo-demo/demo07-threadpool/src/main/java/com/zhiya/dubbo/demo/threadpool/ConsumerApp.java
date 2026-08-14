package com.zhiya.dubbo.demo.threadpool;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import com.zhiya.dubbo.demo.api.GreetingRequest;
import com.zhiya.dubbo.demo.api.GreetingService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * E07 消费端：直连引用，发起 N 路并发调用，报告成功/失败分布与单次耗时。
 *
 * 系统属性：
 *   demo.concurrent — 并发工作线程数（默认 1）
 *   demo.calls      — 总调用次数（默认 1）
 *   demo.timeout    — 消费端超时 ms（默认 30000；饱和实验需要留足余量）
 */
public class ConsumerApp {

    public static void main(String[] args) throws Exception {
        ApplicationConfig application = new ApplicationConfig("demo07-consumer");

        RegistryConfig registry = new RegistryConfig();
        registry.setAddress(RegistryConfig.NO_AVAILABLE);

        ReferenceConfig<GreetingService> reference = new ReferenceConfig<>();
        reference.setApplication(application);
        reference.setRegistry(registry);
        reference.setInterface(GreetingService.class);
        reference.setUrl("tri://127.0.0.1:50051/com.zhiya.dubbo.demo.api.GreetingService");
        reference.setTimeout(Integer.parseInt(System.getProperty("demo.timeout", "30000")));

        GreetingService service = reference.get();
        System.out.println("=== [CONSUMER] got reference, warming up ===");
        System.out.println("=== [CONSUMER] warmup: " + service.greet(new GreetingRequest("warmup", 0)));

        int concurrent = Integer.parseInt(System.getProperty("demo.concurrent", "1"));
        int calls = Integer.parseInt(System.getProperty("demo.calls", "1"));

        ExecutorService pool = Executors.newFixedThreadPool(concurrent);
        CountDownLatch ready = new CountDownLatch(concurrent);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        List<Future<String>> futures = new ArrayList<>();
        List<Long> latencies = java.util.Collections.synchronizedList(new ArrayList<>());

        long start = System.currentTimeMillis();
        for (int i = 1; i <= calls; i++) {
            final int seq = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                fire.await();
                long t0 = System.currentTimeMillis();
                try {
                    String r = service.greet(new GreetingRequest("O", seq));
                    ok.incrementAndGet();
                    latencies.add(System.currentTimeMillis() - t0);
                    return r;
                } catch (Throwable e) {
                    fail.incrementAndGet();
                    System.out.println("    [CONSUMER] seq=" + seq + " FAILED: "
                            + e.getClass().getSimpleName() + ": " + truncate(e.getMessage()));
                    return null;
                }
            }));
        }
        ready.await();
        fire.countDown();
        pool.shutdown();
        pool.awaitTermination(120, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        long min = latencies.stream().mapToLong(Long::longValue).min().orElse(-1);
        long max = latencies.stream().mapToLong(Long::longValue).max().orElse(-1);
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(-1);
        System.out.println("=== [CONSUMER] RESULT: ok=" + ok.get() + " fail=" + fail.get()
                + " total=" + calls + " elapsedMs=" + elapsed
                + " latencyMs(min/avg/max)=" + min + "/" + String.format("%.0f", avg) + "/" + max);
        System.exit(0);
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 180 ? s.substring(0, 180) + "..." : s;
    }
}
