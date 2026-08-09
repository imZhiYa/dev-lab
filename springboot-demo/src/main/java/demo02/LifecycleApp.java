package demo02;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 一个 Bean 的一生：把生命周期每个阶段都挂上打印钩子，
 * 看真实执行顺序（JDK 21.0.11 + spring-context 6.1.14）：
 *
 * 1. 实例化（构造器）
 * 2. BeanNameAware
 * 3. BeanFactoryAware
 * 4. BeanPostProcessor.postProcessBeforeInitialization
 * 5. @PostConstruct（由 CommonAnnotationBeanPostProcessor 在 4 内部调用）
 * 6. InitializingBean.afterPropertiesSet
 * 7. initMethod(customInit)
 * 8. BeanPostProcessor.postProcessAfterInitialization（★ AOP 代理在这里包装）
 * ---- 使用期 ----
 * 9. @PreDestroy
 * 10. DisposableBean.destroy
 * 11. destroyMethod(customDestroy)
 */
public class LifecycleApp {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Cfg.class)) {
            ctx.getBean(LifecycleBean.class).serve();
        }
        System.out.println("[容器关闭] 关闭动作完成");
    }

    @Configuration
    static class Cfg {
        @Bean(initMethod = "customInit", destroyMethod = "customDestroy")
        public LifecycleBean lifecycleBean() {
            return new LifecycleBean();
        }

        @Bean
        public static LifecycleLogger logger() {
            return new LifecycleLogger();
        }
    }

    /** 观察者：在初始化前后两个钩子打点 */
    static class LifecycleLogger implements BeanPostProcessor {
        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof LifecycleBean) {
                System.out.println("4. BeanPostProcessor.postProcessBeforeInitialization");
            }
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof LifecycleBean) {
                System.out.println("8. BeanPostProcessor.postProcessAfterInitialization（★ AOP 代理在这里包装）");
            }
            return bean;
        }
    }

    /** 主线角色：把一生所有钩子都实现一遍，观察执行顺序 */
    static class LifecycleBean implements BeanNameAware, BeanFactoryAware, InitializingBean, DisposableBean {

        public LifecycleBean() {
            System.out.println("1. 实例化（构造器）");
        }

        @Override
        public void setBeanName(String name) {
            System.out.println("2. BeanNameAware: 我的名字是 " + name);
        }

        @Override
        public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
            System.out.println("3. BeanFactoryAware");
        }

        @PostConstruct
        public void postConstruct() {
            System.out.println("5. @PostConstruct（CommonAnnotationBeanPostProcessor 调用）");
        }

        @Override
        public void afterPropertiesSet() {
            System.out.println("6. InitializingBean.afterPropertiesSet");
        }

        public void customInit() {
            System.out.println("7. initMethod(customInit)");
        }

        public void serve() {
            System.out.println("[使用期] serve() 被调用");
        }

        @PreDestroy
        public void preDestroy() {
            System.out.println("9. @PreDestroy");
        }

        @Override
        public void destroy() {
            System.out.println("10. DisposableBean.destroy");
        }

        public void customDestroy() {
            System.out.println("11. destroyMethod(customDestroy)");
        }
    }
}
