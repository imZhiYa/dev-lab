package demo03;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

/**
 * 三种注入姿势 + 注入时机对比。
 * 关键观测点：构造器注入在"实例化"阶段就拿到依赖（final 字段）；
 * 字段/setter 注入在"属性填充"阶段才完成（构造器里还是 null）。
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 *   [构造器注入] 构造器执行时 greeter 已注入: true（final 字段，实例化期完成）
 *   [字段注入] 构造器执行时字段还是 null: true（尚未 populateBean）
 *   [字段注入] @PostConstruct 时字段已注入: true（populateBean 已完成）
 *   [setter注入] setter 被调用: true
 */
public class InjectionStylesApp {

    @Component
    static class Greeter {
        public String hello() { return "hello"; }
    }

    @Component
    static class FieldInjected {
        @Autowired Greeter greeter;

        public FieldInjected() {
            System.out.println("[字段注入] 构造器执行时字段还是 null: " + (greeter == null));
        }

        @PostConstruct
        public void check() {
            System.out.println("[字段注入] @PostConstruct 时字段已注入: " + (greeter != null));
        }
    }

    @Component
    static class SetterInjected {
        private Greeter greeter;

        @Autowired
        public void setGreeter(Greeter greeter) {
            this.greeter = greeter;
            System.out.println("[setter注入] setter 被调用: " + (greeter != null));
        }
    }

    @Component
    static class CtorInjected {
        private final Greeter greeter;   // final：实例化后不可变，只能构造器给

        @Autowired
        public CtorInjected(Greeter greeter) {
            this.greeter = greeter;
            System.out.println("[构造器注入] 构造器执行时 greeter 已注入: " + (greeter != null) + "（实例化期完成）");
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
                Greeter.class, FieldInjected.class, SetterInjected.class, CtorInjected.class)) {
            System.out.println("三种姿势全部可用: " +
                    (ctx.getBean(FieldInjected.class).greeter != null
                            && ctx.getBean(SetterInjected.class).greeter != null
                            && ctx.getBean(CtorInjected.class).greeter != null));
        }
    }
}
