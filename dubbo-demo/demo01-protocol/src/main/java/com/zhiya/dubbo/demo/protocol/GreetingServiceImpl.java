package com.zhiya.dubbo.demo.protocol;

import com.zhiya.dubbo.demo.api.GreetingRequest;
import com.zhiya.dubbo.demo.api.GreetingService;

/**
 * 两种协议背后同一份业务逻辑：回显 "Hello <name> (sequence=<seq>)"。
 */
public class GreetingServiceImpl implements GreetingService {

    @Override
    public String greet(GreetingRequest request) {
        return "Hello " + request.getName() + " (sequence=" + request.getSequence() + ")";
    }
}
