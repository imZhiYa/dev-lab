package demo04;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * 构造器注入的循环依赖：A 的构造器需要 B、B 的构造器需要 A。
 * 构造器注入发生在"实例化"阶段——此时还没有任何对象可以提前暴露，
 * 三级缓存救不了 → 启动失败。
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 *   启动失败: BeanCurrentlyInCreationException
 *   消息首行: Error creating bean with name 'ctorA': Requested bean is currently in creation: Is there an unresolvable circular reference?
 */
public class CtorCircularApp {

    @Configuration
    static class Cfg {
    }

    @Component
    static class CtorA {
        final CtorB b;

        public CtorA(CtorB b) {
            this.b = b;
        }
    }

    @Component
    static class CtorB {
        final CtorA a;

        public CtorB(CtorA a) {
            this.a = a;
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(Cfg.class, CtorA.class, CtorB.class)) {
            ctx.getBean(CtorA.class);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            System.out.println("启动失败: " + root.getClass().getSimpleName());
            String first = root.getMessage() != null ? root.getMessage().split("\n")[0] : "";
            System.out.println("消息首行: " + first);
        }
    }
}
