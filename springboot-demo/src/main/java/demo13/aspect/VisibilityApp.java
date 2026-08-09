package demo13.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * 横切面与 AOP（06 篇 Level 4 补充）：static / private / final / protected / 包可见性
 * 方法的切点匹配与拦截能力实测
 *
 * 机制（spring-aop 6.1.14 + CGLIB 代理）：
 *   - Spring AOP = 运行时代理：拦截能力 = "代理能覆写目标方法"的能力
 *   - CGLIB 是子类化：能覆写非 final 的实例方法（含 protected/包可见）；
 *     不能覆写 final / private / static 方法 → 切点命中也无法拦截
 *   - 静态方法绑定类而非实例，调用不经过任何代理
 *   - 注意：切点表达式能否"匹配"方法 与 代理能否"拦截"方法是两回事
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5 + aspectjweaver 1.9.22.1，本机）：
 *   [bean 类] demo13.aspect.VisibilityApp$TargetService$$SpringCGLIB$$0
 *   doPublic / doProtected / doPackage：均被切面拦截（CGLIB 子类可覆写）
 *   doFinal / doStatic / doPrivate：不被拦截（final 不可覆写、static 不经实例、
 *     private 类外不可见且不可覆写；类内部 this 调用也不经过代理）
 *
 * 关键区分：切点表达式"匹配"方法 与 代理"拦截"方法是两回事——且两者对
 * 不同修饰符的组合不同（补充验证，真实输出）：
 *   [验证] matches(doFinal)=true        → final：表达式匹配成功，但运行时无法拦截
 *   [验证] matches(doStatic)=false      → static：表达式层面就不匹配（Spring AOP 语境）
 *   [验证] matches(doPrivate, 表达式写 private)=false → private：表达式层面就不匹配
 * 即：final 是"匹配≠拦截"；static/private 是"根本不匹配，更无从拦截"。
 * （注意：这是 Spring AOP 运行时代理语境；AspectJ 编译期织入 ajc 连
 *   private/static/final 都能织入——不依赖覆写，见文章 Level 4 前置条件矩阵）
 *
 * 完整命中矩阵（真实输出）：
 *   方法            外部经代理调用    切面拦截？    原因
 *   public          是               ✅           CGLIB 子类可覆写
 *   protected       是（同包可调）    ✅           可覆写
 *   package-private 是（同包可调）    ✅           可覆写
 *   final          是               ❌           不可覆写（表达式匹配也拦不住）
 *   static         否（类名直调）    ❌           表达式不匹配 + 绑定类不经实例代理
 *   private        否（类外不可见）  ❌           表达式不匹配 + 不可见不可覆写
 */
public class VisibilityApp {

    @SpringBootApplication
    static class BootConfig {
    }

    @Service
    static class TargetService {

        public void doPublic() {
            System.out.println("    [目标] doPublic");
        }

        protected void doProtected() {
            System.out.println("    [目标] doProtected");
        }

        void doPackage() {
            System.out.println("    [目标] doPackage");
        }

        public final void doFinal() {
            System.out.println("    [目标] doFinal");
        }

        public static void doStatic() {
            System.out.println("    [目标] doStatic");
        }

        public void caller() {
            doPrivate();     // 类内部调用 private：与代理无关（不经过代理）
            doFinal();
        }

        private void doPrivate() {
            System.out.println("    [目标] doPrivate（类内部 this 调用）");
        }
    }

    @Aspect
    @Component
    @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
    static class WatchAspect {

        @Pointcut("within(demo13.aspect.VisibilityApp$TargetService)")
        void inTarget() {
        }

        @Around("inTarget()")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("  [切面拦截] " + pjp.getSignature().getName());
            return pjp.proceed();
        }

        @Around("execution(* demo13.aspect.VisibilityApp$TargetService.doFinal())")
        public Object aroundFinal(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("  [execution 精确切点拦截 doFinal]");
            return pjp.proceed();
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();

        TargetService svc = ctx.getBean(TargetService.class);
        System.out.println("[bean 类] " + svc.getClass().getName());
        try {
            java.lang.reflect.Method mFinal = TargetService.class.getDeclaredMethod("doFinal");
            java.lang.reflect.Method mStatic = TargetService.class.getDeclaredMethod("doStatic");
            java.lang.reflect.Method mPriv = TargetService.class.getDeclaredMethod("doPrivate");
            org.springframework.aop.aspectj.AspectJExpressionPointcut pc =
                    new org.springframework.aop.aspectj.AspectJExpressionPointcut();
            pc.setExpression("execution(* demo13.aspect.VisibilityApp$TargetService.doFinal())");
            System.out.println("[验证] matches(doFinal)=" + pc.matches(mFinal, TargetService.class));
            pc.setExpression("execution(* demo13.aspect.VisibilityApp$TargetService.doStatic())");
            System.out.println("[验证] matches(doStatic)=" + pc.matches(mStatic, TargetService.class));
            pc.setExpression("execution(private * demo13.aspect.VisibilityApp$TargetService.doPrivate())");
            System.out.println("[验证] matches(doPrivate, 表达式写 private)=" + pc.matches(mPriv, TargetService.class));
        } catch (Exception e) {
            System.out.println("[验证] 失败: " + e);
        }
        System.out.println("--- 调用 doPublic（外部经代理） ---");
        svc.doPublic();
        System.out.println("--- 调用 doProtected（外部经代理） ---");
        svc.doProtected();
        System.out.println("--- 调用 doPackage（外部经代理，同包） ---");
        svc.doPackage();
        System.out.println("--- 调用 doFinal（final 方法） ---");
        svc.doFinal();
        System.out.println("--- 调用 doStatic（静态方法） ---");
        TargetService.doStatic();
        System.out.println("--- 调用 caller（内部调 private/final，外层被拦） ---");
        svc.caller();
        ctx.close();
    }
}
