package demo10.way;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 通道 4：@Import 的形态 2——ImportSelector。
 * 不直接导入类，而是"返回一批候选类名"由容器按名称导入。
 * 返回的候选类会被当配置类处理（可含 @Bean 方法）。
 * 注意：这是 Spring Boot 自动装配的入口形态（@EnableAutoConfiguration
 * 内部就是 @Import(AutoConfigurationImportSelector.class)）。
 */
public class GreeterSelector implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[] {"importedcfg.SelectorTargetConfig"};
    }
}
