package demo13.aspect;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 横切面与 AOP（06 篇 Level 4）：代理对象 vs 目标对象的关系实测
 *
 * 机制（spring-aop 6.1.14）：
 *   - 容器里的 bean 是代理；代理内部持有一个"目标对象"
 *   - 字段注入发生在目标实例上（代理实例的注入字段为 null——
 *     05 篇 SelfInvocationApp 实测过 svc.self NPE）
 *   - 解包：((Advised) proxy).getTargetSource().getTarget() 拿原始目标
 *   - AopUtils.getTargetClass 拿目标类（非代理类）
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5，本机）：
 *   [容器 bean] 类=...UserService$$SpringCGLIB$$1（代理，事务 advisor 命中）
 *   [容器 bean 的 self 字段] null ← 代理实例上字段未注入（05 篇 NPE 复现）
 *   [AopUtils.getTargetClass] 类=demo13.aspect.UnwrapApp$UserService
 *   [解包 target] 类=demo13.aspect.UnwrapApp$UserService
 *   [解包 target 的 self 字段类] demo13.aspect.UnwrapApp$UserService$$SpringCGLIB$$0；isAopProxy=true
 *   [容器 bean instanceof UserService] true
 *
 * 附注：CGLIB 代理 toString 输出"目标类名@hash"（DynamicAdvisedInterceptor
 * 把 toString 转发给目标对象保持语义），与 getClass().getName() 的代理类名不同。
 */
public class UnwrapApp {

    @SpringBootApplication
    static class BootConfig {
    }

    @Service
    static class UserService {

        @Autowired
        @Lazy
        private UserService self;

        public String work() {
            return "done";
        }

        @Transactional
        public void txWork() {
        }
    }

    public static void main(String[] args) throws Exception {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();

        UserService svc = ctx.getBean(UserService.class);
        System.out.println("[容器 bean] 类=" + svc.getClass().getName()
                + "（isAopProxy=" + AopUtils.isAopProxy(svc) + "）");
        java.lang.reflect.Field f = UserService.class.getDeclaredField("self");
        f.setAccessible(true);
        System.out.println("[容器 bean 的 self 字段] " + f.get(svc) + " ← 代理实例上字段未注入");

        System.out.println("[AopUtils.getTargetClass] 类=" + AopUtils.getTargetClass(svc).getName());

        Object target = ((Advised) svc).getTargetSource().getTarget();
        System.out.println("[解包 target] 类=" + target.getClass().getName());
        Object targetSelf = f.get(target);
        System.out.println("[解包 target 的 self 字段类] " + targetSelf.getClass().getName()
                + "；isAopProxy=" + AopUtils.isAopProxy(targetSelf));

        System.out.println("[容器 bean instanceof UserService] " + (svc instanceof UserService));
        System.out.println("[容器 bean toString] " + svc + "（代理转发给目标 toString）");
        ctx.close();
    }
}
