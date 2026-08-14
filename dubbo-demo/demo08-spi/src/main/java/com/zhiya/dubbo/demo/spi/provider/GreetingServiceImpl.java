package com.zhiya.dubbo.demo.spi.provider;

import com.zhiya.dubbo.demo.api.GreetingRequest;
import com.zhiya.dubbo.demo.api.GreetingService;

public class GreetingServiceImpl implements GreetingService {

    @Override
    public String greet(GreetingRequest request) {
        String thread = Thread.currentThread().getName();
        System.out.println("    [PROVIDER Business] greet() enter, thread=" + thread + ", seq=" + request.getSequence());
        System.out.println("    [PROVIDER Business] greet() done, thread=" + thread + ", seq=" + request.getSequence());
        return "Hello " + request.getName() + " (sequence=" + request.getSequence() + ")";
    }
}
