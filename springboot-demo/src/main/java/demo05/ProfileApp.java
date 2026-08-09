package demo05;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * profile 的实证：Environment 的第二个维度（除了"来源优先级"还有"激活状态"）。
 * @Profile("dev") 的 Bean 只在 activeProfiles 包含 dev 时注册；
 * 配置怎么进 Environment 之前已证（来源堆叠），profile 决定"哪些来源/Bean 被激活"。
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 *   [无 profile] dev 专属 bean 存在? false
 *   [无 profile] 默认 bean 存在? true
 *   [activeProfiles=dev] dev 专属 bean 存在? true
 */
public class ProfileApp {

    @Component
    @Profile("dev")
    static class DevOnlyBean {
    }

    @Component
    static class DefaultBean {
    }

    @Configuration
    @Profile("dev")
    static class DevConfig {
        @Bean
        String devFlag() {
            return "dev-mode";
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        // 1. 不激活任何 profile
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(DevOnlyBean.class, DefaultBean.class)) {
            System.out.println("[无 profile] dev 专属 bean 存在? "
                    + !ctx.getBeansOfType(DevOnlyBean.class).isEmpty());
            System.out.println("[无 profile] 默认 bean 存在? "
                    + !ctx.getBeansOfType(DefaultBean.class).isEmpty());
        }

        // 2. 激活 dev
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().setActiveProfiles("dev");
            ctx.register(DevOnlyBean.class, DefaultBean.class, DevConfig.class);
            ctx.refresh();
            System.out.println("[activeProfiles=dev] dev 专属 bean 存在? "
                    + !ctx.getBeansOfType(DevOnlyBean.class).isEmpty());
            System.out.println("[activeProfiles=dev] @Profile 配置类的 @Bean 存在? "
                    + ctx.containsBean("devFlag"));
        }
    }
}
