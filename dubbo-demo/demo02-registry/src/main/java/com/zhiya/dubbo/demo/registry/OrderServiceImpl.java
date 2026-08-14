package com.zhiya.dubbo.demo.registry;

import com.zhiya.dubbo.demo.api.OrderRequest;
import com.zhiya.dubbo.demo.api.OrderService;

public class OrderServiceImpl implements OrderService {

    @Override
    public String create(OrderRequest request) {
        return "order " + request.getOrderId() + " created, amount=" + request.getAmount() + " by demo02-provider";
    }

    @Override
    public String queryStatus(String orderId) {
        return "order " + orderId + " status=PAID";
    }
}
