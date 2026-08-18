package com.zhiya.ddd.adapters.memory;

import com.zhiya.ddd.ports.CandidateRecallPort;

import java.util.List;

/** 召回适配器：固定候选集（可验证排序与过滤）。 */
public final class FakeCandidateRecall implements CandidateRecallPort {

    @Override
    public List<String> recall(String userId, String scene) {
        return List.of(
                "personalized:ipad",
                "popular:1001",
                "personalized:keyboard",
                "popular:1002"
        );
    }
}
