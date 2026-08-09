package demo15;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * refresh 失败后容器状态（P-BS08 对应实验）：
 * 单例实例化阶段（finishBeanFactoryInitialization）某个 Bean 的
 * @PostConstruct 抛异常 → refresh 整体失败。
 * 观察：
 *   1. 异常类型与失败位置；
 *   2. 失败前已创建的单例是否被清理（refresh 的 catch 分支 destroyBeans）；
 *   3. 失败后容器 isActive 状态、再 getBean 的行为；
 *   4. 失败后 close 再 refresh 能否重启。
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14，本机）：
 *   [创建] first(失败前) 构造完成
 *   [创建] Boom 进入 @PostConstruct
 *   [销毁] first(失败前)          ← refresh 失败分支自动销毁已创建单例
 *   [1] refresh 失败，根因: IllegalStateException : Boom: 初始化爆炸
 *   [2] 失败后 ctx.isActive() = false
 *   [3] 失败后 getBean(first) 抛: IllegalStateException : ... has not been refreshed yet
 *   [4] 失败后 getBean(last) 抛: IllegalStateException : ... has not been refreshed yet
 *   [5] close() 完成（无异常）
 *   [6] close 后再 refresh 抛: IllegalStateException : GenericApplicationContext does
 *       not support multiple refresh attempts: just call 'refresh' once
 */
@Configuration
public class RefreshFailApp {

    static class Orderly {
        private final String name;

        Orderly(String name) {
            this.name = name;
            System.out.println("[创建] " + name + " 构造完成");
        }

        @PreDestroy
        void bye() {
            System.out.println("[销毁] " + name);
        }
    }

    static class Boom {
        @PostConstruct
        void kaboom() {
            System.out.println("[创建] Boom 进入 @PostConstruct");
            throw new IllegalStateException("Boom: 初始化爆炸");
        }
    }

    @Bean
    Orderly first() {
        return new Orderly("first(失败前)");
    }

    @Bean
    Boom boom() {
        return new Boom();
    }

    @Bean
    Orderly last() {
        return new Orderly("last(失败后)");
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(RefreshFailApp.class);

        try {
            ctx.refresh();
            System.out.println("（不该到达）refresh 成功");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            System.out.println("[1] refresh 失败，根因: " + root.getClass().getSimpleName()
                    + " : " + (root.getMessage() != null ? root.getMessage().split("\n")[0] : ""));
        }

        System.out.println("[2] 失败后 ctx.isActive() = " + ctx.isActive());

        try {
            Object b = ctx.getBean("first");
            System.out.println("[3] 失败后 getBean(first) 成功: " + b.getClass().getSimpleName());
        } catch (Exception e) {
            System.out.println("[3] 失败后 getBean(first) 抛: " + e.getClass().getSimpleName()
                    + " : " + (e.getMessage() != null ? e.getMessage().split("\n")[0] : ""));
        }

        try {
            Object b = ctx.getBean("last");
            System.out.println("[4] 失败后 getBean(last) 成功: " + b.getClass().getSimpleName());
        } catch (Exception e) {
            System.out.println("[4] 失败后 getBean(last) 抛: " + e.getClass().getSimpleName()
                    + " : " + (e.getMessage() != null ? e.getMessage().split("\n")[0] : ""));
        }

        ctx.close();
        System.out.println("[5] close() 完成（无异常）");

        try {
            ctx.refresh();
            System.out.println("[6] close 后再 refresh 成功");
            ctx.close();
        } catch (Exception e) {
            System.out.println("[6] close 后再 refresh 抛: " + e.getClass().getSimpleName()
                    + " : " + (e.getMessage() != null ? e.getMessage().split("\n")[0] : ""));
        }
    }
}
