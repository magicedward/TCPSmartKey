package com.erobbing.sdk.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class ByteBufferBE {
    public static ByteBuffer wrap(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.order(ByteOrder.BIG_ENDIAN);
        return bb;
    }

    public static ByteBuffer allocate(int buffSize) {
        ByteBuffer bb = ByteBuffer.allocate(buffSize);
        bb.order(ByteOrder.BIG_ENDIAN);
        return bb;
    }
}
