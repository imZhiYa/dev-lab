package autoext;

/**
 * 自动装配实验包：自定义 @AutoConfiguration 类，全部注册在
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports。
 *
 * 放在独立包（不在 demo10.* 扫描范围）——否则会被 @SpringBootApplication 的
 * ComponentScan 当普通组件扫到，走不到自动装配路径（DeferredImportSelector 延迟导入）。
 */
public class Greeter {

    private final String source;

    public Greeter(String source) {
        this.source = source;
    }

    public String greet() {
        return "greet from " + source;
    }

    @Override
    public String toString() {
        return "Greeter(" + source + ")";
    }
}
