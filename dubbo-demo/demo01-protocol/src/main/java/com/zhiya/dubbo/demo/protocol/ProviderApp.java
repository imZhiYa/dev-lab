package com.zhiya.dubbo.demo.protocol;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.RegistryConfig;
import com.zhiya.dubbo.demo.api.GreetingService;

/**
 * demo01 提供端：把同一个 GreetingService 以「两种信封（协议）× 两种盒子（序列化）」
 * 在四个端口独立导出，便于把信封与盒子两个变量拆开对比：
 *   - dubbo 协议 + Hessian2（默认）：dubbo://0.0.0.0:20882
 *   - dubbo 协议 + Kryo          ：dubbo://0.0.0.0:20883
 *   - triple 协议 + Hessian2（默认）：tri://0.0.0.0:50052
 *   - triple 协议 + Kryo          ：tri://0.0.0.0:50053
 * 配套：tech-knowledge-docs/docs/08-dubbo/dubbo-01-protocol.md（E01 实测）
 */
public class ProviderApp {

    private static ServiceConfig<GreetingService> export(String appName, String protocol, int port, String serialization) {
        ApplicationConfig application = new ApplicationConfig(appName);
        ServiceConfig<GreetingService> svc = new ServiceConfig<>();
        svc.setApplication(application);
        svc.setInterface(GreetingService.class);
        svc.setRef(new GreetingServiceImpl());
        ProtocolConfig proto = new ProtocolConfig();
        proto.setName(protocol);
        proto.setPort(port);
        if (serialization != null) {
            proto.setSerialization(serialization);
        }
        svc.setProtocol(proto);
        svc.setRegistry(new RegistryConfig("N/A"));
        svc.export();
        System.out.printf("=== [PROVIDER] exported GreetingService over %s://0.0.0.0:%d serialization=%s%n",
                protocol, port, serialization == null ? "hessian2(default)" : serialization);
        return svc;
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("dubbo.network.interface.preferred", "lo0");
        System.setProperty("qos.enable", "false");

        export("demo01-provider", "dubbo", 20882, null);
        export("demo01-provider", "dubbo", 20883, "kryo");
        export("demo01-provider", "tri", 50052, null);
        export("demo01-provider", "tri", 50053, "kryo");

        Thread.currentThread().join();
    }
}
