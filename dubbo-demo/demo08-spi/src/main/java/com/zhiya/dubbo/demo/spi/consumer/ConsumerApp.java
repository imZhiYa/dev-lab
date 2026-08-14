package com.zhiya.dubbo.demo.spi.consumer;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import com.zhiya.dubbo.demo.api.GreetingRequest;
import com.zhiya.dubbo.demo.api.GreetingService;

/**
 * E09-5 消费端：经 Nacos 订阅（两个 provider 已注册），配 loadbalance=demo
 * （自定义 DemoLoadBalance），证明扩展被真实 cluster 链选中。
 */
public class ConsumerApp {

    public static void main(String[] args) throws Exception {
        ApplicationConfig application = new ApplicationConfig("demo08-consumer");

        RegistryConfig registry = new RegistryConfig();
        registry.setAddress("nacos://127.0.0.1:8848");

        ReferenceConfig<GreetingService> reference = new ReferenceConfig<>();
        reference.setApplication(application);
        reference.setRegistry(registry);
        reference.setInterface(GreetingService.class);
        reference.setLoadbalance("demo");
        reference.setTimeout(10000);

        GreetingService service = reference.get();
        System.out.println("=== [CONSUMER] got reference (nacos subscribe), warmup ===");
        for (int i = 0; i < 5; i++) {
            System.out.println("=== [CONSUMER] call " + i + ": " + service.greet(new GreetingRequest("O", i)));
        }
        System.exit(0);
    }
}
