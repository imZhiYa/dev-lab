package demo17.permcheck;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * 权限切面（demo17 实证）：@RequireRole 的校验器。
 *
 * 机制（spring-aop 6.1.14 + aspectjweaver 1.9.22.1）：
 *   - @Aspect 类由 AnnotationAwareAspectJAutoProxyCreator 收集（aspectjweaver 分支，
 *     06 篇 Level 2 实证：无 aspectjweaver 时用户 Advisor 被 role 过滤静默丢弃）；
 *   - @Around("@annotation(requireRole)")：切点绑定注解参数（06 篇坑 3 的正确写法，
 *     参数类型与注解类型精确匹配，通知内可读 requireRole.value()）；
 *   - 角色校验失败抛 PermissionDeniedException——"拒绝语义由切面决定"，
 *     业务方法无感知，只写业务逻辑。
 *
 * 注意：本类刻意不被组件扫描（demo17.permcheck 包不在 scanBasePackages 内），
 * 只由 PermissionAutoConfiguration 的 @Bean 注册——模拟"starter jar 里的类"。
 */
@Aspect
public class PermissionAspect {

    @Around("@annotation(requireRole)")
    public Object check(ProceedingJoinPoint pjp, RequireRole requireRole) throws Throwable {
        String needed = requireRole.value();
        String current = RoleContext.get();
        System.out.println("[切面] 拦截 " + pjp.getSignature().toShortString()
                + "：@RequireRole(\"" + needed + "\") 绑定参数生效；当前角色 = " + (current == null ? "未登录" : current));
        if (current == null || !needed.equals(current)) {
            throw new PermissionDeniedException("需要角色 " + needed + "，当前角色 " + (current == null ? "未登录" : current));
        }
        return pjp.proceed();
    }
}
