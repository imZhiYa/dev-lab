package demo05;

import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @ConfigurationProperties 的绑定机制实证（spring-boot 的 Binder）：
 * 1. Binder 从 Environment 读值，按前缀 app.order 绑到 POJO（构造 POJO → bind 覆盖）；
 * 2. 宽松绑定：配置文件里写 kebab-case（callback-url / max-retries），字段是 camelCase，能绑定；
 * 3. 绑定缺失 key → 字段保持默认值（不炸）；
 * 4. 类型错误（"abc" 绑 int）→ BindException，启动期显式失败（配置错误早暴露）。
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5）：
 *   [绑定成功] callbackUrl=https://pay.example.com/cb, maxRetries=3, enabled=true（宽松绑定 kebab→camel 生效）
 *   [缺失key] 保持默认值: 5
 *   [类型错误] 绑定失败: BindException（启动期显式失败）
 */
public class BinderApp {

    public static class OrderProps {
        private String callbackUrl = "https://default.example.com/cb";
        private int maxRetries = 5;
        private boolean enabled;

        public String getCallbackUrl() { return callbackUrl; }
        public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    private static StandardEnvironment envWith(Map<String, Object> props, String name) {
        StandardEnvironment env = new StandardEnvironment();
        MutablePropertySources sources = env.getPropertySources();
        sources.addLast(new MapPropertySource(name, props));
        return env;
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        // 1. 正常绑定：配置文件 key 用 kebab-case（application.properties 的常见写法）
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("app.order.callback-url", "https://pay.example.com/cb");
        props.put("app.order.max-retries", "3");
        props.put("app.order.enabled", "true");
        Binder binder = Binder.get(envWith(props, "appProps"));

        OrderProps order = binder.bind("app.order", OrderProps.class).get();
        System.out.println("[绑定成功] callbackUrl=" + order.getCallbackUrl()
                + ", maxRetries=" + order.getMaxRetries()
                + ", enabled=" + order.isEnabled()
                + "（宽松绑定 kebab→camel 生效）");

        // 2. 缺失 key：不炸，保持 POJO 默认值
        Map<String, Object> sparse = new LinkedHashMap<>();
        BindResult<OrderProps> sparseResult = Binder.get(envWith(sparse, "emptyProps")).bind("app.order", OrderProps.class);
        OrderProps sparseOrder = sparseResult.orElseGet(OrderProps::new);
        System.out.println("[缺失key] 保持默认值: " + sparseOrder.getMaxRetries());

        // 3. 类型错误：字符串绑 int → BindException（启动期显式失败）
        Map<String, Object> badProps = new LinkedHashMap<>();
        badProps.put("app.order.max-retries", "abc");
        Binder badBinder = Binder.get(envWith(badProps, "badProps"));
        try {
            BindResult<OrderProps> result = badBinder.bind("app.order", OrderProps.class);
            if (result.isBound()) {
                System.out.println("[类型错误] 绑定成功？（不应发生）");
            } else {
                System.out.println("[类型错误] 未绑定成功（BindException 在 get 时才抛）");
                result.get();
            }
        } catch (BindException e) {
            System.out.println("[类型错误] 绑定失败: " + e.getClass().getSimpleName() + "（启动期显式失败）");
        }
    }
}
