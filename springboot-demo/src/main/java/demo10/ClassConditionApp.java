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
 * 类条件 + ASM 检测（03 篇 Level 4）：@ConditionalOnClass 用元数据判断类是否存在，不加载类
 *
 * 机制（Boot 3.3.5）：
 *   - OnClassCondition extends FilteringSpringBootCondition（无 ConfigurationPhase）
 *     → 类条件可以在候选注册前先行过滤（AutoConfigurationImportSelector 的 filter 阶段）
 *   - 判断依据：MetadataReaderFactory（ASM 读 .class 字节码元数据），不触发 Class.forName
 *     → com.example.never.Exists 不在 classpath，条件为负，但全程不抛 NoClassDefFoundError
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5）：
 *   [验证] com.example.never.Exists 在 classpath？Class.forName 探测 = false
 *   [验证] 启动全程未抛 NoClassDefFoundError——条件检测未加载类（ASM 元数据）
 *   [报告] autoext.ClassAutoConfigP
 *   [条件] OnClassCondition → 匹配：类 java.util.ArrayList 存在
 *   [报告] autoext.ClassAutoConfigN
 *   [条件] OnClassCondition → 不匹配：类 com.example.never.Exists 不存在
 *   [容器] demo10-class-p 已注册=true；demo10-class-n 已注册=false
 */
public class ClassConditionApp {

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

        boolean present;
        try {
            Class.forName("com.example.never.Exists");
            present = true;
        } catch (ClassNotFoundException e) {
            present = false;
        }
        System.out.println("[验证] com.example.never.Exists 在 classpath？Class.forName 探测 = " + present);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();
        System.out.println("[验证] 启动全程未抛 NoClassDefFoundError——条件检测未加载类（ASM 元数据）");

        printOutcomes(ctx, "autoext.ClassAutoConfigP");
        printOutcomes(ctx, "autoext.ClassAutoConfigN");
        System.out.println("[容器] demo10-class-p 已注册=" + ctx.containsBean("demo10-class-p")
                + "；demo10-class-n 已注册=" + ctx.containsBean("demo10-class-n"));
        ctx.close();
    }
}
