package com.zhiya.dubbo.demo.spi;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import com.zhiya.dubbo.demo.spi.extension.DemoBalance;

/**
 * E09-2：@Adaptive——ExtensionLoader 运行时生成自适应类；
 * URL 参数 "demo.balance" 决定每次调用选用哪个实现。
 */
public class AdaptiveApp {

    public static void main(String[] args) {
        DemoBalance adaptive = ExtensionLoader.getExtensionLoader(DemoBalance.class).getAdaptiveExtension();
        System.out.println("=== [E09-2] adaptive class: " + adaptive.getClass().getName());

        URL urlRandom = URL.valueOf("tri://127.0.0.1:50051").addParameter("demo.balance", "random");
        URL urlCh = URL.valueOf("tri://127.0.0.1:50051").addParameter("demo.balance", "consistentHash");
        URL urlDefault = URL.valueOf("tri://127.0.0.1:50051");

        System.out.println("=== [E09-2] demo.balance=random        -> " + adaptive.pick(urlRandom));
        System.out.println("=== [E09-2] demo.balance=consistentHash -> " + adaptive.pick(urlCh));
        System.out.println("=== [E09-2] no param (default=random)   -> " + adaptive.pick(urlDefault));
    }
}
