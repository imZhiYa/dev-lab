package demo13.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * 横切面与 AOP（06 篇 Level 4）：自动代理创建者内部结构实证
 *
 * 机制（spring-aop 6.1.14 + Boot 3.3.5）：
 *   - 声明式 AOP 的装配链：@EnableAspectJAutoProxy（Boot 自动开）
 *     → AnnotationAwareAspectJAutoProxyCreator（BeanPostProcessor！）
 *     → postProcessAfterInitialization 时按 Advisor 创建代理
 *   - 代理对象实现 Advised 接口（可查 advisor 链）
 *   - 底层 API：ProxyFactory 手动创建代理（不依赖自动配置）
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5，本机）：
 *   [诊断] autoProxyCreator 是 BeanPostProcessor: true
 *   [手动 ProxyFactory 无 advisor] 类=demo13.aspect.$Proxy94；isAopProxy=true；advisor 数=0
 *     → ProxyFactory 手动创建：无 advisor 也出代理（与自动 creator 不同：
 *       自动 creator 无 advisor 时不代理，见 ProxyKindApp 跑法 3）
 *   [自动代理 GreetingImpl] 类=...$GreetingImpl$$SpringCGLIB$$0
 *   [自动代理] advisor 数=3（LogAspect 的 2 个 + 事务 advisor 1 个）
 */
public class ProxyInternalsApp {

    @SpringBootApplication
    static class BootConfig {
    }

    interface Greeting {
        String hello();
    }

    @Service
    static class GreetingImpl implements Greeting {
        @Override
        public String hello() {
            return "hello";
        }
    }

    @Aspect
    @Component
    @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
    static class LogAspect {
        @Around("execution(* demo13.aspect.ProxyInternalsApp$GreetingImpl.hello())")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            return pjp.proceed();
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();

        Object creator = ctx.getBean("org.springframework.aop.config.internalAutoProxyCreator");
        System.out.println("[诊断] autoProxyCreator 是 BeanPostProcessor: "
                + (creator instanceof BeanPostProcessor));

        ProxyFactory factory = new ProxyFactory(new GreetingImpl());
        Object manualProxy = factory.getProxy();
        System.out.println("[手动 ProxyFactory 无 advisor] 类=" + manualProxy.getClass().getName()
                + "；isAopProxy=" + AopUtils.isAopProxy(manualProxy)
                + "；advisor 数=" + ((Advised) manualProxy).getAdvisors().length);

        Greeting g = ctx.getBean(Greeting.class);
        System.out.println("[自动代理 GreetingImpl] 类=" + g.getClass().getName());
        if (g instanceof Advised) {
            System.out.println("[自动代理] advisor 数=" + ((Advised) g).getAdvisors().length);
        }
        ctx.close();
    }
}
