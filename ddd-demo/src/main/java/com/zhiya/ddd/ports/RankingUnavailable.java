package com.zhiya.ddd.ports;

/** 排序能力不可用：适配器只报告失败，不决定返回什么商品。 */
public class RankingUnavailable extends RuntimeException {
    public RankingUnavailable(String message, Throwable cause) {
        super(message, cause);
    }

    public RankingUnavailable(String message) {
        super(message);
    }
}
