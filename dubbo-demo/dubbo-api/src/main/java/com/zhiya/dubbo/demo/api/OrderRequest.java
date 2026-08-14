package com.zhiya.dubbo.demo.api;

import java.io.Serializable;

public class OrderRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderId;
    private int amount;

    public OrderRequest() {
    }

    public OrderRequest(String orderId, int amount) {
        this.orderId = orderId;
        this.amount = amount;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "OrderRequest{orderId='" + orderId + "', amount=" + amount + '}';
    }
}
