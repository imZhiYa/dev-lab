package demo06;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

/**
 * 自动装配链的最小 Boot 工程实证（spring-boot 3.3.5，无 web）：
 * 1. imports 白名单发现：自动配置类 DemoAutoConfiguration 在扫描包之外，只靠
 *    META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 加载；
 * 2. 条件评估：demo.enabled=true 注册 demoFlag；--demo.enabled=false（命令行参数优先级最高）
 *    → 条件否决 → bean 不存在；
 * 3. config data：application.properties 被 ConfigData 加载进 Environment
 *    （propertySources 里出现 applicationConfig: [classpath:/application.properties]）；
 * 4. @ConfigurationProperties 绑定：Boot 的 ConfigurationPropertiesBindingPostProcessor 自动绑定。
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5）：
 *   [imports 加载] 自动配置类注册的 bean demoFlag 存在? true, 值=auto-config-ok
 *   [config data] application.properties 已加载进 Environment: true
 *   [自动绑定] app.order.callback-url=https://pay.example.com/cb, maxRetries=3
 *   [条件否决] --demo.enabled=false 时 demoFlag 不存在? true
 */
@SpringBootApplication
public class MinimalBootApp {

    @Component
    @ConfigurationProperties(prefix = "app.order")
    public static class OrderProps {
        private String callbackUrl;
        private int maxRetries;

        public String getCallbackUrl() { return callbackUrl; }
        public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }

    @Bean
    String appName() {
        return "minimal-boot-app";
    }

    public static void main(String[] args) {
        // 场景 1：读 application.properties（demo.enabled=true）
        try (ConfigurableApplicationContext ctx = SpringApplication.run(MinimalBootApp.class, args)) {
            boolean flag = ctx.containsBean("demoFlag");
            System.out.println("[imports 加载] 自动配置类注册的 bean demoFlag 存在? "
                    + flag + (flag ? ", 值=" + ctx.getBean("demoFlag") : ""));

            boolean configData = false;
            for (PropertySource<?> ps : ctx.getEnvironment().getPropertySources()) {
                if (ps.getName().contains("application.properties")
                        || ps.getName().contains("applicationConfig")) {
                    configData = true;
                    break;
                }
            }
            System.out.println("[config data] application.properties 已加载进 Environment: " + configData
                    + "（ConfigData 把文件变成 PropertySource 塞进列表）");

            OrderProps props = ctx.getBean(OrderProps.class);
            System.out.println("[自动绑定] app.order.callback-url=" + props.getCallbackUrl()
                    + ", maxRetries=" + props.getMaxRetries());
        }

        // 场景 2：命令行参数覆盖（优先级最高）→ 条件否决
        try (ConfigurableApplicationContext ctx =
                     SpringApplication.run(MinimalBootApp.class, "--demo.enabled=false")) {
            System.out.println("[条件否决] --demo.enabled=false 时 demoFlag 不存在? "
                    + !ctx.containsBean("demoFlag"));
        }
    }
}
