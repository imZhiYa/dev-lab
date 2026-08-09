package demo17;

import demo17.app.OrderService;
import demo17.permcheck.PermissionDeniedException;
import demo17.permcheck.RoleContext;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 权限切面 starter 端到端实证（06 篇决策卡 3 的 Validation）。
 *
 * 链路（Boot 3.3.5 + spring-aop 6.1.14 + aspectjweaver 1.9.22.1）：
 *   imports 一行收录（demo17.permcheck.PermissionAutoConfiguration）
 *     → 条件链（@ConditionalOnClass + @ConditionalOnProperty）
 *     → @Bean 注册 PermissionAspect
 *     → AnnotationAwareAspectJAutoProxyCreator 收集 @Aspect bean
 *     → OrderService 被 CGLIB 代理（proxyTargetClass=true，Boot 3 默认）
 *     → 调用 adminOnly() 走代理 → 切面校验角色
 *
 * 运行方式：
 *   ./run.sh demo17.PermissionStarterApp                 → 切面装配，拦截生效
 *   ./run.sh demo17.PermissionStarterApp --demo17.permission.enabled=false
 *                        → 条件关闭 = 模拟"没引 starter"：切面不装配，注解静默失效
 *                          （USER 也能调用成功——权限漏洞无任何报错！）
 *
 * 真实输出（主场景）：
 *   [装配] PermissionAutoConfiguration 条件通过 → 注册 PermissionAspect（模拟"引了 starter"）
 *   [装配] 容器中 permissionAspect bean 存在 = true
 *   [装配] OrderService 是 AOP 代理 = true
 *   [切面] 拦截 OrderService.adminOnly()：@RequireRole("ADMIN") 绑定参数生效；当前角色 = ADMIN
 *   [放行] 管理员操作执行成功
 *   [切面] 拦截 OrderService.adminOnly()：@RequireRole("ADMIN") 绑定参数生效；当前角色 = USER
 *   [拒绝] 无权限被拦截：需要角色 ADMIN，当前角色 USER
 *
 * 真实输出（对照场景，--demo17.permission.enabled=false）：
 *   [装配] 容器中 permissionAspect bean 存在 = false
 *   [装配] OrderService 是 AOP 代理 = false
 *   [放行] 管理员操作执行成功（USER 调用也一样——注解形同虚设，静默失效！）
 */
@SpringBootApplication(scanBasePackages = "demo17.app")
public class PermissionStarterApp {

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);
        SpringApplication app = new SpringApplication(PermissionStarterApp.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        try (ConfigurableApplicationContext ctx = app.run(args)) {
            System.out.println("[装配] 容器中 permissionAspect bean 存在 = " + ctx.containsBean("permissionAspect"));
            OrderService order = ctx.getBean(OrderService.class);
            System.out.println("[装配] OrderService 是 AOP 代理 = " + AopUtils.isAopProxy(order));

            System.out.println("--- 场景 1：ADMIN 调用（应放行） ---");
            RoleContext.set("ADMIN");
            order.adminOnly();

            System.out.println("--- 场景 2：USER 调用（应拒绝） ---");
            RoleContext.set("USER");
            try {
                order.adminOnly();
                System.out.println("[异常] 竟然放行了（切面失效！）");
            } catch (PermissionDeniedException e) {
                System.out.println("[拒绝] 无权限被拦截：" + e.getMessage());
            }
            RoleContext.clear();
        }
    }
}
