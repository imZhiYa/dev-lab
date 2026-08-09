package autoext;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 排序实证 B：imports 文件中 OrdB 写在 OrdA 之前（B 先被候选收集），
 * 但 OrdA 声明 @AutoConfiguration(before=OrdB.class)，排序器重排后 A 在 B 前
 * （demo10.OrderingApp 通过 beanDefinitionNames 顺序观察）。
 */
@AutoConfiguration
public class OrdB {

    @Bean("demo10-order-b")
    public String orderB() {
        return "order-b";
    }
}
