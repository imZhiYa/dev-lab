package demo03;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 注入解析链五场景：类型匹配 → @Qualifier → @Primary → 名称回退 → 启动失败
 * 每个场景一个独立容器，看真实输出（JDK 21.0.11 + spring-context 6.1.14）。
 */
public class ResolutionChainApp {

    interface Greeter {
        String hello();
    }

    @Component("greeterA")
    static class GreeterA implements Greeter {
        public String hello() {
            return "A";
        }
    }

    @Component("greeterB")
    static class GreeterB implements Greeter {
        public String hello() {
            return "B";
        }
    }

    /**
     * 场景 3 专用：带 @Primary 的候选
     */
    @Component
    @Primary
    static class GreeterBPrime implements Greeter {
        public String hello() {
            return "B(@Primary)";
        }
    }

    /**
     * 场景 5 专用：bean 名刻意取成与字段名相同的 "greeter"
     */
    @Component("greeter")
    static class GreeterNamed implements Greeter {
        public String hello() {
            return "B(名字=greeter)";
        }
    }

    static class SingleUser {
        @Autowired
        Greeter greeter;
    }

    static class AmbiguousUser {
        @Autowired
        Greeter greeter;
    }

    static class PrimaryUser {
        @Autowired
        Greeter greeter;
    }

    static class QualifiedUser {
        @Autowired
        @Qualifier("greeterA")
        Greeter greeter;
    }

    static class NameFallbackUser {
        @Autowired
        Greeter greeter;
    }

    private static void scene1_onlyOne() {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(SingleUser.class, GreeterA.class)) {
            System.out.println("[场景1 单候选] 注入成功，hello() = " + ctx.getBean(SingleUser.class).greeter.hello());
        }
    }

    private static void scene2_ambiguous() {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(AmbiguousUser.class, GreeterA.class, GreeterB.class)) {
            ctx.getBean(AmbiguousUser.class);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            System.out.println("[场景2 两候选无提示] 启动失败，异常 = " + root.getClass().getSimpleName());
            System.out.println("  消息首行: " + root.getMessage().split("\n")[0]);
        }
    }

    private static void scene3_primary() {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(PrimaryUser.class, GreeterA.class, GreeterBPrime.class)) {
            System.out.println("[场景3 有@Primary] 注入成功，hello() = " + ctx.getBean(PrimaryUser.class).greeter.hello());
        }
    }

    private static void scene4_qualifier() {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(QualifiedUser.class, GreeterA.class, GreeterB.class)) {
            System.out.println("[场景4 有@Qualifier] 注入成功，hello() = " + ctx.getBean(QualifiedUser.class).greeter.hello());
        }
    }

    private static void scene5_nameFallback() {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(NameFallbackUser.class, GreeterA.class, GreeterNamed.class)) {
            System.out.println("[场景5 名称回退] 注入成功，hello() = " + ctx.getBean(NameFallbackUser.class).greeter.hello());
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);
        scene1_onlyOne();
        scene2_ambiguous();
        scene3_primary();
        scene4_qualifier();
        scene5_nameFallback();
    }
}
