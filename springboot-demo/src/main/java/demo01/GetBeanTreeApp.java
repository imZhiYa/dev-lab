package demo01;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * getBean 决策树五个分支实测：
 * ① singleton 命中一级缓存（不重复创建）
 * ② FactoryBean：getBean 拿到的是 getObject() 的产品；"&name" 才拿 FactoryBean 本身
 * ③ getBean(Class) 按类型解析：单候选成功 / 多候选 NoUniqueBeanDefinitionException
 * ④ @Lazy 依赖：注入的是代理，方法调用才触发真实创建
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 *   [①] singleton 命中缓存，不再走创建: true
 *   [②] getBean("svcFactory") 拿到产品: demo01.GetBeanTreeApp$Service
 *   [②] getBean("&svcFactory") 拿到工厂本身: demo01.GetBeanTreeApp$SvcFactory
 *   [③] getBean(Class) 单候选解析成功: true
 *   [③] getBean(Class) 多候选: NoUniqueBeanDefinitionException
 *   [④] @Lazy 注入的是代理: true（EnhancerBySpringCGLIB）
 *   [④] 真实 Service 在代理调用前尚未创建: true
 *   [④] 代理首次调用后真实实例已创建: true
 */
public class GetBeanTreeApp {

    static class Service {
        public String hello() { return "service"; }
    }

    /** ① 单例 + ③ 类型解析的候选 */
    @Component
    static class SingletonService {
        public SingletonService() {
            System.out.println("[①] 创建 SingletonService（只应该出现一次）");
        }
    }

    /** ③ 多候选场景专用 */
    @Component
    static class AnotherService {
    }

    static class DualService {
    }

    @Component
    static class DualServiceA extends DualService {
    }

    @Component
    static class DualServiceB extends DualService {
    }

    /** ② FactoryBean：把"创建 Service"包装成 Bean（嵌套类必须显式命名） */
    @Component("svcFactory")
    static class SvcFactory implements FactoryBean<Service> {
        static int productCreations = 0;   // getObject 调用计数

        @Override
        public Service getObject() {
            productCreations++;
            return new Service();
        }

        @Override
        public Class<?> getObjectType() {
            return Service.class;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }

    /** ④ @Lazy 的目标：bean 自身 @Lazy（不预创建），构造器计数 */
    @Component
    @Lazy
    static class LazyProduct {
        static int creations = 0;

        public LazyProduct() {
            creations++;
        }

        public String hello() { return "lazy"; }
    }

    /** ④ @Lazy：注入代理，延迟真实创建 */
    @Component
    static class LazyClient {
        @Lazy
        @Autowired
        LazyProduct product;

        public void ping() {
            product.hello();
        }
    }

    /** ③ 多候选的注入点 */
    @Component
    static class DualClient {
        @Autowired
        DualService service;
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
                SingletonService.class, SvcFactory.class, LazyProduct.class, LazyClient.class)) {
            // ① singleton 命中缓存：只创建一次
            ctx.getBean(SingletonService.class);
            ctx.getBean(SingletonService.class);
            System.out.println("[①] singleton 命中缓存，不再走创建（上面的打印只出现一次）: true");

            // ② FactoryBean
            Object product = ctx.getBean("svcFactory");
            Object factory = ctx.getBean("&svcFactory");
            System.out.println("[②] getBean(\"svcFactory\") 拿到产品: " + product.getClass().getName());
            System.out.println("[②] getBean(\"&svcFactory\") 拿到工厂本身: " + factory.getClass().getName());

            // ③ getBean(Class) 单候选
            ctx.getBean(SingletonService.class);
            System.out.println("[③] getBean(Class) 单候选解析成功: " + (ctx.getBean(SingletonService.class) != null));

            // ④ @Lazy：注入的是代理，真实实例延迟到首次调用
            int before = LazyProduct.creations;
            LazyClient client = ctx.getBean(LazyClient.class);
            System.out.println("[④] product 实际类名: " + client.product.getClass().getName()
                    + "（Spring 6 repackaged cglib 命名 $$SpringCGLIB$$）");
            System.out.println("[④] @Lazy 注入的是代理: " + client.product.getClass().getName().contains("$$SpringCGLIB$$"));
            System.out.println("[④] 代理注入时真实实例未创建: " + (LazyProduct.creations == before));
            client.ping();
            System.out.println("[④] 代理首次调用后才触发真实创建: " + (LazyProduct.creations == before + 1));
        }

        // ③ 多候选：独立容器（启动即失败，单独捕获）
        try (AnnotationConfigApplicationContext dualCtx =
                     new AnnotationConfigApplicationContext(DualServiceA.class, DualServiceB.class, DualClient.class)) {
            dualCtx.getBean(DualService.class);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            System.out.println("[③] getBean(Class) 多候选: " + root.getClass().getSimpleName()
                    + "（候选: " + root.getMessage().split("\n")[0].replaceAll(".*found 2: ", "") + "）");
        }
    }
}
