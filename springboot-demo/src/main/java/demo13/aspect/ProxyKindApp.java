package demo13.aspect;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.NameMatchMethodPointcutAdvisor;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * 横切面与 AOP（06 篇 Level 2）：JDK 动态代理 vs CGLIB 选择 + 自动代理创建者双分支实测
 *
 * 机制（spring-aop 6.1.14 + Boot 3.3.5 + aspectjweaver 1.9.22.1）：
 *   1) 代理工厂选择（DefaultAopProxyFactory 语义）：目标有接口且未强制
 *      proxyTargetClass → JDK 动态代理；否则 CGLIB（无接口只能 CGLIB）
 *   2) Boot 3.x 默认 spring.aop.proxy-target-class=true → CGLIB
 *   3) AopAutoConfiguration 双分支（03 篇只测了配置评估，本篇测行为差异）：
 *      - 有 aspectjweaver → AspectJAutoProxyingConfiguration → @EnableAspectJAutoProxy
 *        → AnnotationAwareAspectJAutoProxyCreator：收集所有 Advisor bean + @Aspect
 *      - 无 aspectjweaver → ClassProxyingConfiguration → InfrastructureAdvisorAutoProxyCreator：
 *        isEligibleAdvisorBean 只收 role==ROLE_INFRASTRUCTURE(2) 的 advisor（javap 反编译实证）
 *        → 用户自定义 Advisor（role=0）被过滤 → 用户切面静默失效！
 *
 * 三种跑法：
 *   1) 全 lib（含 aspectjweaver）默认：./run.sh demo13.aspect.ProxyKindApp
 *   2) 全 lib + -Dapp.proxyTargetClass=false：有接口→JDK，无接口→CGLIB
 *   3) 去 aspectjweaver：java -cp "out:$(find lib -name '*.jar' ! -name 'aspectjweaver*' ...)"
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5，本机）：
 *   跑法 1（全 lib 默认）：
 *     [诊断] creator 实际类型 = AnnotationAwareAspectJAutoProxyCreator
 *     [诊断] findCandidateAdvisors 数量=20（demo13 包内全部 App 的切面都被组件
 *       扫描到——数量随包内 @Aspect 类总数变化；含 pingAdvisor + 各 App 切面 + 事务 advisor）
 *     [有接口 GreetingImpl] 类=demo13.aspect.ProxyKindApp$GreetingImpl$$SpringCGLIB$$0；AOP 代理=true
 *     [无接口 PlainService] 类=demo13.aspect.ProxyKindApp$PlainService$$SpringCGLIB$$0；AOP 代理=true
 *   跑法 2（proxyTargetClass=false）：
 *     [有接口 GreetingImpl] 类=demo13.aspect.$Proxy76；AOP 代理=true
 *     [无接口 PlainService] 类=demo13.aspect.ProxyKindApp$PlainService$$SpringCGLIB$$0；AOP 代理=true
 *   跑法 3（去 aspectjweaver；demo13 包内所有 @Aspect 类都被 @ConditionalOnClass
 *     排除——含本 App 与其他 App，否则类加载失败）：
 *     [诊断] creator 实际类型 = InfrastructureAdvisorAutoProxyCreator
 *     [诊断] findCandidateAdvisors 数量=1（只收 role=2 的事务 advisor，pingAdvisor 被过滤）
 *     [有接口 GreetingImpl] 类=...GreetingImpl；AOP 代理=false
 *     [无接口 PlainService] 类=...PlainService；AOP 代理=false
 *     → 意外发现：有 advisor（canApply=true）也不代理！Infrastructure 版只认
 *        ROLE_INFRASTRUCTURE advisor → 用户切面静默失效（Boot 依赖 aspectjweaver 的原因）
 *
 * 意外发现 2：@Aspect 类若被扫描而 aspectjweaver 缺失 →
 *   ClassNotFoundException: org.aspectj.lang.ProceedingJoinPoint
 *   ——@Aspect/@Around 注解类型本身由 aspectjweaver 提供（不是 spring-aop）
 */
public class ProxyKindApp {

    @SpringBootApplication
    static class BootConfig {
        @Bean
        Advisor pingAdvisor() {
            NameMatchMethodPointcutAdvisor a = new NameMatchMethodPointcutAdvisor();
            a.setMappedNames("hello", "ping");
            a.setAdvice(new MethodInterceptor() {
                @Override
                public Object invoke(MethodInvocation invocation) throws Throwable {
                    return invocation.proceed();
                }
            });
            return a;
        }
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

    @Service
    static class PlainService {
        public String ping() {
            return "pong";
        }
    }

    @Aspect
    @Component
    @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
    static class LogAspect {
        @Around("execution(* demo13.aspect.ProxyKindApp$GreetingImpl.hello())"
                + " || execution(* demo13.aspect.ProxyKindApp$PlainService.ping())")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            return pjp.proceed();
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        String flag = System.getProperty("app.proxyTargetClass");
        if (flag != null) {
            System.setProperty("spring.aop.proxy-target-class", flag);
        }

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();

        Object creator = ctx.getBean("org.springframework.aop.config.internalAutoProxyCreator");
        System.out.println("[诊断] creator 实际类型 = " + creator.getClass().getName());
        try {
            java.lang.reflect.Method m = org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator.class
                    .getDeclaredMethod("findCandidateAdvisors");
            m.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Advisor> found = (java.util.List<Advisor>) m.invoke(creator);
            System.out.println("[诊断] findCandidateAdvisors 数量=" + found.size());
        } catch (Exception e) {
            System.out.println("[诊断] 反射失败: " + e);
        }

        Greeting g = ctx.getBean(Greeting.class);
        PlainService p = ctx.getBean(PlainService.class);
        System.out.println("[有接口 GreetingImpl] 类=" + g.getClass().getName()
                + "；AOP 代理=" + AopUtils.isAopProxy(g));
        System.out.println("[无接口 PlainService] 类=" + p.getClass().getName()
                + "；AOP 代理=" + AopUtils.isAopProxy(p));
        ctx.close();
    }
}
