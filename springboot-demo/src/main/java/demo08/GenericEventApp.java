package demo08;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 泛型事件匹配（02 篇坑 9 实证）：
 * 事件载荷是泛型类 GenericEvent<T>，两个监听器分别监听 <String> 和 <Integer>。
 * 发布 GenericEvent<String>(hello)——猜猜哪个监听器执行？
 * <p>
 * 实测：两个都执行！原因：
 * 发布端 multicastEvent 用 ResolvableType.forInstance(event) 推导事件类型，
 * 泛型类的运行时 Class 拿不到类型参数（擦除）→ 事件类型退化为 raw GenericEvent
 * → raw 类型"能容纳"任何泛型参数的监听器签名 → 全部匹配
 * 结论：泛型事件无法精确路由——不是"收不到"，而是"全收误收"
 * <p>
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 * [发布] GenericEvent<String>(hello) 发布
 * [执行] String 监听器执行，payload=hello
 * [执行] Integer 监听器执行，payload=hello
 * [结果] String 监听器执行=true；Integer 监听器执行=true
 */
public class GenericEventApp {

    static class GenericEvent<T> extends ApplicationEvent {
        final T payload;

        GenericEvent(Object source, T payload) {
            super(source);
            this.payload = payload;
        }
    }

    static final java.util.concurrent.atomic.AtomicBoolean stringRan = new java.util.concurrent.atomic.AtomicBoolean(false);
    static final java.util.concurrent.atomic.AtomicBoolean integerRan = new java.util.concurrent.atomic.AtomicBoolean(false);

    @Component
    static class StringListener {
        @EventListener
        public void on(GenericEvent<String> e) {
            stringRan.set(true);
            System.out.println("[执行] String 监听器执行，payload=" + e.payload);
        }
    }

    @Component
    static class IntegerListener {
        @EventListener
        public void on(GenericEvent<Integer> e) {
            integerRan.set(true);
            System.out.println("[执行] Integer 监听器执行，payload=" + e.payload);
        }
    }

    @Configuration
    @Import({StringListener.class, IntegerListener.class})
    static class AppConfig {
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            System.out.println("[发布] GenericEvent<String>(hello) 发布");
            ctx.publishEvent(new GenericEvent<>(ctx, "hello"));
            System.out.println("[结果] String 监听器执行=" + stringRan.get()
                    + "；Integer 监听器执行=" + integerRan.get());
        }
    }
}
