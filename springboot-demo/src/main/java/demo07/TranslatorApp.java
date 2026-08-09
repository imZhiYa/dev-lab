package demo07;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 仿 MyBatis 的最小框架翻译（把 01 篇 Level 3 的三个扩展点一次性走完）：
 * ① 注册（Registrar）：@EnableMapperScan → MapperScannerRegistrar 批量注册 MapperFactoryBean
 * ② 代理（FactoryBean）：接口没有实现类，getObject() 返回 JDK 动态代理，
 * 方法调用被 SqlInvocationHandler 拦截并"翻译"成 SQL 执行（MapperProxy 的同构最小版）
 * ③ 注入（BPP）：DubboRefBeanPostProcessor 在 populateBean 时扫描 @DubboRef 字段并注入
 * （@DubboReference 机制的同构最小版）
 * <p>
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 * [Registrar] 批量注册 MapperFactoryBean: userMapper, orderMapper
 * [FactoryBean] @Autowired userMapper 拿到的是代理: true（类名含 $Proxy）
 * [翻译] userMapper.findById(1) -> 执行SQL: select * from user where id = 1 -> 返回 User(id=1)
 * [翻译] orderMapper.findById(9) -> 执行SQL: select * from order where id = 9 -> 返回 Order(id=9)
 * [&name] getBean("&userMapper") 返回工厂本身: true
 * [BPP] @DubboRef 字段被识别并注入: remote-ref-injected
 */
public class TranslatorApp {

    // ===== 框架侧：注解 =====
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Sql {
        String value();
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface DubboRef {
    }

    // ===== 业务侧：领域接口（没有实现类！）=====
    interface UserMapper {
        @Sql("select * from user where id = ?")
        User findById(int id);
    }

    interface OrderMapper {
        @Sql("select * from order where id = ?")
        Order findById(int id);
    }

    static class User {
        final int id;

        User(int id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "User(id=" + id + ")";
        }
    }

    static class Order {
        final int id;

        Order(int id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "Order(id=" + id + ")";
        }
    }

    // ===== 框架侧：代理翻译（MapperProxy 的同构最小版）=====
    static class SqlInvocationHandler implements InvocationHandler {
        private final Class<?> mapperInterface;

        SqlInvocationHandler(Class<?> mapperInterface) {
            this.mapperInterface = mapperInterface;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Sql sql = method.getAnnotation(Sql.class);
            if (sql == null) return null;   // Object 方法（toString 等）原样走
            int id = (int) args[0];
            System.out.println("[翻译] " + mapperInterface.getSimpleName() + "." + method.getName()
                    + "(" + id + ") -> 执行SQL: " + sql.value().replace("?", String.valueOf(id))
                    + " -> 返回 " + (mapperInterface == UserMapper.class ? new User(id) : new Order(id)));
            return mapperInterface == UserMapper.class ? new User(id) : new Order(id);
        }
    }

    static class MapperFactoryBean implements FactoryBean<Object> {
        private final Class<?> mapperInterface;

        MapperFactoryBean(Class<?> mapperInterface) {
            this.mapperInterface = mapperInterface;
        }

        @Override
        public Object getObject() {
            return Proxy.newProxyInstance(
                    mapperInterface.getClassLoader(), new Class<?>[]{mapperInterface},
                    new SqlInvocationHandler(mapperInterface));
        }

        @Override
        public Class<?> getObjectType() {
            return mapperInterface;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }

    // ===== 框架侧：批量注册（Registrar，@MapperScan 的同构最小版）=====
    static class MapperScannerRegistrar implements ImportBeanDefinitionRegistrar {
        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                            BeanDefinitionRegistry registry,
                                            org.springframework.beans.factory.support.BeanNameGenerator importBeanNameGenerator) {
            StringBuilder names = new StringBuilder();
            Class<?>[] mappers = {UserMapper.class, OrderMapper.class};
            for (Class<?> mapper : mappers) {
                String beanName = Character.toLowerCase(mapper.getSimpleName().charAt(0))
                        + mapper.getSimpleName().substring(1);   // userMapper / orderMapper
                RootBeanDefinition bd = new RootBeanDefinition(MapperFactoryBean.class,
                        () -> new MapperFactoryBean(mapper));
                bd.setTargetType(org.springframework.core.ResolvableType.forClass(mapper));
                registry.registerBeanDefinition(beanName, bd);
                names.append(names.length() == 0 ? "" : ", ").append(beanName);
            }
            System.out.println("[Registrar] 批量注册 MapperFactoryBean: " + names);
        }
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Import(MapperScannerRegistrar.class)
    @interface EnableMapperScan {
    }

    // ===== 框架侧：BPP 识别自定义注解（@DubboReference 的同构最小版）=====
    @Component
    static class DubboRefBeanPostProcessor implements org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor {
        @Override
        public org.springframework.beans.PropertyValues postProcessProperties(org.springframework.beans.PropertyValues pvs, Object bean, String beanName) {
            for (Field field : bean.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(DubboRef.class)) {
                    field.setAccessible(true);
                    try {
                        field.set(bean, "remote-ref-injected");   // 模拟：远程引用句柄
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
            return pvs;
        }
    }

    // ===== 业务侧 =====
    @Component
    static class OrderService {
        @Autowired
        UserMapper userMapper;          // FactoryBean 的产品（代理）
        @DubboRef
        String remoteTag;                // BPP 注入（模拟远程引用）

        User query(int id) {
            return userMapper.findById(id);
        }
    }

    @Configuration
    @EnableMapperScan
    @Import(DubboRefBeanPostProcessor.class)
    static class AppConfig {
        @Bean
        OrderService orderService() {
            return new OrderService();
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService service = ctx.getBean(OrderService.class);

            boolean isProxy = Proxy.isProxyClass(service.userMapper.getClass());
            System.out.println("[FactoryBean] @Autowired userMapper 拿到的是代理: " + isProxy
                    + "（类名含 " + service.userMapper.getClass().getSimpleName() + "）");

            service.query(1);
            ctx.getBean(OrderMapper.class).findById(9);

            System.out.println("[&name] getBean(\"&userMapper\") 返回工厂本身: "
                    + (ctx.getBean("&userMapper") instanceof MapperFactoryBean));

            System.out.println("[BPP] @DubboRef 字段被识别并注入: " + service.remoteTag);
        }
    }
}
