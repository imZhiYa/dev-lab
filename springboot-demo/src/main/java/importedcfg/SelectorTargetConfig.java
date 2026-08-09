package importedcfg;

import demo10.way.Greeter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ImportSelector 返回的候选配置类（通道 4 的产物）。
 * 同样放在 demo10 包外：只有 GreeterSelector.selectImports 点名，它才进容器。
 */
@Configuration
public class SelectorTargetConfig {

    @Bean
    Greeter greeterSelector() {
        return new Greeter("selector");
    }
}
