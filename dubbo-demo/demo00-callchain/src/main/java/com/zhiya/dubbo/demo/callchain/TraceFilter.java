package com.zhiya.dubbo.demo.callchain;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;

/**
 * TraceFilter：在双端打印订单 O 沿途每一站（E00 的逐站打印模板）。
 * 仅本 demo 全局激活（经 SPI 文件注册），生产可用作可观测性埋点参照。
 */
@Activate(group = {CommonConstants.CONSUMER, CommonConstants.PROVIDER})
public class TraceFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String side = RpcContext.getServiceContext().isProviderSide() ? "PROVIDER" : "CONSUMER";
        String thread = Thread.currentThread().getName();
        System.out.println("  [" + side + " TraceFilter] enter, thread=" + thread
                + ", invoker=" + invoker.getClass().getName()
                + ", method=" + invocation.getMethodName()
                + ", args=" + java.util.Arrays.toString(invocation.getArguments()));
        try {
            Result result = invoker.invoke(invocation);
            System.out.println("  [" + side + " TraceFilter] exit, result=" + result.getValue());
            return result;
        } catch (Exception e) {
            System.out.println("  [" + side + " TraceFilter] ERROR: " + e.getMessage());
            throw e;
        }
    }
}
