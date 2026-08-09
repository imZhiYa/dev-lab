package demo01;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;

/**
 * DefaultListableBeanFactory 的原始用法（不经过注解扫描、不经过 Boot）。
 * 展示 IoC 容器的三件套：
 *   注册表（registerBeanDefinition，存"说明书"而非对象）
 *   + 反射创建（getBean）
 *   + 单例池（singleton scope 复用 / prototype 每次新实例）
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 *   [注册] 只注册了"怎么创建"的说明书（BeanDefinition），此时没有对象
 *   [创建] getBean("userService") 触发反射实例化
 *   singleton scope 两次 getBean 同一实例: true
 *   prototype scope 两次 getBean 不同实例: true
 *   单例池当前数量: 2（orderService 因属性引用也被创建入池）
 */
public class DefaultListableBeanFactoryApp {

    public static void main(String[] args) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();

        // ① 注册"说明书"（元数据），而不是对象——容器此刻没有任何实例
        RootBeanDefinition orderDef = new RootBeanDefinition(OrderService.class);
        factory.registerBeanDefinition("orderService", orderDef);

        RootBeanDefinition userDef = new RootBeanDefinition(UserService.class);
        MutablePropertyValues pv = new MutablePropertyValues();
        pv.add("orderService", new RuntimeBeanReference("orderService")); // 属性引用另一个 Bean 名
        userDef.setPropertyValues(pv);
        factory.registerBeanDefinition("userService", userDef);

        System.out.println("[注册] 只注册了 BeanDefinition 说明书，单例池当前数量: " + factory.getSingletonCount());

        // ② getBean 才真正反射创建
        UserService s1 = factory.getBean("userService", UserService.class);
        UserService s2 = factory.getBean("userService", UserService.class);
        System.out.println("singleton scope 两次 getBean 同一实例: " + (s1 == s2));
        System.out.println("依赖被注入: " + (s1.getOrderService() != null));
        System.out.println("单例池当前数量: " + factory.getSingletonCount());

        // ③ prototype scope：每次 getBean 都新建
        RootBeanDefinition protoDef = new RootBeanDefinition(OrderService.class);
        protoDef.setScope(BeanDefinition.SCOPE_PROTOTYPE);
        factory.registerBeanDefinition("orderProto", protoDef);
        OrderService p1 = factory.getBean("orderProto", OrderService.class);
        OrderService p2 = factory.getBean("orderProto", OrderService.class);
        System.out.println("prototype scope 两次 getBean 不同实例: " + (p1 != p2));
    }
}

/** 属性注入版（本 demo 专用）：无参构造器 + setter，供 BeanDefinition 属性引用注入 */
class UserService {
    private OrderService orderService;

    public UserService() {
    }

    public void setOrderService(OrderService orderService) {
        this.orderService = orderService;
    }

    public OrderService getOrderService() { return orderService; }
}
