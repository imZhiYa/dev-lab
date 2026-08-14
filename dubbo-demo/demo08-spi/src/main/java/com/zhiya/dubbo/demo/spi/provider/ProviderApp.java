package com.zhiya.dubbo.demo.spi.provider;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import com.zhiya.dubbo.demo.api.GreetingService;

/**
 * E09-5 提供端：tri 导出 GreetingService 并注册到 Nacos。
 * 起两个实例（不同端口），让消费端 LoadBalance 必须二选一。
 *
 * 系统属性：
 *   demo.port — 监听端口（默认 50061）
 *   demo.app  — 应用名（默认 demo08-provider）
 */
public class ProviderApp {

    public static void main(String[] args) throws Exception {
        System.setProperty("dubbo.network.interface.preferred", "lo0");

        ApplicationConfig application = new ApplicationConfig(System.getProperty("demo.app", "demo08-provider"));

        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setName("tri");
        protocol.setPort(Integer.parseInt(System.getProperty("demo.port", "50061")));

        RegistryConfig registry = new RegistryConfig();
        registry.setAddress("nacos://127.0.0.1:8848");

        ServiceConfig<GreetingService> service = new ServiceConfig<>();
        service.setApplication(application);
        service.setRegistry(registry);
        service.setProtocol(protocol);
        service.setInterface(GreetingService.class);
        service.setRef(new GreetingServiceImpl());

        System.out.println("=== [PROVIDER] exporting GreetingService via tri://127.0.0.1:" + protocol.getPort()
                + " (registry=nacos) ===");
        service.export();
        System.out.println("=== [PROVIDER] exported, waiting for orders ... ===");
        Thread.currentThread().join();
    }
}
