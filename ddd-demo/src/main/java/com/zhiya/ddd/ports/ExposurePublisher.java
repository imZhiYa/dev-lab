package com.zhiya.ddd.ports;

import java.util.List;

/**
 * 端口：发布"推荐已曝光"事实。
 * send 成功 != 业务可靠完成 —— Outbox/幂等在 ddd-05 / EX-05 清算。
 */
public interface ExposurePublisher {

    void publish(String userId, List<String> items);
}
