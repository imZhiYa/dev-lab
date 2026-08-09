package demo10;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.context.LifecycleAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * 排除通道（03 篇 Level 2）：@SpringBootApplication(exclude=...) 关闭一个自动配置
 *
 * 机制（Boot 3.3.5）：
 *   - @SpringBootApplication 上有 exclude/excludeName 属性（@EnableAutoConfiguration 声明）
 *   - AutoConfigurationImportSelector.getExclusionFilter() 生成 Predicate，候选收集时剔除
 *   - 排除动作被 ConditionEvaluationReport.recordExclusions 记录，代码可读回
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5）：
 *   [配置] @SpringBootApplication(exclude=LifecycleAutoConfiguration.class)
 *   [报告] Exclusions: [org.springframework.boot.autoconfigure.context.LifecycleAutoConfiguration]
 *   [验证] LifecycleAutoConfiguration 未注册（排除生效），容器正常启动
 */
@SpringBootApplication(exclude = LifecycleAutoConfiguration.class)
public class ExclusionApp {

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        System.out.println("[配置] @SpringBootApplication(exclude=LifecycleAutoConfiguration.class)");
        SpringApplication app = new SpringApplication(ExclusionApp.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();

        ConditionEvaluationReport report = ConditionEvaluationReport.get(
                (ConfigurableListableBeanFactory) ctx.getBeanFactory());
        System.out.println("[报告] Exclusions: " + report.getExclusions());
        System.out.println("[验证] LifecycleAutoConfiguration 未注册（排除生效），容器正常启动");
        ctx.close();
    }
}
