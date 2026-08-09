package demo08;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 异步事件（02 篇 Level 3）：
 * ① @Async + @EventListener：监听器在 executor 线程执行（线程名不同）
 * ② publishEvent 立即返回，不等异步监听器
 * ③ 异步监听器抛异常不穿透发布者（由 AsyncUncaughtExceptionHandler 处理，默认只打日志）
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 *   [发布] publishEvent 开始（线程=main）
 *   [发布] publishEvent 立即返回——异步广播不等监听器
 *   [异步监听] 线程=async-event-1，收到 msg=hello-async
 *   [异步异常] 监听器抛错不冒泡，发布者正常继续（异常由异步执行器吞掉）
 */
public class AsyncEventApp {

    static class AsyncMsgEvent extends ApplicationEvent {
        final String msg;

        AsyncMsgEvent(Object source, String msg) {
            super(source);
            this.msg = msg;
        }
    }

    static class AsyncBoomEvent extends ApplicationEvent {
        AsyncBoomEvent(Object source) { super(source); }
    }

    @Component
    static class Publisher implements org.springframework.context.ApplicationEventPublisherAware {
        private org.springframework.context.ApplicationEventPublisher publisher;

        @Override
        public void setApplicationEventPublisher(org.springframework.context.ApplicationEventPublisher p) {
            this.publisher = p;
        }

        void fire(String msg) {
            System.out.println("[发布] publishEvent 开始（线程=" + Thread.currentThread().getName() + "）");
            publisher.publishEvent(new AsyncMsgEvent(this, msg));
            publisher.publishEvent(new AsyncBoomEvent(this));
            System.out.println("[发布] publishEvent 立即返回——异步广播不等监听器");
        }
    }

    @Component
    static class AsyncListener {
        @Async
        @EventListener
        public void on(AsyncMsgEvent e) throws InterruptedException {
            Thread.sleep(300);
            System.out.println("[异步监听] 线程=" + Thread.currentThread().getName() + "，收到 msg=" + e.msg);
        }

        @Async
        @EventListener
        public void onBoom(AsyncBoomEvent e) {
            System.out.println("[异步异常] 监听器抛错不冒泡，发布者正常继续（异常由异步执行器吞掉）");
            throw new IllegalStateException("异步监听器炸了（但发布者无感）");
        }
    }

    @Configuration
    @EnableAsync
    @Import({Publisher.class, AsyncListener.class})
    static class AppConfig {
        @Bean
        ThreadPoolTaskExecutor eventExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(2);
            executor.setQueueCapacity(100);
            executor.setThreadNamePrefix("async-event-");
            return executor;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            ctx.getBean(Publisher.class).fire("hello-async");
            Thread.sleep(800);
        }
    }
}
