package com.zhiya.dubbo.demo.api;

public interface GreetingService {

    /**
     * 订单 O 的唯一入口：回显请求并附问候语。
     */
    String greet(GreetingRequest request);
}
