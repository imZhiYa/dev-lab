package com.zhiya.dubbo.demo.protocol;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.serialize.ObjectInput;
import org.apache.dubbo.common.serialize.ObjectOutput;
import org.apache.dubbo.common.serialize.Serialization;
import org.apache.dubbo.common.serialize.fastjson2.FastJson2Serialization;
import org.apache.dubbo.common.serialize.hessian2.Hessian2Serialization;
import org.apache.dubbo.config.ApplicationConfig;
import com.zhiya.dubbo.demo.api.GreetingRequest;
import com.zhiya.dubbo.demo.protocol.serialization.KryoSerialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class SerializeDump {

    private static final URL DUMMY_URL = URL.valueOf("serializedump://0.0.0.0");

    public static void main(String[] args) throws Exception {
        new ApplicationConfig("serializedump-app");
        GreetingRequest small = new GreetingRequest("DubboDemo", 42);
        StringBuilder sb = new StringBuilder("DubboDemo-");
        for (int i = 0; i < 50; i++) {
            sb.append("abcdefghij");
        }
        GreetingRequest large = new GreetingRequest(sb.toString(), 20260810);

        Serialization hessian2 = new Hessian2Serialization();
        Serialization kryo = new KryoSerialization();
        Serialization fastjson2 = new FastJson2Serialization();

        for (GreetingRequest obj : new GreetingRequest[] {small, large}) {
            System.out.println("==== " + obj);
            for (Serialization s : new Serialization[] {hessian2, kryo, fastjson2}) {
                byte[] bytes = ser(s, obj);
                GreetingRequest back = deser(s, bytes, GreetingRequest.class);
                String rt = back != null && obj.toString().equals(back.toString()) ? "OK" : "MISMATCH:" + back;
                System.out.printf("  %-10s contentTypeId=%-2d size=%-4d roundtrip=%s%n",
                        s.getClass().getSimpleName(), s.getContentTypeId(), bytes.length, rt);
                System.out.println("    hex(48B): " + hex(bytes, 48));
            }
        }
    }

    static byte[] ser(Serialization s, Object obj) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = s.serialize(DUMMY_URL, bos);
        out.writeObject(obj);
        out.flushBuffer();
        return bos.toByteArray();
    }

    static <T> T deser(Serialization s, byte[] bytes, Class<T> clazz) throws Exception {
        ObjectInput in = s.deserialize(DUMMY_URL, new ByteArrayInputStream(bytes));
        return in.readObject(clazz);
    }

    static String hex(byte[] bytes, int n) {
        StringBuilder sb = new StringBuilder();
        int len = Math.min(bytes.length, n);
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02x ", bytes[i] & 0xff));
            if ((i + 1) % 16 == 0) {
                sb.append('\n').append("         ");
            }
        }
        return sb.toString().trim();
    }
}
