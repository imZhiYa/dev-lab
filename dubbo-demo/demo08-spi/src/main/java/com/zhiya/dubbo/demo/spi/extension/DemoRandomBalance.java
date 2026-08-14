package com.zhiya.dubbo.demo.spi.extension;

import org.apache.dubbo.common.URL;

public class DemoRandomBalance implements DemoBalance {

    @Override
    public String pick(URL url) {
        return "random picked";
    }
}
