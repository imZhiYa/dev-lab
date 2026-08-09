package demo04;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * 字段注入循环依赖：A ↔ B 成功创建。
 * 关键验证：B 在属性填充阶段（setter 被调用时）拿到的 A，
 * 与容器最终暴露的 A 是【同一个引用】——这就是三级缓存保证的"引用一致性"。
 * <p>
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 * B 的属性填充阶段拿到 A（早期引用）
 * A 创建完成
 * 环成立: A.b != null && B.a != null
 * B 手里的 A 与容器最终暴露的 A 是同一引用: true
 */
public class FieldCircularApp {

    @Configuration
    static class Cfg {
    }

    @Component
    static class A {
        @Autowired
        B b;
    }

    @Component
    static class B {
        A aAtPopulate;         // 记录"属性填充那一刻"拿到的 A
        @Autowired
        A a;

        @Autowired
        public void setA(A a) {
            this.aAtPopulate = a;     // 这一刻 a 来自三级缓存的"早期引用"
            this.a = a;
            System.out.println("B 的属性填充阶段拿到 A（早期引用）");
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(Cfg.class, A.class, B.class)) {
            A a = ctx.getBean(A.class);
            B b = ctx.getBean(B.class);
            System.out.println("A 创建完成");
            System.out.println("环成立: A.b != null && B.a != null -> " + (a.b != null && b.a != null));
            System.out.println("B 手里的 A 与容器最终暴露的 A 是同一引用: " + (b.a == a));
            System.out.println("属性填充时刻的 A 与最终 A 是同一引用: " + (b.aAtPopulate == a));
        }
    }
}
