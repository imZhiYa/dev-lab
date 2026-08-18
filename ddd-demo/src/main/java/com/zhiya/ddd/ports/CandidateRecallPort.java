package com.zhiya.ddd.ports;

import java.util.List;

/** 端口：候选召回能力（算法侧，非领域规则）。 */
public interface CandidateRecallPort {

    List<String> recall(String userId, String scene);
}
