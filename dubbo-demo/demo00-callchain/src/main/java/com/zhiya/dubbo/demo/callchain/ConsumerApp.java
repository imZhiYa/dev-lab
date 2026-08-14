package com.zhiya.dubbo.demo.callchain;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import com.zhiya.dubbo.demo.api.GreetingRequest;
import com.zhiya.dubbo.demo.api.GreetingService;

/**
 * demo00 消费端：顾客（Consumer）通过透明代理下一单（订单 O），
 * 经 triple（HTTP/2）直连后厨（Provider），无注册中心。
 * 配套：tech-knowledge-docs/docs/08-dubbo/dubbo-00-backbone.md（E00 实测）
 */
public class ConsumerApp {

    public static void main(String[] args) throws Exception {
        // 与 Provider 侧一致的网卡偏好：优先环回接口，避开虚拟网卡
        System.setProperty("dubbo.network.interface.preferred", "lo0");
        // QoS 默认绑定 localhost:22222；本机同跑 consumer/provider 会端口冲突，关闭
        System.setProperty("qos.enable", "false");

        ApplicationConfig application = new ApplicationConfig("demo00-consumer");

        ReferenceConfig<GreetingService> reference = new ReferenceConfig<>();
        reference.setApplication(application);
        reference.setInterface(GreetingService.class);
        // 直连 URL；需要时可经 -Dprovider.host=... 覆盖 host：
        // Dubbo 会把 localhost/127.0.0.1 重写为本地主机地址——机器上装有代理软件
        // （存在 TUN 虚拟网卡）时，重写后变成 TUN 地址、流量被代理劫持；
        // 显式指定物理网卡 IP 可绕开该重写/劫持。
        String host = System.getProperty("provider.host", "127.0.0.1");
        reference.setUrl("tri://" + host + ":50051/com.zhiya.dubbo.demo.api.GreetingService");
        reference.setRetries(0);

        System.out.println("=== [CONSUMER] building reference ...");
        GreetingService service = reference.get();
        System.out.println("=== [CONSUMER] proxy class = " + service.getClass().getName());

        GreetingRequest order = new GreetingRequest("O", 1);
        System.out.println("=== [CONSUMER] customer places order O: greet(" + order + ")");

        long start = System.nanoTime();
        String response = service.greet(order);
        long costUs = (System.nanoTime() - start) / 1000;
        System.out.println("=== [CONSUMER] order done, response = " + response
                + ", round trip = " + costUs + " us");

        reference.destroy();
        System.exit(0);
    }
}
