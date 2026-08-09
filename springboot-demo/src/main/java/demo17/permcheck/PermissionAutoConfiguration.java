package demo17.permcheck;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 权限切面 starter 的自动配置类（demo17 实证）——"加依赖即生效"背后的门。
 *
 * 机制（Boot 3.3.5）：
 *   - 收录：本类全限定名写在 META-INF/spring/...AutoConfiguration.imports
 *     （与 03 篇 autoext 同文件，demo17 贡献自己的一行 = 模拟"另一个 jar 贡献一行"）；
 *   - 条件链：@ConditionalOnClass(name=...) 用 ASM 检测不触发类加载（03 篇实证）；
 *     @ConditionalOnProperty 提供对照开关（--demo17.permission.enabled=false
 *     = 模拟"没引/关掉 starter"）；
 *   - @Bean 注册切面：切面由容器装配而非组件扫描（业务包扫描不到本包，
 *     与真实 starter jar 的"类在依赖里、不参与业务扫描"同构）。
 */
@AutoConfiguration
@ConditionalOnClass(name = "demo17.permcheck.RequireRole")
@ConditionalOnProperty(name = "demo17.permission.enabled", havingValue = "true", matchIfMissing = true)
public class PermissionAutoConfiguration {

    @Bean
    public PermissionAspect permissionAspect() {
        System.out.println("[装配] PermissionAutoConfiguration 条件通过 → 注册 PermissionAspect（模拟\"引了 starter\"）");
        return new PermissionAspect();
    }
}
