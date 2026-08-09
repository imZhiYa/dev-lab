package demo10;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 属性条件的四种状态（03 篇 Level 4）：@ConditionalOnProperty 的匹配语义
 *
 * 机制（Boot 3.3.5 Implementation，javap 反编译 OnPropertyCondition$Spec）：
 *   - 匹配 = PropertyResolver.containsProperty(name) + getProperty(name) 精确 key 查找
 *   - 无宽松匹配：@ConditionalOnProperty 直接查 Environment，不做属性名规范化
 *     （宽松匹配是 @ConfigurationProperties 绑定阶段的特性；Boot 2 的
 *     RelaxedPropertyResolver 已在 Boot 3 移除）→ DEMO10_FLAG 不命中 demo10.flag
 *   - havingValue 值比较用 equalsIgnoreCase（不区分大小写）→ demo10.flag=TRUE 命中
 *   - 默认 matchIfMissing=false → 未配置即不匹配
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5）：
 *   [场景1] 未配置 demo10.flag → demo10-prop-bean 已注册=false（matchIfMissing=false）
 *   [场景2] systemProperties: demo10.flag=true → demo10-prop-bean 已注册=true
 *   [场景3] systemProperties: DEMO10_FLAG=true（移除 demo10.flag）→ 已注册=false（无宽松匹配）
 *   [场景4] systemProperties: demo10.flag=TRUE → 已注册=true（值比较 equalsIgnoreCase）
 */
public class PropertyConditionApp {

    @SpringBootApplication
    static class BootConfig {
    }

    static ConfigurableApplicationContext runCtx() {
        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();
        System.out.println("  [结果] demo10-prop-bean 已注册=" + ctx.containsBean("demo10-prop-bean"));
        return ctx;
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        System.out.println("[场景1] 未配置 demo10.flag（matchIfMissing 默认 false）");
        runCtx().close();

        System.setProperty("demo10.flag", "true");
        System.out.println("[场景2] systemProperties: demo10.flag=true");
        runCtx().close();

        System.clearProperty("demo10.flag");
        System.setProperty("DEMO10_FLAG", "true");
        System.out.println("[场景3] systemProperties: DEMO10_FLAG=true（无宽松匹配：@ConditionalOnProperty 精确查 key）");
        runCtx().close();

        System.clearProperty("DEMO10_FLAG");
        System.setProperty("demo10.flag", "TRUE");
        System.out.println("[场景4] systemProperties: demo10.flag=TRUE（值比较 equalsIgnoreCase，不区分大小写）");
        runCtx().close();
    }
}
