package com.zhiya.ddd.ports;

import java.util.function.Supplier;

/**
 * 端口：本地事务边界。
 * 应用层声明"这些写入要一起成功/失败"，具体 @Transactional/JDBC/内存原子由适配器实现。
 */
public interface TransactionRunner {

    <T> T inTx(Supplier<T> action);
}
