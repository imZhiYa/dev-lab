package com.zhiya.dubbo.demo.spi.consumer;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import com.zhiya.dubbo.demo.api.GreetingRequest;
import com.zhiya.dubbo.demo.api.GreetingService;

/**
 * E10：注册中心路径（Cluster 层）的 Mock 降级。
 *
 * 场景（全部经 Nacos 订阅；A-E 轮 provider 未启动）：
 *  A. mock=true                        -> 失败 -> 需接口同名 XxxMock 类（未提供时报 mock error）
 *  B. mock=force:return null           -> 不碰 provider，直接返回 null
 *  C. mock=force:throw RuntimeException -> 直接抛降级异常
 *  D. mock=fail:return null            -> 无 provider -> 降级 null
 *  E. control（无 mock）                -> 无 provider -> 抛异常
 *  F. mock=fail:return null，provider 在线 -> 走真实调用成功
 *  G. control，provider 在线            -> 真实调用成功
 * 边界：mock 是 Cluster 层特性，setUrl 直连返回裸 TripleInvoker、mock 参数无人解析（E10 实测）。
 */
public class MockApp {

    static final String REG = "nacos://127.0.0.1:8848";

    static void call(String tag, boolean mock, String mockVal) throws Exception {
        ReferenceConfig<GreetingService> ref = new ReferenceConfig<>();
        ref.setApplication(new ApplicationConfig("demo10-mock"));
        ref.setRegistry(new RegistryConfig(REG));
        ref.setInterface(GreetingService.class);
        ref.setProtocol("tri");
        ref.setTimeout(3000);
        ref.setCheck(false);
        if (mock) {
            ref.setMock(mockVal);
        }
        try {
            GreetingService svc = ref.get();
            Thread.sleep(4000);
            String r = svc.greet(new GreetingRequest("MockTest", 0));
            System.out.println("=== [E10] " + tag + " -> returned: " + r);
        } catch (Exception e) {
            System.out.println("=== [E10] " + tag + " -> exception: " + e.getClass().getSimpleName() + " : "
                    + String.valueOf(e.getMessage()).replaceAll("\\s+", " ").substring(0, Math.min(110, String.valueOf(e.getMessage()).length())));
        } finally {
            ref.destroy();
        }
    }

    public static void main(String[] args) throws Exception {
        call("A.mock=true", true, "true");
        call("B.force:return null", true, "force:return null");
        call("C.force:throw", true, "force:throw java.lang.RuntimeException");
        call("D.fail:return null", true, "fail:return null");
        call("E.control-no-mock", false, null);
        Thread.sleep(2000);
        System.out.println("=== [E10] DONE (A-E, no provider) ===");
    }
}
