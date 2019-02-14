package com.erobbing.tcpsmartkey.common;

import java.util.Arrays;

public class PackageData {

    /**
     * 16byte 消息头(一般是12byte)
     */
    protected MsgHeader msgHeader;

    // 消息体字节数组
    protected byte[] msgBodyBytes;

    /**
     * 校验码 1byte
     */
    protected int checkSum;

    /**
     * 消息体字串(本地添加)
     */
    private String bodyString;

    //@JSONField(serialize = false)
    //protected Channel channel;

    public MsgHeader getMsgHeader() {
        return msgHeader;
    }

    public void setMsgHeader(MsgHeader msgHeader) {
        this.msgHeader = msgHeader;
    }

    public byte[] getMsgBodyBytes() {
        return msgBodyBytes;
    }

    public void setMsgBodyBytes(byte[] msgBodyBytes) {
        this.msgBodyBytes = msgBodyBytes;
    }

    public int getCheckSum() {
        return checkSum;
    }

    public void setCheckSum(int checkSum) {
        this.checkSum = checkSum;
    }

    public String getBodyString() {
        return bodyString;
    }

    public void setBodyString(String bodyString) {
        this.bodyString = bodyString;
    }

    @Override
    public String toString() {
        return "PackageData [msgHeader=" + msgHeader + ", msgBodyBytes=" + Arrays.toString(msgBodyBytes) + ", checkSum="
                + checkSum + "]";
    }

    public static class MsgHeader {
        // 消息ID
        protected int msgId;

        /////// ========消息体属性
        // byte[2-3]
        protected int msgBodyPropsField;
        // 消息体长度
        protected int msgBodyLength;
        // 数据加密方式
        protected int encryptionType;
        // 是否分包,true==>有消息包封装项//false=0
        protected boolean hasSubPackage;
        // 保留位[14-15]
        protected int reservedBit;
        /////// ========消息体属性

        // 终端手机号(箱子id)
        protected String terminalPhone;
        // 流水号
        protected int flowId;

        //////// =====消息包封装项
        // byte[12-15]
        protected int packageInfoField;
        // 消息包总数(word(16))
        protected long totalSubPackage;
        // 包序号(word(16))这次发送的这个消息包是分包中的第几个消息包, 从 1 开始
        protected long subPackageSeq;
        //////// =====消息包封装项

        public int getMsgId() {
            return msgId;
        }

        public void setMsgId(int msgId) {
            this.msgId = msgId;
        }

        public int getMsgBodyLength() {
            return msgBodyLength;
        }

        public void setMsgBodyLength(int msgBodyLength) {
            this.msgBodyLength = msgBodyLength;
        }

        public int getEncryptionType() {
            return encryptionType;
        }

        public void setEncryptionType(int encryptionType) {
            this.encryptionType = encryptionType;
        }

        public String getTerminalPhone() {
            return terminalPhone;
        }

        public void setTerminalPhone(String terminalPhone) {
            this.terminalPhone = terminalPhone;
        }

        public int getFlowId() {
            return flowId;
        }

        public void setFlowId(int flowId) {
            this.flowId = flowId;
        }

        public boolean isHasSubPackage() {
            return hasSubPackage;
        }

        public void setHasSubPackage(boolean hasSubPackage) {
            this.hasSubPackage = hasSubPackage;
        }

        public int getReservedBit() {
            return reservedBit;
        }

        public void setReservedBit(int reservedBit) {
            this.reservedBit = reservedBit;
        }

        public long getTotalSubPackage() {
            return totalSubPackage;
        }

        public void setTotalSubPackage(long totalSubPackage) {
            this.totalSubPackage = totalSubPackage;
        }

        public long getSubPackageSeq() {
            return subPackageSeq;
        }

        public void setSubPackageSeq(long subPackageSeq) {
            this.subPackageSeq = subPackageSeq;
        }

        public int getMsgBodyPropsField() {
            return msgBodyPropsField;
        }

        public void setMsgBodyPropsField(int msgBodyPropsField) {
            this.msgBodyPropsField = msgBodyPropsField;
        }

        public void setPackageInfoField(int packageInfoField) {
            this.packageInfoField = packageInfoField;
        }

        public int getPackageInfoField() {
            return packageInfoField;
        }

        @Override
        public String toString() {
            return "MsgHeader [msgId=" + msgId + ", msgBodyPropsField=" + msgBodyPropsField + ", msgBodyLength="
                    + msgBodyLength + ", encryptionType=" + encryptionType + ", hasSubPackage=" + hasSubPackage
                    + ", reservedBit=" + reservedBit + ", terminalPhone=" + terminalPhone + ", flowId=" + flowId
                    + ", packageInfoField=" + packageInfoField + ", totalSubPackage=" + totalSubPackage
                    + ", subPackageSeq=" + subPackageSeq + "]";
        }
    }

    public String toHexString() {
        return "7e" + msgHeader.getMsgId() + msgHeader.getMsgBodyPropsField() + msgHeader.getTerminalPhone()
                + msgHeader.getFlowId() + getBodyString() + getCheckSum() + "7e";
    }
}
