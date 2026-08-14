package com.zhiya.dubbo.demo.threadpool;

import com.zhiya.dubbo.demo.api.GreetingRequest;
import com.zhiya.dubbo.demo.api.GreetingService;

/**
 * E07 业务实现：睡眠时长由系统属性 demo.sleep.ms 控制，
 * 便于按需打满业务线程池（250 并发饱和实验）。
 */
public class GreetingServiceImpl implements GreetingService {

    private final long sleepMs = Long.parseLong(System.getProperty("demo.sleep.ms", "0"));

    @Override
    public String greet(GreetingRequest request) {
        String thread = Thread.currentThread().getName();
        System.out.println("    [PROVIDER Business] greet() enter, thread=" + thread
                + ", seq=" + request.getSequence() + ", sleepMs=" + sleepMs);
        if (sleepMs > 0) {
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("    [PROVIDER Business] greet() INTERRUPTED, thread=" + thread
                        + ", seq=" + request.getSequence());
                return "INTERRUPTED seq=" + request.getSequence();
            }
        }
        System.out.println("    [PROVIDER Business] greet() done, thread=" + thread
                + ", seq=" + request.getSequence());
        return "Hello " + request.getName() + " (sequence=" + request.getSequence()
                + "), cooked by " + thread;
    }
}
