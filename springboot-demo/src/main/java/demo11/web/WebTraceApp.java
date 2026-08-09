package demo11.web;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * 请求链路（04 篇 Level 4）：内嵌 Tomcat + DispatcherServlet 全链路打点
 *
 * 机制（Boot 3.3.5 + Tomcat 10.1.31）：
 *   - WebApplicationType 由 classpath 决定（spring-web + tomcat 存在 → SERVLET）
 *   - context 类 = AnnotationConfigServletWebServerApplicationContext
 *   - refresh 第 9 步 onRefresh → createWebServer → Tomcat 连接器绑定端口
 *   - 请求链路：Tomcat connector 线程（http-nio-8080-exec-N）→ Filter 链
 *     → DispatcherServlet → HandlerMapping 定位 → Interceptor.preHandle
 *     → HandlerAdapter 调用 Controller → 返回值经 Jackson（HttpMessageConverter）
 *     序列化 → postHandle → afterCompletion → Filter 返回
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5 + tomcat 10.1.31，本机）：
 *   [MAIN] main 入口
 *   [监听器] WebServer 启动完成：端口=8080
 *   [M2] run() 返回
 *   [请求] Filter.doFilter 进入
 *   [请求] Interceptor.preHandle
 *   [请求] Controller 执行：hello("world") → 返回 Map
 *   [请求] Interceptor.postHandle（响应即将生成）
 *   [请求] Interceptor.afterCompletion（响应已完成）
 *   [请求] Filter.doFilter 返回（响应已生成）
 *   [响应] HTTP 200 body={"message":"hello world","thread":"http-nio-8080-exec-1"}
 *
 * 注：响应体为 Map.of 序列化，HashMap 键序不保证，两次运行键序可能互换；
 * 内容与"请求跑在连接器线程 http-nio-8080-exec-1"的结论不受影响。
 */
public class WebTraceApp {

    @SpringBootApplication
    static class BootConfig {
    }

    @RestController
    static class HelloController {
        @GetMapping("/hello")
        public Map<String, Object> hello(@RequestParam(value = "name", defaultValue = "world") String name) {
            System.out.println("[请求] Controller 执行：hello(\"" + name + "\") → 返回 Map");
            return Map.of("message", "hello " + name,
                    "thread", Thread.currentThread().getName());
        }
    }

    @Component
    static class TraceFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            System.out.println("[请求] Filter.doFilter 进入");
            filterChain.doFilter(request, response);
            System.out.println("[请求] Filter.doFilter 返回（响应已生成）");
        }
    }

    @Component
    static class TraceInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            System.out.println("[请求] Interceptor.preHandle");
            return true;
        }

        @Override
        public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                org.springframework.web.servlet.ModelAndView modelAndView) {
            System.out.println("[请求] Interceptor.postHandle（响应即将生成）");
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                Exception ex) {
            System.out.println("[请求] Interceptor.afterCompletion（响应已完成）");
        }
    }

    @Configuration
    static class MvcConfig implements WebMvcConfigurer {
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new TraceInterceptor());
        }
    }

    public static void main(String[] args) throws Exception {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.addListeners((ApplicationListener<WebServerInitializedEvent>) event ->
                System.out.println("[监听器] WebServer 启动完成：端口=" + event.getWebServer().getPort()));
        ConfigurableApplicationContext ctx = app.run();
        System.out.println("[M2] run() 返回");

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:8080/hello")).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("[响应] HTTP " + response.statusCode() + " body=" + response.body());
        ctx.close();
    }
}
