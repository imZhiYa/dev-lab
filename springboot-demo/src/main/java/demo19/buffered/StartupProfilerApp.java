package demo19.buffered;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 启动慢排查 · 方法 1-3 端到端实测（07 篇 7.2 指纹 2）。
 *
 * 链路（四条方法的前三条）：
 *   1. 引入 actuator 依赖 + 暴露 startup 端点（management.endpoints.web.exposure.include=startup）
 *   2. 启动类注册 BufferingApplicationStartup
 *   3. 启动完成后 HTTP GET /actuator/startup 获取每步耗时快照
 *   4. （JFR 深度剖析见 JfrStartupApp）
 *
 * 版本实证（Boot 3.3.5 + spring-core 6.1.14，jar 实证）：
 *   - BufferingApplicationStartup 全限定名是
 *     org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup（在 spring-boot jar），
 *     不是 org.springframework.core.metrics（spring-core 6.1.14 的 metrics 包只有
 *     DefaultApplicationStartup/ApplicationStartup/StartupStep + jfr 实现，无 buffering 实现）
 *   - StartupEndpoint 在 spring-boot-actuator-3.3.5.jar，构造器只接受 BufferingApplicationStartup
 *
 * 构造慢 bean：SlowBean 构造 sleep 2500ms，模拟连接池/RPC 客户端等重初始化
 * （启动步骤树里应看到 spring.context.bean-creation 步骤的 duration 大头）
 */
@SpringBootApplication
public class StartupProfilerApp {

    public static void main(String[] args) {
        long t0 = System.currentTimeMillis();
        SpringApplication app = new SpringApplication(StartupProfilerApp.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setApplicationStartup(new BufferingApplicationStartup(4096));
        app.setDefaultProperties(java.util.Map.of(
                "management.endpoints.web.exposure.include", "startup",
                "server.port", "18090"));
        app.run(args);
        System.out.println("[总耗时] 启动完成: " + (System.currentTimeMillis() - t0) + " ms");
    }

    @Bean
    ApplicationRunner profilerRunner(org.springframework.context.ConfigurableApplicationContext ctx) throws Exception {
        return args -> {
            // 方法 3：访问端点获取耗时快照
            int port = ((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) ctx)
                    .getWebServer().getPort();
            String url = "http://127.0.0.1:" + port + "/actuator/startup";
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.println("[端点] GET " + url + " -> HTTP " + resp.statusCode());
            System.out.println("[端点] 原始响应（前 800 字符）: " + resp.body().substring(0, Math.min(800, resp.body().length())));

            // 方法 3 输出：按耗时排序的 Top 步骤
            // 真实结构（3.3.5）：{"springBootVersion":"3.3.5","timeline":{"startTime":...,"events":[
            //   {"startupStep":{"name":"spring.boot.application.starting","id":0,"tags":[...]},
            //    "startTime":...,"endTime":...,"duration":"PT0.026322S"}]}}
            JsonNode root = new ObjectMapper().readTree(resp.body());
            List<JsonNode> all = new ArrayList<>();
            root.path("timeline").path("events").forEach(all::add);
            System.out.println("[端点] 总步骤数: " + all.size());
            Comparator<JsonNode> byDuration = (a, b) -> Long.compare(
                    java.time.Duration.parse(b.path("duration").asText()).toMillis(),
                    java.time.Duration.parse(a.path("duration").asText()).toMillis());
            all.sort(byDuration);
            System.out.println("[端点] 按耗时排序 Top 15 步骤（duration 单位毫秒）：");
            for (int i = 0; i < Math.min(15, all.size()); i++) {
                JsonNode s = all.get(i);
                JsonNode step = s.path("startupStep");
                System.out.printf("  %-45s %6d ms   %s%n",
                        step.path("name").asText(),
                        java.time.Duration.parse(s.path("duration").asText()).toMillis(),
                        step.path("tags").toString().length() > 90
                                ? step.path("tags").toString().substring(0, 90) + "..."
                                : step.path("tags").toString());
            }
            // 慢 bean 定位
            System.out.println("[端点] 慢 bean 步骤（beanName 含 SlowBean）：");
            for (JsonNode s : all) {
                String tags = s.path("startupStep").path("tags").toString();
                if (tags.contains("SlowBean") || tags.contains("slowBean")) {
                    System.out.printf("  %-45s %6d ms   %s%n",
                            s.path("startupStep").path("name").asText(),
                            java.time.Duration.parse(s.path("duration").asText()).toMillis(),
                            tags);
                }
            }
            // web server 非 daemon 线程，跑完即退（demo 自包含）
            System.exit(0);
        };
    }

    /** 正常 bean */
    @Component
    static class FastBean {
    }

    /** 慢 bean：构造 sleep 2.5s，模拟重初始化（连接池/RPC 客户端/线程池） */
    @Component
    static class SlowBean {
        SlowBean() {
            long t0 = System.currentTimeMillis();
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("[SlowBean] 构造完成，耗时 " + (System.currentTimeMillis() - t0) + " ms（模拟连接池初始化）");
        }
    }
}
