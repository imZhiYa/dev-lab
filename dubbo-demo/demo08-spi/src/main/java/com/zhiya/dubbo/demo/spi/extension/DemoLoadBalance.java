package com.zhiya.dubbo.demo.spi.extension;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.LoadBalance;

import java.util.List;

/**
 * E09-5：自定义 LoadBalance 挂真实 tri 调用链。
 * 消费端经 loadbalance=demo URL 参数选中。
 */
public class DemoLoadBalance implements LoadBalance {

    @Override
    public <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        System.out.println("[DEMO-LB] select called, invokers=" + invokers.size()
                + ", method=" + invocation.getMethodName() + ", thread=" + Thread.currentThread().getName());
        return invokers.get(0);
    }
}
