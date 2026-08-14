package com.zhiya.dubbo.demo.spi;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import com.zhiya.dubbo.demo.spi.extension.DemoBalance;
import com.zhiya.dubbo.demo.spi.extension.DemoBalanceWrapper;

/**
 * E09-4：Wrapper——构造器仅接收扩展接口单参数的类，会被 ExtensionLoader
 * 自动包装在真实实现外层；getExtension 返回包装后的（装饰）实例。
 * 注意：wrapper 必须以"无名字行"注册进 SPI 文件才生效。
 */
public class WrapperApp {

    public static void main(String[] args) {
        ExtensionLoader<DemoBalance> loader = ExtensionLoader.getExtensionLoader(DemoBalance.class);

        DemoBalance random = loader.getExtension("random");
        System.out.println("=== [E09-4] getExtension class: " + random.getClass().getName());
        System.out.println("=== [E09-4] is wrapped by DemoBalanceWrapper? " + (random instanceof DemoBalanceWrapper));

        URL url = URL.valueOf("tri://127.0.0.1:50051").addParameter("demo.balance", "random");
        System.out.println("=== [E09-4] pick through wrapper -> " + random.pick(url));
    }
}
