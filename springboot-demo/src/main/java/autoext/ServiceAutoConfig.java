package autoext;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 覆盖通道实证：自动配置提供默认 Greeter，但 @ConditionalOnMissingBean 让位于用户定义。
 * 机制：自动配置类经 DeferredImportSelector 延迟导入，在用户 @Configuration 之后处理——
 * OnBeanCondition 评估时用户 bean 已注册，条件不匹配 → 默认实现让位（demo10.OverrideApp）。
 */
@AutoConfiguration
public class ServiceAutoConfig {

    @Bean("demo10-default-greeter")
    @ConditionalOnMissingBean(Greeter.class)
    public Greeter defaultGreeter() {
        return new Greeter("自动配置的默认实现");
    }
}
