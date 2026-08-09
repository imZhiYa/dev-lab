package demo02;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Full 模式 vs Lite 模式：@Bean 方法被调用多次时，返回同一实例吗？
 * <p>
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 * Full 模式：调用两次 @Bean 方法，同一实例? true
 * Lite 模式：调用两次 @Bean 方法，同一实例? false  ← 每次调用都执行方法体！
 */
public class FullVsLiteApp {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(FullCfg.class, LiteCfg.class)) {

            FullCfg full = ctx.getBean(FullCfg.class);
            LiteCfg lite = ctx.getBean(LiteCfg.class);

            System.out.println("Full 模式：调用两次 @Bean 方法，同一实例? " + (full.tool() == full.tool()));
            System.out.println("Lite 模式：调用两次 @Bean 方法，同一实例? " + (lite.tool() == lite.tool()));
        }
    }

    /**
     * Full 模式：@Configuration 会被 CGLIB 子类化，@Bean 方法被拦截做方法级单例
     */
    @Configuration
    static class FullCfg {
        @Bean
        public Tool tool() {
            return new Tool("full");
        }
    }

    /**
     * Lite 模式：普通 @Component 不被子类化，每次调用都执行方法体 → 每次 new！
     */
    @Component
    static class LiteCfg {
        @Bean
        public Tool tool() {
            return new Tool("lite");
        }
    }

    static class Tool {
        private final String tag;

        Tool(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }
}
