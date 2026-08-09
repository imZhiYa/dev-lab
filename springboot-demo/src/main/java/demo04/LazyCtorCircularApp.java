package demo04;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 构造器循环依赖的唯一兼容解法：在构造器参数上加 @Lazy
 * （00 篇 4.4① 的"救不了的环"——这里救给你看）
 *
 * 机制（spring-context 6.1.14）：
 *   - 构造器注入发生在实例化阶段，三级缓存救不了（CtorCircularApp 已证）；
 *   - 参数上加 @Lazy → 注入点不解析真实实例，注入 CGLIB 代理
 *     （$$SpringCGLIB$$ 命名，Spring 6 repackaged cglib）；
 *   - 打破环的本质：A 创建时不需要先创建 B（拿到的是代理）→
 *     环退化成"顺序链"：A 完成 → B 创建（A 已就绪）→ 启动成功；
 *   - 注意：B 作为普通单例，容器启动时（preInstantiateSingletons）照样会被创建，
 *     @Lazy 推迟的是"解析动作"不是"单例创建"；
 *   - "唯一兼容"的限定：保持构造器注入风格、依赖类型不变、调用方代码零改动。
 *     ObjectProvider 参数也能破环（同样是懒引用），但调用方必须改成 getObject()
 *     ——获取形态变了，不如 @Lazy 透明。
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 *   [验证] CtorA 构造：b = class demo04.LazyCtorCircularApp$CtorB$$SpringCGLIB$$0（懒代理）
 *   [验证] CtorB 构造执行（A 已就绪——环退化成顺序链；B 作为普通单例在启动时照样创建）
 *   [验证] 容器启动成功：构造器环被 @Lazy 参数打破
 *   [验证] A→B 调用：B
 */
public class LazyCtorCircularApp {

    @Configuration
    static class Cfg {
    }

    @Component
    static class CtorA {
        final CtorB b;

        public CtorA(@Lazy CtorB b) {
            this.b = b;
            System.out.println("[验证] CtorA 构造：b = " + b.getClass().getName() + "（懒代理）");
        }

        String useB() {
            return "A→B 调用：" + b.name();
        }
    }

    @Component
    static class CtorB {
        final CtorA a;

        public CtorB(CtorA a) {
            this.a = a;
            System.out.println("[验证] CtorB 构造执行（A 已就绪——环退化成顺序链；B 作为普通单例在启动时照样创建）");
        }

        String name() {
            return "B";
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(Cfg.class, CtorA.class, CtorB.class)) {
            CtorA a = ctx.getBean(CtorA.class);
            System.out.println("[验证] 容器启动成功：构造器环被 @Lazy 参数打破");
            System.out.println("[验证] " + a.useB());
        }
    }
}
