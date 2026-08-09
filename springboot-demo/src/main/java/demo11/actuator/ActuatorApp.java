package demo11.actuator;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * 运行时刻可观测性（04 篇 Level 5）：Actuator 端点实测
 *
 * 机制（Boot 3.3.5 + actuator）：
 *   - actuator 在 classpath 时自动暴露端点；默认只暴露 health（安全考虑）
 *   - management.endpoints.web.exposure.include 打开更多端点
 *   - health 探针组（readiness/liveness）需 management.endpoint.health.probes.enabled=true
 *     ——否则 /actuator/health/readiness 404（K8s 探针默认关闭，实测 404 → 开启后 200）
 *   - /actuator/health = K8s 探针语义入口（UP/DOWN + show-details 组件明细）
 *   - /actuator/conditions = 03 篇条件评估报告的运行时版本，JSON 结构：
 *     {"positiveMatches":{类名:{matched:[...],notMatched:[...]}},"negativeMatches":{...}}
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5，本机）：
 *   [端点] GET /actuator/health → HTTP 200 {"status":"UP",...,"groups":["liveness","readiness"]}
 *   [端点] GET /actuator/health/readiness → HTTP 200 {"status":"UP"}
 *   [端点] GET /actuator/health/liveness → HTTP 200 {"status":"UP"}
 *   [报告] conditions 里 DataSourceAutoConfiguration → NEGATIVE（有未满足条件）
 *   [报告] conditions 里 AopAutoConfiguration → POSITIVE（条件全通过，放行）
 */
public class ActuatorApp {

    @SpringBootApplication
    static class BootConfig {
    }

    public static void main(String[] args) throws Exception {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.setDefaultProperties(Map.of(
                "management.endpoints.web.exposure.include", "health,conditions",
                "management.endpoint.health.show-details", "always",
                "management.endpoint.health.probes.enabled", "true"));
        ConfigurableApplicationContext ctx = app.run();

        HttpClient client = HttpClient.newHttpClient();
        for (String path : new String[] { "/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness" }) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:8080" + path)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[端点] GET " + path + " → HTTP " + response.statusCode() + " " + response.body());
        }

        HttpRequest conditions = HttpRequest.newBuilder(URI.create("http://localhost:8080/actuator/conditions")).GET().build();
        String body = client.send(conditions, HttpResponse.BodyHandlers.ofString()).body();
        for (String key : new String[] { "DataSourceAutoConfiguration", "AopAutoConfiguration" }) {
            int i = body.indexOf("\"" + key + "\"");
            if (i < 0) {
                System.out.println("[报告] conditions 里 " + key + " → 未找到");
                continue;
            }
            int pos = body.lastIndexOf("positiveMatches", i);
            int neg = body.lastIndexOf("negativeMatches", i);
            System.out.println("[报告] conditions 里 " + key + " → "
                    + (pos > neg ? "POSITIVE（条件全通过，放行）" : "NEGATIVE（有未满足条件）"));
        }
        ctx.close();
    }
}
