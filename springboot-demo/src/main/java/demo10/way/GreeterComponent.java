package demo10.way;

import org.springframework.stereotype.Component;

/**
 * 通道 3：@Component 组件扫描。
 * 声明式自我标记：类声明"我是组件"，容器在启动时扫描注册（本 demo 由
 * BeanRegisterWaysApp 的 @SpringBootApplication 默认扫描 demo10 包及子包命中）。
 */
@Component
public class GreeterComponent extends Greeter {

    public GreeterComponent() {
        super("component");
    }
}
