package demo10;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * 自动配置排序（03 篇 Level 3）：@AutoConfiguration(before=...) 改变处理顺序
 *
 * 机制（Boot 3.3.5）：
 *   - imports 文件里 OrdB 写在 OrdA 之前（候选收集顺序 = B 先）
 *   - OrdA 声明 @AutoConfiguration(before=OrdB.class)
 *   - AutoConfigurationSorter.getInPriorityOrder 按 before/after 依赖拓扑重排
 *     → 处理顺序 A 在 B 前 → bean 定义注册顺序 A 在 B 前（beanDefinitionNames 可观察）
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5）：
 *   [imports] 候选收集顺序：autoext.OrdB → autoext.OrdA（B 在前）
 *   [声明] OrdA @AutoConfiguration(before=OrdB.class)
 *   [容器] beanDefinitionNames（demo10-order-*）：[demo10-order-a, demo10-order-b]
 *   [结论] 排序器按 before 依赖重排——A 在 B 前（拓扑排序生效）
 */
public class OrderingApp {

    @SpringBootApplication
    static class BootConfig {
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        System.out.println("[imports] 候选收集顺序：autoext.OrdB → autoext.OrdA（B 在前）");
        System.out.println("[声明] OrdA 标注 @AutoConfiguration(before=OrdB.class)");
        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();

        String[] names = ((ConfigurableListableBeanFactory) ctx.getBeanFactory()).getBeanDefinitionNames();
        StringBuilder order = new StringBuilder();
        for (String n : names) {
            if (n.startsWith("demo10-order-")) {
                if (order.length() > 0) {
                    order.append(", ");
                }
                order.append(n);
            }
        }
        System.out.println("[容器] beanDefinitionNames（demo10-order-*）：[" + order + "]");
        System.out.println("[结论] 排序器按 before 依赖重排——A 在 B 前（拓扑排序生效）");
        ctx.close();
    }
}
