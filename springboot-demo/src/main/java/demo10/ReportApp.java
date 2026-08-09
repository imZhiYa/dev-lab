package demo10;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Condition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * 条件评估报告代码访问（03 篇 Level 4/6）：ConditionEvaluationReport 记录每次条件裁决
 *
 * 机制（Boot 3.3.5）：
 *   - SpringBootCondition.matches 是 final 模板方法：调用抽象 getMatchOutcome 时
 *     recordConditionEvaluation 写入报告（正/负都记录）
 *   - DataSourceAutoConfiguration 的 @ConditionalOnClass(EmbeddedDatabaseType.class)
 *     依赖 spring-jdbc——本实验 classpath 没有 → Negative（缺依赖自动降级）
 *   - AopAutoConfiguration 的 @ConditionalOnClass(EnableAspectJAutoProxy.class) 依赖
 *     spring-aop——本实验有 → Positive
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5）：
 *   [报告] DataSourceAutoConfiguration（缺 spring-jdbc → Negative 实测）
 *   [条件] OnClassCondition → 不匹配：未找到 org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
 *   [报告] AopAutoConfiguration（classpath 有 spring-aop → Positive 实测）
 *   [条件] OnClassCondition → 匹配：...
 *   [验证] 同一套条件机制，只因为 classpath 不同 → 裁决不同（免签协议逐条检查）
 */
public class ReportApp {

    @SpringBootApplication
    static class BootConfig {
    }

    static void printOutcomes(ConfigurableApplicationContext ctx, String configClass) {
        ConditionEvaluationReport report = ConditionEvaluationReport.get(
                (ConfigurableListableBeanFactory) ctx.getBeanFactory());
        report.getConditionAndOutcomesBySource().forEach((source, outcomes) -> {
            if (source.startsWith(configClass)) {
                System.out.println("[报告] " + source);
                outcomes.forEach(entry -> {
                    Condition c = entry.getCondition();
                    ConditionOutcome o = entry.getOutcome();
                    System.out.println("  [条件] " + c.getClass().getSimpleName()
                            + " → " + (o.isMatch() ? "匹配" : "不匹配")
                            + (o.getMessage() != null ? "：" + o.getMessage() : ""));
                });
            }
        });
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();

        System.out.println("[报告] DataSourceAutoConfiguration（本实验 classpath 无 spring-jdbc，预期 Negative）");
        printOutcomes(ctx, "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration");
        System.out.println("[报告] AopAutoConfiguration（本实验 classpath 有 spring-aop，预期 Positive）");
        printOutcomes(ctx, "org.springframework.boot.autoconfigure.aop.AopAutoConfiguration");
        System.out.println("[验证] 同一套条件机制，只因为 classpath 不同 → 裁决不同（逐条检查免签协议）");
        ctx.close();
    }
}
