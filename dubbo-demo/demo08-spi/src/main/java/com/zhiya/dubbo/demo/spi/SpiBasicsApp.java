package com.zhiya.dubbo.demo.spi;

import org.apache.dubbo.common.extension.ExtensionLoader;
import com.zhiya.dubbo.demo.spi.extension.DemoBalance;

/**
 * E09-1：@SPI 基础——ExtensionLoader 加载、默认扩展名、
 * 同实例缓存、不存在扩展名的报错格式。
 */
public class SpiBasicsApp {

    public static void main(String[] args) {
        ExtensionLoader<DemoBalance> loader = ExtensionLoader.getExtensionLoader(DemoBalance.class);
        org.apache.dubbo.common.URL url = org.apache.dubbo.common.URL.valueOf("tri://localhost:20880/demo?demo.balance=random");

        DemoBalance def = loader.getDefaultExtension();
        System.out.println("=== [E09-1] default extension name = " + loader.getDefaultExtensionName());

        DemoBalance random = loader.getExtension("random");
        DemoBalance consistentHash = loader.getExtension("consistentHash");
        System.out.println("=== [E09-1] random.pick -> " + random.pick(url));
        System.out.println("=== [E09-1] consistentHash.pick -> " + consistentHash.pick(url));

        DemoBalance random2 = loader.getExtension("random");
        System.out.println("=== [E09-1] same instance cached? " + (random == random2));

        try {
            loader.getExtension("no-such-name");
        } catch (IllegalStateException e) {
            System.out.println("=== [E09-1] error msg: " + e.getMessage());
        }
    }
}
