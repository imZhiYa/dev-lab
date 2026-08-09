package demo13.aspect;

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

import java.lang.annotation.*;

/**
 * 横切面与 AOP（06 篇 Level 3）：切点表达式命中矩阵实测
 * <p>
 * 机制（AspectJ 切点语法，spring-aop 6.1.14 解析）：
 * - execution(修饰符? 返回类型 类名.方法名(参数) 异常?)：按方法签名匹配
 * * 通配任意；.. 通配任意参数
 * - within(类型)：按类匹配（含继承）
 * - @annotation(注解)：方法上有指定注解
 * <p>
 * 命中矩阵（真实输出，JDK 21.0.11 + spring-boot 3.3.5）：
 * ServiceA.methodA()  ServiceA.methodB()  ServiceB.methodC()
 * execution(*      ●（方法名前缀）      ●                   ○
 * ...method*(..))
 *
 * @annotation(Marked) ●（有注解）       ○                   ○
 * within(...ServiceB)  ○                 ○                   ●
 * <p>
 * ●=切点命中该调用 ○=未命中
 * <p>
 * 开发期真实踩坑：@annotation 绑定的参数类型用 java.lang.annotation.Annotation
 * 基类 → 该通知整体静默失效（其他通知正常）；绑定具体注解类型 Marked → 生效。
 * AspectJ 绑定要求参数类型与注解类型匹配（规范语义）。
 */
public class PointcutApp {

    @SpringBootApplication
    static class BootConfig {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Marked {
    }

    @Service
    static class ServiceA {
        @Marked
        public void methodA() {
            System.out.println("  [ServiceA.methodA]");
        }

        public void methodB() {
            System.out.println("  [ServiceA.methodB]");
        }
    }

    @Service
    static class ServiceB {
        public void methodC() {
            System.out.println("  [ServiceB.methodC]");
        }
    }

    @Aspect
    @Component
    @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
    static class PointcutAspect {

        @Pointcut("execution(* demo13.aspect.PointcutApp$ServiceA.method*(..))")
        void execMethodPrefix() {
        }

        @Before("@annotation(marked)")
        public void byAnnotation(Marked marked) {
            System.out.println("  [切点2 @annotation: 命中]");
        }

        @Before("execMethodPrefix()")
        public void byExecution() {
            System.out.println("  [切点1 execution: 命中]");
        }

        @Before("within(demo13.aspect.PointcutApp$ServiceB)")
        public void byWithin() {
            System.out.println("  [切点3 within: 命中]");
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();

        ServiceA a = ctx.getBean(ServiceA.class);
        ServiceB b = ctx.getBean(ServiceB.class);
        System.out.println("--- 调用 methodA（带 @Marked） ---");
        a.methodA();
        System.out.println("--- 调用 methodB（无注解，method 前缀命中） ---");
        a.methodB();
        System.out.println("--- 调用 methodC（within 命中） ---");
        b.methodC();
        ctx.close();
    }
}
