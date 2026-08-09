package demo04;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * allowCircularReferences 开关：
 * 三级缓存是 DefaultListableBeanFactory 的实现，可以被关掉。
 * Boot 2.6+ 默认关闭（SpringApplication 在启动时对容器设置
 * allowCircularReferences=false）——这正是"升级 Boot 2.6 后
 * 字段注入的环突然启动失败"的原因。
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 *   [默认开启] 字段环创建成功: true
 *   [显式关闭] 字段环启动失败: BeanCurrentlyInCreationException
 *   消息首行: Error creating bean with name 'a': Requested bean is currently in creation: Is there an unresolvable circular reference?
 */
public class DisableCircularApp {

    @Configuration
    static class Cfg {
    }

    @Component
    static class A {
        @Autowired B b;
    }

    @Component
    static class B {
        @Autowired A a;
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);
        // 场景 1：默认（true）
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(Cfg.class, A.class, B.class)) {
            System.out.println("[默认开启] 字段环创建成功: " + (ctx.getBean(A.class).b != null));
        }

        // 场景 2：refresh 前显式关闭
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(Cfg.class, A.class, B.class);
            org.springframework.beans.factory.support.DefaultListableBeanFactory bf =
                    (org.springframework.beans.factory.support.DefaultListableBeanFactory) ctx.getBeanFactory();
            bf.setAllowCircularReferences(false);
            ctx.refresh();
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            System.out.println("[显式关闭] 字段环启动失败: " + root.getClass().getSimpleName());
            String first = root.getMessage() != null ? root.getMessage().split("\n")[0] : "";
            System.out.println("消息首行: " + first);
        }
    }
}
