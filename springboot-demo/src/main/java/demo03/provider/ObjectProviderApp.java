package demo03.provider;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * ObjectProvider：把"容器查找"从注入阶段推迟到调用时刻（00 篇 3.3.5）
 *
 * 机制（spring-beans 6.1.14）：
 *   - doResolveDependency 识别注入点类型是 ObjectProvider → 返回"懒引用"，
 *     不解析目标 bean；getObject()/getIfAvailable() 等调用时才走完整解析链
 *     （@Primary/@Qualifier/@Order 全生效）
 *   - ObjectProvider 接口方法（反编译确认）：getObject / getIfAvailable /
 *     getIfAvailable(Supplier) / ifAvailable(Consumer) / getIfUnique /
 *     stream() / orderedStream()
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5）：
 *   [场景5] TimingConsumer 构造器执行 = ObjectProvider 已注入，LazyService 未创建
 *   [场景1] 无 bean：getIfAvailable() = null
 *   [场景1] 无 bean：getObject() 抛 NoSuchBeanDefinitionException
 *   [场景2] 无 bean：getIfAvailable(默认值) = 00000000-0000-0000-0000-000000000000
 *   [场景3] 三候选（含 @Primary）：stream() 拿到 3 个 = [A, B, B(@Primary)]
 *   [场景3] 三候选（含 @Primary）：getIfUnique() = B(@Primary)（多候选但有唯一 @Primary → 返回 @Primary）
 *   [场景3b] 两候选（无 @Primary）：stream() 拿到 2 个 = [solo-A, solo-B]
 *   [场景3b] 两候选（无 @Primary）：getIfUnique() = null（多候选且无 @Primary → null）
 *   [场景4] 有 @Primary：getIfAvailable() = B(@Primary)
 *   [场景5] LazyService 构造器执行 = 被创建（getObject() 触发）
 *   [场景5] getObject() 调用时：lazy = LazyService
 *   [场景6] ifAvailable(Consumer)：
 *           有 bean 才消费 = 消费了:B(@Primary)
 *
 * 两个实测钉死的机制细节（八股易错）：
 *   1. getIfUnique() 不是"多候选一律 null"——多候选但有唯一 @Primary 时返回 @Primary；
 *      无 @Primary 的多候选才返回 null（requireUnique 语义：先按优先级挑"最优唯一"）
 *   2. ObjectProvider 只延迟"解析动作"，不延迟"单例创建"——普通 @Component 单例
 *      在 preInstantiateSingletons 照样创建；要"调用才创建"需配合 @Lazy（场景5 即 @Lazy 单例）
 */
public class ObjectProviderApp {

    interface Greeter {
        String hello();
    }

    @Component("greeterA")
    static class GreeterA implements Greeter {
        public String hello() {
            return "A";
        }
    }

    @Component("greeterB")
    static class GreeterB implements Greeter {
        public String hello() {
            return "B";
        }
    }

    @Component
    @Primary
    static class GreeterBPrime implements Greeter {
        public String hello() {
            return "B(@Primary)";
        }
    }

    @Component
    static class ConsumerBean {
        final ObjectProvider<Greeter> greeterProvider;

        ConsumerBean(ObjectProvider<Greeter> greeterProvider) {
            this.greeterProvider = greeterProvider;
        }
    }

    @Component
    @org.springframework.context.annotation.Lazy
    static class LazyService {
        LazyService() {
            System.out.println("[场景5] LazyService 构造器执行 = 被创建（getObject() 触发）");
        }
    }

    @Component
    static class TimingConsumer {
        final ObjectProvider<LazyService> lazyProvider;

        TimingConsumer(ObjectProvider<LazyService> lazyProvider) {
            this.lazyProvider = lazyProvider;
            System.out.println("[场景5] TimingConsumer 构造器执行 = ObjectProvider 已注入，LazyService 未创建");
        }

        void trigger() {
            System.out.println("[场景5] getObject() 调用时：lazy = " + lazyProvider.getObject().getClass().getSimpleName());
        }
    }

    interface Solo {
        String tag();
    }

    @Component("soloA")
    static class SoloA implements Solo {
        public String tag() {
            return "solo-A";
        }
    }

    @Component("soloB")
    static class SoloB implements Solo {
        public String tag() {
            return "solo-B";
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(Launcher.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.setDefaultProperties(java.util.Map.of("server.port", "0"));
        org.springframework.context.ConfigurableApplicationContext ctx;
        try {
            ctx = app.run();
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(t);
        }

        runScenes(ctx);
        ctx.close();
    }

    @SpringBootApplication
    static class Launcher {
    }

    static void runScenes(org.springframework.context.ConfigurableApplicationContext ctx) {
        scene1(ctx);
        scene2(ctx);
        scene3(ctx);
        scene4(ctx);
        scene5(ctx);
        scene6(ctx);
    }

    static void scene1(org.springframework.context.ConfigurableApplicationContext ctx) {
        ObjectProvider<java.util.UUID> provider = ctx.getBeanProvider(java.util.UUID.class);
        System.out.println("[场景1] 无 bean：getIfAvailable() = " + provider.getIfAvailable());
        try {
            provider.getObject();
        } catch (NoSuchBeanDefinitionException e) {
            System.out.println("[场景1] 无 bean：getObject() 抛 NoSuchBeanDefinitionException");
        }
    }

    static void scene2(org.springframework.context.ConfigurableApplicationContext ctx) {
        ObjectProvider<java.util.UUID> provider = ctx.getBeanProvider(java.util.UUID.class);
        java.util.UUID fallback = provider.getIfAvailable(() -> java.util.UUID.fromString("00000000-0000-0000-0000-000000000000"));
        System.out.println("[场景2] 无 bean：getIfAvailable(默认值) = " + fallback);
    }

    static void scene3(org.springframework.context.ConfigurableApplicationContext ctx) {
        ObjectProvider<Greeter> provider = ctx.getBeanProvider(Greeter.class);
        java.util.List<String> all = new java.util.ArrayList<>();
        provider.stream().forEach(g -> all.add(g.hello()));
        System.out.println("[场景3] 三候选（含 @Primary）：stream() 拿到 " + all.size() + " 个 = " + all);
        Greeter unique = provider.getIfUnique();
        System.out.println("[场景3] 三候选（含 @Primary）：getIfUnique() = " + (unique == null ? "null" : unique.hello())
                + "（多候选但有唯一 @Primary → 返回 @Primary）");

        ObjectProvider<Solo> soloProvider = ctx.getBeanProvider(Solo.class);
        java.util.List<String> solos = new java.util.ArrayList<>();
        soloProvider.stream().forEach(s -> solos.add(s.tag()));
        System.out.println("[场景3b] 两候选（无 @Primary）：stream() 拿到 " + solos.size() + " 个 = " + solos);
        Solo soloUnique = soloProvider.getIfUnique();
        System.out.println("[场景3b] 两候选（无 @Primary）：getIfUnique() = " + soloUnique + "（多候选且无 @Primary → null）");
    }

    static void scene4(org.springframework.context.ConfigurableApplicationContext ctx) {
        ObjectProvider<Greeter> provider = ctx.getBeanProvider(Greeter.class);
        System.out.println("[场景4] 有 @Primary：getIfAvailable() = " + provider.getIfAvailable().hello());
    }

    static void scene5(org.springframework.context.ConfigurableApplicationContext ctx) {
        TimingConsumer consumer = ctx.getBean(TimingConsumer.class);
        consumer.trigger();
    }

    static void scene6(org.springframework.context.ConfigurableApplicationContext ctx) {
        ObjectProvider<Greeter> provider = ctx.getBeanProvider(Greeter.class);
        System.out.println("[场景6] ifAvailable(Consumer)：");
        provider.ifAvailable(g -> System.out.println("        有 bean 才消费 = 消费了:" + g.hello()));
    }
}
