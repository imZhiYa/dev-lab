package demo08;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 早期事件（02 篇 Level 4）：refresh 早期发布的事件为什么只有部分监听器能收到
 *
 * 时序（Framework 6.1.14 AbstractApplicationContext.refresh）：
 *   prepareRefresh：初始化 earlyApplicationEvents 缓存集合
 *   invokeBeanFactoryPostProcessors（第 5 步）：本 demo 的 EarlyPublisher 在这里发布事件
 *     → applicationEventMulticaster 还没创建 → 事件进 earlyApplicationEvents 缓存
 *   initApplicationEventMulticaster（第 8 步）：创建多播器
 *   registerListeners（第 10 步）：getApplicationListenerBeans() 提前实例化
 *     "implements ApplicationListener" 的 bean → 回放缓存事件 → DirectListener 收到
 *     （回放时 @EventListener 方法的处理器 EventListenerMethodProcessor 还没创建）
 *   finishBeanFactoryInitialization：创建 EventListenerMethodProcessor → 注册 @EventListener 方法
 *     → 回放早已结束 → MethodListener 收不到早期事件
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 *   [BFPP] refresh 早期发布事件（多播器未初始化 → 进 earlyApplicationEvents 缓存）
 *   [DirectListener] 收到早期事件（registerListeners 回放时被提前实例化）
 *   [验证] 直接 ApplicationListener 收到=true；@EventListener 方法收到=false
 */
public class EarlyEventApp {

    static class EarlyMarkerEvent extends ApplicationEvent {
        EarlyMarkerEvent(Object source) { super(source); }
    }

    static final AtomicBoolean directReceived = new AtomicBoolean(false);
    static final AtomicBoolean methodReceived = new AtomicBoolean(false);

    // 直接实现 ApplicationListener：registerListeners 阶段被提前实例化 → 收到回放
    @Component
    static class DirectListener implements ApplicationListener<EarlyMarkerEvent> {
        @Override
        public void onApplicationEvent(EarlyMarkerEvent e) {
            directReceived.set(true);
            System.out.println("[DirectListener] 收到早期事件（registerListeners 回放时被提前实例化）");
        }
    }

    // @EventListener 方法：处理器 EventListenerMethodProcessor 在回放之后才创建 → 收不到
    @Component
    static class MethodListener {
        @EventListener
        public void on(EarlyMarkerEvent e) {
            methodReceived.set(true);
            System.out.println("[@EventListener] 收到早期事件");
        }
    }

    // BeanFactoryPostProcessor 在 refresh 第 5 步执行：多播器未初始化
    @Component
    static class EarlyPublisher implements BeanFactoryPostProcessor, ApplicationContextAware {
        private ApplicationContext ctx;

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) {
            this.ctx = applicationContext;
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            System.out.println("[BFPP] refresh 早期发布事件（多播器未初始化 → 进 earlyApplicationEvents 缓存）");
            ctx.publishEvent(new EarlyMarkerEvent(this));
        }
    }

    @Configuration
    @Import({DirectListener.class, MethodListener.class, EarlyPublisher.class})
    static class AppConfig {
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            System.out.println("[验证] 直接 ApplicationListener 收到=" + directReceived.get()
                    + "；@EventListener 方法收到=" + methodReceived.get());
        }
    }
}
