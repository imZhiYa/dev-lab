package demo10;

import autoext.Greeter;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * 覆盖通道（03 篇 Level 5）：@ConditionalOnMissingBean 让自动配置让位于用户定义
 *
 * 机制（Boot 3.3.5）：
 *   - 自动配置类经 DeferredImportSelector 延迟导入 → 在用户 @Configuration 之后处理
 *   - ServiceAutoConfig 的 @Bean 方法标 @ConditionalOnMissingBean(Greeter.class)：
 *     OnBeanCondition 在 REGISTER_BEAN 阶段评估，此时用户 Greeter 已注册 → 条件不匹配
 *   - 结果：默认实现让位，用户实现生效（"约定优于配置，用户优先"）
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5）：
 *   [用户] 用户 @Bean Greeter 已注册
 *   [报告] autoext.ServiceAutoConfig 的条件链：
 *   [条件] OnBeanCondition → 不匹配：@ConditionalOnMissingBean (types: autoext.Greeter) 找到已注册 bean
 *   [容器] demo10-default-greeter 已注册=false；注入的 Greeter = Greeter(用户定义)
 *   [机制] DeferredImportSelector 延迟导入 → 自动配置类后处理 → 条件能"看见"用户 bean
 *
 * 前提（Demo 纪律）：@Bean 方法必须声明在 @SpringBootApplication 配置类内部——
 * 放在外部类里不会被当作配置方法处理（编译产物无工厂方法，定义根本不注册）。
 */
public class OverrideApp {

    @SpringBootApplication
    static class BootConfig {
        @Bean
        public Greeter userGreeter() {
            return new Greeter("用户定义");
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();

        Greeter greeter = ctx.getBean(Greeter.class);
        System.out.println("[用户] 用户 @Bean Greeter 已注册");
        ConditionEvaluationReport report = ConditionEvaluationReport.get(
                (ConfigurableListableBeanFactory) ctx.getBeanFactory());
        System.out.println("[报告] autoext.ServiceAutoConfig 的条件链：");
        report.getConditionAndOutcomesBySource().forEach((source, outcomes) -> {
            if (source.startsWith("autoext.ServiceAutoConfig")) {
                outcomes.forEach(entry -> {
                    Condition c = entry.getCondition();
                    ConditionOutcome o = entry.getOutcome();
                    System.out.println("  [条件] " + c.getClass().getSimpleName()
                            + " → " + (o.isMatch() ? "匹配" : "不匹配")
                            + (o.getMessage() != null ? "：" + o.getMessage() : ""));
                });
            }
        });
        System.out.println("[容器] demo10-default-greeter 已注册=" + ctx.containsBean("demo10-default-greeter")
                + "；注入的 Greeter = " + greeter);
        System.out.println("[机制] DeferredImportSelector 延迟导入 → 自动配置类后处理 → 条件能\"看见\"用户 bean");
        ctx.close();
    }
}
