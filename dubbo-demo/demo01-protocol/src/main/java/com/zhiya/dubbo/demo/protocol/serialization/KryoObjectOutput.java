package com.zhiya.dubbo.demo.protocol.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import org.apache.dubbo.common.serialize.ObjectOutput;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * 基于 Kryo 5.x 的 ObjectOutput 实现。
 * Kryo 实例非线程安全：经 ThreadLocal 每线程一个。
 */
public class KryoObjectOutput implements ObjectOutput {

    private final Kryo kryo;
    private final Output output;

    public KryoObjectOutput(OutputStream out) {
        this.kryo = KryoFactory.get();
        this.output = new Output(out);
    }

    @Override
    public void writeBool(boolean v) {
        output.writeBoolean(v);
    }

    @Override
    public void writeByte(byte v) {
        output.writeByte(v);
    }

    @Override
    public void writeShort(short v) {
        output.writeShort(v);
    }

    @Override
    public void writeInt(int v) {
        output.writeInt(v);
    }

    @Override
    public void writeLong(long v) {
        output.writeLong(v);
    }

    @Override
    public void writeFloat(float v) {
        output.writeFloat(v);
    }

    @Override
    public void writeDouble(double v) {
        output.writeDouble(v);
    }

    @Override
    public void writeUTF(String v) {
        output.writeString(v);
    }

    @Override
    public void writeBytes(byte[] b) {
        output.writeBytes(b);
    }

    @Override
    public void writeBytes(byte[] b, int off, int len) {
        output.writeBytes(b, off, len);
    }

    @Override
    public void writeObject(Object obj) {
        kryo.writeClassAndObject(output, obj);
    }

    @Override
    public void writeThrowable(Throwable t) {
        kryo.writeClassAndObject(output, t);
    }

    @Override
    public void writeEvent(String event) {
        output.writeString(event);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void writeAttachments(Map attachments) {
        kryo.writeClassAndObject(output, attachments);
    }

    @Override
    public void flushBuffer() {
        output.flush();
    }
}
