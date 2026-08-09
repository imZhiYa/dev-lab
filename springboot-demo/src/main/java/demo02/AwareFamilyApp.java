package demo02;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

/**
 * Aware 家族调用顺序 + "populateBean 先于 initializeBean"的实证：
 * 字段注入（@Value）发生在 populateBean；Aware 回调在 initializeBean。
 * 如果 populateBean 真的先跑，那么 BeanNameAware 回调里就能读到已注入的字段。
 * <p>
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 * 1. 实例化（构造器）：@Value 字段 = null   ← 字段还没注入
 * 2. BeanNameAware：@Value 字段 = order-service  ← populateBean 已完成！
 * 3. BeanFactoryAware：@Value 字段 = order-service
 * 4. EnvironmentAware：@Value 字段 = order-service（ApplicationContextAwareProcessor，BPP.before 内部）
 * 5. ApplicationContextAware：@Value 字段 = order-service（同一处理器内部，Environment 先于 Context）
 * -- 自定义 BPP.before 触发（第二拨 Aware 之后、@PostConstruct 之前）
 * 6. @PostConstruct：@Value 字段 = order-service
 * <p>
 * 实测 BPP 链（postProcessBeforeInitialization 按此顺序执行）：
 * ApplicationContextAwareProcessor   ← 第二拨 Aware 的执行者，永远在链首
 * ImportAwareBeanPostProcessor
 * BeanPostProcessorChecker
 * CustomBPP（自定义）                ← 因 MergedBeanDefinition 组后注册重放，反被"前移"
 * CommonAnnotationBeanPostProcessor  ← @PostConstruct 的执行者（PriorityOrdered, order=0）
 * AutowiredAnnotationBeanPostProcessor（PriorityOrdered, order=LOWEST_PRECEDENCE）
 * ApplicationListenerDetector
 */
public class AwareFamilyApp {

    @Configuration
    @PropertySource("classpath:demo02/aware.properties")
    static class Cfg {
        @Bean
        public static PropertySourcesPlaceholderConfigurer placeholder() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }

    @Component
    static class AwareBean implements BeanNameAware, BeanFactoryAware, ApplicationContextAware, EnvironmentAware {

        @Value("${app.name}")
        String name;   // 字段注入点（populateBean 阶段处理）

        public AwareBean() {
            System.out.println("1. 实例化（构造器）：@Value 字段 = " + name + "   ← 字段还没注入");
        }

        @Override
        public void setBeanName(String s) {
            System.out.println("2. BeanNameAware：@Value 字段 = " + name + "   ← populateBean 已完成！");
        }

        @Override
        public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
            System.out.println("3. BeanFactoryAware：@Value 字段 = " + name);
        }

        @Override
        public void setEnvironment(Environment environment) {
            System.out.println("4. EnvironmentAware：@Value 字段 = " + name
                    + "（ApplicationContextAwareProcessor 内部先于 ContextAware）");
        }

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            System.out.println("5. ApplicationContextAware：@Value 字段 = " + name
                    + "（同一个 BPP.before 处理器）");
        }

        @PostConstruct
        public void init() {
            System.out.println("6. @PostConstruct：@Value 字段 = " + name);
        }
    }

    /**
     * 自定义 BPP：钉死"第二拨 Aware 与 BPP 链上其他处理器"的相对顺序
     */
    @Component
    static class CustomBPP implements BeanPostProcessor {

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof AwareBean aware) {
                System.out.println("-- 自定义 BPP.before 触发（第二拨 Aware 之后、@PostConstruct 之前）");
            }
            return bean;
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(Cfg.class, AwareBean.class, CustomBPP.class)) {
            System.out.println("容器就绪，Bean 可用: " + (ctx.getBean(AwareBean.class).name != null));
            System.out.println("--- getBeanNamesForType(BeanPostProcessor) 原始顺序 ---");
            for (String name : ctx.getBeanFactory().getBeanNamesForType(BeanPostProcessor.class, true, false)) {
                System.out.println("  " + name + " -> " + ctx.getBeanFactory().getType(name).getSimpleName());
            }
            System.out.println("--- BPP 链（postProcessBeforeInitialization 按此顺序执行）---");
            for (BeanPostProcessor bpp : ((AbstractBeanFactory) ctx.getBeanFactory()).getBeanPostProcessors()) {
                System.out.println("  " + bpp.getClass().getSimpleName());
            }
        }
    }
}
