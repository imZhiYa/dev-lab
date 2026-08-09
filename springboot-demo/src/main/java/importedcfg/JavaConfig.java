package importedcfg;

import demo10.way.Greeter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通道 2：@Bean（Java Config）。
 * 放在 demo10 包之外：组件扫描不会命中它，只有 BeanRegisterWaysApp 的
 * @Import(JavaConfig.class) 能把它的 @Bean 定义带进容器——同屏对比
 * "@Import 形态 1（导入 @Configuration 类）"与"@Component 扫描"的路径差异。
 */
@Configuration
public class JavaConfig {

    @Bean
    Greeter greeterBean() {
        return new Greeter("bean");
    }
}
