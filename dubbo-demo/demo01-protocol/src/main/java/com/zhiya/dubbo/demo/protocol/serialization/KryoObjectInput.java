package com.zhiya.dubbo.demo.protocol.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.apache.dubbo.common.serialize.ObjectInput;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * 基于 Kryo 5.x 的 ObjectInput 实现。
 *
 * Dubbo 3.3.4 不再内置 kryo 扩展（dubbo-serialization-kryo 止步 2.7.23），
 * 本类经 SPI（Microkernel + Plugin）自插一个"盒子"：
 * 本类 + KryoSerialization + META-INF/dubbo/internal/ 下的 SPI 文件即完整扩展。
 *
 * Kryo 实例非线程安全：经 ThreadLocal 每线程一个。
 */
public class KryoObjectInput implements ObjectInput {

    private final Kryo kryo;
    private final Input input;

    public KryoObjectInput(InputStream in) {
        this.kryo = KryoFactory.get();
        this.input = new Input(in);
    }

    @Override
    public boolean readBool() {
        return input.readBoolean();
    }

    @Override
    public byte readByte() {
        return input.readByte();
    }

    @Override
    public short readShort() {
        return input.readShort();
    }

    @Override
    public int readInt() {
        return input.readInt();
    }

    @Override
    public long readLong() {
        return input.readLong();
    }

    @Override
    public float readFloat() {
        return input.readFloat();
    }

    @Override
    public double readDouble() {
        return input.readDouble();
    }

    @Override
    public String readUTF() throws IOException {
        return input.readString();
    }

    @Override
    public byte[] readBytes() {
        try {
            return input.readBytes(input.available());
        } catch (IOException e) {
            throw new IllegalStateException("kryo input read failed", e);
        }
    }

    @Override
    public Object readObject() {
        return kryo.readClassAndObject(input);
    }

    @Override
    public <T> T readObject(Class<T> clazz) {
        // 写侧永远"写类标记 + 对象"（writeClassAndObject），读侧必须对称。
        // 若用 readObject(input, clazz) 会按 clazz 实例化——接口类型（如 Map）
        // 无 no-arg 构造直接报
        // "Class cannot be created (missing no-arg constructor)"（E01 在 dubbo 协议路径实测踩中）
        return (T) kryo.readClassAndObject(input);
    }

    @Override
    public <T> T readObject(Class<T> clazz, Type type) {
        // 与 readObject(Class) 相同的对称要求：写侧永远写类标记 + 对象，读侧同样先读类标记
        return (T) kryo.readClassAndObject(input);
    }
}
