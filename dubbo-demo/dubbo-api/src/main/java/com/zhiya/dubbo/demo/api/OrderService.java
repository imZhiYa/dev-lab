package com.zhiya.dubbo.demo.api;

public interface OrderService {

    /**
     * 下单，返回订单确认文本。
     */
    String create(OrderRequest request);

    /**
     * 按 id 查询订单状态。
     */
    String queryStatus(String orderId);
}
