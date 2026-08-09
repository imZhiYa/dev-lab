package demo11;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * 运行时刻（04 篇 Level 2）：SpringApplication.run 全流程阶段打点
 *
 * 机制（Boot 3.3.5，javap 反编译 run(String[]) 调用序列）：
 *   run() 的步骤（字节码顺序实证，3.3.5）：
 *     Startup 计时 → shutdownHook.enableShutdownHookAddition（开钩子开关，钩子本体
 *     是全局单例 SpringApplicationShutdownHook）→ configureHeadlessProperty
 *     → getRunListeners → listeners.starting
 *     → prepareEnvironment（配置加载）→ printBanner → createApplicationContext
 *     → setApplicationStartup → prepareContext（initializers/contextPrepared/
 *       bootstrapContext.close/循环引用开关/KeepAlive 保活）
 *     → refreshContext（AbstractApplicationContext.refresh 12 步）
 *     → afterRefresh + logStarted → listeners.started → callRunners → listeners.ready
 *   refresh() 的关键步骤（反编译 6.1.14）：prepareRefresh → obtainFreshBeanFactory
 *     → prepareBeanFactory → postProcessBeanFactory → invokeBeanFactoryPostProcessors
 *     → registerBeanPostProcessors → initMessageSource → initApplicationEventMulticaster
 *     → onRefresh（Web 场景 = 内嵌容器启动点）→ registerListeners
 *     → finishBeanFactoryInitialization（单例实例化）→ finishRefresh（ContextRefreshedEvent）
 *
 * 可观测标记（与步骤的对应）：
 *   Initializer   → prepareContext 的 applyInitializers
 *   BFPP 执行     → refresh 的 invokeBeanFactoryPostProcessors（含自动配置注册）
 *   BPP 注册后     → registerBeanPostProcessors 之后
 *   ContextRefreshedEvent → finishRefresh
 *   Runner         → callRunners
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5，本机）：
 *   [事件] ApplicationStartingEvent
 *   [事件] ApplicationEnvironmentPreparedEvent
 *   [阶段] prepareContext：Initializer 执行（applyInitializers）
 *   [事件] ApplicationContextInitializedEvent
 *   [事件] ApplicationPreparedEvent
 *   [阶段] refresh 第 5 步 invokeBeanFactoryPostProcessors：BFPP 执行（此时已注册 Bean 定义数 = 314）
 *   [阶段] registerBeanPostProcessors 之后，第一个经过 BPP 的 bean =
 *          org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryConfiguration$EmbeddedTomcat
 *          ← onRefresh（第 9 步）触发 Web 服务器创建，强制实例化工厂单例
 *   [事件] ServletWebServerInitializedEvent
 *   [事件] ContextRefreshedEvent（finishRefresh 发布）
 *   [事件] ApplicationStartedEvent
 *   [Runner] ApplicationRunner 执行（callRunners）
 *   [事件] ApplicationReadyEvent
 *   [阶段] run() 返回——上下文类 = AnnotationConfigServletWebServerApplicationContext（SERVLET）
 *   [事件] AvailabilityChangeEvent（ctx.close() 关闭流程触发）
 *   [事件] ContextClosedEvent
 *
 * 注：Bean 定义数随 classpath 变化：22 jar（含 actuator）时 280，37 jar（加 webflux/netty）
 * 后 314。任何固定数字都只是本机快照，机制不变。
 */
public class RunTraceApp {

    @SpringBootApplication
    static class BootConfig {
    }

    @Configuration
    static class TraceConfig {

        @Component
        static class TraceBfpp implements org.springframework.beans.factory.config.BeanFactoryPostProcessor {
            @Override
            public void postProcessBeanFactory(
                    org.springframework.beans.factory.config.ConfigurableListableBeanFactory bf) {
                System.out.println("[阶段] refresh 第 5 步 invokeBeanFactoryPostProcessors：BFPP 执行"
                        + "（此时已注册 Bean 定义数 = " + bf.getBeanDefinitionCount() + "）");
            }
        }

        @Component
        static class TraceBpp implements org.springframework.beans.factory.config.BeanPostProcessor {
            private boolean printed;

            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (!printed) {
                    printed = true;
                    System.out.println("[阶段] registerBeanPostProcessors 之后，第一个经过 BPP 的 bean = "
                            + beanName + "（" + bean.getClass().getSimpleName() + "）");
                }
                return bean;
            }
        }

        @Bean
        static ApplicationRunner traceRunner() {
            return args -> System.out.println("[Runner] ApplicationRunner 执行（callRunners）");
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.addInitializers(ctx ->
                System.out.println("[阶段] prepareContext：Initializer 执行（applyInitializers）"));
        app.addListeners((ApplicationListener<ApplicationEvent>) event -> {
            String name = event.getClass().getSimpleName();
            if (event instanceof ContextRefreshedEvent) {
                System.out.println("[事件] ContextRefreshedEvent（refresh 第 12 步 finishRefresh 发布）");
            } else {
                System.out.println("[事件] " + name);
            }
        });

        ConfigurableApplicationContext ctx = app.run();
        System.out.println("[阶段] run() 返回——上下文类 = " + ctx.getClass().getSimpleName()
                + "（classpath 有 spring-web/tomcat → WebApplicationType=SERVLET）");
        System.out.println("[运行] run() 返回——上下文已就绪");
        ctx.close();
    }
}
