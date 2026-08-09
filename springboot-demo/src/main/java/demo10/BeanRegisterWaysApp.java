package demo10;

import demo10.way.Greeter;
import importedcfg.JavaConfig;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * 五种 Bean 注册通道（03 篇 Level 0）：XML / @Bean / @Component / @Import / 程序化注册
 *
 * 设计：五种方式在同一个容器里各注册一个 Greeter（prefix 区分来源）：
 *   1. XML        ：@ImportResource 读 demo10/way/beans.xml → XmlBeanDefinitionReader 解析
 *   2. @Bean      ：@Import(JavaConfig.class) 导入 @Configuration（@Import 形态 1）
 *   3. @Component ：组件扫描（demo10 包及子包，@SpringBootApplication 默认扫描）
 *   4. @Import    ：形态 2 ImportSelector——返回候选类名，由容器按名称导入
 *                    （= Spring Boot 自动装配的入口形态）
 *   5. 程序化     ：BeanDefinitionRegistryPostProcessor 直接 registerBeanDefinition
 *
 * 关键认知（验证目标）：
 *   - 五种方式最终都通向同一个动作：向 BeanDefinitionRegistry 注册一个 BeanDefinition
 *   - "定义从哪来"不同（XML 文件 / 配置类方法 / 扫描 / 选择器 / 代码），
 *     "定义怎么被创建"完全相同（同一套 getBean → 创建链）
 *   - BeanDefinition 指纹可区分来源：XML 定义带 resource 描述，
 *     @Bean/@Import 定义带 factoryMethodName，扫描定义 beanClass 是具体子类，
 *     程序化注册无 resource/factoryMethod
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5）：
 *   [通道] greeterXml 已注册=true prefix=xml
 *   [通道] greeterBean 已注册=true prefix=bean
 *   [通道] greeterComponent 已注册=true prefix=component
 *   [通道] greeterSelector 已注册=true prefix=selector
 *   [通道] greeterManual 已注册=true prefix=manual
 *   [验证] 同容器同生命周期：greeterXml getBean 两次返回同一实例 = true
 *
 *   [定义] BeanDefinition 指纹（5 个定义都注册在同一个 BeanDefinitionRegistry）：
 *     greeterXml       beanClass=Greeter           factoryMethod=null            resource=class path resource [demo10/way/beans.xml]
 *     greeterBean      beanClass=-                 factoryMethod=greeterBean     resource=importedcfg.JavaConfig
 *     greeterComponent beanClass=GreeterComponent  factoryMethod=null            resource=file [.../out/demo10/way/GreeterComponent.class]
 *     greeterSelector  beanClass=-                 factoryMethod=greeterSelector resource=class path resource [importedcfg/SelectorTargetConfig.class]
 *     greeterManual    beanClass=Greeter           factoryMethod=null            resource=null
 */
public class BeanRegisterWaysApp {

    @SpringBootApplication
    @Import({JavaConfig.class, demo10.way.GreeterSelector.class})
    @ImportResource("classpath:demo10/way/beans.xml")
    static class BootConfig {
    }

    static void verify(ConfigurableApplicationContext ctx, String name, String expectPrefix) {
        Greeter g = ctx.getBean(name, Greeter.class);
        boolean ok = expectPrefix.equals(g.getPrefix());
        System.out.println("[通道] " + name + " 已注册=" + ctx.containsBean(name)
                + " prefix=" + g.getPrefix() + (ok ? "" : "（期望 " + expectPrefix + "，断言失败！）"));
    }

    static void fingerprint(ConfigurableApplicationContext ctx) {
        System.out.println("\n[定义] BeanDefinition 指纹（5 个定义都注册在同一个 BeanDefinitionRegistry）：");
        ConfigurableListableBeanFactory bf = ctx.getBeanFactory();
        for (String name : new String[]{"greeterXml", "greeterBean", "greeterComponent", "greeterSelector", "greeterManual"}) {
            BeanDefinition bd = bf.getBeanDefinition(name);
            String cls = bd.getBeanClassName() == null ? "-"
                    : bd.getBeanClassName().replace("demo10.way.", "").replace("importedcfg.", "");
            System.out.println("  " + pad(name, 16)
                    + "beanClass=" + pad(cls, 20)
                    + "factoryMethod=" + pad(String.valueOf(bd.getFactoryMethodName()), 18)
                    + "resource=" + bd.getResourceDescription());
        }
    }

    static String pad(String s, int width) {
        if (s.length() >= width) {
            return s + " ";
        }
        return s + " ".repeat(width - s.length());
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
            // SLF4J NOP 会吞掉 Boot 启动异常，这里兜底打印堆栈便于定位
            t.printStackTrace();
            throw new RuntimeException(t);
        }

        verify(ctx, "greeterXml", "xml");
        verify(ctx, "greeterBean", "bean");
        verify(ctx, "greeterComponent", "component");
        verify(ctx, "greeterSelector", "selector");
        verify(ctx, "greeterManual", "manual");

        Object a = ctx.getBean("greeterXml");
        Object b = ctx.getBean("greeterXml");
        System.out.println("[验证] 同容器同生命周期：greeterXml getBean 两次返回同一实例 = " + (a == b));

        fingerprint(ctx);
        ctx.close();
    }
}
