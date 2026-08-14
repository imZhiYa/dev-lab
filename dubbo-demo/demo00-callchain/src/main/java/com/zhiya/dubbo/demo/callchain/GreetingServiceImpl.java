package com.zhiya.dubbo.demo.callchain;

import com.zhiya.dubbo.demo.api.GreetingRequest;
import com.zhiya.dubbo.demo.api.GreetingService;

public class GreetingServiceImpl implements GreetingService {

    @Override
    public String greet(GreetingRequest request) {
        String thread = Thread.currentThread().getName();
        System.out.println("    [PROVIDER Business] greet() executing, thread=" + thread
                + ", request=" + request);
        return "Hello " + request.getName() + " (sequence=" + request.getSequence()
                + "), cooked by " + thread;
    }
}
