package com.zhiya.dubbo.demo.protocol;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.service.GenericService;

import java.util.HashMap;
import java.util.Map;

/**
 * E06：泛化调用（免编译期契约）demo。
 *
 * 经 GenericService.$invoke 以 Map 形参（class 键 + 字段）调用 GreetingService.greet，
 * 替代强类型 GreetingRequest——验证"Map → provider POJO"翻译链在 3.3.4 + Nacos 下走通。
 *
 * 方向性验证：只证明泛化路径可用，非 OOM 复现（OOM 机制见知识库 06 篇 issue 锚点）。
 *
 * 用法：-Dexec.args="-n 3 -i 200"（3 次、间隔 200ms）
 */
public class GenericCallApp {

    private static final long T0 = System.nanoTime();

    public static void main(String[] args) throws Exception {
        System.setProperty("dubbo.network.interface.preferred", "lo0");
        System.setProperty("qos.enable", "false");

        int iterations = 3;
        long intervalMs = 200;
        for (int i = 0; i < args.length; i++) {
            if ("-n".equals(args[i])) iterations = Integer.parseInt(args[++i]);
            if ("-i".equals(args[i])) intervalMs = Long.parseLong(args[++i]);
        }

        ApplicationConfig application = new ApplicationConfig("demo04-generic-consumer");

        RegistryConfig registry = new RegistryConfig();
        registry.setAddress("nacos://127.0.0.1:8848");

        String iface = "com.zhiya.dubbo.demo.api.GreetingService";
        ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
        reference.setApplication(application);
        reference.setRegistry(registry);
        reference.setInterface(iface);
        reference.setGeneric("true");

        System.out.println(ts() + " [GENERIC] building " + iface + " via GenericService ...");
        GenericService service = reference.get();

        int ok = 0, fail = 0;
        for (int i = 1; i <= iterations; i++) {
            try {
                Map<String, Object> param = new HashMap<>();
                param.put("class", "com.zhiya.dubbo.demo.api.GreetingRequest");
                param.put("name", "generic-" + i);
                param.put("sequence", i);
                Object result = service.$invoke("greet",
                        new String[]{"com.zhiya.dubbo.demo.api.GreetingRequest"},
                        new Object[]{param});
                System.out.println(ts() + " [GENERIC] #" + i + " -> " + result);
                ok++;
            } catch (Throwable t) {
                System.out.println(ts() + " [GENERIC] #" + i + " FAIL: " + t);
                fail++;
            }
            if (i < iterations) Thread.sleep(intervalMs);
        }
        System.out.println(ts() + " [GENERIC] done ok=" + ok + " fail=" + fail);
        reference.destroy();
    }

    private static String ts() {
        return String.format("[%6dms]", (System.nanoTime() - T0) / 1_000_000);
    }
}