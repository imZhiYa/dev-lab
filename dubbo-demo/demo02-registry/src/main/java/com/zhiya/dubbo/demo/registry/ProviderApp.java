package com.zhiya.dubbo.demo.registry;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import com.zhiya.dubbo.demo.api.GreetingService;
import com.zhiya.dubbo.demo.api.OrderService;

import java.util.List;

/**
 * demo02 提供端：向 Nacos 导出 2 个服务（GreetingService + OrderService），
 * 并打印实际登记进注册中心的数据。
 *
 * register-mode 由 -Ddubbo.application.register-mode=all|interface|instance 驱动，
 * 同一份二进制跑完 E03 三轮实验（接口级/应用级/双轨并存）。
 */
public class ProviderApp {

    public static void main(String[] args) throws Exception {
        System.setProperty("dubbo.network.interface.preferred", "lo0");
        System.setProperty("qos.enable", "false");

        String registerMode = System.getProperty("dubbo.application.register-mode", "instance");
        int port = Integer.parseInt(System.getProperty("dubbo.protocol.port", "50052"));
        System.out.println("=== [PROVIDER] register-mode = " + registerMode + ", port = " + port);

        ApplicationConfig application = new ApplicationConfig("demo02-provider");
        // 显式设置 register-mode，不依赖系统属性隐式读取（Dubbo 配置框架会自动读
        // dubbo.application.* 系统属性，但显式声明让行为可读、可审计）
        application.setRegisterMode(registerMode);

        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setName("tri");
        protocol.setPort(port);
        protocol.setHost("127.0.0.1");

        RegistryConfig registry = new RegistryConfig();
        registry.setAddress("nacos://127.0.0.1:8848");

        ServiceConfig<GreetingService> greeting = new ServiceConfig<>();
        greeting.setApplication(application);
        greeting.setRegistry(registry);
        greeting.setProtocol(protocol);
        greeting.setInterface(GreetingService.class);
        greeting.setRef(new GreetingServiceImpl());

        ServiceConfig<OrderService> order = new ServiceConfig<>();
        order.setApplication(application);
        order.setRegistry(registry);
        order.setProtocol(protocol);
        order.setInterface(OrderService.class);
        order.setRef(new OrderServiceImpl());

        System.out.println("=== [PROVIDER] exporting 2 services ...");
        greeting.export();
        order.export();

        System.out.println("=== [PROVIDER] GreetingService registered URLs:");
        printUrls(greeting.getExportedUrls());
        System.out.println("=== [PROVIDER] OrderService registered URLs:");
        printUrls(order.getExportedUrls());

        System.out.println("=== [PROVIDER] exported, waiting for orders ...");
        Thread.currentThread().join();
    }

    private static void printUrls(List<org.apache.dubbo.common.URL> urls) {
        for (org.apache.dubbo.common.URL url : urls) {
            System.out.println("    " + url.getProtocol() + "://" + url.getHost() + ":" + url.getPort()
                    + "/" + url.getPath() + "?register-mode=" + url.getParameter("register-mode", "n/a"));
        }
    }
}
