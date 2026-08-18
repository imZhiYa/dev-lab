package com.zhiya.ddd.ports;

/** 特征能力不可用（适配器翻译后的内部语义，不是 Redis 原生异常）。 */
public class FeatureUnavailable extends RuntimeException {
    public FeatureUnavailable(String message, Throwable cause) {
        super(message, cause);
    }
}
