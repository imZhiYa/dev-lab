package demo10.destroy;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * @Bean 销毁阶段最隐蔽的坑：destroyMethod 默认推断 "(inferred)"
 * （03 篇 Level 0 的 @Bean 通道延伸）
 *
 * 机制（spring-beans 6.1.14 反编译 DisposableBeanAdapter.inferDestroyMethodsIfNecessary）：
 *   - @Bean.destroyMethod() 默认值 = AbstractBeanDefinition.INFER_METHOD = "(inferred)"
 *   - 推断触发：destroyMethodName 为 "(inferred)"，
 *     或 (destroyMethodName == null 且 beanClass 实现 AutoCloseable)——后者对
 *     XML / 组件扫描同样生效，不只 @Bean！
 *   - 推断过程：Class.getMethod("close", 无参) → 命中即 "close"（public、无参、含继承层次）；
 *     抛 NoSuchMethodException 再试 getMethod("shutdown", 无参)；都没有 → 不销毁
 *   - 已实现 DisposableBean 接口的不推断（走接口 destroy()）
 *   - @Bean(destroyMethod = "") 显式空串 = 关闭推断
 *   - 内置去重：创建阶段 InitDestroyAnnotationBeanPostProcessor$LifecycleMetadata.
 *     checkInitDestroyMethods 把 @PreDestroy 方法名注册进 mbd.externallyManagedDestroyMethods；
 *     DisposableBeanAdapter 构造器发现推断方法名已被外部管理 → 跳过推断
 *     → @PreDestroy 直接标在 close()/shutdown() 上只执行一次；
 *     @PreDestroy 标在别的名字（cleanup）上则与推断的 shutdown() 各执行一次 = 重复清理
 *
 * 实测场景（每个类在 close()/shutdown() 里打印自己的名字，关闭后看谁被调）：
 *   closableThing        普通类有 public close()           → 应被调（推断命中）
 *   closeShutdownThing   同时有 close() + shutdown()       → 应只调 close（close 优先）
 *   privateCloseThing    private close()                   → 不应被调（getMethod 只找 public）
 *   argCloseThing        public close(String) 带参         → 不应被调（getMethod 查无参）
 *   inheritedCloseThing  父类有 public close()             → 应被调（继承的 public 算）
 *   explicitEmptyThing   @Bean(destroyMethod = "")        → 不应被调（显式关闭推断）
 *   autoCloseableThing   实现 AutoCloseable                → 应被调（close 推断）
 *   shutdownOnlyThing    只有 public shutdown()            → 应被调（close 找不到 → shutdown）
 *   scanCloseThing       @Component 普通类有 public close()→ 不应被调（扫描不填 "(inferred)"）
 *   scanAutoCloseThing   @Component 实现 AutoCloseable     → 应被调（null + AutoCloseable 分支）
 *   xmlCloseThing        XML 注册普通类（不写 destroy-method）→ 不应被调
 *   xmlAutoCloseThing    XML 注册实现 AutoCloseable        → 应被调
 *   sharedA/sharedB      两个 @Bean 定义返回同一实例        → close 调 2 次（案例三）
 *   preDestroyShutdownThing @PreDestroy cleanup() + 推断 shutdown() → 两个都执行（案例四）
 *   preDestroyCloseThing @PreDestroy 标在 public close() 上 → 只执行 1 次（内置去重）
 *
 * 修复方式：显式 @Bean(destroyMethod = "") 关闭推断通道——
 *   案例三：共享实例的重复销毁，把其中一个 @Bean 置 ""（保留一个销毁回调）；
 *   案例四：@PreDestroy 已负责清理时，置 "" 取消推断通道的重复执行。
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5，销毁顺序 = 依赖逆序）：
 *   [验证] 所有 bean 已注册，开始关闭容器...
 *   [销毁] xmlAutoCloseThing: close() 被调用
 *   [销毁] preDestroyCloseThing: close() 执行 1 次（@PreDestroy 通道执行；推断通道被 externallyManaged 跳过=内置去重）
 *   [销毁] preDestroyShutdownThing: @PreDestroy cleanup() 执行
 *   [销毁] preDestroyShutdownThing: shutdown()（destroyMethod 推断通道）执行
 *   [销毁] sharedCloseThing: close() 第 1 次被调用（同一实例两个 bean 定义）
 *   [销毁] sharedCloseThing: close() 第 2 次被调用（同一实例两个 bean 定义）
 *   [销毁] sharedCloseThing: 资源已释放——重复 close 抛异常，Spring 捕获记 WARN，不中断后续销毁
 *   [销毁] shutdownOnlyThing: shutdown() 被调用
 *   [销毁] autoCloseableThing: close() 被调用
 *   [销毁] inheritedCloseThing: 父类 close() 被调用
 *   [销毁] closeShutdownThing: close() 被调用
 *   [销毁] closableThing: close() 被调用
 *   [销毁] scanAutoCloseThing: close() 被调用
 *   [验证] 容器关闭完成。被调用的销毁方法如上；未出现的 = 未推断
 *
 * 结果判读（15 场景）：
 *   被调 10 个：closableThing / closeShutdownThing(只 close) / inheritedCloseThing /
 *              autoCloseableThing / shutdownOnlyThing / scanAutoCloseThing / xmlAutoCloseThing /
 *              sharedCloseThing(×2，案例三) / preDestroyShutdownThing(两方法，案例四) /
 *              preDestroyCloseThing(1 次，内置去重)
 *   未调 5 个：privateCloseThing / argCloseThing / explicitEmptyThing /
 *              scanCloseThing / xmlCloseThing
 */
public class DestroyInferenceApp {

    @SpringBootApplication
    @Configuration
    @org.springframework.context.annotation.ImportResource("classpath:demo10/destroy/beans.xml")
    static class BootConfig {
        @Bean
        ClosableThing closableThing() {
            return new ClosableThing();
        }

        @Bean
        CloseShutdownThing closeShutdownThing() {
            return new CloseShutdownThing();
        }

        @Bean
        PrivateCloseThing privateCloseThing() {
            return new PrivateCloseThing();
        }

        @Bean
        ArgCloseThing argCloseThing() {
            return new ArgCloseThing();
        }

        @Bean
        InheritedCloseThing inheritedCloseThing() {
            return new InheritedCloseThing();
        }

        @Bean(destroyMethod = "")
        ExplicitEmptyThing explicitEmptyThing() {
            return new ExplicitEmptyThing();
        }

        @Bean
        AutoCloseableThing autoCloseableThing() {
            return new AutoCloseableThing();
        }

        @Bean
        ShutdownOnlyThing shutdownOnlyThing() {
            return new ShutdownOnlyThing();
        }

        private static final SharedCloseThing SHARED = new SharedCloseThing();

        @Bean
        SharedCloseThing sharedA() {
            return SHARED;
        }

        @Bean
        SharedCloseThing sharedB() {
            return SHARED;
        }

        @Bean
        PreDestroyShutdownThing preDestroyShutdownThing() {
            return new PreDestroyShutdownThing();
        }

        @Bean
        PreDestroyCloseThing preDestroyCloseThing() {
            return new PreDestroyCloseThing();
        }
    }

    static class ClosableThing {
        public void close() {
            System.out.println("[销毁] closableThing: close() 被调用");
        }
    }

    static class CloseShutdownThing {
        public void close() {
            System.out.println("[销毁] closeShutdownThing: close() 被调用");
        }

        public void shutdown() {
            System.out.println("[销毁] closeShutdownThing: shutdown() 被调用");
        }
    }

    static class PrivateCloseThing {
        private void close() {
            System.out.println("[销毁] privateCloseThing: private close() 被调用");
        }
    }

    static class ArgCloseThing {
        public void close(String reason) {
            System.out.println("[销毁] argCloseThing: close(String) 被调用");
        }
    }

    static class BaseCloseThing {
        public void close() {
            System.out.println("[销毁] inheritedCloseThing: 父类 close() 被调用");
        }
    }

    static class InheritedCloseThing extends BaseCloseThing {
    }

    static class ExplicitEmptyThing {
        public void close() {
            System.out.println("[销毁] explicitEmptyThing: close() 被调用");
        }
    }

    static class AutoCloseableThing implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("[销毁] autoCloseableThing: close() 被调用");
        }
    }

    static class ShutdownOnlyThing {
        public void shutdown() {
            System.out.println("[销毁] shutdownOnlyThing: shutdown() 被调用");
        }
    }

    @Component
    static class ScanCloseThing {
        public void close() {
            System.out.println("[销毁] scanCloseThing: close() 被调用");
        }
    }

    @Component
    static class ScanAutoCloseThing implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("[销毁] scanAutoCloseThing: close() 被调用");
        }
    }

    public static class XmlCloseThing {
        public void close() {
            System.out.println("[销毁] xmlCloseThing: close() 被调用");
        }
    }

    public static class XmlAutoCloseThing implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("[销毁] xmlAutoCloseThing: close() 被调用");
        }
    }

    static class SharedCloseThing {
        private Object resource = new Object();
        private int closed = 0;

        public void close() {
            closed++;
            System.out.println("[销毁] sharedCloseThing: close() 第 " + closed + " 次被调用（同一实例两个 bean 定义）");
            if (resource == null) {
                System.out.println("[销毁] sharedCloseThing: 资源已释放——重复 close 抛异常，Spring 捕获记 WARN，不中断后续销毁");
                throw new IllegalStateException("重复 close：" + this);
            }
            resource = null;
        }
    }

    static class PreDestroyShutdownThing {
        @jakarta.annotation.PreDestroy
        void cleanup() {
            System.out.println("[销毁] preDestroyShutdownThing: @PreDestroy cleanup() 执行");
        }

        public void shutdown() {
            System.out.println("[销毁] preDestroyShutdownThing: shutdown()（destroyMethod 推断通道）执行");
        }
    }

    static class PreDestroyCloseThing {
        @jakarta.annotation.PreDestroy
        public void close() {
            System.out.println("[销毁] preDestroyCloseThing: close() 执行 1 次（@PreDestroy 通道执行；推断通道被 externallyManaged 跳过=内置去重）");
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.setDefaultProperties(java.util.Map.of("server.port", "0"));
        ConfigurableApplicationContext ctx;
        try {
            ctx = app.run();
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(t);
        }

        System.out.println("[验证] 所有 bean 已注册，开始关闭容器...");
        ctx.close();
        System.out.println("[验证] 容器关闭完成。被调用的销毁方法如上；未出现的 = 未推断");
    }
}
