package com.zhiya.dubbo.demo.registry;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import com.zhiya.dubbo.demo.api.GreetingRequest;
import com.zhiya.dubbo.demo.api.GreetingService;
import com.zhiya.dubbo.demo.api.OrderRequest;
import com.zhiya.dubbo.demo.api.OrderService;

/**
 * demo02 消费端：从 Nacos 发现 GreetingService + OrderService 并调用，
 * 打印实际发现的注册元数据用于三态对比（E03 实测）。
 */
public class ConsumerApp {

    public static void main(String[] args) throws Exception {
        System.setProperty("dubbo.network.interface.preferred", "lo0");
        System.setProperty("qos.enable", "false");

        String registerMode = System.getProperty("dubbo.application.register-mode", "instance");
        System.out.println("=== [CONSUMER] register-mode = " + registerMode);

        ApplicationConfig application = new ApplicationConfig("demo02-consumer");

        RegistryConfig registry = new RegistryConfig();
        registry.setAddress("nacos://127.0.0.1:8848");

        String providedBy = System.getProperty("dubbo.consumer.providedBy", "");
        if (!providedBy.isEmpty()) {
            System.out.println("=== [CONSUMER] providedBy = " + providedBy);
        }

        ReferenceConfig<GreetingService> greetingRef = new ReferenceConfig<>();
        greetingRef.setApplication(application);
        greetingRef.setRegistry(registry);
        greetingRef.setInterface(GreetingService.class);
        greetingRef.setRetries(0);
        if (!providedBy.isEmpty()) {
            greetingRef.setProvidedBy(providedBy);
        }

        ReferenceConfig<OrderService> orderRef = new ReferenceConfig<>();
        orderRef.setApplication(application);
        orderRef.setRegistry(registry);
        orderRef.setInterface(OrderService.class);
        orderRef.setRetries(0);
        if (!providedBy.isEmpty()) {
            orderRef.setProvidedBy(providedBy);
        }

        System.out.println("=== [CONSUMER] building references from Nacos ...");
        GreetingService greeting = greetingRef.get();
        OrderService order = orderRef.get();

        String greetResult = greeting.greet(new GreetingRequest("O", 1));
        System.out.println("=== [CONSUMER] greet() -> " + greetResult);

        String orderResult = order.create(new OrderRequest("T-20260811-001", 100));
        System.out.println("=== [CONSUMER] create() -> " + orderResult);

        String statusResult = order.queryStatus("T-20260811-001");
        System.out.println("=== [CONSUMER] queryStatus() -> " + statusResult);

        greetingRef.destroy();
        orderRef.destroy();
        System.exit(0);
    }
}
