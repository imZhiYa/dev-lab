package com.zhiya.dubbo.demo.threadpool;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import com.zhiya.dubbo.demo.api.GreetingService;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * E07 提供端：triple 直连导出 GreetingService（无注册中心）。
 *
 * 系统属性：
 *   demo.dispatcher  — 分发模式（默认 Dubbo 默认值 "all"；E07-3 设 "direct" 验证 triple 是否读取）
 *   demo.sleep.ms    — 每次调用业务睡眠时长（默认 0；E07-2 饱和实验设 3000）
 *   demo.threads     — 覆盖业务线程数（默认 Dubbo 默认 200）
 *   demo.queues      — 覆盖队列容量（默认 Dubbo 默认 0 => SynchronousQueue）
 *   demo.thread.dump — "true" 时每 2s 打印线程普查（Netty IO vs DubboServerHandler）
 * 配套：tech-knowledge-docs/docs/08-dubbo/dubbo-07-threadmodel-quality.md（E07 实测）
 */
public class ProviderApp {

    public static void main(String[] args) throws Exception {
        System.setProperty("dubbo.network.interface.preferred", "lo0");

        ApplicationConfig application = new ApplicationConfig("demo07-provider");

        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setName("tri");
        protocol.setPort(50051);
        String dispatcher = System.getProperty("demo.dispatcher");
        if (dispatcher != null) {
            protocol.setDispatcher(dispatcher);
        }
        String threads = System.getProperty("demo.threads");
        if (threads != null) {
            protocol.setThreads(Integer.parseInt(threads));
        }
        String queues = System.getProperty("demo.queues");
        if (queues != null) {
            protocol.setQueues(Integer.parseInt(queues));
        }

        RegistryConfig registry = new RegistryConfig();
        registry.setAddress(RegistryConfig.NO_AVAILABLE);

        ServiceConfig<GreetingService> service = new ServiceConfig<>();
        service.setApplication(application);
        service.setRegistry(registry);
        service.setProtocol(protocol);
        service.setInterface(GreetingService.class);
        service.setRef(new GreetingServiceImpl());

        System.out.println("=== [PROVIDER] exporting GreetingService via triple://127.0.0.1:50051 "
                + "(dispatcher=" + (dispatcher == null ? "<default>" : dispatcher)
                + ", sleepMs=" + System.getProperty("demo.sleep.ms", "0")
                + ", threads=" + (threads == null ? "<default>" : threads)
                + ", queues=" + (queues == null ? "<default>" : queues) + ") ===");
        service.export();
        System.out.println("=== [PROVIDER] exported, waiting for orders ... ===");

        if ("true".equals(System.getProperty("demo.thread.dump", "false"))) {
            startThreadCensus();
        }

        Thread.currentThread().join();
    }

    /** Periodically prints a census of relevant thread groups (prefix-grouped counts). */
    private static void startThreadCensus() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Map<String, AtomicInteger> census = new TreeMap<>();
                    int nettyIo = 0;
                    int dubboServerHandler = 0;
                    int qos = 0;
                    for (Thread th : Thread.getAllStackTraces().keySet()) {
                        String n = th.getName();
                        if (n.contains("Netty") || n.contains("nioEventLoopGroup") || n.contains("epollEventLoopGroup")) {
                            nettyIo++;
                        } else if (n.startsWith("DubboServerHandler")) {
                            dubboServerHandler++;
                        } else if (n.toLowerCase().contains("qos")) {
                            qos++;
                        }
                        census.computeIfAbsent(prefix(n), k -> new AtomicInteger()).incrementAndGet();
                    }
                    System.out.println("[CENSUS] NettyIo=" + nettyIo
                            + " DubboServerHandler=" + dubboServerHandler
                            + " qos=" + qos
                            + " total=" + Thread.getAllStackTraces().size());
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "thread-census");
        t.setDaemon(true);
        t.start();
    }

    private static String prefix(String name) {
        int idx = name.indexOf('-');
        return idx > 0 ? name.substring(0, idx) : name;
    }
}
