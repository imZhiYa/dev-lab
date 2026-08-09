package demo11.webflux;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * 运行时刻（04 篇 Level 4 补充）：WebFlux（REACTIVE 栈）请求链路实测
 *
 * 机制（Boot 3.3.5）：
 *   - WebApplicationType 探测：classpath 有 reactive DispatcherHandler（spring-webflux）
 *     且 无 DispatcherServlet（spring-webmvc）→ REACTIVE；两者都有 → SERVLET（MVC 优先）
 *   - REACTIVE 下：上下文类 = AnnotationConfigReactiveWebServerApplicationContext，
 *     容器 = Netty（Reactor Netty，非 Tomcat），端口同样默认 8080
 *   - 链路：Netty EventLoop 线程 → WebFilter 链 → DispatcherHandler → HandlerMapping
 *     → Controller → 序列化 → 响应（全程非阻塞）
 *   - WebServerInitializedEvent 是 Servlet/Reactive 两个版本的父类，两种栈都能监听
 *   - 映射路径用 /flux 而非 /hello：RunTraceApp 主类在 demo11 顶层包，组件扫描覆盖
 *     整个 demo11 子树，同路径 Controller 会 Ambiguous mapping（坑 2 变体实证）
 *
 * 双跑法（同代码、不同 classpath，实测 classpath 决定请求栈）：
 *   1) 全 lib（含 spring-webmvc + tomcat）→ SERVLET 分支：
 *      java -cp "out:$(find lib -name '*.jar' | tr '\n' ':')" demo11.webflux.WebFluxApp
 *   2) 去掉 spring-webmvc + tomcat-embed-core → REACTIVE 分支：
 *      java -cp "out:$(find lib -name '*.jar' ! -name 'spring-webmvc*' ! -name 'tomcat-embed*' | tr '\n' ':')" demo11.webflux.WebFluxApp
 *
 * 真实输出 1（全 lib，SERVLET 分支，JDK 21.0.11 + spring-boot 3.3.5）：
 *   [事件] WebServerInitializedEvent（TomcatWebServer，端口 8080）
 *   [事件] ContextRefreshedEvent
 *   [探测] 上下文类 = org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext
 *   [请求] Controller 执行：hello("flux")（http-nio-8080-exec-2）
 *   [响应] HTTP 200 body={"message":"hello flux","thread":"http-nio-8080-exec-2"}
 *   （注意：MVC 优先——webmvc 在 classpath 时 WebFlux 代码跑在 Servlet 栈，
 *     线程名 http-nio-8080-exec-* 是 Tomcat 连接器线程池；WebFilter 不生效——无打点）
 *
 * 真实输出 2（无 webmvc，REACTIVE 分支）：
 *   [事件] WebServerInitializedEvent（NettyWebServer，端口 8080）
 *   [事件] ContextRefreshedEvent
 *   [探测] 上下文类 = org.springframework.boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext
 *   [请求] WebFilter 链进入（reactor-http-nio-2）
 *   [请求] Controller 执行：hello("flux")（reactor-http-nio-2）
 *   [请求] WebFilter 链返回（reactor-http-nio-2）
 *   [响应] HTTP 200 body={"thread":"reactor-http-nio-2","message":"hello flux"}
 *   （线程名 reactor-http-nio-* = Netty EventLoop 线程，非阻塞模型；
 *     与 Servlet 栈的 http-nio-8080-exec-* 阻塞线程池形成对比）
 */
public class WebFluxApp {

    @SpringBootApplication
    static class BootConfig {
    }

    @RestController
    static class HelloController {

        @GetMapping("/flux")
        public Map<String, String> hello(@RequestParam("name") String name) {
            System.out.println("[请求] Controller 执行：hello(\"" + name + "\")"
                    + "（" + Thread.currentThread().getName() + "）");
            return Map.of("message", "hello " + name,
                    "thread", Thread.currentThread().getName());
        }
    }

    @Component
    static class TraceFilter implements WebFilter {

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            System.out.println("[请求] WebFilter 链进入（" + Thread.currentThread().getName() + "）");
            return chain.filter(exchange).doFinally(signal -> System.out
                    .println("[请求] WebFilter 链返回（" + Thread.currentThread().getName() + "）"));
        }
    }

    @Component
    static class StartupListener implements ApplicationListener<WebServerInitializedEvent> {
        @Override
        public void onApplicationEvent(WebServerInitializedEvent event) {
            System.out.println("[事件] WebServerInitializedEvent（"
                    + event.getWebServer().getClass().getSimpleName() + "，端口 " + event.getWebServer().getPort() + "）");
        }
    }

    @Component
    static class RefreshListener {
        @EventListener
        public void onRefresh(ContextRefreshedEvent event) {
            System.out.println("[事件] ContextRefreshedEvent");
        }
    }

    public static void main(String[] args) throws Exception {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.INFO);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();
        System.out.println("[探测] 上下文类 = " + ctx.getClass().getName());

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:8080/flux?name=flux")).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("[响应] HTTP " + response.statusCode() + " body=" + response.body());
        ctx.close();
    }
}
