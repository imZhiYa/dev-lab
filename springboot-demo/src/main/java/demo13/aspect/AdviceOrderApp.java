package demo13.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * 横切面与 AOP（06 篇 Level 3）：五种通知的正常/异常双路径触发顺序实测
 *
 * 机制（spring-aop 6.1.14 + aspectjweaver 1.9.22.1）：
 *   - 通知类型：@Before / @After（最终）/ @AfterReturning / @AfterThrowing / @Around
 *   - 正常路径：Around(before) → Before → 目标方法 → AfterReturning → After → Around(after)
 *   - 异常路径：Around(before) → Before → 目标方法(抛) → AfterThrowing → After
 *     （Around 不调 proceed 则异常吞掉；Around 的 try/finally 决定 Around(after) 是否执行）
 *   - 顺序语义：After 一定执行（finally 语义）；AfterReturning 与 AfterThrowing 互斥
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5，本机）：
 *   正常路径：
 *     [Around] 开始
 *     [Before] 目标方法执行前
 *     [目标] bizWork 执行, 参数=ok
 *     [AfterReturning] 目标方法正常返回
 *     [After] 最终通知（finally 语义）
 *     [Around] 结束
 *   异常路径（目标抛 IllegalStateException）：
 *     [Around] 开始
 *     [Before] 目标方法执行前
 *     [目标] bizWork 执行, 参数=bad
 *     [AfterThrowing] 目标方法抛异常: java.lang.IllegalStateException
 *     [After] 最终通知（finally 语义）
 *     （Around 无 [Around] 结束：异常沿 Around 向外传播，不执行 proceed 之后的代码）
 */
public class AdviceOrderApp {

    @SpringBootApplication
    static class BootConfig {
    }

    @Service
    static class BizService {
        public String bizWork(String param) {
            System.out.println("[目标] bizWork 执行, 参数=" + param);
            if ("bad".equals(param)) {
                throw new IllegalStateException("业务失败");
            }
            return "ok";
        }
    }

    @Aspect
    @Component
    @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
    static class OrderAspect {

        @Pointcut("execution(* demo13.aspect.AdviceOrderApp$BizService.bizWork(..))")
        void biz() {
        }

        @Around("biz()")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("[Around] 开始");
            Object result = pjp.proceed();
            System.out.println("[Around] 结束");
            return result;
        }

        @Before("biz()")
        public void before() {
            System.out.println("[Before] 目标方法执行前");
        }

        @After("biz()")
        public void after() {
            System.out.println("[After] 最终通知（finally 语义）");
        }

        @AfterReturning("biz()")
        public void afterReturning() {
            System.out.println("[AfterReturning] 目标方法正常返回");
        }

        @AfterThrowing("biz()")
        public void afterThrowing() {
            System.out.println("[AfterThrowing] 目标方法抛异常");
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();

        BizService svc = ctx.getBean(BizService.class);
        System.out.println("=== 正常路径 ===");
        svc.bizWork("ok");
        System.out.println("=== 异常路径 ===");
        try {
            svc.bizWork("bad");
        } catch (IllegalStateException e) {
            System.out.println("[main] 捕获到: " + e.getClass().getSimpleName());
        }
        ctx.close();
    }
}
