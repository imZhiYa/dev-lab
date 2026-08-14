package com.zhiya.dubbo.demo.protocol.serialization;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.serialize.ObjectInput;
import org.apache.dubbo.common.serialize.ObjectOutput;
import org.apache.dubbo.common.serialize.Serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Dubbo 3.3.4 自定义 Kryo 序列化扩展（E01 自实现"盒子"）。
 *
 * 为什么存在：Dubbo 3.x 停止发布 dubbo-serialization-kryo（止步 2.7.23；
 * 3.3.4 内置 SPI 仅 hessian2/fastjson2，已核验）。自插盒子 = Microkernel + Plugin
 * 契约的完整走一遍：实现 Serialization、经 SPI 文件注册。
 *
 * contentTypeId=8 与 contentType="x-application/kryo" 对齐 2.7.x 常量（javap 2.7.23 jar 核验）。
 */
public class KryoSerialization implements Serialization {

    public static final byte CONTENT_TYPE_ID = 8;
    public static final String CONTENT_TYPE = "x-application/kryo";

    @Override
    public byte getContentTypeId() {
        return CONTENT_TYPE_ID;
    }

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    @Override
    public ObjectOutput serialize(URL url, OutputStream out) throws IOException {
        return new KryoObjectOutput(out);
    }

    @Override
    public ObjectInput deserialize(URL url, InputStream in) throws IOException {
        return new KryoObjectInput(in);
    }
}
