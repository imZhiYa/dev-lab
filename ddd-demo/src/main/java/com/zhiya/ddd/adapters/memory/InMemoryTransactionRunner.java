package com.zhiya.ddd.adapters.memory;

import com.zhiya.ddd.ports.TransactionRunner;

/** 内存事务适配器：直接执行 = 原子成功（无法回滚正是"内存模拟"的边界，见 README）。 */
public final class InMemoryTransactionRunner implements TransactionRunner {

    @Override
    public <T> T inTx(java.util.function.Supplier<T> action) {
        return action.get();
    }
}
