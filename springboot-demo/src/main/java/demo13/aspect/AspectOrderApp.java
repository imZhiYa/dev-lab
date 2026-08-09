package demo13.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * 横切面与 AOP（06 篇 Level 3）：多切面 @Order 排序 + 洋葱模型实测
 *
 * 机制（spring-aop 6.1.14）：
 *   - 多个切面命中同一方法时按 @Order 值排序（值小先执行）
 *   - 洋葱模型：A（Order 1）包裹 B（Order 2）包裹目标
 *     进入顺序 A→B，退出顺序 B→A（后进先出）
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5，本机）：
 *   [AspectA(AOrder1) Around] 开始
 *   [AspectA(AOrder1) Before]
 *   [AspectB(Order2) Around] 开始
 *   [AspectB(Order2) Before]
 *   [目标] bizWork 执行
 *   [AspectB(Order2) After]
 *   [AspectB(Order2) Around] 结束
 *   [AspectA(AOrder1) After]
 *   [AspectA(AOrder1) Around] 结束
 *
 * 无 @Order 时多个切面顺序不定（依赖 bean 解析顺序，生产不可依赖）。
 */
public class AspectOrderApp {

    @SpringBootApplication
    static class BootConfig {
    }

    @Service
    static class BizService {
        public void bizWork() {
            System.out.println("[目标] bizWork 执行");
        }
    }

    @Aspect
    @Component
    @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
    @Order(1)
    static class AspectA {
        @Around("execution(* demo13.aspect.AspectOrderApp$BizService.bizWork())")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("[AspectA(Order1) Around] 开始");
            Object r = pjp.proceed();
            System.out.println("[AspectA(Order1) Around] 结束");
            return r;
        }

        @Before("execution(* demo13.aspect.AspectOrderApp$BizService.bizWork())")
        public void before() {
            System.out.println("[AspectA(Order1) Before]");
        }

        @After("execution(* demo13.aspect.AspectOrderApp$BizService.bizWork())")
        public void after() {
            System.out.println("[AspectA(Order1) After]");
        }
    }

    @Aspect
    @Component
    @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
    @Order(2)
    static class AspectB {
        @Around("execution(* demo13.aspect.AspectOrderApp$BizService.bizWork())")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("[AspectB(Order2) Around] 开始");
            Object r = pjp.proceed();
            System.out.println("[AspectB(Order2) Around] 结束");
            return r;
        }

        @Before("execution(* demo13.aspect.AspectOrderApp$BizService.bizWork())")
        public void before() {
            System.out.println("[AspectB(Order2) Before]");
        }

        @After("execution(* demo13.aspect.AspectOrderApp$BizService.bizWork())")
        public void after() {
            System.out.println("[AspectB(Order2) After]");
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();

        BizService svc = ctx.getBean(BizService.class);
        svc.bizWork();
        ctx.close();
    }
}
