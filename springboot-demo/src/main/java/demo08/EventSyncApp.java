package demo08;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 事件机制核心：同步广播三件事（02 篇 Level 2）：
 * ① 一次发布广播给多个监听器（按 @Order 排序）
 * ② 同步：监听器在发布者线程里执行（线程名相同）
 * ③ 异常穿透：监听器抛异常，publishEvent 直接冒泡给发布者
 * ④ 类型匹配：监听基类 ApplicationEvent 也能收到子类事件（父类匹配）
 * <p>
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 * [发布] publishEvent 开始（线程=main）
 * [监听1] SMS 同步收到: order=1001（线程=main）
 * [监听2] 日志同步收到: order=1001（线程=main）
 * [监听3] 基类监听: ApplicationEvent 也收到 order=1001（线程=main）
 * [发布] publishEvent 返回——同步广播，发布线程被阻塞到监听器跑完
 * [异常] 监听器抛异常，publishEvent 冒泡给发布者（同步穿透）
 */
public class EventSyncApp {

    static class OrderCreatedEvent extends ApplicationEvent {
        final String orderId;

        OrderCreatedEvent(Object source, String orderId) {
            super(source);
            this.orderId = orderId;
        }
    }

    // 只有 BoomListener 监听的类型，用于单独演示异常穿透
    static class BoomEvent extends ApplicationEvent {
        BoomEvent(Object source) {
            super(source);
        }
    }

    @Component
    static class OrderService implements org.springframework.context.ApplicationEventPublisherAware {
        private org.springframework.context.ApplicationEventPublisher publisher;

        @Override
        public void setApplicationEventPublisher(org.springframework.context.ApplicationEventPublisher applicationEventPublisher) {
            this.publisher = applicationEventPublisher;
        }

        void create(String id) {
            System.out.println("[发布] publishEvent 开始（线程=" + Thread.currentThread().getName() + "）");
            publisher.publishEvent(new OrderCreatedEvent(this, id));
            System.out.println("[发布] publishEvent 返回——同步广播，发布线程被阻塞到监听器跑完");
        }

        void boom() {
            publisher.publishEvent(new BoomEvent(this));
        }
    }

    @Component
    static class SmsListener {
        @EventListener
        @Order(1)
        public void on(OrderCreatedEvent e) {
            System.out.println("[监听1] SMS 同步收到: order=" + e.orderId
                    + "（线程=" + Thread.currentThread().getName() + "）");
        }
    }

    @Component
    static class LogListener {
        @EventListener
        @Order(2)
        public void on(OrderCreatedEvent e) {
            System.out.println("[监听2] 日志同步收到: order=" + e.orderId
                    + "（线程=" + Thread.currentThread().getName() + "）");
        }
    }

    @Component
    static class BaseListener {
        @EventListener
        public void onAll(ApplicationEvent e) {
            if (e instanceof OrderCreatedEvent o) {
                System.out.println("[监听3] 基类监听: ApplicationEvent 也收到 order=" + o.orderId
                        + "（线程=" + Thread.currentThread().getName() + "）");
            }
        }
    }

    @Component
    static class BoomListener {
        @EventListener
        public void on(BoomEvent e) {
            throw new IllegalStateException("监听器炸了");
        }
    }

    @Configuration
    @Import({OrderService.class, SmsListener.class, LogListener.class, BaseListener.class, BoomListener.class})
    static class AppConfig {
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService service = ctx.getBean(OrderService.class);
            service.create("1001");
            try {
                service.boom();
            } catch (IllegalStateException ex) {
                System.out.println("[异常] 监听器抛异常，publishEvent 冒泡给发布者（同步穿透）");
            }
        }
    }
}
