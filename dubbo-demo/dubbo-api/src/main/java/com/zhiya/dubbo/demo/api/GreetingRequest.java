package com.zhiya.dubbo.demo.api;

import java.io.Serializable;

public class GreetingRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private int sequence;

    public GreetingRequest() {
    }

    public GreetingRequest(String name, int sequence) {
        this.name = name;
        this.sequence = sequence;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    @Override
    public String toString() {
        return "GreetingRequest{name='" + name + "', sequence=" + sequence + '}';
    }
}
