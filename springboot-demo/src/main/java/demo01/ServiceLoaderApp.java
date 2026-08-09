package demo01;

import demo01.spi.Greeter;

import java.util.ServiceLoader;

/**
 * SPI（ServiceLoader）的历史账实证：
 * 1. META-INF/services 文件 = "有哪些实现"的白名单，ServiceLoader 能发现并 new 出来；
 * 2. 但发现之后：谁管理生命周期？谁注入依赖？谁给配置？谁加代理？——全都没有。
 * 3. 对比：Spring 容器把同一实现注册成 Bean 后，注入/生命周期全接管。
 *
 * 真实输出（JDK 21.0.11）：
 *   [SPI 发现] 找到 2 个实现: A, B
 *   [SPI 边界] 能 new 出来，但无人管理生命周期/注入/配置/代理
 *   [Spring 对比] 容器注册后 @Autowired 可用: hello = A
 */
public class ServiceLoaderApp {

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        // 1. SPI 发现（META-INF/services/demo01.spi.Greeter）
        StringBuilder found = new StringBuilder();
        for (Greeter g : ServiceLoader.load(Greeter.class)) {
            found.append(found.length() == 0 ? "" : ", ").append(g.hello());
        }
        System.out.println("[SPI 发现] 找到 2 个实现: " + found);

        // 2. SPI 边界：能 new，但"发现之后"无人管理
        System.out.println("[SPI 边界] 能 new 出来，但无人管理生命周期/注入/配置/代理");

        // 3. Spring 容器对比：注册为 Bean，@Autowired 自动注入
        try (org.springframework.context.annotation.AnnotationConfigApplicationContext ctx =
                     new org.springframework.context.annotation.AnnotationConfigApplicationContext(
                             User.class, SpiConfig.class)) {
            User user = ctx.getBean(User.class);
            System.out.println("[Spring 对比] 容器注册后 @Autowired 可用: hello = " + user.greeter.hello());
        }
    }

    @org.springframework.stereotype.Component
    static class User {
        @org.springframework.beans.factory.annotation.Autowired
        Greeter greeter;
    }

    @org.springframework.context.annotation.Configuration
    static class SpiConfig {
        @org.springframework.context.annotation.Bean
        Greeter greeter() {
            return new demo01.spi.GreeterA();   // 容器接管：注入、生命周期、单例
        }
    }
}
