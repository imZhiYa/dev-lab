package com.zhiya.dubbo.demo.protocol;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import com.zhiya.dubbo.demo.api.GreetingRequest;
import com.zhiya.dubbo.demo.api.GreetingService;
import com.zhiya.dubbo.demo.api.OrderRequest;
import com.zhiya.dubbo.demo.api.OrderService;
import org.apache.dubbo.rpc.RpcContext;

import java.time.Duration;
import java.time.Instant;

/**
 * E04：长跑型消费端。
 *
 * 在注册中心（Nacos）被 kill/重启期间持续循环调用 provider，
 * 观察控制面宕机时本地缓存目录（RegistryDirectory 快照）是否继续在数据面派单。
 *
 * 用法：-Dexec.args="-n 60 -i 1000"（60 次、间隔 1000ms；默认不限次、2000ms）
 */
public class ConsumerLoopApp {

    private static final long T0 = System.nanoTime();

    public static void main(String[] args) throws Exception {
        System.setProperty("dubbo.network.interface.preferred", "lo0");
        System.setProperty("qos.enable", "false");

        int iterations = Integer.MAX_VALUE;
        long intervalMs = 2000;
        for (int i = 0; i < args.length; i++) {
            if ("-n".equals(args[i])) iterations = Integer.parseInt(args[++i]);
            if ("-i".equals(args[i])) intervalMs = Long.parseLong(args[++i]);
        }

        String providedBy = System.getProperty("dubbo.consumer.providedBy", "");
        int retries = Integer.parseInt(System.getProperty("dubbo.consumer.retries", "0"));
        String loadbalance = System.getProperty("dubbo.consumer.loadbalance", "");
        System.out.println(ts() + " [CONSUMER] retries=" + retries + " loadbalance=" + (loadbalance.isEmpty() ? "(default random)" : loadbalance));

        ApplicationConfig application = new ApplicationConfig("demo04-consumer");

        RegistryConfig registry = new RegistryConfig();
        registry.setAddress("nacos://127.0.0.1:8848");

        ReferenceConfig<GreetingService> greetingRef = new ReferenceConfig<>();
        greetingRef.setApplication(application);
        greetingRef.setRegistry(registry);
        greetingRef.setInterface(GreetingService.class);
        greetingRef.setRetries(retries);
        if (!loadbalance.isEmpty()) {
            greetingRef.setLoadbalance(loadbalance);
        }
        if (!providedBy.isEmpty()) {
            greetingRef.setProvidedBy(providedBy);
        }

        ReferenceConfig<OrderService> orderRef = new ReferenceConfig<>();
        orderRef.setApplication(application);
        orderRef.setRegistry(registry);
        orderRef.setInterface(OrderService.class);
        orderRef.setRetries(retries);
        if (!loadbalance.isEmpty()) {
            orderRef.setLoadbalance(loadbalance);
        }
        if (!providedBy.isEmpty()) {
            orderRef.setProvidedBy(providedBy);
        }

        System.out.println(ts() + " [CONSUMER] building references from Nacos ...");
        GreetingService greeting = greetingRef.get();
        // OrderService 仅订阅不调用：E04 观察"控制面宕机时本地缓存继续派单"只需 greeting 的 OK/FAIL，
        // 第二个订阅用于对照"多服务订阅快照"在 Nacos 重启后是否一并恢复
        orderRef.get();
        System.out.println(ts() + " [CONSUMER] references built, starting loop (n=" + iterations + ", interval=" + intervalMs + "ms)");

        int ok = 0, fail = 0;
        for (int i = 1; i <= iterations; i++) {
            long started = System.nanoTime();
            try {
                String r = greeting.greet(new GreetingRequest("O", i));
                long costMs = (System.nanoTime() - started) / 1_000_000;
                ok++;
                String remote = String.valueOf(RpcContext.getServiceContext().getRemoteAddress());
                System.out.println(ts() + " [CONSUMER] #" + i + " OK  cost=" + costMs + "ms from=" + remote + " -> " + r);
            } catch (Throwable t) {
                long costMs = (System.nanoTime() - started) / 1_000_000;
                fail++;
                String msg = String.valueOf(t.getMessage());
                if (msg.length() > 120) msg = msg.substring(0, 120);
                System.out.println(ts() + " [CONSUMER] #" + i + " FAIL cost=" + costMs + "ms " + t.getClass().getSimpleName() + ": " + msg);
            }
            Thread.sleep(intervalMs);
        }
        System.out.println(ts() + " [CONSUMER] done ok=" + ok + " fail=" + fail);

        greetingRef.destroy();
        orderRef.destroy();
        System.exit(0);
    }

    private static String ts() {
        Instant now = Instant.now();
        long uptimeMs = Duration.ofNanos(System.nanoTime() - T0).toMillis();
        return String.format("[t=%+06.3fs %s]",
                uptimeMs / 1000.0, now.toString());
    }
}
