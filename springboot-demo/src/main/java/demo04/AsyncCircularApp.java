package demo04;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

/**
 * @Async 代理的循环依赖：AsyncA 最终会被包装成代理，
 * 但 B 在属性填充阶段拿到的是"原始版本"的 AsyncA。
 * 包装后容器发现"同一 Bean 出现了两个版本"→ 启动失败。
 *
 * 这解释了为什么"字段环能过，但加了 @Async 就死"。
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 *   启动失败: BeanCurrentlyInCreationException
 *   消息首行: Error creating bean with name 'asyncA': Bean with name 'asyncA' has been injected into other beans [asyncB] in its raw version as part of a circular reference, but has eventually been wrapped...
 */
public class AsyncCircularApp {

    @Configuration
    @EnableAsync
    static class Cfg {
    }

    @Component
    static class AsyncA {
        @Autowired AsyncB b;

        @Async
        public void ping() {
        }
    }

    @Component
    static class AsyncB {
        @Autowired AsyncA a;

        public void doIt() {
            a.ping();
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(Cfg.class, AsyncA.class, AsyncB.class)) {
            ctx.getBean(AsyncA.class);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            System.out.println("启动失败: " + root.getClass().getSimpleName());
            String first = root.getMessage() != null ? root.getMessage().split("\n")[0] : "";
            if (first.length() > 220) first = first.substring(0, 220) + "...";
            System.out.println("消息首行: " + first);
        }
    }
}
