package com.erobbing.sdk.utils;


import org.apache.commons.lang3.ArrayUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteUtils {
    /**
     * UINT 杞负 4瀛楄妭鏁扮粍
     */
    public static byte[] uintToBytes(long x) {
        ByteBuffer buffer = ByteBufferLE.allocate(4);
        buffer.putInt((int) x);
        return buffer.array();
    }

    /**
     * 4瀛楄妭鏁扮粍杞负 UINT
     */
    public static long bytesToUInt(byte[] bytes) {
        MyAssert.Assert(4, bytes.length, "BytesToUInt");
        ByteBuffer buffer = ByteBufferLE.wrap(bytes);
        return buffer.getInt() & 0xffffffffL;

    }

    /**
     * 4瀛楄妭鏁扮粍杞负 UINT锛屽ぇ绔�
     */
    public static long bytesToUIntBig(byte[] bytes) {
        MyAssert.Assert(4, bytes.length, "BytesToUInt");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.BIG_ENDIAN);
        return buffer.getInt() & 0xffffffffL;

    }

    /**
     * 浠庢寚瀹氫綅缃紝杞崲涓や釜瀛楄妭涓� int
     *
     * @param buff
     * @param offset
     * @return
     */
    public static int makeUShort(byte[] buff, int offset) {
        ByteBuffer bb = ByteBufferLE.wrap(ArrayUtils.subarray(buff, offset, offset + 2));
        return bb.getShort() & 0xffff;
    }

    public static int makeUShort(byte[] buff) {
        ByteBuffer bb = ByteBufferLE.wrap(buff);
        return bb.getShort() & 0xffff;
    }

    public static int makeUShortBig(byte[] buff) {
        ByteBuffer bb = ByteBuffer.wrap(buff);
        bb.order(ByteOrder.BIG_ENDIAN);
        return bb.getShort() & 0xffff;
    }


    public static byte[] ushortToBytes(int value) {
        ByteBuffer bb = ByteBufferLE.allocate(2);
        bb.putShort((short) value);
        return bb.array();
    }
}