package com.erobbing.tcpsmartkey.common.req;

import com.erobbing.tcpsmartkey.common.PackageData;

import java.util.Arrays;

/**
 * 终端注册消息
 *
 * @author hylexus
 */
public class TerminalRegisterMsg extends PackageData {

    private TerminalRegInfo terminalRegInfo;

    public TerminalRegisterMsg() {
    }

    public TerminalRegisterMsg(PackageData packageData) {
        this();
        //this.channel = packageData.getChannel();
        this.checkSum = packageData.getCheckSum();
        this.msgBodyBytes = packageData.getMsgBodyBytes();
        this.msgHeader = packageData.getMsgHeader();
    }

    public TerminalRegInfo getTerminalRegInfo() {
        return terminalRegInfo;
    }

    public void setTerminalRegInfo(TerminalRegInfo msgBody) {
        this.terminalRegInfo = msgBody;
    }

    @Override
    public String toString() {
        return "TerminalRegisterMsg [terminalRegInfo=" + terminalRegInfo + ", msgHeader=" + msgHeader
                + ", msgBodyBytes=" + Arrays.toString(msgBodyBytes) + ", checkSum=" + checkSum + "]";
    }

    public static class TerminalRegInfo {
        // byte[0-1] 省域ID(WORD),设备安装车辆所在的省域，省域ID采用GB/T2260中规定的行政区划代码6位中前两位
        // 0保留，由平台取默认值
        private int provinceId;
        // byte[2-3] 市县域ID(WORD) 设备安装车辆所在的市域或县域,市县域ID采用GB/T2260中规定的行 政区划代码6位中后四位
        // 0保留，由平台取默认值
        private int cityId;
        // byte[4-7]制造商ID(BYTE[4]) 4 个字节，终端制造商编码
        private String manufacturerId;
        //byte[8-17] BYTE[10] 10 个字节，此字段暂定10个字节，是终端使用店面的ID
        //private String terminalType;
        private String shopId;
        //byte[18-23] BYTE[6] 6个字节,最高两位表示终端类型，00表示钥匙扣，01表示24孔钥匙箱，99表示空,
        //剩余十位数字，制造商自行定义[建议 1位表示批次，2-5 年月  6-10 序列号]，
        private String terminalId;

        /**
         * 车牌颜色(BYTE) 车牌颜色，按照 JT/T415-2006 的 5.4.12 未上牌时，取值为0<br>
         * 0===未上车牌<br>
         * 1===蓝色<br>
         * 2===黄色<br>
         * 3===黑色<br>
         * 4===白色<br>
         * 9===其他
         */
        //private int licensePlateColor;
        // 车牌(STRING) 公安交 通管理部门颁 发的机动车号牌
        //private String licensePlate;
        public TerminalRegInfo() {
        }

        public int getProvinceId() {
            return provinceId;
        }

        public void setProvinceId(int provinceId) {
            this.provinceId = provinceId;
        }

        public int getCityId() {
            return cityId;
        }

        public void setCityId(int cityId) {
            this.cityId = cityId;
        }

        public String getManufacturerId() {
            return manufacturerId;
        }

        public void setManufacturerId(String manufacturerId) {
            this.manufacturerId = manufacturerId;
        }

        public String getShopId() {
            return shopId;
        }

        public void setShopId(String shopId) {
            this.shopId = shopId;
        }

        public String getTerminalId() {
            return terminalId;
        }

        public void setTerminalId(String terminalId) {
            this.terminalId = terminalId;
        }

        /*public int getLicensePlateColor() {
            return licensePlateColor;
        }

        public void setLicensePlateColor(int licensePlate) {
            this.licensePlateColor = licensePlate;
        }

        public String getLicensePlate() {
            return licensePlate;
        }

        public void setLicensePlate(String licensePlate) {
            this.licensePlate = licensePlate;
        }*/

        @Override
        public String toString() {
            return "TerminalRegInfo [provinceId=" + provinceId + ", cityId=" + cityId + ", manufacturerId="
                    + manufacturerId + ", shopId=" + shopId + ", terminalId=" + terminalId + "]";
        }

    }
}
