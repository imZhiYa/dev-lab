package demo05;

import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Environment 的机制最小实证：
 * 1. Environment = 有序 PropertySource 列表（getProperty 按顺序查，先命中先返回）；
 * 2. 默认来源：systemProperties（-D 系统属性）在前，systemEnvironment（OS 环境变量）在后；
 * 3. 文件配置（自定义 PropertySource）默认在最后——同名 key 被系统属性覆盖（00 篇 3.6 场景 E 的机制）；
 * 4. addFirst 插入新来源 = "配置中心刷新"的机制本质：新值进 Environment 前排，getProperty 立即返回新值。
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14）：
 *   [默认来源] 数量=2: systemProperties, systemEnvironment
 *   [同key] -D 系统属性抢赢文件配置: from-sys-prop
 *   [addFirst 模拟配置中心推送] 推送后取到新值: from-apollo
 *   [addFirst 再推一次] 最新推送覆盖旧推送: newer-value
 *   [优先级队列顺序] systemProperties > systemEnvironment > 文件配置 > Apollo(后推的在前)
 */
public class EnvironmentSourcesApp {

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);
        System.setProperty("app.env.key", "from-sys-prop");   // 模拟 -D 系统属性

        StandardEnvironment env = new StandardEnvironment();
        MutablePropertySources sources = env.getPropertySources();

        // 1. 默认来源：只有 systemProperties + systemEnvironment
        StringBuilder names = new StringBuilder();
        int i = 0;
        for (PropertySource<?> ps : sources) {
            names.append(i++ == 0 ? "" : ", ").append(ps.getName());
        }
        System.out.println("[默认来源] 数量=" + i + ": " + names);

        // 2. 文件配置作为 PropertySource 追加（模拟 application.properties，Boot 里由 config data 加载）
        Map<String, Object> fileProps = new LinkedHashMap<>();
        fileProps.put("app.env.key", "from-file-value");
        fileProps.put("app.env.only.file", "file-only");
        sources.addLast(new MapPropertySource("fileProps", fileProps));

        // 同 key：系统属性（在前）抢赢文件配置（在后）
        System.out.println("[同key] -D 系统属性抢赢文件配置: " + env.getProperty("app.env.key"));

        // 3. 配置中心刷新 = addFirst 插新来源：旧来源同一 key 立即"失去可见性"
        Map<String, Object> apollo1 = new LinkedHashMap<>();
        apollo1.put("app.env.key", "from-apollo");
        sources.addFirst(new MapPropertySource("apollo-config", apollo1));
        System.out.println("[addFirst 模拟配置中心推送] 推送后取到新值: " + env.getProperty("app.env.key"));

        // 再推一次，最新的推送在最前面（后推的覆盖先推的）
        Map<String, Object> apollo2 = new LinkedHashMap<>();
        apollo2.put("app.env.key", "newer-value");
        sources.addFirst(new MapPropertySource("apollo-config-new", apollo2));
        System.out.println("[addFirst 再推一次] 最新推送覆盖旧推送: " + env.getProperty("app.env.key"));

        // 4. 完整优先级顺序
        StringBuilder order = new StringBuilder();
        for (PropertySource<?> ps : sources) {
            order.append(order.length() == 0 ? "" : " > ").append(ps.getName());
        }
        System.out.println("[优先级队列顺序] " + order);

        // 5. getProperty 找不到 key → null（这正是"配置没进来"的表现）
        System.out.println("[缺失key] getProperty 返回: " + env.getProperty("no.such.key.anywhere"));
    }
}
