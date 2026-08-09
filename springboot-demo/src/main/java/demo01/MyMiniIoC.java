package demo01;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/**
 * 手写迷你 IoC 容器：只保留 Spring 容器最核心的三件套
 *   - 注册表（BeanDefinition 元数据：Map<String, Class>，记录"怎么创建"）
 *   - 反射创建（getBean 时按元数据实例化）
 *   - 单例池（创建过的实例缓存复用）
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14 无关，纯 JDK）：
 *   两次 getBean 是否同一实例（单例池生效）: true
 *   构造器依赖注入成功: true
 */
public class MyMiniIoC {

    /** 注册表：Bean 名 -> 创建说明书（这里用 Class 充当 BeanDefinition 元数据） */
    private final Map<String, Class<?>> registry = new HashMap<>();

    /** 单例池：Bean 名 -> 实例（scope=singleton 的复用缓存） */
    private final Map<String, Object> singletonPool = new HashMap<>();

    public void register(String name, Class<?> clazz) {
        registry.put(name, clazz);
    }

    public Object getBean(String name) throws Exception {
        Object cached = singletonPool.get(name);
        if (cached != null) {
            return cached;                                   // 单例：命中即返回，不再创建
        }
        Class<?> clazz = registry.get(name);
        Object instance = createInstance(clazz);              // 反射：按元数据创建
        singletonPool.put(name, instance);
        return instance;
    }

    private Object createInstance(Class<?> clazz) throws Exception {
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            if (ctor.getParameterCount() == 0) {
                ctor.setAccessible(true);
                return ctor.newInstance();
            }
            if (ctor.getParameterCount() == 1) {              // 极简依赖查找：按参数类型找已注册 Bean
                Class<?> depType = ctor.getParameterTypes()[0];
                Object dep = null;
                for (Map.Entry<String, Class<?>> e : registry.entrySet()) {
                    if (depType.isAssignableFrom(e.getValue())) {
                        dep = getBean(e.getKey());
                    }
                }
                ctor.setAccessible(true);
                return ctor.newInstance(dep);
            }
        }
        throw new IllegalStateException("no suitable constructor for " + clazz.getName());
    }
}
