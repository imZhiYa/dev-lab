package com.zhiya.dubbo.demo.spi.extension;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

/**
 * E09-1/2：自定义 @SPI 扩展点。
 *
 * 默认名 = "random"（@SPI 值）。
 * {@link Adaptive} 标注的 {@link #pick(URL)} 使 ExtensionLoader 生成自适应类，
 * 按 URL 参数 "demo.balance" 选择实现。
 */
@SPI("random")
public interface DemoBalance {

    @Adaptive("demo.balance")
    String pick(URL url);
}
