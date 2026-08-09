package demo17.permcheck;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限切面 starter 的"业务注解"（demo17 实证）：
 * 标在方法上声明所需角色，由 PermissionAspect 在调用时校验。
 * 注解类型必须 RUNTIME 保留——切面 @Around("@annotation(requireRole)")
 * 的绑定参数要求在运行时能读到注解实例（06 篇坑 3 的正确用法演示）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    String value();
}
