package com.zhiya.dubbo.demo.registry;

import org.apache.dubbo.rpc.RpcContext;
import com.zhiya.dubbo.demo.api.GreetingRequest;
import com.zhiya.dubbo.demo.api.GreetingService;

import java.net.InetSocketAddress;

public class GreetingServiceImpl implements GreetingService {

    @Override
    public String greet(GreetingRequest request) {
        InetSocketAddress local = RpcContext.getServiceContext().getLocalAddress();
        String port = local == null ? "?" : String.valueOf(local.getPort());
        return "Hello " + request.getName() + " (seq=" + request.getSequence() + ") from demo02-provider:" + port;
    }
}
