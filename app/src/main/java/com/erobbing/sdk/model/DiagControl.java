package com.erobbing.sdk.model;

import com.erobbing.sdk.utils.ByteBufferBE;
import com.erobbing.sdk.utils.ByteBufferLE;
import com.erobbing.sdk.utils.HexTools;
import com.erobbing.sdk.utils.MyLogger;
import com.erobbing.tcpsmartkey.util.BitOperator;
import com.erobbing.tcpsmartkey.util.JT808ProtocolUtils;

import java.io.Serializable;
import java.nio.ByteBuffer;

/*
7e000200000200000000150003327e
7e # 标识位
000200000200000000150003 # 消息头
    0002 # 消息ID
    0000 # 消息体属性，消息体属性每个位都为零,也即第12-15位的消息包封装项不存在,消息体也为空
    020000000015 # 终端手机号
    0003 # 流水号
32 # 校验码
7e # 标识位
* */
public class DiagControl implements Serializable {
    public short msgID;
    public short msgProperty;
    public byte[] phoneNum = new byte[12];
    public short streamNum;
    public byte[] body;
    public static final byte HEAD = 0x7E;
    public static final byte TAIL = 0x7E;

    public int getMsgID() {
        return msgID;
    }

    public void setMsgID(short msgID) {
        this.msgID = msgID;
    }

    public int getMsgProperty() {
        return msgProperty;
    }

    public void setMsgProperty(short msgProperty) {
        this.msgProperty = msgProperty;
    }

    public byte[] getPhoneNum() {
        return phoneNum;
    }

    public void setPhoneNum(byte[] phoneNum) {
        this.phoneNum = phoneNum;
    }

    public int getStreamNum() {
        return streamNum;
    }

    public void setStreamNum(short streamNum) {
        this.streamNum = streamNum;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public byte[] toByteArray() {
        try {
            int len = 0;
            if (body == null) {
                len = 0;
            } else {
                len = body.length;
            }

            //3-shorts, 4 bytes phone num, crc, 7e,7e
            ByteBuffer bb = ByteBufferBE.allocate(len + 3 * 2 + 7); //3: 7e,7e, crc
            //bb.put(HEAD);
            bb.putShort(msgID);
            bb.putShort(msgProperty);
            bb.put(phoneNum);
            bb.putShort(streamNum);
            if (len > 0) {
                bb.put(body);
            }

            byte crc = (byte) (BitOperator.getCheckSum4JT808(bb.array(), 0, bb.position()) & 0xff);
            bb.put(crc);

            bb.flip();
            JT808ProtocolUtils jt808 = new JT808ProtocolUtils();
            byte[] escapedBuf = jt808.doEscape4Send(bb.array(), 0, bb.limit());
            ByteBuffer bb2 = ByteBufferLE.allocate(escapedBuf.length + 2);
            bb2.put(HEAD);
            bb2.put(escapedBuf);
            bb2.put(TAIL);

            MyLogger.jLog().v("打包数据:" + HexTools.byteArrayToHexReadable(bb2.array(), 100));
            return bb2.array();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
