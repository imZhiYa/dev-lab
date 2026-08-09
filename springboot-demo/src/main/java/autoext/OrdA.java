package autoext;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 排序实证 A：@AutoConfiguration(before=OrdB.class)（Boot 3 注解自带排序属性，
 * @AutoConfiguration 无 order 属性，全局顺序用 @AutoConfigureOrder）。
 * 排序器（AutoConfigurationSorter.getInPriorityOrder）按 before/after 依赖做拓扑重排。
 */
@AutoConfiguration(before = OrdB.class)
public class OrdA {

    @Bean("demo10-order-a")
    public String orderA() {
        return "order-a";
    }
}
