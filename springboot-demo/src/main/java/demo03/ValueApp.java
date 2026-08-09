package demo03;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * @Value("${...}") 的三个事实：
 * 1. 没有 PropertySourcesPlaceholderConfigurer 时，${} 不被解析，注入的就是字面量；
 * 2. 有了它，占位符从 Environment 取值，并做类型转换（"8080" → int 8080）；
 * 3. 占位符缺失且无默认值 → 启动失败。
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 *   [无解析器] name 注入的是字面量: "${app.name}"
 *   [有解析器] name = order-service, port = 8080 (类型: int), 缺失key回退 = defaultVal
 *   [缺key无默认] 启动失败: BeanCreationException (Could not resolve placeholder 'no.such.key')
 */
public class ValueApp {

    @Component
    static class ValueHolder {
        @Value("${app.name}") String name;
        @Value("${app.port}") int port;
        @Value("${missing.key:defaultVal}") String fallback;

        void dump() {
            System.out.println("name = " + name + ", port = " + port
                    + " (类型: " + ((Object) port).getClass().getSimpleName() + "), 缺失key回退 = " + fallback);
        }
    }

    /** 场景 1a：只有 String 字段，没有解析器 → 注入的是字面量 */
    @Component
    static class OnlyStringHolder {
        @Value("${app.name}") String name;
    }

    /** 场景 1b：int 字段，没有解析器 → 类型转换直接失败（"${app.port}" 不是数字） */
    @Component
    static class OnlyIntHolder {
        @Value("${app.port}") int port;
    }

    /** 空配置类：不注册 PropertySourcesPlaceholderConfigurer，故无占位符解析器 */
    static class NoPlaceholderCtx {
    }

    @Configuration
    @PropertySource("classpath:demo03/app.properties")
    static class WithPlaceholderCtx {
        @Bean
        public static PropertySourcesPlaceholderConfigurer placeholder() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }

    @Configuration
    @PropertySource("classpath:demo03/app.properties")
    static class MissingKeyCtx {
        @Bean
        public static PropertySourcesPlaceholderConfigurer placeholder() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }

    @Component
    static class MissingKeyHolder {
        @Value("${no.such.key}") String missing;   // 无默认值
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        // 场景 1a：没有占位符解析器 → String 字段注入的是字面量
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(NoPlaceholderCtx.class, OnlyStringHolder.class)) {
            System.out.println("[无解析器·String字段] name 注入的是字面量: \"" + ctx.getBean(OnlyStringHolder.class).name + "\"");
        }

        // 场景 1b：没有解析器 → int 字段类型转换直接失败
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(NoPlaceholderCtx.class, OnlyIntHolder.class)) {
            ctx.getBean(OnlyIntHolder.class);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            System.out.println("[无解析器·int字段] 启动失败: " + root.getClass().getSimpleName()
                    + " (" + root.getMessage() + ")");
        }

        // 场景 2：有解析器 → 从 Environment 取 + 类型转换 + 默认值
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(WithPlaceholderCtx.class, ValueHolder.class)) {
            System.out.print("[有解析器] ");
            ctx.getBean(ValueHolder.class).dump();
        }

        // 场景 3：key 缺失且无默认值 → 启动失败
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(MissingKeyCtx.class, MissingKeyHolder.class)) {
            ctx.getBean(MissingKeyHolder.class);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            System.out.println("[缺key无默认] 启动失败: " + root.getClass().getSimpleName());
            String first = root.getMessage() != null ? root.getMessage().split("\n")[0] : "";
            if (first.length() > 100) first = first.substring(0, 100) + "...";
            System.out.println("  消息首行: " + first);
        }
    }
}
