package com.zhiya.dubbo.demo.spi.extension;

import org.apache.dubbo.common.URL;

public class DemoConsistentHashBalance implements DemoBalance {

    @Override
    public String pick(URL url) {
        return "consistentHash picked";
    }
}
