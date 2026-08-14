package com.zhiya.dubbo.demo.spi.extension;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

/**
 * E09-3：@Activate Filter——自动注册进 provider 过滤链
 * （group="provider"、无 value = provider 侧无条件激活）。
 */
@Activate(group = "provider")
public class DemoProviderFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        System.out.println("[FILTER] DemoProviderFilter active, method=" + invocation.getMethodName());
        return invoker.invoke(invocation);
    }
}
