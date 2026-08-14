package com.zhiya.dubbo.demo.callchain;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import com.zhiya.dubbo.demo.api.GreetingService;

/**
 * demo00 提供端：中央厨房（Provider）经 triple（HTTP/2）导出 GreetingService，
 * 无注册中心（直连模式）。
 * 配套：tech-knowledge-docs/docs/08-dubbo/dubbo-00-backbone.md（E00 实测）
 */
public class ProviderApp {

    public static void main(String[] args) throws Exception {
        // 强制 Dubbo 优先环回网卡而非虚拟网卡（如代理软件创建的 utun），
        // 见 NetUtils.isPreferredNetworkInterface。
        System.setProperty("dubbo.network.interface.preferred", "lo0");

        ApplicationConfig application = new ApplicationConfig("demo00-provider");

        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setName("tri");
        protocol.setPort(50051);

        RegistryConfig registry = new RegistryConfig();
        registry.setAddress(RegistryConfig.NO_AVAILABLE);

        ServiceConfig<GreetingService> service = new ServiceConfig<>();
        service.setApplication(application);
        service.setRegistry(registry);
        service.setProtocol(protocol);
        service.setInterface(GreetingService.class);
        service.setRef(new GreetingServiceImpl());

        System.out.println("=== [PROVIDER] exporting GreetingService via triple://127.0.0.1:50051 ===");
        service.export();
        System.out.println("=== [PROVIDER] exported, waiting for orders ... ===");

        Thread.currentThread().join();
    }
}
