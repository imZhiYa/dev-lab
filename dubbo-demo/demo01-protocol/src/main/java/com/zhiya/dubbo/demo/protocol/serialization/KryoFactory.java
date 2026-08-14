package com.zhiya.dubbo.demo.protocol.serialization;

import com.esotericsoftware.kryo.Kryo;

/**
 * 每线程一个 Kryo 实例（Kryo 非线程安全）。
 * registrationRequired=false：类以全限定名序列化，无需预注册。
 * 注意与 2.7.x hessian2 泄漏史的差异：这里的 ThreadLocal 持有的是配置对象
 * （类注册表），不含请求数据，不会重演"ThreadLocal 持响应缓冲"的泄漏。
 */
public final class KryoFactory {

    private static final ThreadLocal<Kryo> KRYOS = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        return kryo;
    });

    private KryoFactory() {
    }

    public static Kryo get() {
        return KRYOS.get();
    }
}
