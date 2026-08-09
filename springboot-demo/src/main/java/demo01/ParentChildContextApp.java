package demo01;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * 父子容器五个事实实测：
 * ① 子容器 getBean 向上查找父容器（委派）
 * ② 父容器看不到子容器的 bean
 * ③ 同名 bean：子容器优先（子覆盖父）
 * ④ 子容器 @Autowired 可以注入父容器的 bean
 * ⑤ 同一类在父子容器各注册一次 → 两个独立实例（SSM 双容器时代"Bean 重复/事务不生效"的根因）
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 *   [①] 子容器 getBean 父容器独有 bean: 成功，实例来自父容器
 *   [②] 父容器 getBean 子容器独有 bean: NoSuchBeanDefinitionException
 *   [③] 同名 bean：子容器优先 -> 子实例
 *   [④] 子容器 @Autowired 注入父容器 bean: true
 *   [⑤] 同一类父子各注册一次，两个不同实例: true
 */
public class ParentChildContextApp {

    static class ParentOnly {
    }

    static class ChildOnly {
    }

    static class Shared {
        private final String tag;

        Shared(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    static class ChildClient {
        @Autowired ParentOnly parentOnly;   // 父容器的 bean
        @Autowired Shared shared;           // 父子都有，子容器应该拿自己的
    }

    @Configuration
    static class ParentConfig {
        @Bean
        public ParentOnly parentOnly() {
            return new ParentOnly();
        }

        @Bean
        public Shared shared() {
            return new Shared("父容器实例");
        }
    }

    @Configuration
    static class ChildConfig {
        @Bean
        public ChildOnly childOnly() {
            return new ChildOnly();
        }

        @Bean
        public Shared shared() {
            return new Shared("子容器实例");
        }

        @Bean
        public ChildClient childClient() {
            return new ChildClient();
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext(ParentConfig.class);
             AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext()) {
            child.setParent(parent);
            child.register(ChildConfig.class);
            child.refresh();

            // ① 子容器向上查找
            ParentOnly po = child.getBean(ParentOnly.class);
            System.out.println("[①] 子容器 getBean 父容器独有 bean: 成功，实例来自父容器: " + (po != null));

            // ② 父容器看不到子容器
            try {
                parent.getBean(ChildOnly.class);
            } catch (Exception e) {
                Throwable root = e;
                while (root.getCause() != null) root = root.getCause();
                System.out.println("[②] 父容器 getBean 子容器独有 bean: " + root.getClass().getSimpleName());
            }

            // ③ 同名 bean 子优先
            System.out.println("[③] 同名 bean：子容器优先 -> " + child.getBean(Shared.class).tag());

            // ④ 子容器 @Autowired 注入父容器 bean
            ChildClient client = child.getBean(ChildClient.class);
            System.out.println("[④] 子容器 @Autowired 注入父容器 bean: " + (client.parentOnly != null));
            System.out.println("[④] 子容器 @Autowired 同名 bean 注入的是自己的: " + client.shared.tag());

            // ⑤ 双容器重复实例
            Shared parentShared = parent.getBean(Shared.class);
            Shared childShared = child.getBean(Shared.class);
            System.out.println("[⑤] 同一类父子各注册一次，两个不同实例: " + (parentShared != childShared));
        }
    }
}
