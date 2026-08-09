package demo03;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

/**
 * "@Value 为什么是 NULL"专题：六场景实测（对应生产事故"排查 NULL"）
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 *   [A] static 字段 @Value: null（static 字段不走属性填充，且 6.x 不注入 static 注入点）
 *   [B] new 出来的对象 @Value: null（容器不管理它，没人注入）
 *   [C1] 构造器里读 @Value 字段: null（属性填充在构造器之后）
 *   [C2] @PostConstruct 里读 @Value 字段: order-service（populateBean 已完成）
 *   [D] static setter @Value: 未注入（static 方法注入点被忽略）
 *   [E] System property 覆盖 properties 里的同名 key: system-cover（Environment 优先级）
 */
public class ValueNullApp {

    @Configuration
    @PropertySource("classpath:demo03/app.properties")
    static class Cfg {
        @Bean
        public static PropertySourcesPlaceholderConfigurer placeholder() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }

    // ---- [A] static 字段 ----
    @Component
    static class StaticFieldHolder {
        @Value("${app.name}")
        static String name;   // 反模式：static 字段注入

        @PostConstruct
        public void check() {
            System.out.println("[A] static 字段 @Value: " + name + "（null = 未注入）");
        }
    }

    // ---- [B] new 出来的对象 ----
    static class NewedObject {
        @Value("${app.name}")
        String name;
    }

    // ---- [C] 构造器时机 ----
    @Component
    static class CtorTimingHolder {
        @Value("${app.name}")
        String name;

        public CtorTimingHolder() {
            System.out.println("[C1] 构造器里读 @Value 字段: " + name + "（null = 属性填充在构造器之后）");
        }

        @PostConstruct
        public void after() {
            System.out.println("[C2] @PostConstruct 里读 @Value 字段: " + name + "（populateBean 已完成）");
        }
    }

    // ---- [D] static setter ----
    @Component
    static class StaticSetterHolder {
        static String name;

        @Value("${app.name}")
        public static void setName(String n) {
            StaticSetterHolder.name = n;
        }

        @PostConstruct
        public void check() {
            System.out.println("[D] static setter @Value: " + name + "（未注入 = static 方法注入点被忽略）");
        }
    }

    // ---- [E] Environment 优先级 ----
    @Component
    static class EnvPriorityHolder {
        @Value("${app.env.priority}")
        String which;
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);
        // E 场景需要 app.properties 里有 app.env.priority（见 ValueNullCfg 专用资源）
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(Cfg.class, StaticFieldHolder.class,
                             CtorTimingHolder.class, StaticSetterHolder.class)) {
            // [B] new 的对象
            NewedObject no = new NewedObject();
            System.out.println("[B] new 出来的对象 @Value: " + no.name + "（null = 容器不管理它）");
        }

        // [E] 优先级：System property 覆盖资源文件
        System.setProperty("app.env.priority", "system-cover");
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(CfgE.class, EnvPriorityHolder.class)) {
            System.out.println("[E] System property 覆盖 properties 同名 key: " + ctx.getBean(EnvPriorityHolder.class).which);
        }
        System.clearProperty("app.env.priority");
    }

    @Configuration
    @PropertySource("classpath:demo03/app-env.properties")
    static class CfgE {
        @Bean
        public static PropertySourcesPlaceholderConfigurer placeholder() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }
}
