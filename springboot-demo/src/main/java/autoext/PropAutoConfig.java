package autoext;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 属性条件实证：@ConditionalOnProperty(name="demo10.flag")，默认 matchIfMissing=false。
 * - 未配置 → 条件不匹配，bean 不注册
 * - demo10.flag=true → 匹配
 * - DEMO10_FLAG=true（宽松匹配的系统属性变体）→ 匹配
 * （demo10.PropertyConditionApp 三次启动验证）
 */
@AutoConfiguration
@ConditionalOnProperty(name = "demo10.flag")
public class PropAutoConfig {

    @Bean("demo10-prop-bean")
    public String propBean() {
        return "prop-bean";
    }
}
