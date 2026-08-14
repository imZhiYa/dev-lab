package com.zhiya.dubbo.demo.spi.extension;

import org.apache.dubbo.common.URL;

/**
 * E09-4：Wrapper 扩展——构造器仅接收扩展接口单参数。
 * ExtensionLoader 用本类包装真实实现（装饰链），getExtension 返回包装实例。
 */
public class DemoBalanceWrapper implements DemoBalance {

    private final DemoBalance delegate;

    public DemoBalanceWrapper(DemoBalance delegate) {
        this.delegate = delegate;
    }

    @Override
    public String pick(URL url) {
        System.out.println("[WRAPPER] DemoBalanceWrapper before delegate, url=" + url.getParameter("demo.balance"));
        return delegate.pick(url);
    }
}
