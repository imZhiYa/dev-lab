package com.zhiya.ddd.adapters.memory;

import com.zhiya.ddd.application.translation.ProfileToDecisionTranslator.Counter;

/** 计数适配器：记录 ACL 丢弃的非法标签（EX-02 断言用）。 */
public final class DropCounter implements Counter {

    private final java.util.List<String> dropped = new java.util.ArrayList<>();

    @Override
    public void increment(String reason) {
        dropped.add(reason);
    }

    public int count() {
        return dropped.size();
    }

    public java.util.List<String> reasons() {
        return new java.util.ArrayList<>(dropped);
    }
}