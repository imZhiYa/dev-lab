package com.zhiya.ddd.domain.strategy;

/** 值对象：流量比例，构造即校验合法范围（0-100），消灭裸 int 误用（ddd-03 Level 3）。 */
public record TrafficRatio(int percentage) {
    public TrafficRatio {
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("percentage must be between 0 and 100: " + percentage);
        }
    }
}
