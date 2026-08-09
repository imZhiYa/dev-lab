package demo10.way;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.stereotype.Component;

/**
 * 通道 5：程序化 BeanDefinition 注册。
 * BeanDefinitionRegistryPostProcessor 在容器刷新早期（单例实例化之前）回调，
 * 直接把 BeanDefinition 注册进 BeanDefinitionRegistry——五种方式里唯一
 * "不经过任何配置文件/注解解析器"、纯代码直达容器内部的通道。
 */
@Component
public class ManualRegistrar implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        BeanDefinition definition = BeanDefinitionBuilder
                .genericBeanDefinition(Greeter.class)
                .addPropertyValue("prefix", "manual")
                .getBeanDefinition();
        registry.registerBeanDefinition("greeterManual", definition);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // BFPP 主接口方法，本 demo 不需要额外动作
    }
}
