package demo18;

import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.GeneratedFiles;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.annotation.Reflective;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.InputStreamSource;
import org.springframework.javapoet.ClassName;
import org.springframework.stereotype.Component;

/**
 * Spring AOT 引擎端到端实证（07 篇 7.4 的 Validation）。
 *
 * 机制（spring-context 6.1.14，无需 GraalVM）：
 *   - Spring AOT = 构建期分析工具：把"运行时解析 bean 定义"的工作在构建期做完，
 *     生成"直接注册代码"（BeanFactoryInitializationAotProcessor 等）。
 *   - 本 demo 复刻 Boot maven 插件 process-aot 的核心调用：
 *     GenericApplicationContext（只加载 bean 定义，不 refresh）
 *       → ApplicationContextAotGenerator.processAheadOfTime(context, generationContext)
 *       → 生成 ApplicationContextInitializer 源码 + RuntimeHints（反射/代理/资源）
 *   - 观察点：
 *       ① 生成代码里 bean 注册变成"直接 registerBean/构造调用"，不再靠运行时
 *          注解扫描与反射（00 篇创建链第 2 步被前移到构建期）；
 *       ② @Reflective 注解 → RuntimeHints.reflection() 收集反射需求
 *          （native image 下反射不再天然可用，hint 是配套解药）；
 *       ③ @Configuration 的 CGLIB 代理需求 → ProxyHints。
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14，本机）：
 *   [文件:CLASS] demo18/AotGenerationApp$Cfg$$SpringCGLIB$$0.class（构建期生成的 CGLIB 代理字节码）
 *   [文件:CLASS] demo18/AotGenerationApp$Cfg$$SpringCGLIB$$FastClass$$0/1.class
 *   [AOT] 生成初始器类名: demo18.generated.AotGenerated__ApplicationContextInitializer
 *   [AOT] RuntimeHints 反射类型数: 170（Boot 基础设施 + Cfg$$SpringCGLIB$$0/FastClass 等）
 *   [AOT] JDK 代理 hint 总数: 0（CGLIB 走构建期字节码生成，不注册 JDK 代理 hint）
 *   生成 5 个源码文件，核心内容：
 *     AotGenerated__BeanFactoryRegistrations.registerBeanDefinitions():
 *       registerBeanDefinition("aotGenerationApp.Cfg", Cfg.getCfgBeanDefinition());
 *       registerBeanDefinition("greeter", Cfg.getGreeterBeanDefinition());   ← 组件扫描/工厂方法全部编译成直接注册
 *     Cfg.getCfgBeanDefinition():
 *       setInstanceSupplier(AotGenerationApp$Cfg$$SpringCGLIB$$0::new);     ← 代理类构建期生成，直接方法引用
 *     Greeter.getGreeterBeanDefinition():
 *       setInstanceSupplier(AotGenerationApp.Greeter::new);                 ← 扫描结果 → 构造器引用
 */
public class AotGenerationApp {

    @Configuration
    @ComponentScan(basePackageClasses = AotGenerationApp.class)
    static class Cfg {

        @Bean
        public Greeter greeter() {
            return new Greeter();
        }
    }

    @Component
    @Reflective
    static class Greeter {

        public String hello() {
            return "hi";
        }
    }

    /** 打印式 GeneratedFiles：每次生成源码文件就输出到控制台 */
    static class PrintingFiles implements GeneratedFiles {

        @Override
        public void addSourceFile(org.springframework.javapoet.JavaFile javaFile) {
            System.out.println("======== 生成的源码文件 ========");
            System.out.println(javaFile.toString());
        }

        @Override
        public void addSourceFile(String name, CharSequence content) {
            System.out.println("======== 生成的源码文件: " + name + " ========");
            System.out.println(content);
        }

        @Override
        public void addSourceFile(String name, org.springframework.util.function.ThrowingConsumer<java.lang.Appendable> writer) {
            java.lang.StringBuilder sb = new StringBuilder();
            try {
                writer.accept(sb);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            addSourceFile(name, sb.toString());
        }

        @Override
        public void addSourceFile(String name, InputStreamSource content) {
            addSourceFile(name, "(流式内容，不展开)");
        }

        @Override
        public void addResourceFile(String name, CharSequence content) {
            System.out.println("[资源文件] " + name + " = " + content);
        }

        @Override
        public void addResourceFile(String name, org.springframework.util.function.ThrowingConsumer<java.lang.Appendable> writer) {
            java.lang.StringBuilder sb = new StringBuilder();
            try {
                writer.accept(sb);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            addResourceFile(name, sb.toString());
        }

        @Override
        public void addResourceFile(String name, InputStreamSource content) {
            System.out.println("[资源文件] " + name + " = (流式)");
        }

        @Override
        public void addClassFile(String name, InputStreamSource content) {
            System.out.println("[类文件] " + name + "（AOT 生成的字节码/资源）");
        }

        @Override
        public void addFile(GeneratedFiles.Kind kind, String name, CharSequence content) {
            System.out.println("[文件:" + kind + "] " + name + " = " + content);
        }

        @Override
        public void addFile(GeneratedFiles.Kind kind, String name, org.springframework.util.function.ThrowingConsumer<java.lang.Appendable> writer) {
            java.lang.StringBuilder sb = new StringBuilder();
            try {
                writer.accept(sb);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            addFile(kind, name, sb.toString());
        }

        @Override
        public void addFile(GeneratedFiles.Kind kind, String name, InputStreamSource content) {
            System.out.println("[文件:" + kind + "] " + name + " = (流式)");
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        GenericApplicationContext context = new GenericApplicationContext();
        AnnotatedBeanDefinitionReader reader = new AnnotatedBeanDefinitionReader(context);
        reader.register(Cfg.class);

        PrintingFiles files = new PrintingFiles();
        ClassNameGenerator nameGenerator = new ClassNameGenerator(ClassName.get("demo18.generated", "AotGenerated"));
        DefaultGenerationContext generationContext =
                new DefaultGenerationContext(nameGenerator, files, new RuntimeHints());

        ClassName initializerName = new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext);
        System.out.println("[AOT] 生成初始器类名: " + initializerName);
        generationContext.writeGeneratedContent();

        RuntimeHints hints = generationContext.getRuntimeHints();
        System.out.println("[AOT] RuntimeHints 反射类型数: "
                + hints.reflection().typeHints().count());
        hints.reflection().typeHints().forEach(t -> System.out.println("[AOT] 反射类型 hint: " + t));
        hints.proxies().jdkProxyHints().forEach(p -> System.out.println("[AOT] JDK 代理 hint: " + p));
        System.out.println("[AOT] JDK 代理 hint 总数: " + hints.proxies().jdkProxyHints().count());
        context.close();
    }
}
