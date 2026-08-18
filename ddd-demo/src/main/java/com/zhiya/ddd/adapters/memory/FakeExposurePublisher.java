package com.zhiya.ddd.adapters.memory;

import com.zhiya.ddd.ports.ExposurePublisher;

import java.util.ArrayList;
import java.util.List;

/** 曝光记录适配器：把"推荐了什么"记录成列表，供断言。 */
public final class FakeExposurePublisher implements ExposurePublisher {

    private final List<String> records = new ArrayList<>();

    @Override
    public void publish(String userId, List<String> items) {
        records.add(userId + "->" + String.join(",", items));
    }

    public List<String> records() {
        return new ArrayList<>(records);
    }

    public int size() {
        return records.size();
    }
}
