package demo15;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Boot 3.3.5 默认循环依赖行为定案：
 * 裸 Framework（6.1.14）默认 allowCircularReferences=true（demo04 已实测）；
 * Boot 从 2.6 起在 SpringApplication 启动路径上显式关闭（文档语义）。
 * 本实验：同样两个字段环组件，走 Boot 启动路径（web-application-type=none），
 * 默认参数下是否启动失败。
 *
 * 预期结论（2026-08-06 本机实测）：
 *   默认参数 → [结果] 字段环启动失败: BeanCurrentlyInCreationException
 *   消息首行: Error creating bean with name 'bootCircularApp.A': Requested bean is
 *       currently in creation: Is there an unresolvable circular reference?
 *   → Boot 3.3.5 默认关闭循环依赖；与 demo04 对照：裸 Framework 默认允许。
 *   传 --spring.main.allow-circular-references=true → [结果] 字段环启动成功
 *   → 放行开关实测生效（3.3.5 属性绑定链：prepareEnvironment → bindToSpringApplication
 *     → Binder.bind("spring.main", Bindable.ofInstance(this)) → setAllowCircularReferences
 *     → run 阶段 BeanFactory.setAllowCircularReferences）。
 * 注：BootCircularApp 与 RefreshFailApp 同包，@ComponentScan 会互相扫到，
 *     故 excludeFilters 排除 demo15.RefreshFailApp 隔离（否则放行后会被
 *     RefreshFailApp 的 boom Bean 炸掉，污染结果）。
 */
@SpringBootApplication
@ComponentScan(excludeFilters =
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RefreshFailApp.class))
public class BootCircularApp {

    @Component
    static class A {
        @Autowired
        B b;
    }

    @Component
    static class B {
        @Autowired
        A a;
    }

    @Bean
    String marker() {
        return "boot-circular-app";
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.WARNING);
        String[] allArgs = new String[args.length + 1];
        allArgs[0] = "--spring.main.web-application-type=none";
        System.arraycopy(args, 0, allArgs, 1, args.length);
        try {
            SpringApplication.run(BootCircularApp.class, allArgs);
            System.out.println("[结果] 字段环启动成功");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            System.out.println("[结果] 字段环启动失败: " + root.getClass().getSimpleName());
            String first = root.getMessage() != null ? root.getMessage().split("\n")[0] : "";
            System.out.println("消息首行: " + first);
        }
    }
}
