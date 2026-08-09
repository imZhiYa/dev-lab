package autoconfig;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 模拟一个 starter 的自动配置类（放扫描包之外，只能靠 AutoConfiguration.imports 白名单加载）：
 * - imports 白名单：src/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * - 条件评估：demo.enabled=true 才注册 demoFlag
 * - @ConditionalOnProperty 否决时，连这个类都不会被当配置类处理（条件评估在注册前）
 */
@AutoConfiguration
@ConditionalOnProperty(name = "demo.enabled", havingValue = "true")
public class DemoAutoConfiguration {

    @Bean
    String demoFlag() {
        return "auto-config-ok";
    }
}
