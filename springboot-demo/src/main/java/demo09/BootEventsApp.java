package demo09;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Boot 启动事件全景（02 篇 Level 4）：SpringApplication.run 依次广播哪些事件
 *
 * 机制（Boot 3.3.5，SpringApplicationRunListeners + EventPublishingRunListener）：
 *   run() 阶段（context 创建/刷新前）通过 run listeners 广播 4 个事件：
 *     ApplicationStartingEvent → ApplicationEnvironmentPreparedEvent
 *     → ApplicationContextInitializedEvent → ApplicationPreparedEvent
 *   这 4 个事件发生时容器还不存在——@Bean 监听器当然收不到（早期事件因果）
 *   refresh 完成后：ApplicationStartedEvent → ApplicationReadyEvent
 *   关闭时：ContextClosedEvent（容器发布）
 *  app.addListeners() 注册的监听器由 EventPublishingRunListener 转发收到全部 run 事件
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5）：
 *   [事件] ApplicationStartingEvent
 *   [事件] ApplicationEnvironmentPreparedEvent
 *   [事件] ApplicationContextInitializedEvent
 *   [事件] ApplicationPreparedEvent
 *   [事件] ContextRefreshedEvent
 *   [事件] ApplicationStartedEvent
 *   [事件] AvailabilityChangeEvent(CORRECT)
 *   [Runner] ApplicationRunner 执行
 *   [事件] ApplicationReadyEvent
 *   [事件] AvailabilityChangeEvent(ACCEPTING_TRAFFIC)
 *   [运行] run() 返回——全部启动事件已广播完毕
 *   [事件] ContextClosedEvent
 */
public class BootEventsApp {

    @SpringBootApplication
    static class BootConfig {
    }

    @org.springframework.stereotype.Component
    static class Runner implements org.springframework.boot.ApplicationRunner {
        @Override
        public void run(org.springframework.boot.ApplicationArguments args) {
            System.out.println("[Runner] ApplicationRunner 执行");
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.addListeners(event -> {
            String name = event.getClass().getSimpleName();
            if (event instanceof org.springframework.boot.availability.AvailabilityChangeEvent<?> ace) {
                name += "(" + ace.getPayload() + ")";
            }
            System.out.println("[事件] " + name);
        });

        ConfigurableApplicationContext ctx = app.run();
        System.out.println("[运行] run() 返回——全部启动事件已广播完毕");
        ctx.close();
    }
}
