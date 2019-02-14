package com.erobbing.tcpsmartkey.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.hardware.usb.UsbManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.os.UEventObserver;
import android.util.Log;
import android.widget.Toast;

import com.erobbing.tcpsmartkey.R;
import com.erobbing.tcpsmartkey.common.PackageData;
import com.erobbing.tcpsmartkey.common.TPMSConsts;
import com.erobbing.tcpsmartkey.common.tcpclient.TcpClient;
import com.erobbing.tcpsmartkey.service.codec.MsgDecoder;
import com.erobbing.tcpsmartkey.service.codec.MsgEncoder;
import com.erobbing.tcpsmartkey.util.BCD8421Operater;
import com.erobbing.tcpsmartkey.util.BitOperator;
import com.erobbing.tcpsmartkey.util.HexStringUtils;
import com.erobbing.tcpsmartkey.util.JT808ProtocolUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by zhangzhaolei on 2018/12/25.
 */

public class TcpService extends Service {
    private static final String TAG = "TcpService";
    private Context mContext;
    private AlarmManager mAlarmManager;
    private String mLocationFrequency = "360000";
    private static final String IP = "140.143.7.147";//"192.168.1.58";//"192.168.0.175";//"140.143.7.147";//"172.23.130.2";
    private static final int PORT = 20048;//20048;//1500;

    private static final String REG_TMP = "7e0100002c0200000000150025002c0133373039363054372d54383038000000000000000000000000003033323931373001d4c142383838387b7e";
    //标识位,消息头尾各一个
    private static final String MSG_FLAG = "7e";

    //reg 注册 head 12byte
    //byte[0-1]   消息ID word(16)
    private String mHeadMsgID = "0100";
    //byte[2-3]   消息体属性 word(16)
    private String mHeadAttributes = "";
    //bit[0-9]    消息体长度
    private String mHeadAttributesBodyLength = "";
    //bit[10-12]  数据加密方式, 此三位都为 0，表示消息体不加密;第 10 位为 1，表示消息体经过 RSA 算法加密,其它保留
    private String mHeadAttributesEncryptionType = "";
    //bit[13]   分包
    //          1：消息体卫长消息，进行分包发送处理，具体分包信息由消息包封装项决定
    //          0：则消息头中无消息包封装项字段
    private String mHeadAttributesSubPac = "0";
    //bit[14-15]  保留
    private String mHeadAttributesLast = "0";

    //byte[4-9]   终端手机号或设备ID bcd[6]
    private String mHeadPhoneNum = "000123456789";
    //byte[10-11]     消息流水号 word(16) 按发送顺序从 0 开始循环累加
    private String mHeadMsgSeq = "0001";
    private int mHeadMsgSeqInt = 0;
    //byte[12-15]     消息包封装项
    private String mHeadSubPac = "00000000";
    //bit[0-1]   消息包总数(word(16)) 该消息分包后得总包数
    private String mHeadSubPacNum = "00";
    //bit[2-3]   包序号(word(16)) 从 1 开始
    private String mHeadSubPacSeq = "00";

    //msg reg body
    //byte[0-1]     省域ID  word(16) 标示终端安装车辆所在的省域，0 保留，由平台取默
    //认值。省域 ID 采用 GB/T 2260 中规定的行政区划代 码六位中前两位
    private String mBodyProvinceID = "000b";//370  202青岛市南区(0025 00ca)//110108 北京海淀区
    //byte[2-3]     市县域ID  word(16)
    private String mBodyCityID = "006c";//北京海淀区
    //byte[4-7]     制造商ID BYTE[4],4个字节，终端制造商编码
    private String mBodyManufacturerID = "00000000";
    //byte[8-17] BYTE[10] 10 个字节，此字段暂定10个字节，是终端使用店面的ID
    private String mBodyShopID = "00000000000000000000";
    //byte[18-23] BYTE[6] 6个字节,最高两位表示终端类型，00表示钥匙扣，01表示24孔钥匙箱，99表示空,
    //剩余十位数字，制造商自行定义[建议 1位表示批次，2-5 年月  6-10 序列号]，
    private String mBodyTerminalID = "011181200001";

    //reg checksum
    private String mCheckSum = "00";

    //server Authentication 鉴权
    private String mResponseMsgID = "8100";

    private String mBodyString = "";
    private String mHeaderString = "";
    private String headerAndBodyString = "";
    private String tmpFlow = "";
    private String tmpReqFlow = "";
    private String mBodyFlow = "";//tmpReqFlow
    private String mBodyRespType = "";

    //终端普通应答
    private static final String MSG_TYPE_CLIENT_RESPONSE = "0001";//"0001";
    //心跳
    private static final String MSG_TYPE_HEARTBEAT = "0002";//"0002";
    //注销
    private static final String MSG_TYPE_UNREGISTER = "0003";
    //注册
    private static final String MSG_TYPE_REGISTER = "0100";
    //终端鉴权
    private static final String MSG_TYPE_AUTHENTICATION = "0102";
    //借钥匙
    private static final String MSG_TYPE_BORROW = "0103";//暂时未定义
    //还钥匙
    private static final String MSG_TYPE_RETURN = "0104";//暂时未定义
    //平台普通应答
    private static final String MSG_TYPE_SERVER_RESPONSE = "8001";
    //补传分包请求
    private static final String MSG_TYPE_SUB_PAC_REQ = "8003";
    //终端注册应答
    private static final String MSG_TYPE_REG_RESPONSE = "8100";
    //终端控制
    private static final String MSG_TYPE_SERVER_CONTROL = "8105";
    //终端控制通用应答
    private static final String MSG_TYPE_SERVER_CONTROL_RESPONSE = "8106";
    //终端主动上报
    private static final String MSG_TYPE_CLIENT_REQ = "0817";
    //设置终端参数
    private static final String MSG_TYPE_CLIENT_SETTINGS = "8103";

    private BitOperator mBitOperator;
    private BCD8421Operater mBCD8421Operater;
    private JT808ProtocolUtils mJT808ProtocolUtils;
    private MsgDecoder mMsgDecoder;
    private MsgEncoder mMsgEncoder;
    PackageData mPackageData;
    PackageData.MsgHeader mPackageDataMsgHeader;

    //演示用终端(箱子)id
    private String tmpPhoneId = "010000090911";//019999999998
    private String tmpKeyId = "000000090901";
    private String tmpKey01Id = "000000090901";
    private String tmpKey02Id = "000000090902";
    private String tmpKey03Id = "000000090903";
    private String tmpKey04Id = "000000090904";
    private String tmpKey05Id = "000000090905";
    private String tmpKey06Id = "000000090906";
    private String tmpKey07Id = "000000090907";
    private String tmpKey08Id = "000000090908";
    private String tmpKey09Id = "000000090909";
    private String tmpKey10Id = "000000090910";

    private boolean needKeyStatusReport = false;


    //private BroadcastReceiver mUsbReceiver;


    public TcpService() {
    }

    private static final String KEY_INT01_PATH = "/sys/devices/virtual/switch/KEY_INT01/state";
    private static final String KEY_INT01_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY_INT01";
    private static final String KEY_INT02_PATH = "/sys/devices/virtual/switch/KEY_INT02/state";
    private static final String KEY_INT02_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY_INT02";
    private static final String KEY_INT03_PATH = "/sys/devices/virtual/switch/KEY_INT03/state";
    private static final String KEY_INT03_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY_INT03";
    private static final String KEY_INT04_PATH = "/sys/devices/virtual/switch/KEY_INT04/state";
    private static final String KEY_INT04_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY_INT04";
    private static final String KEY_INT05_PATH = "/sys/devices/virtual/switch/KEY_INT05/state";
    private static final String KEY_INT05_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY_INT05";
    private static final String KEY_INT06_PATH = "/sys/devices/virtual/switch/KEY_INT06/state";
    private static final String KEY_INT06_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY_INT06";
    private static final String KEY_INT07_PATH = "/sys/devices/virtual/switch/KEY_INT07/state";
    private static final String KEY_INT07_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY_INT07";
    private static final String KEY_INT08_PATH = "/sys/devices/virtual/switch/KEY_INT08/state";
    private static final String KEY_INT08_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY_INT08";
    private static final String KEY_INT09_PATH = "/sys/devices/virtual/switch/KEY_INT09/state";
    private static final String KEY_INT09_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY_INT09";
    private static final String KEY_INT10_PATH = "/sys/devices/virtual/switch/KEY_INT10/state";
    private static final String KEY_INT10_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY_INT10";

    private static final String KEY0_INT_01_PATH = "/sys/devices/virtual/switch/KEY0_INT_01/state";
    private static final String KEY0_INT_01_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY0_INT_01";
    private static final String KEY0_INT_02_PATH = "/sys/devices/virtual/switch/KEY0_INT_02/state";
    private static final String KEY0_INT_02_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY0_INT_02";
    private static final String KEY0_INT_03_PATH = "/sys/devices/virtual/switch/KEY0_INT_03/state";
    private static final String KEY0_INT_03_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY0_INT_03";
    private static final String KEY0_INT_04_PATH = "/sys/devices/virtual/switch/KEY0_INT_04/state";
    private static final String KEY0_INT_04_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY0_INT_04";
    private static final String KEY0_INT_05_PATH = "/sys/devices/virtual/switch/KEY0_INT_05/state";
    private static final String KEY0_INT_05_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY0_INT_05";
    private static final String KEY0_INT_06_PATH = "/sys/devices/virtual/switch/KEY0_INT_06/state";
    private static final String KEY0_INT_06_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY0_INT_06";
    private static final String KEY0_INT_07_PATH = "/sys/devices/virtual/switch/KEY0_INT_07/state";
    private static final String KEY0_INT_07_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY0_INT_07";
    private static final String KEY0_INT_08_PATH = "/sys/devices/virtual/switch/KEY0_INT_08/state";
    private static final String KEY0_INT_08_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY0_INT_08";
    private static final String KEY0_INT_09_PATH = "/sys/devices/virtual/switch/KEY0_INT_09/state";
    private static final String KEY0_INT_09_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY0_INT_09";
    private static final String KEY0_INT_10_PATH = "/sys/devices/virtual/switch/KEY0_INT_10/state";
    private static final String KEY0_INT_10_STATE_MATCH = "DEVPATH=/devices/virtual/switch/KEY0_INT_10";

    //leds link node -> /sys/class/leds/led-ct-02
    //led series
    private static final String LED_SERIES_01 = "/sys/devices/soc.0/gpio-leds.70/leds/led-ct-01/brightness";
    private static final String LED_SERIES_02 = "/sys/devices/soc.0/gpio-leds.70/leds/led-ct-02/brightness";
    private static final String LED_SERIES_03 = "/sys/devices/soc.0/gpio-leds.70/leds/led-ct-03/brightness";
    //
    private static final String LED_RED_01 = "/sys/devices/soc.0/gpio-leds.70/leds/led-r-01/brightness";
    private static final String LED_RED_02 = "/sys/devices/soc.0/gpio-leds.70/leds/led-r-02/brightness";
    private static final String LED_RED_03 = "/sys/devices/soc.0/gpio-leds.70/leds/led-r-03/brightness";
    private static final String LED_RED_04 = "/sys/devices/soc.0/gpio-leds.70/leds/led-r-04/brightness";
    private static final String LED_RED_05 = "/sys/devices/soc.0/gpio-leds.70/leds/led-r-05/brightness";
    private static final String LED_RED_06 = "/sys/devices/soc.0/gpio-leds.70/leds/led-r-06/brightness";
    private static final String LED_RED_07 = "/sys/devices/soc.0/gpio-leds.70/leds/led-r-07/brightness";
    private static final String LED_RED_08 = "/sys/devices/soc.0/gpio-leds.70/leds/led-r-08/brightness";
    private static final String LED_RED_09 = "/sys/devices/soc.0/gpio-leds.70/leds/led-r-09/brightness";
    private static final String LED_RED_10 = "/sys/devices/soc.0/gpio-leds.70/leds/led-r-10/brightness";
    private static final String LED_GREEN_01 = "/sys/devices/soc.0/gpio-leds.70/leds/led-g-01/brightness";
    private static final String LED_GREEN_02 = "/sys/devices/soc.0/gpio-leds.70/leds/led-g-02/brightness";
    private static final String LED_GREEN_03 = "/sys/devices/soc.0/gpio-leds.70/leds/led-g-03/brightness";
    private static final String LED_GREEN_04 = "/sys/devices/soc.0/gpio-leds.70/leds/led-g-04/brightness";
    private static final String LED_GREEN_05 = "/sys/devices/soc.0/gpio-leds.70/leds/led-g-05/brightness";
    private static final String LED_GREEN_06 = "/sys/devices/soc.0/gpio-leds.70/leds/led-g-06/brightness";
    private static final String LED_GREEN_07 = "/sys/devices/soc.0/gpio-leds.70/leds/led-g-07/brightness";
    private static final String LED_GREEN_08 = "/sys/devices/soc.0/gpio-leds.70/leds/led-g-08/brightness";
    private static final String LED_GREEN_09 = "/sys/devices/soc.0/gpio-leds.70/leds/led-g-09/brightness";
    private static final String LED_GREEN_10 = "/sys/devices/soc.0/gpio-leds.70/leds/led-g-10/brightness";

    private boolean isKey01On = false;
    private boolean isKey01MiddleOn = false;
    private boolean isKey01BottomOn = false;

    private boolean isKey02On = false;
    private boolean isKey02MiddleOn = false;
    private boolean isKey02BottomOn = false;

    private boolean isKey03On = false;
    private boolean isKey03MiddleOn = false;
    private boolean isKey03BottomOn = false;

    private boolean isKey04On = false;
    private boolean isKey04MiddleOn = false;
    private boolean isKey04BottomOn = false;

    private boolean isKey05On = false;
    private boolean isKey05MiddleOn = false;
    private boolean isKey05BottomOn = false;

    private boolean isKey06On = false;
    private boolean isKey06MiddleOn = false;
    private boolean isKey06BottomOn = false;

    private boolean isKey07On = false;
    private boolean isKey07MiddleOn = false;
    private boolean isKey07BottomOn = false;

    private boolean isKey08On = false;
    private boolean isKey08MiddleOn = false;
    private boolean isKey08BottomOn = false;

    private boolean isKey09On = false;
    private boolean isKey09MiddleOn = false;
    private boolean isKey09BottomOn = false;

    private boolean isKey10On = false;
    private boolean isKey10MiddleOn = false;
    private boolean isKey10BottomOn = false;

    private boolean isAllKeysCpuBus = true;
    private String IdReadFromKeys = "";

    private boolean isNetworkAvailable = true;

    private static final String ALL_KEYS_ID_PATH = "/sys/devices/virtual/switch/rkeyid";
    private static final String ALL_KEYS_ID_REQ_PATH = "/sys/class/gpio_switch/wkeyid";
    private static final String ALL_KEYS_ID_CLEAR_COMMAND = "cat /sys/devices/virtual/switch/rkeyid";

    private static final String MOTOR_01_PATH = "/sys/class/gpio_switch/motor_ct_01";
    private static final String MOTOR_02_PATH = "/sys/class/gpio_switch/motor_ct_02";
    private static final String MOTOR_03_PATH = "/sys/class/gpio_switch/motor_ct_03";
    private static final String MOTOR_04_PATH = "/sys/class/gpio_switch/motor_ct_04";
    private static final String MOTOR_05_PATH = "/sys/class/gpio_switch/motor_ct_05";
    private static final String MOTOR_06_PATH = "/sys/class/gpio_switch/motor_ct_06";
    private static final String MOTOR_07_PATH = "/sys/class/gpio_switch/motor_ct_07";
    private static final String MOTOR_08_PATH = "/sys/class/gpio_switch/motor_ct_08";
    private static final String MOTOR_09_PATH = "/sys/class/gpio_switch/motor_ct_09";
    private static final String MOTOR_10_PATH = "/sys/class/gpio_switch/motor_ct_10";

    private static final String SWITCH_01_PATH = "/sys/class/gpio_switch/switch_ct_01";
    private static final String SWITCH_02_PATH = "/sys/class/gpio_switch/switch_ct_02";
    private static final String SWITCH_03_PATH = "/sys/class/gpio_switch/switch_ct_03";
    private static final String SWITCH_04_PATH = "/sys/class/gpio_switch/switch_ct_04";
    private static final String SWITCH_05_PATH = "/sys/class/gpio_switch/switch_ct_05";
    private static final String SWITCH_06_PATH = "/sys/class/gpio_switch/switch_ct_06";
    private static final String SWITCH_07_PATH = "/sys/class/gpio_switch/switch_ct_07";
    private static final String SWITCH_08_PATH = "/sys/class/gpio_switch/switch_ct_08";
    private static final String SWITCH_09_PATH = "/sys/class/gpio_switch/switch_ct_09";
    private static final String SWITCH_10_PATH = "/sys/class/gpio_switch/switch_ct_10";

    //key 01
    private static final int HANDLER_MSG_KEY_01_MOTOR_ON = 1;
    private static final int HANDLER_MSG_KEY_01_POWER_ON = 2;
    private static final int HANDLER_MSG_KEY_01_ID_CLEAR = 3;
    private static final int HANDLER_MSG_KEY_01_REQ_READ_ID = 4;
    private static final int HANDLER_MSG_KEY_01_READ_ID = 5;
    private static final int HANDLER_MSG_KEY_01_SEND_AUTH_CODE = 6;
    private static final int HANDLER_MSG_KEY_01_MOTOR_OFF = 7;
    private static final int HANDLER_MSG_KEY_01_POWER_OFF = 8;
    //key 02
    private static final int HANDLER_MSG_KEY_02_MOTOR_ON = 9;
    private static final int HANDLER_MSG_KEY_02_POWER_ON = 10;
    private static final int HANDLER_MSG_KEY_02_ID_CLEAR = 11;
    private static final int HANDLER_MSG_KEY_02_REQ_READ_ID = 12;
    private static final int HANDLER_MSG_KEY_02_READ_ID = 13;
    private static final int HANDLER_MSG_KEY_02_SEND_AUTH_CODE = 14;
    private static final int HANDLER_MSG_KEY_02_MOTOR_OFF = 15;
    private static final int HANDLER_MSG_KEY_02_POWER_OFF = 16;
    //key 03
    private static final int HANDLER_MSG_KEY_03_MOTOR_ON = 17;
    private static final int HANDLER_MSG_KEY_03_POWER_ON = 18;
    private static final int HANDLER_MSG_KEY_03_ID_CLEAR = 19;
    private static final int HANDLER_MSG_KEY_03_REQ_READ_ID = 20;
    private static final int HANDLER_MSG_KEY_03_READ_ID = 21;
    private static final int HANDLER_MSG_KEY_03_SEND_AUTH_CODE = 22;
    private static final int HANDLER_MSG_KEY_03_MOTOR_OFF = 23;
    private static final int HANDLER_MSG_KEY_03_POWER_OFF = 24;
    //key 04
    private static final int HANDLER_MSG_KEY_04_MOTOR_ON = 25;
    private static final int HANDLER_MSG_KEY_04_POWER_ON = 26;
    private static final int HANDLER_MSG_KEY_04_ID_CLEAR = 27;
    private static final int HANDLER_MSG_KEY_04_REQ_READ_ID = 28;
    private static final int HANDLER_MSG_KEY_04_READ_ID = 29;
    private static final int HANDLER_MSG_KEY_04_SEND_AUTH_CODE = 30;
    private static final int HANDLER_MSG_KEY_04_MOTOR_OFF = 31;
    private static final int HANDLER_MSG_KEY_04_POWER_OFF = 32;
    //key 05
    private static final int HANDLER_MSG_KEY_05_MOTOR_ON = 33;
    private static final int HANDLER_MSG_KEY_05_POWER_ON = 34;
    private static final int HANDLER_MSG_KEY_05_ID_CLEAR = 35;
    private static final int HANDLER_MSG_KEY_05_REQ_READ_ID = 36;
    private static final int HANDLER_MSG_KEY_05_READ_ID = 37;
    private static final int HANDLER_MSG_KEY_05_SEND_AUTH_CODE = 38;
    private static final int HANDLER_MSG_KEY_05_MOTOR_OFF = 39;
    private static final int HANDLER_MSG_KEY_05_POWER_OFF = 40;
    //key 06
    private static final int HANDLER_MSG_KEY_06_MOTOR_ON = 41;
    private static final int HANDLER_MSG_KEY_06_POWER_ON = 42;
    private static final int HANDLER_MSG_KEY_06_ID_CLEAR = 43;
    private static final int HANDLER_MSG_KEY_06_REQ_READ_ID = 44;
    private static final int HANDLER_MSG_KEY_06_READ_ID = 45;
    private static final int HANDLER_MSG_KEY_06_SEND_AUTH_CODE = 46;
    private static final int HANDLER_MSG_KEY_06_MOTOR_OFF = 47;
    private static final int HANDLER_MSG_KEY_06_POWER_OFF = 48;
    //key 07
    private static final int HANDLER_MSG_KEY_07_MOTOR_ON = 49;
    private static final int HANDLER_MSG_KEY_07_POWER_ON = 50;
    private static final int HANDLER_MSG_KEY_07_ID_CLEAR = 51;
    private static final int HANDLER_MSG_KEY_07_REQ_READ_ID = 52;
    private static final int HANDLER_MSG_KEY_07_READ_ID = 53;
    private static final int HANDLER_MSG_KEY_07_SEND_AUTH_CODE = 54;
    private static final int HANDLER_MSG_KEY_07_MOTOR_OFF = 55;
    private static final int HANDLER_MSG_KEY_07_POWER_OFF = 56;
    //key 08
    private static final int HANDLER_MSG_KEY_08_MOTOR_ON = 57;
    private static final int HANDLER_MSG_KEY_08_POWER_ON = 58;
    private static final int HANDLER_MSG_KEY_08_ID_CLEAR = 59;
    private static final int HANDLER_MSG_KEY_08_REQ_READ_ID = 60;
    private static final int HANDLER_MSG_KEY_08_READ_ID = 61;
    private static final int HANDLER_MSG_KEY_08_SEND_AUTH_CODE = 62;
    private static final int HANDLER_MSG_KEY_08_MOTOR_OFF = 63;
    private static final int HANDLER_MSG_KEY_08_POWER_OFF = 64;
    //key 09
    private static final int HANDLER_MSG_KEY_09_MOTOR_ON = 65;
    private static final int HANDLER_MSG_KEY_09_POWER_ON = 66;
    private static final int HANDLER_MSG_KEY_09_ID_CLEAR = 67;
    private static final int HANDLER_MSG_KEY_09_REQ_READ_ID = 68;
    private static final int HANDLER_MSG_KEY_09_READ_ID = 69;
    private static final int HANDLER_MSG_KEY_09_SEND_AUTH_CODE = 70;
    private static final int HANDLER_MSG_KEY_09_MOTOR_OFF = 71;
    private static final int HANDLER_MSG_KEY_09_POWER_OFF = 72;
    //key 10
    private static final int HANDLER_MSG_KEY_10_MOTOR_ON = 73;
    private static final int HANDLER_MSG_KEY_10_POWER_ON = 74;
    private static final int HANDLER_MSG_KEY_10_ID_CLEAR = 75;
    private static final int HANDLER_MSG_KEY_10_REQ_READ_ID = 76;
    private static final int HANDLER_MSG_KEY_10_READ_ID = 77;
    private static final int HANDLER_MSG_KEY_10_SEND_AUTH_CODE = 78;
    private static final int HANDLER_MSG_KEY_10_MOTOR_OFF = 79;
    private static final int HANDLER_MSG_KEY_10_POWER_OFF = 80;

    private DroidToKey01Handler mDroidToKey01Handler;
    private DroidToKey02Handler mDroidToKey02Handler;
    private DroidToKey03Handler mDroidToKey03Handler;
    private DroidToKey04Handler mDroidToKey04Handler;
    private DroidToKey05Handler mDroidToKey05Handler;
    private DroidToKey06Handler mDroidToKey06Handler;
    private DroidToKey07Handler mDroidToKey07Handler;
    private DroidToKey08Handler mDroidToKey08Handler;
    private DroidToKey09Handler mDroidToKey09Handler;
    private DroidToKey10Handler mDroidToKey10Handler;


    /*private final UEventObserver mMiddleSwitch01Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mMiddleSwitch01Observer", "onUEvent.event.toString()=" + event.toString());
            isKey01MiddleOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            //handler open motor
            if (isKey01MiddleOn) {
                mDroidToKey01Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_01_MOTOR_ON, 100);
                ledSeriesCtrl(LED_GREEN_01, true);
            } else {
                mDroidToKey01Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_01_MOTOR_OFF, 100);//拔钥匙
                //playFromRawFile(mContext, R.raw.key_out);
                mDroidToKey04Handler.postDelayed(playSoundKeyOut, 50);
            }
        }
    };

    private final UEventObserver mMiddleSwitch02Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mMiddleSwitch02Observer", "onUEvent.event.toString()=" + event.toString());
            isKey02MiddleOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            //handler open motor
            if (isKey02MiddleOn) {
                mDroidToKey02Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_02_MOTOR_ON, 100);
                ledSeriesCtrl(LED_GREEN_02, true);
            } else {
                mDroidToKey02Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_02_MOTOR_OFF, 100);//拔钥匙
                //playFromRawFile(mContext, R.raw.key_out);
                mDroidToKey04Handler.postDelayed(playSoundKeyOut, 50);
                //ledSeriesCtrl(LED_SERIES_01, false);
            }
        }
    };
    private final UEventObserver mMiddleSwitch03Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mMiddleSwitch03Observer", "onUEvent.event.toString()=" + event.toString());
            isKey03MiddleOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            //handler open motor
            if (isKey03MiddleOn) {
                mDroidToKey03Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_03_MOTOR_ON, 100);
                ledSeriesCtrl(LED_GREEN_03, true);
            } else {
                mDroidToKey03Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_03_MOTOR_OFF, 100);//拔钥匙
                //playFromRawFile(mContext, R.raw.key_out);
                mDroidToKey04Handler.postDelayed(playSoundKeyOut, 50);
            }
        }
    };
    private final UEventObserver mMiddleSwitch04Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mMiddleSwitch04Observer", "onUEvent.event.toString()=" + event.toString());
            isKey04MiddleOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            //handler open motor
            if (isKey04MiddleOn) {
                mDroidToKey04Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_MOTOR_ON, 100);
                ledSeriesCtrl(LED_GREEN_04, true);
            } else {
                mDroidToKey04Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_MOTOR_OFF, 100);//拔钥匙
                //playFromRawFile(mContext, R.raw.key_out);
                mDroidToKey04Handler.postDelayed(playSoundKeyOut, 50);
                //ledSeriesCtrl(LED_SERIES_01, false);
            }
        }
    };
    private final UEventObserver mMiddleSwitch05Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mMiddleSwitch05Observer", "onUEvent.event.toString()=" + event.toString());
            isKey05MiddleOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            //handler open motor
            if (isKey05MiddleOn) {
                mDroidToKey05Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_05_MOTOR_ON, 100);
                ledSeriesCtrl(LED_GREEN_05, true);
            } else {
                mDroidToKey05Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_05_MOTOR_OFF, 100);//拔钥匙
                //playFromRawFile(mContext, R.raw.key_out);
                mDroidToKey04Handler.postDelayed(playSoundKeyOut, 50);
                //ledSeriesCtrl(LED_SERIES_01, false);
            }
        }
    };
    private final UEventObserver mMiddleSwitch06Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mMiddleSwitch06Observer", "onUEvent.event.toString()=" + event.toString());
            isKey06MiddleOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            //handler open motor
            if (isKey06MiddleOn) {
                mDroidToKey06Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_06_MOTOR_ON, 100);
                ledSeriesCtrl(LED_GREEN_06, true);
            } else {
                mDroidToKey06Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_06_MOTOR_OFF, 100);//拔钥匙
                //playFromRawFile(mContext, R.raw.key_out);
                mDroidToKey04Handler.postDelayed(playSoundKeyOut, 50);
                //ledSeriesCtrl(LED_SERIES_01, false);
            }
        }
    };
    private final UEventObserver mMiddleSwitch07Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mMiddleSwitch07Observer", "onUEvent.event.toString()=" + event.toString());
            isKey07MiddleOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            //handler open motor
            if (isKey07MiddleOn) {
                mDroidToKey07Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_07_MOTOR_ON, 100);
            } else {
                mDroidToKey07Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_07_MOTOR_OFF, 100);//拔钥匙
                //playFromRawFile(mContext, R.raw.key_out);
                mDroidToKey04Handler.postDelayed(playSoundKeyOut, 50);
                //ledSeriesCtrl(LED_SERIES_01, false);
            }
        }
    };
    private final UEventObserver mMiddleSwitch08Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mMiddleSwitch08Observer", "onUEvent.event.toString()=" + event.toString());
            isKey08MiddleOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            //handler open motor
            if (isKey08MiddleOn) {
                mDroidToKey08Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_08_MOTOR_ON, 100);
            } else {
                mDroidToKey08Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_08_MOTOR_OFF, 100);//拔钥匙
                //playFromRawFile(mContext, R.raw.key_out);
                mDroidToKey04Handler.postDelayed(playSoundKeyOut, 50);
                //ledSeriesCtrl(LED_SERIES_01, false);
            }
        }
    };
    private final UEventObserver mMiddleSwitch09Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mMiddleSwitch09Observer", "onUEvent.event.toString()=" + event.toString());
            isKey09MiddleOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            //handler open motor
            if (isKey09MiddleOn) {
                mDroidToKey09Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_09_MOTOR_ON, 100);
            } else {
                mDroidToKey09Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_09_MOTOR_OFF, 100);//拔钥匙
                //playFromRawFile(mContext, R.raw.key_out);
                mDroidToKey04Handler.postDelayed(playSoundKeyOut, 50);
                //ledSeriesCtrl(LED_SERIES_01, false);
            }
        }
    };
    private final UEventObserver mMiddleSwitch10Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mMiddleSwitch10Observer", "onUEvent.event.toString()=" + event.toString());
            isKey10MiddleOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            //handler open motor
            if (isKey10MiddleOn) {
                mDroidToKey10Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_10_MOTOR_ON, 100);
            } else {
                mDroidToKey10Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_10_MOTOR_OFF, 100);//拔钥匙
                //playFromRawFile(mContext, R.raw.key_out);
                mDroidToKey04Handler.postDelayed(playSoundKeyOut, 50);
                //ledSeriesCtrl(LED_SERIES_01, false);
            }
        }
    };*/

    /////////////////////////////////////////////////////////////
    private final UEventObserver mBottomSwitch01Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch01Observer", "onUEvent.event.toString()=" + event.toString());
            isKey01BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            if (isKey01BottomOn) {
                /*//playFromRawFile(mContext, R.raw.key_insert);
                mDroidToKey04Handler.postDelayed(playSoundKeyInsert, 50);
                ledSeriesCtrl(LED_GREEN_01, false);
                mDroidToKey01Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_01_POWER_ON, 100);
                //tmpKeyId
                //String ttString = "0100001e" + tmpPhoneId + "0018053105320000000322222222222222222222" + tmpKey01Id + "990000000000";
                String ttString = "0102000A" + tmpKey01Id + "0019" + "66697662526F44705A31";
                int checkint = mBitOperator.getCheckSum4JT808(
                        mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                try {
                    sendMessage(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                } catch (Exception e) {
                    e.printStackTrace();
                }*/
                //tmpKeyId
                String proAndCity = "01000633";//getSavedProvinceAndCity();
                String manufacturerID = "88888888";//getSavedManufacturerID();
                String shopID = "77777777777777777777";//getSavedShopID();
                //getSavedBoxID();
                sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + tmpKey01Id));
            } else {

            }
        }
    };
    private final UEventObserver mBottomSwitch02Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch02Observer", "onUEvent.event.toString()=" + event.toString());
            isKey02BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            if (isKey02BottomOn) {
                mDroidToKey04Handler.postDelayed(playSoundKeyInsert, 50);
                ledSeriesCtrl(LED_GREEN_02, false);
                mDroidToKey02Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_02_POWER_ON, 100);
                //tmpKeyId
                String ttString = "0102000A" + tmpKey02Id + "0019" + "434E6B47356F63683142";
                int checkint = mBitOperator.getCheckSum4JT808(
                        mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                try {
                    sendMessage(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {

            }
        }
    };
    private final UEventObserver mBottomSwitch03Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch03Observer", "onUEvent.event.toString()=" + event.toString());
            isKey03BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            if (isKey03BottomOn) {
                mDroidToKey04Handler.postDelayed(playSoundKeyInsert, 50);
                ledSeriesCtrl(LED_GREEN_03, false);
                mDroidToKey03Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_03_POWER_ON, 100);
                //tmpKeyId
                String ttString = "0102000A" + tmpKey03Id + "0019" + "5A47794F317468677876";
                int checkint = mBitOperator.getCheckSum4JT808(
                        mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                try {
                    sendMessage(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {

            }
        }
    };
    private final UEventObserver mBottomSwitch04Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch04Observer", "onUEvent.event.toString()=" + event.toString());
            isKey04BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            if (isKey04BottomOn) {
                mDroidToKey04Handler.postDelayed(playSoundKeyInsert, 50);
                ledSeriesCtrl(LED_GREEN_04, false);
                mDroidToKey04Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_POWER_ON, 100);
                //tmpKeyId
                String ttString = "0102000A" + tmpKey04Id + "0019" + "3944465673654E386B4B";
                int checkint = mBitOperator.getCheckSum4JT808(
                        mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                try {
                    sendMessage(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {

            }
        }
    };
    private final UEventObserver mBottomSwitch05Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch05Observer", "onUEvent.event.toString()=" + event.toString());
            isKey05BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            if (isKey05BottomOn) {
                mDroidToKey04Handler.postDelayed(playSoundKeyInsert, 50);
                ledSeriesCtrl(LED_GREEN_05, false);
                mDroidToKey05Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_05_POWER_ON, 100);
                //tmpKeyId
                String ttString = "0102000A" + tmpKey05Id + "0019" + "746654456F6865736A51";
                int checkint = mBitOperator.getCheckSum4JT808(
                        mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                try {
                    sendMessage(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {

            }
        }
    };
    private final UEventObserver mBottomSwitch06Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch06Observer", "onUEvent.event.toString()=" + event.toString());
            isKey06BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            if (isKey06BottomOn) {
                mDroidToKey04Handler.postDelayed(playSoundKeyInsert, 50);
                ledSeriesCtrl(LED_GREEN_06, false);
                mDroidToKey06Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_06_POWER_ON, 100);
                //tmpKeyId
                String ttString = "0102000A" + tmpKey06Id + "0019" + "3237644E4D7570334D6C";
                int checkint = mBitOperator.getCheckSum4JT808(
                        mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                try {
                    sendMessage(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {

            }
        }
    };
    private final UEventObserver mBottomSwitch07Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch07Observer", "onUEvent.event.toString()=" + event.toString());
            isKey07BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            if (isKey07BottomOn) {
                mDroidToKey04Handler.postDelayed(playSoundKeyInsert, 50);
                mDroidToKey07Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_07_POWER_ON, 100);
                //tmpKeyId
                String ttString = "0102000A" + tmpKey07Id + "0019" + "6B557943376A6E465566";
                int checkint = mBitOperator.getCheckSum4JT808(
                        mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                try {
                    sendMessage(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {

            }
        }
    };
    private final UEventObserver mBottomSwitch08Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch08Observer", "onUEvent.event.toString()=" + event.toString());
            isKey08BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            if (isKey08BottomOn) {
                mDroidToKey04Handler.postDelayed(playSoundKeyInsert, 50);
                mDroidToKey08Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_08_POWER_ON, 100);
                //tmpKeyId
                String ttString = "0102000A" + tmpKey08Id + "0019" + "6A50777552367A417A4F";
                int checkint = mBitOperator.getCheckSum4JT808(
                        mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                try {
                    sendMessage(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {

            }
        }
    };
    private final UEventObserver mBottomSwitch09Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch09Observer", "onUEvent.event.toString()=" + event.toString());
            isKey09BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            if (isKey09BottomOn) {
                mDroidToKey04Handler.postDelayed(playSoundKeyInsert, 50);
                mDroidToKey09Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_09_POWER_ON, 100);
                //tmpKeyId
                String ttString = "0102000A" + tmpKey09Id + "0019" + "4A696554323132466656";
                int checkint = mBitOperator.getCheckSum4JT808(
                        mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                try {
                    sendMessage(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {

            }
        }
    };
    private final UEventObserver mBottomSwitch10Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch10Observer", "onUEvent.event.toString()=" + event.toString());
            isKey10BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            if (isKey10BottomOn) {
                mDroidToKey04Handler.postDelayed(playSoundKeyInsert, 50);
                mDroidToKey10Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_10_POWER_ON, 100);
                //tmpKeyId
                String ttString = "0102000A" + tmpKey10Id + "0019" + "565063306E3766337832";
                int checkint = mBitOperator.getCheckSum4JT808(
                        mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                try {
                    sendMessage(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {

            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        Log.d(TAG, "TcpService.onCreate");
        //mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        connect(IP, PORT);
        TcpClient.init().setDisconnectedCallback(new TcpClient.OnServerDisconnectedCallbackBlock() {
            @Override
            public void callback(IOException e) {
                Log.e("====", "=========service=断开连接" + "\n");
            }
        });
        TcpClient.init().setConnectedCallback(new TcpClient.OnServerConnectedCallbackBlock() {
            @Override
            public void callback() {
                Log.e("====", "=========service=连接成功" + "\n");
                //sendMessage(REG_TMP);
            }
        });
        TcpClient.init().setReceivedCallback(new TcpClient.OnReceiveCallbackBlock() {
            @Override
            public void callback(String receicedMessage) {
                Log.e("====", "=========service=receive=" + receicedMessage + "\n");
                //byte[] bs = receicedMessage.getBytes();
                // 字节数据转换为针对于808消息结构的实体类
                //PackageData pkg = new MsgDecoder().bytes2PackageData(bs);
                // 引用channel,以便回送数据给硬件
                //this.processPackageData(pkg);
                //Log.e("====", "========PackageData=" + pkg.getMsgHeader().getMsgId());
                String tmp = "7E0002000000B002C787870005997E";
                //Log.e("====", "=======toLowerCase=" + tmp.toLowerCase());
                processReceiveData(receicedMessage.toString());
            }
        });
        mBitOperator = new BitOperator();
        mBCD8421Operater = new BCD8421Operater();
        mJT808ProtocolUtils = new JT808ProtocolUtils();
        mMsgDecoder = new MsgDecoder();
        mMsgEncoder = new MsgEncoder();

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        //filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        //filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        filter.addAction("android.hardware.usb.action.USB_STATE");
        //filter.addDataScheme("file");
        registerReceiver(mUsbReceiver, filter);
        registerPC2DroidReceiver();

        //IntentFilter networkFilter = new IntentFilter();
        //networkFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        //registerReceiver(mNetworkChangeBroadcastReceiver, networkFilter);

        if (new File(KEY_INT01_PATH).exists()) {
            //mMiddleSwitch01Observer.startObserving(KEY_INT01_STATE_MATCH);
        }
        if (new File(KEY_INT02_PATH).exists()) {
            //mMiddleSwitch02Observer.startObserving(KEY_INT02_STATE_MATCH);
        }
        if (new File(KEY_INT03_PATH).exists()) {
            //mMiddleSwitch03Observer.startObserving(KEY_INT03_STATE_MATCH);
        }
        if (new File(KEY_INT04_PATH).exists()) {
            //mMiddleSwitch04Observer.startObserving(KEY_INT04_STATE_MATCH);
        }
        if (new File(KEY_INT05_PATH).exists()) {
            //mMiddleSwitch05Observer.startObserving(KEY_INT05_STATE_MATCH);
        }
        if (new File(KEY_INT06_PATH).exists()) {
            //mMiddleSwitch06Observer.startObserving(KEY_INT06_STATE_MATCH);
        }
        if (new File(KEY_INT07_PATH).exists()) {
            //mMiddleSwitch07Observer.startObserving(KEY_INT07_STATE_MATCH);
        }
        if (new File(KEY_INT08_PATH).exists()) {
            //mMiddleSwitch08Observer.startObserving(KEY_INT08_STATE_MATCH);
        }
        if (new File(KEY_INT09_PATH).exists()) {
            //mMiddleSwitch09Observer.startObserving(KEY_INT09_STATE_MATCH);
        }
        if (new File(KEY_INT10_PATH).exists()) {
            //mMiddleSwitch10Observer.startObserving(KEY_INT10_STATE_MATCH);
        }

        if (new File(KEY0_INT_01_PATH).exists()) {
            mBottomSwitch01Observer.startObserving(KEY0_INT_01_STATE_MATCH);
        }
        if (new File(KEY0_INT_02_PATH).exists()) {
            mBottomSwitch02Observer.startObserving(KEY0_INT_02_STATE_MATCH);
        }
        if (new File(KEY0_INT_03_PATH).exists()) {
            mBottomSwitch03Observer.startObserving(KEY0_INT_03_STATE_MATCH);
        }
        if (new File(KEY0_INT_04_PATH).exists()) {
            mBottomSwitch04Observer.startObserving(KEY0_INT_04_STATE_MATCH);
        }
        if (new File(KEY0_INT_05_PATH).exists()) {
            mBottomSwitch05Observer.startObserving(KEY0_INT_05_STATE_MATCH);
        }
        if (new File(KEY0_INT_06_PATH).exists()) {
            mBottomSwitch06Observer.startObserving(KEY0_INT_06_STATE_MATCH);
        }
        if (new File(KEY0_INT_07_PATH).exists()) {
            mBottomSwitch07Observer.startObserving(KEY0_INT_07_STATE_MATCH);
        }
        if (new File(KEY0_INT_08_PATH).exists()) {
            mBottomSwitch08Observer.startObserving(KEY0_INT_08_STATE_MATCH);
        }
        if (new File(KEY0_INT_09_PATH).exists()) {
            mBottomSwitch09Observer.startObserving(KEY0_INT_09_STATE_MATCH);
        }
        if (new File(KEY0_INT_10_PATH).exists()) {
            mBottomSwitch10Observer.startObserving(KEY0_INT_10_STATE_MATCH);
        }
        mDroidToKey01Handler = new DroidToKey01Handler();
        mDroidToKey02Handler = new DroidToKey02Handler();
        mDroidToKey03Handler = new DroidToKey03Handler();
        mDroidToKey04Handler = new DroidToKey04Handler();
        mDroidToKey05Handler = new DroidToKey05Handler();
        mDroidToKey06Handler = new DroidToKey06Handler();
        mDroidToKey07Handler = new DroidToKey07Handler();
        mDroidToKey08Handler = new DroidToKey08Handler();
        mDroidToKey09Handler = new DroidToKey09Handler();
        mDroidToKey10Handler = new DroidToKey10Handler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "TcpService.onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "TcpService.onDestroy");
        if (mUsbReceiver != null) {
            unregisterReceiver(mUsbReceiver);
        }
        unregisterPC2DroidReceiver();
        /*if (mNetworkChangeBroadcastReceiver != null) {
            //unregisterReceiver(mNetworkChangeBroadcastReceiver);
        }*/
        /*mMiddleSwitch01Observer.stopObserving();
        mMiddleSwitch02Observer.stopObserving();
        mMiddleSwitch03Observer.stopObserving();
        mMiddleSwitch04Observer.stopObserving();
        mMiddleSwitch05Observer.stopObserving();
        mMiddleSwitch06Observer.stopObserving();
        mMiddleSwitch07Observer.stopObserving();
        mMiddleSwitch08Observer.stopObserving();
        mMiddleSwitch09Observer.stopObserving();
        mMiddleSwitch10Observer.stopObserving();*/

        mBottomSwitch01Observer.stopObserving();
        mBottomSwitch02Observer.stopObserving();
        mBottomSwitch03Observer.stopObserving();
        mBottomSwitch04Observer.stopObserving();
        mBottomSwitch05Observer.stopObserving();
        mBottomSwitch06Observer.stopObserving();
        mBottomSwitch07Observer.stopObserving();
        mBottomSwitch08Observer.stopObserving();
        mBottomSwitch09Observer.stopObserving();
        mBottomSwitch10Observer.stopObserving();
        //mBottomSwitch04Observer.stopObserving();
        super.onDestroy();
    }

    public void sendMessage(String msg) {
        if (msg != null) {
            //TcpClient.init().send(msg.getBytes());
            TcpClient.init().send(hexString2Intger(msg));
        }
    }

    public void sendMessage(byte[] msg) {
        if (msg != null) {
            TcpClient.init().send(msg);
        }
    }

    public void connect(String ip, int port) {
        if (ip != null && port > 0) {
            TcpClient.init().connect(ip, port);
        }
    }

    public void disconnect() {
        TcpClient.init().disconnect();
    }

    /**
     * 设定心跳间隔
     */
    public void setAlarm() {
        long triggerAtTime = SystemClock.elapsedRealtime() + Long.valueOf(mLocationFrequency) * 1000;
        Intent intent = new Intent("com.erobbing.fishery.alarm");
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent, 0);
        //mAlarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtTime, five, pi);//api 19以后不准确
        mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtTime, pi);
    }

    /**
     * 取消心跳间隔
     */
    public void cancelAlarm() {
        Intent intent = new Intent("com.erobbing.fishery.alarm");
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent, 0);
        mAlarmManager.cancel(pi);
    }

    public void processReceiveData(String data) {
        if (data != null && data != "") {
            String msg = data = data.toLowerCase().replace(MSG_FLAG, "");
            Log.e("====", "==========data.toLowerCase()=" + data.toLowerCase());
            //处理转义
            //发送内容为0x30 0x7e 0x08 0x7d 0x55，封装如下：0x7e 0x30 0x7d 0x02 0x08 0x7d 0x01 0x55 0x7e
            //7d02   7d01
            msg = msg.replace("7d02", "7e").replace("7d01", "7d");
            Log.e("====", "==========trans 7d0201***=" + msg);
            headerAndBodyString = msg.substring(0, msg.length() - 2);
            Log.e("====", "=======receive-headerAndBodyString=" + headerAndBodyString);

            String msgHead = msg.substring(0, 24);//000200000000099787870005 //length=24
            mHeaderString = msgHead;
            String msgHeadId = msg.substring(0, 4);//byte[0-1]
            mHeadMsgID = msgHeadId;
            String msgHeadBodyAttr = msg.substring(4, 8);//byte[2-3]
            mHeadAttributes = msgHeadBodyAttr;
            String msgHeadPhoneNum = msg.substring(8, 20);//byte[4-9]   终端手机号或设备ID bcd[6]
            mHeadPhoneNum = msgHeadPhoneNum;
            String msgHeadseq = msg.substring(20, 24);//byte[10-11]
            mHeadMsgSeq = msgHeadseq;
            mHeadMsgSeqInt = mBitOperator.twoBytesToInteger(HexStringUtils.hexStringToBytes(mHeadMsgSeq));
            mHeadMsgSeqInt += 1;


            //byte[12-15] 消息包封装项 //一般为空
            //消息体
            if (msg.length() - 2 > 24) {
                String msgBody = msg.substring(24, msg.length() - 2);
                Log.e("====", "========msgBody=" + msgBody);
                mBodyString = msgBody;
                if (mBodyString.length() == 10) {//终端普通应答
                    mBodyRespType = mBodyString.substring(4, 8);
                }
            }

            String msgCheckSum = msg.substring(msg.length() - 2, msg.length());
            Log.e("====", "=========msgHead=" + msgHead + "#msgHeadId=" + msgHeadId + "#msgHeadBodyAttr=" + msgHeadBodyAttr
                    + "#msgHeadPhoneNum=" + msgHeadPhoneNum + "#msgHeadseq=" + msgHeadseq + "****msgCheckSum=" + msgCheckSum);


            switch (msgHeadId) {
                //终端普通应答
                case MSG_TYPE_CLIENT_RESPONSE:
                    Log.e("====", "============MSG_TYPE_CLIENT_RESPONSE");
                    //消息体
                    //byte[0-1]  应答流水号,对应的平台消息的流水号  WORD
                    //byte[2-3]  应答 ID,对应的平台消息的? D  WORD
                    //byte[4]  结果,0：成功/确认；1：失败；2：消息有误；3：不支持  BYTE

                    break;
                //心跳
                case MSG_TYPE_HEARTBEAT:
                    Log.e("====", "============MSG_TYPE_HEARTBEAT");
                    break;
                //注销
                case MSG_TYPE_UNREGISTER:
                    Log.e("====", "============MSG_TYPE_UNREGISTER");
                    //byte[0] 注销原因,0：换店 1：损坏	BYTE
                    break;
                //注册
                case MSG_TYPE_REGISTER:
                    Log.e("====", "============MSG_TYPE_REGISTER");
                    //get auth code
                    //send auth code
                    break;
                //终端鉴权
                case MSG_TYPE_AUTHENTICATION:
                    Log.e("====", "============MSG_TYPE_AUTHENTICATION");

                    //PackageData ppp = new PackageData();
                    //ppp = mMsgDecoder.bytes2PackageData(HexStringUtils.hexStringToBytes(data));
                    //byte[0] 鉴权码,终端重连后上报鉴权码	STRING
                    mPackageData = new PackageData();
                    mPackageDataMsgHeader = new PackageData.MsgHeader();
                    //packageDataAuth.setBodyString("313639333434");
                    int bodyLength = 6;
                    mPackageDataMsgHeader.setMsgId(0x0102);


                    int pro = new JT808ProtocolUtils().generateMsgBodyProps(6, 0b000, false, 0);
                    //setHeaderData(msgBody.length() / 2, );
                    mPackageDataMsgHeader.setMsgBodyLength(6);//00 0000 0110
                    //msgHeaderAuth.setEncryptionType("000");
                    mPackageDataMsgHeader.setEncryptionType((pro & 0x1c00) >> 10);
                    mPackageDataMsgHeader.setHasSubPackage(false);//0=false
                    mPackageDataMsgHeader.setReservedBit(0);


                    //String byteString = "0000000110";
                    //msgHeaderAuth.setMsgBodyPropsField();
                    //int pro = new JT808ProtocolUtils().generateMsgBodyProps(6, 0b000, false, 0);


                    String phone = "020000000015";
                    mPackageDataMsgHeader.setTerminalPhone(phone);
                    int flow = 0x0026;
                    mPackageDataMsgHeader.setFlowId(0x0026);

                    mPackageDataMsgHeader.setTotalSubPackage(0);
                    mPackageDataMsgHeader.setSubPackageSeq(0);


                    String bodyString = "313639333434";
                    mPackageData.setMsgBodyBytes(HexStringUtils.hexStringToBytes(bodyString));
                    byte[] head = null;
                    try {
                        head = new JT808ProtocolUtils().generateMsgHeader(phone, TPMSConsts.msg_id_terminal_authentication, pro, flow);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Log.e("====", "=====pro=" + pro);
                    Log.e("====", "=====head=" + HexStringUtils.toHexString(head));
                    Log.e("====", "=====flow=" + flow);


                    /*byte[] msgBody = mBitOperator.concatAll(Arrays.asList(//
                            mBitOperator.integerTo2Bytes(flow), // 应答流水号
                            mBitOperator.integerTo2Bytes(respMsgBody.getReplyId()), // 应答ID,对应的终端消息的ID
                            new byte[]{respMsgBody.getReplyCode()}// 结果
                    ));

                    // 消息头
                    int msgBodyProps = mJT808ProtocolUtils.generateMsgBodyProps(msgBody.length, 0b000, false, 0);
                    byte[] msgHeader = mJT808ProtocolUtils.generateMsgHeader(phone,
                            TPMSConsts.cmd_common_resp, msgBody, msgBodyProps, flow);
                    byte[] headerAndBody = mBitOperator.concatAll(msgHeader, msgBody);
                    // 校验码
                    int checkSum = mBitOperator.getCheckSum4JT808(headerAndBody, 0, headerAndBody.length - 1);*/

                    //sendMessage(HexStringUtils.toHexString(getAllBytes()));
                    //鉴权完毕主动上报钥匙孔状态
                    if (true) {//reg success //body[0-1]byte flowId,body[2] boolean success=00
                        //成功之后发送24个钥匙孔信息
                        //上报钥匙孔状态  钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间(YY-MM-DD-hh-mm-ss 190123164147)
                        //allKeysStatus();
                    }
                    break;
                //借钥匙
                case MSG_TYPE_BORROW://暂时未定义
                    Log.e("====", "============MSG_TYPE_BORROW");
                    break;
                //还钥匙
                case MSG_TYPE_RETURN://暂时未定义
                    Log.e("====", "============MSG_TYPE_RETURN");
                    break;
                //平台普通应答
                case MSG_TYPE_SERVER_RESPONSE:
                    //消息体
                    //byte[0-1]  应答流水号,对应的终端消息的流水号  WORD
                    //byte[2-3]  应答 ID,对应的终端消息的 ID  WORD
                    //byte[4]  结果,0：成功/确认；1：失败；2：消息有误；3：不支持；4：报警处理确认  BYTE
                    //鉴权应答7E 8001 0005 019999999995 0001 0019010205 0E7E
                    Log.e("====", "==============MSG_TYPE_SERVER_RESPONSE");
                    if (mHeadMsgSeq.equals("0019")) {
                        //if (needKeyStatusReport) {
                        //allKeysStatus();
                        //箱子鉴权成功
                        Log.e("====", "==============box auth succeed");
                        needKeyStatusReport = false;
                    }
                    //如果是钥匙扣的鉴权，需要发送鉴权码给钥匙扣
                    if (!tmpPhoneId.equals(tmpKeyId)) {
                        //mDroidToKey01Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_01_SEND_AUTH_CODE, 100);
                    }
                    Log.e("====", "==========mBodyRespType=" + mBodyRespType);
                    if ("0102".equals(mBodyRespType)) {
                        //鉴权成功
                        //上报
                        allKeysStatus();
                        mBodyRespType = "";
                    }
                    break;
                //补传分包请求
                case MSG_TYPE_SUB_PAC_REQ:
                    break;
                //终端注册应答
                case MSG_TYPE_REG_RESPONSE:

                    //0：成功；1：终端已被注册；2：数据库中无绑定的钥匙箱ID；
                    //3：绑定的钥匙箱ID 没有注册；4：数据库中无绑定的店面ID
                    //5：钥匙扣绑定的店面ID 和钥匙箱绑定的店面ID 不一致
                    String result = mBodyString.substring(4, 6);
                    Log.e("====", "========MSG_TYPE_REG_RESPONSE--result=" + result);
                    String savedAuthCode = getSavedAuthCode();
                    boolean hasAuthCode = ("".equals(savedAuthCode) || "unknown".equals(savedAuthCode)) ? false : true;
                    switch (result) {
                        case "00":
                            String authCode = mBodyString.substring(6, 26);
                            if (hasAuthCode) {
                                //扣鉴权
                                //发送鉴权码给扣
                            } else {
                                //箱鉴权
                                saveAuthCode(authCode);
                                sendMessage(getAllBytes(0x0102, mHeadMsgSeqInt, mHeadPhoneNum, authCode));
                            }

                            Log.e("====", "=======MSG_TYPE_REG_RESPONSE--01=authCode=" + authCode);

                            //int flowId = 0x0001;
                            /*String bodyAuthCodeString = authCode;
                            byte[] authBody = hexString2Intger(bodyAuthCodeString);
                            int msgBodyProps = mJT808ProtocolUtils.generateMsgBodyProps(bodyAuthCodeString.length() / 2, 0b000, false, 0);
                            byte[] msgHeader = mJT808ProtocolUtils.generateMsgHeader(mBodyTerminalID,
                                    TPMSConsts.msg_id_terminal_register, authBody, msgBodyProps, mHeadMsgSeqInt);
                            byte[] headerAndBody = mBitOperator.concatAll(msgHeader, authBody);
                            String headerString = HexStringUtils.toHexString(msgHeader);
                            String headerAndBodyString = HexStringUtils.toHexString(headerAndBody);
                            Log.e("====", "=======headerString=" + headerString + "=headerAndBodyString=" + headerAndBodyString);
                            //int checkint = mBitOperator.getCheckSum4JT808(headerAndBody, 0, headerAndBody.length);
                            //sendMessage(mMsgEncoder.doEncode(headerAndBody, checkint));
                            int checkint = mBitOperator.getCheckSum4JT808(
                                    mBCD8421Operater.string2Bcd(headerAndBodyString), 0, (headerAndBodyString.length() / 2));
                            sendMessage(mMsgEncoder.doEncode(hexString2Intger(headerAndBodyString), checkint));*/
                            break;
                        case "01":
                            //String savedAuthCode = getSavedAuthCode();
                            break;
                        case "02":
                            break;
                        case "03":
                            Log.e("====", "========MSG_TYPE_REG_RESPONSE--03=");
                            break;
                        case "04":
                            break;
                        case "05":
                            break;
                    }

                    //get auth code
                    //save auth code
                    //String fflow = mBodyString.substring(0, 4);
                    //String success = mBodyString.substring(4, 6);
                    String auth = mBodyString.substring(6, mBodyString.length());
                    needKeyStatusReport = true;
                    //send auth code
                    //"7e0102000D0199999999980001 724b4370794e524f364d 337e"
                    //int check = mBitOperator.getCheckSum4JT808(mBCD8421Operater.string2Bcd("0102000D0199999999980001724b4370794e524f364d"), 0, ("0102000D0199999999980001724b4370794e524f364d".length()) / 2);
                    //Log.e("====", "=====MSG_TYPE_REG_RESPONSE=check=" + String.format("%02x", Math.abs(check)));
                    //Log.e("====", "=====MSG_TYPE_REG_RESPONSE=check=" + Integer.toHexString(check));
                    /*if (auth.length() < 10) {
                        getSavedAuthCode();
                        //send auth code
                        String ttString = "0102000A" + tmpPhoneId + "0019" + getSavedAuthCode();//mHeadMsgSeq
                        int checkint = mBitOperator.getCheckSum4JT808(
                                mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                        try {
                            sendMessage(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        saveAuthCode(auth);
                        //resend auth code
                        //String check = hexStringChecksum(headerAndBodyString);
                        String check = hexStringChecksum("0102000A0199999999930019" + auth);
                        byte[] cccheck = hexChecksum("0102000A0199999999930019" + auth);
                        //sendMessage(MSG_FLAG + "0102000A0199999999940019" + auth + check + MSG_FLAG);
                        byte[] merge = byteMerger3(hexString2Intger((MSG_FLAG + "0102000A0199999999930019" + auth)), cccheck, hexString2Intger(MSG_FLAG));
                        //sendMessage(merge);
                        String ttString = "0102000A" + tmpPhoneId + "0019" + auth;
                        int checkint = mBitOperator.getCheckSum4JT808(
                                mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                        try {
                            sendMessage(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }*/
                    break;
                //终端控制
                case MSG_TYPE_SERVER_CONTROL:
                    //byte[1] 命令参数格式具体见后面描述，每个字段之间采用半角”;”分隔，每个 STRING 字段先按 GBK 编码处理后再组成消息
                    //借钥匙流程 0x10 ,孔编号，扣id
                    Log.e("====", "============MSG_TYPE_SERVER_CONTROL(borrow)");
                    mDroidToKey04Handler.postDelayed(playSoundNotiRemove, 50);
                    //0x10 钥匙箱弹出钥匙扣
                    String kkId = mBodyString.substring(2, 4);
                    switch (mBodyString.substring(2, 4)) {
                        case "01":
                            mDroidToKey01Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_01_MOTOR_ON, 100);
                            break;
                        case "02":
                            mDroidToKey02Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_02_MOTOR_ON, 100);
                            break;
                        case "03":
                            mDroidToKey03Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_03_MOTOR_ON, 100);
                            break;
                        case "04":
                            mDroidToKey04Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_MOTOR_ON, 100);
                            break;
                        case "05":
                            mDroidToKey05Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_05_MOTOR_ON, 100);
                            break;
                        case "06":
                            mDroidToKey06Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_06_MOTOR_ON, 100);
                            break;
                        case "07":
                            mDroidToKey07Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_07_MOTOR_ON, 100);
                            break;
                        case "08":
                            mDroidToKey08Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_08_MOTOR_ON, 100);
                            break;
                        case "09":
                            mDroidToKey09Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_09_MOTOR_ON, 100);
                            break;
                        case "10":
                            mDroidToKey10Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_10_MOTOR_ON, 100);
                            break;
                    }
                    //motor on
                    //mDroidToKey04Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_MOTOR_ON, 100);
                    //playFromRawFile(mContext, R.raw.notify_key_remove);
                    //mDroidToKey04Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_MOTOR_ON, 100);
                    //声音提示
                    //motor off
                    break;
                case MSG_TYPE_SERVER_CONTROL_RESPONSE:
                    //flow，应答id=8105，命令号0x10 弹出钥匙扣，结果00
                    break;
                //终端主动上报
                case MSG_TYPE_CLIENT_REQ:
                    //代码暂时放此处
                    //byte[0] BYTE 上报类型
                    //byte[1] STRING 上报参数
                    //注册完毕之后主动上报钥匙孔状态  钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间(YY-MM-DD-hh-mm-ss 190123164147)

                    break;
                //设置终端参数
                case MSG_TYPE_CLIENT_SETTINGS:
                    break;
            }
        }
    }

    public byte[] generateMsgHeader(String phone, int msgId, int bodyPros, int flowId) {
        byte[] head = null;
        //msgId = TPMSConsts.msg_id_terminal_authentication;
        try {
            head = new JT808ProtocolUtils().generateMsgHeader(phone, msgId, bodyPros, flowId);
        } catch (Exception e) {
            e.printStackTrace();

        }
        return head;
    }

    public int generateMsgBodyProps(int msgLen, int enctyptionType, boolean isSubPackage, int reversed_14_15) {
        int pro = new JT808ProtocolUtils().generateMsgBodyProps(msgLen, enctyptionType, isSubPackage, reversed_14_15);
        return pro;
    }

    public void setHeaderData(int msgbodylength, int encryptiontype, boolean issubpackage, String terminalphone,
                              int flowid, int totalsubpackage, int seq)
    //消息体长度，加密方式，分包，电话，流水号，包总数，包序号
    {
        mPackageDataMsgHeader = new PackageData.MsgHeader();
        mPackageDataMsgHeader.setMsgId(TPMSConsts.msg_id_terminal_heart_beat);
        mPackageDataMsgHeader.setMsgBodyLength(msgbodylength);
        mPackageDataMsgHeader.setEncryptionType(encryptiontype);
        mPackageDataMsgHeader.setHasSubPackage(issubpackage);
        mPackageDataMsgHeader.setReservedBit(0);
        mPackageDataMsgHeader.setTerminalPhone(terminalphone);
        mPackageDataMsgHeader.setFlowId(flowid);
        if (issubpackage) {
            mPackageDataMsgHeader.setTotalSubPackage(totalsubpackage);
            mPackageDataMsgHeader.setSubPackageSeq(seq);
        }
    }

    /*public byte[] getAllBytes() {//所有数据报字节
        BitOperator bitOperator = new BitOperator();
        int bodyPro = generateMsgBodyProps(mPackageDataMsgHeader.getMsgBodyLength(), mPackageDataMsgHeader.getEncryptionType(), mPackageDataMsgHeader.isHasSubPackage(), mPackageDataMsgHeader.getReservedBit());
        byte[] headbytes = generateMsgHeader(mPackageDataMsgHeader.getTerminalPhone(), mPackageDataMsgHeader.getMsgId(), bodyPro, mPackageDataMsgHeader.getFlowId());//mPackageDataMsgHeader.getHeaderbytes();
        byte[] bodybytes = mPackageData.getMsgBodyBytes();

        byte[] check = new byte[]{(byte) (bitOperator.getCheckSum4JT808(headbytes, 0, headbytes.length))};
        byte[] flag = new byte[]{TPMSConsts.pkg_delimiter};
        byte[] sendbytes = bitOperator.concatAll(flag, headbytes, bodybytes, check, flag);
        return sendbytes;
    }*/

    public byte[] getAllBytes(int msgId, int flowId, String terminalId, String bodyString) {
        byte[] sendbytes = null;
        try {
            byte[] bodybytes = hexString2Intger(bodyString);
            int msgBodyProps = mJT808ProtocolUtils.generateMsgBodyProps(bodyString.length() / 2, 0b000, false, 0);
            byte[] headbytes = mJT808ProtocolUtils.generateMsgHeader(terminalId,
                    msgId, bodybytes, msgBodyProps, flowId);
            byte[] headerAndBody = mBitOperator.concatAll(headbytes, bodybytes);
            String headerString = HexStringUtils.toHexString(headbytes);
            String headerAndBodyString = HexStringUtils.toHexString(headerAndBody);
            Log.e("====", "=======headerString=" + headerString + "=headerAndBodyString=" + headerAndBodyString);
            int checksum = mBitOperator.getCheckSum4JT808(headerAndBody, 0, headerAndBody.length);
            byte[] check = new byte[]{(byte) checksum};
            //sendMessage(mMsgEncoder.doEncode(headerAndBody, checkint));
            //int checksum = mBitOperator.getCheckSum4JT808(
            //       mBCD8421Operater.string2Bcd(headerAndBodyString), 0, (headerAndBodyString.length() / 2));
            byte[] flag = new byte[]{TPMSConsts.pkg_delimiter};
            sendbytes = mBitOperator.concatAll(flag, headbytes, bodybytes, check, flag);
            //return sendbytes;
            //sendMessage(mMsgEncoder.doEncode(hexString2Intger(headerAndBodyString), checksum));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sendbytes;
    }

    /*private BroadcastReceiver mNetworkChangeBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if ("android.net.conn.CONNECTIVITY_CHANGE".equals(action)) {
                isNetworkAvailable = isNetworkAvailable(mContext);
                Log.d(TAG, "CONNECTIVITY_CHANGE--isNetworkAvailable=" + isNetworkAvailable);
                if (isNetworkAvailable) {
                    connect(IP, PORT);
                }
            }
        }
    };*/

    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!"android.hardware.usb.action.USB_STATE".equals(action)) {
                return;
            }
            boolean connected = intent.getBooleanExtra("connected", false);
            Toast.makeText(context, "connected=" + connected, Toast.LENGTH_SHORT).show();
            //IntentFilter filter = new IntentFilter();
            //filter.addAction("com.erobbing.action.PC_TO_DROID");

            if (connected) {
                //start socket
                //context.startService(new Intent(context, TcpServer.class));
                //start here get provinceId  cityId  manufacturerId  terminalType?  shopId  terminalId
                //generate TerminalRegInfo
                //sendd();
                //Intent intent = new Intent(this, com.erobbing.adb_config_demo.sdk.service.SdkService.class);
                startService(new Intent(context, com.erobbing.adb_config_demo.sdk.service.SdkService.class));
                //registerReceiver(mPC2DroidReceiver, filter);
                //sendMessage("7E010000180199999999980018010006338888888877777777777777777777019999999998357E");
            } else {
                //stop socket
                //context.stopService(new Intent(context, TcpServer.class));
                //if (mPC2DroidReceiver != null) {
                //    unregisterReceiver(mPC2DroidReceiver);
                //}
                stopService(new Intent(context, com.erobbing.adb_config_demo.sdk.service.SdkService.class));
            }
        }
    };

    private void registerPC2DroidReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.erobbing.action.PC_TO_DROID");
        registerReceiver(mPC2DroidReceiver, filter);
    }

    private void unregisterPC2DroidReceiver() {
        if (mPC2DroidReceiver != null) {
            unregisterReceiver(mPC2DroidReceiver);
        }
    }

    private BroadcastReceiver mPC2DroidReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.erobbing.action.PC_TO_DROID".equals(action)) {
                String provinceID = intent.getStringExtra("province_id");
                int provinceId = (provinceID != null && (!"".equals(provinceID))) ? Integer.parseInt(provinceID) : 0;
                mBodyProvinceID = HexStringUtils.intToHexStringProvinceAndCity(provinceId);
                String cityID = intent.getStringExtra("city_id");
                int cityId = (cityID != null && (!"".equals(cityID))) ? Integer.parseInt(cityID) : 0;
                mBodyCityID = HexStringUtils.intToHexStringProvinceAndCity(cityId);
                String manufacturerId = intent.getStringExtra("manufacturer_id");
                mBodyManufacturerID = manufacturerId;
                String shopId = intent.getStringExtra("shop_id");
                mBodyShopID = shopId;
                String terminalId = intent.getStringExtra("box_id");
                mBodyTerminalID = terminalId;

                Log.d(TAG, "mPC2DroidReceiver.provinceId=" + provinceId
                        + " - cityId=" + cityId
                        + " - manufacturerId=" + manufacturerId
                        + " - shopId=" + shopId
                        + " - terminalId=" + terminalId);
                Log.e("====", "=======provinceID=" + HexStringUtils.intToHexStringProvinceAndCity(provinceId));
                Log.e("====", "=======cityID=" + HexStringUtils.intToHexStringProvinceAndCity(cityId));
                //sendMessage("7E010000180199999999980018010006338888888877777777777777777777019999999998357E");
                //will get 7E8100000D0199999999980001001800724B4370794E524F364D1A7E
                //int check = mBitOperator.getCheckSum4JT808(mBCD8421Operater.string2Bcd("010200060200000000150026313639333434"), 0, ("010200060200000000150026313639333434".length()) / 2);//ok
                //Log.e("====", "=========check=" + check);
                //allKeysStatus();
                //7E 010000180199999999980018 01000633 88888888 77777777777777777777 019999999798 35 7E
                String ccheck = hexStringChecksum("010000180199999999930018010006338888888877777777777777777777019999999993");
                byte[] cccheck = hexChecksum("010000180199999999930018010006338888888877777777777777777777019999999993");
                //sendMessage(MSG_FLAG + "010000180199999999940018010006338888888877777777777777777777019999999994" + ccheck + MSG_FLAG);
                byte[] merge = byteMerger3(hexString2Intger((MSG_FLAG + "010000180199999999930018010006338888888877777777777777777777019999999993")), cccheck, hexString2Intger(MSG_FLAG));

                /*try {
                    //注册
                    //String regString = "0102000A"+mBodyTerminalID+"0001"+mBodyProvinceID+mBodyCityID+
                    int flowId = 0x0001;
                    String bodyString = mBodyProvinceID + mBodyCityID + mBodyManufacturerID + mBodyShopID + mBodyTerminalID;
                    byte[] msgBody = hexString2Intger(bodyString);
                    int msgBodyProps = mJT808ProtocolUtils.generateMsgBodyProps(bodyString.length() / 2, 0b000, false, 0);
                    byte[] msgHeader = mJT808ProtocolUtils.generateMsgHeader(mBodyTerminalID,
                            TPMSConsts.msg_id_terminal_register, msgBody, msgBodyProps, flowId);
                    byte[] headerAndBody = mBitOperator.concatAll(msgHeader, msgBody);
                    String headerString = HexStringUtils.toHexString(msgHeader);
                    String headerAndBodyString = HexStringUtils.toHexString(headerAndBody);
                    Log.e("====", "=======headerString=" + headerString + "=headerAndBodyString=" + headerAndBodyString);
                    //int checkint = mBitOperator.getCheckSum4JT808(headerAndBody, 0, headerAndBody.length);
                    //sendMessage(mMsgEncoder.doEncode(headerAndBody, checkint));
                    int checkint = mBitOperator.getCheckSum4JT808(
                            mBCD8421Operater.string2Bcd(headerAndBodyString), 0, (headerAndBodyString.length() / 2));
                    sendMessage(mMsgEncoder.doEncode(hexString2Intger(headerAndBodyString), checkint));

                    //sendMessage(merge);
                    //String ttString = "010000180199999999900018010006338888888877777777777777777777019999999990";
                    //String ttString = "01000018" + tmpPhoneId + "0018053105320000000322222222222222222222" + tmpPhoneId;
                    //演示直接鉴权
                    String ttString = "0102000A" + tmpPhoneId + "0001" + "34517433696841357147";
                    //int checkint = mBitOperator.getCheckSum4JT808(
                    //       mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));

                    //sendMessage(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                } catch (Exception e) {
                    e.printStackTrace();
                }*/
                //sendMessage(getAllBytes(0x0100, 0x0001, mBodyTerminalID, mBodyProvinceID + mBodyCityID + mBodyManufacturerID + mBodyShopID + mBodyTerminalID));
                //01 99 99 99 99 98
                //7E 0100 0018 019999999998 0018 0100 0633 88888888 77777777777777777777 019999999998 35 7E
                sendMessage(getAllBytes(0x0100, 0x0018, tmpPhoneId, "0100" + "0633" + "88888888" + "77777777777777777777" + tmpPhoneId));
            }
        }
    };

    public static byte[] byteMerger2(byte[] bt1, byte[] bt2) {
        byte[] bt3 = new byte[bt1.length + bt2.length];
        System.arraycopy(bt1, 0, bt3, 0, bt1.length);
        System.arraycopy(bt2, 0, bt3, bt1.length, bt2.length);
        return bt3;
    }

    public static byte[] byteMerger3(byte[] bt1, byte[] bt2, byte[] bt3) {
        byte[] bt4 = new byte[bt1.length + bt2.length + bt3.length];
        System.arraycopy(bt1, 0, bt4, 0, bt1.length);
        System.arraycopy(bt2, 0, bt4, bt1.length, bt2.length);
        System.arraycopy(bt1, 0, bt4, bt2.length, bt3.length);
        return bt4;
    }

    byte[] hexString2Intger(String str) {
        byte[] byteTarget = new byte[str.length() / 2];
        for (int i = 0; i < str.length() / 2; ++i)
            byteTarget[i] = (byte) (Integer.parseInt(str.substring(i * 2, i * 2 + 2), 16) & 0xff);
        return byteTarget;
    }

    public static String hexStr2Str(String hexStr) {
        String str = "0123456789ABCDEF";
        char[] hexs = hexStr.toCharArray();
        byte[] bytes = new byte[hexStr.length() / 2];
        int n;

        for (int i = 0; i < bytes.length; i++) {
            n = str.indexOf(hexs[2 * i]) * 16;
            n += str.indexOf(hexs[2 * i + 1]);
            bytes[i] = (byte) (n & 0xff);
        }
        return new String(bytes);
    }

    private void sendd() {
        //7E010000180199999999980018010006338888888877777777777777777777019999999998357E
        //rep //7E 8100000D0199999999980001 001800724B4370794E524F364D 1A 7E
        //7E 010000180199999999980018 010006338888888877777777777777777777019999999998 35 7E
        //PackageData ppp = new PackageData();
        //ppp = mMsgDecoder.bytes2PackageData(HexStringUtils.hexStringToBytes(data));
        //byte[0] 鉴权码,终端重连后上报鉴权码	STRING
        mPackageData = new PackageData();
        mPackageDataMsgHeader = new PackageData.MsgHeader();
        //packageDataAuth.setBodyString("313639333434");
        int bodyLength = 24;//010006338888888877777777777777777777019999999998
        mPackageDataMsgHeader.setMsgId(0x0100);//0100

        //0018   ->   0000 0000 0001 1000
        int pro = new JT808ProtocolUtils().generateMsgBodyProps(6, 0b000, false, 0);//0018
        //setHeaderData(msgBody.length() / 2, );
        mPackageDataMsgHeader.setMsgBodyLength(6);//00 0000 0110
        //msgHeaderAuth.setEncryptionType("000");
        mPackageDataMsgHeader.setEncryptionType((pro & 0x1c00) >> 10);
        mPackageDataMsgHeader.setHasSubPackage(false);//0=false
        mPackageDataMsgHeader.setReservedBit(0);


        //String byteString = "0000000110";
        //msgHeaderAuth.setMsgBodyPropsField();
        //int pro = new JT808ProtocolUtils().generateMsgBodyProps(6, 0b000, false, 0);


        String phone = "019999999998";
        mPackageDataMsgHeader.setTerminalPhone(phone);
        int flow = 0x0018;
        mPackageDataMsgHeader.setFlowId(0x0018);

        mPackageDataMsgHeader.setTotalSubPackage(0);
        mPackageDataMsgHeader.setSubPackageSeq(0);


        String bodyString = "010006338888888877777777777777777777019999999998";
        mPackageData.setMsgBodyBytes(HexStringUtils.hexStringToBytes(bodyString));
        byte[] head = null;
        try {
            head = new JT808ProtocolUtils().generateMsgHeader(phone, TPMSConsts.msg_id_terminal_authentication, pro, flow);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.e("====", "=====pro=" + pro);
        Log.e("====", "=====head=" + HexStringUtils.toHexString(head));
        Log.e("====", "=====flow=" + flow);


                    /*byte[] msgBody = mBitOperator.concatAll(Arrays.asList(//
                            mBitOperator.integerTo2Bytes(flow), // 应答流水号
                            mBitOperator.integerTo2Bytes(respMsgBody.getReplyId()), // 应答ID,对应的终端消息的ID
                            new byte[]{respMsgBody.getReplyCode()}// 结果
                    ));

                    // 消息头
                    int msgBodyProps = mJT808ProtocolUtils.generateMsgBodyProps(msgBody.length, 0b000, false, 0);
                    byte[] msgHeader = mJT808ProtocolUtils.generateMsgHeader(phone,
                            TPMSConsts.cmd_common_resp, msgBody, msgBodyProps, flow);
                    byte[] headerAndBody = mBitOperator.concatAll(msgHeader, msgBody);
                    // 校验码
                    int checkSum = mBitOperator.getCheckSum4JT808(headerAndBody, 0, headerAndBody.length - 1);*/


        //sendMessage(HexStringUtils.toHexString(getAllBytes()));
        //sendMessage("123");
    }

    public void allKeysStatus() {
        //time
        //0x0817
        String msgId = "0817";
        String headerO = "0008" + tmpPhoneId;
        //String flowId = "0019";
        int flowId = 20;
        //String bodyString = "0a00010013011a023912";
        //String bodyString = "0a000100" + getTimeHexString();
        String bodyString = "0c" + "00" + "999999999999";
        for (int i = 0; i < 24; i++) {
            /*
            //handler.send
            //Log.e("====", "=====flowId++=" + flowId++);
            int ID = flowId + i;
            //String ttString = msgId + headerO + String.format("%04x", ID) + "0c" + String.format("%02x", i + 1) + "999999999999";//bodyString;//bodyString;
            String ttString = msgId + headerO + "0019" + "0c" + String.format("%02x", i + 1) + "999999999999";//bodyString;//bodyString;
            //String checkSum = String.format("%02x", check);
            //Log.e("====", "=====MSG_TYPE_REG_RESPONSE=check=" + String.format("%02x", Math.abs(check)));
            //Log.e("====", "=========checkSum=" + check);
            //sendMessage(MSG_FLAG + msg + checkSum + MSG_FLAG);
            //String ttString = "010000180199999999900018010006338888888877777777777777777777019999999990";
            int checkint = mBitOperator.getCheckSum4JT808(
                    mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
            Log.e("====", "=========allKeysStatus=" + i);
            try {
                sendMessage(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                Thread.sleep(200L);
            } catch (Exception e) {
                e.printStackTrace();
            }*/
            sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0c" + String.format("%02x", i + 1) + "3b" + "999999999999"));
        }
    }

    public String getTimeHexString() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat format = new SimpleDateFormat("yy-MM-dd HH:mm:ss");
        String time = format.format(date);
        Log.e("====", "======time=" + time);//19-01-26 02:35:00
        Calendar calendar = Calendar.getInstance();
        int year = Integer.parseInt((calendar.get(Calendar.YEAR) + "").substring(2, 4));
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DATE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        Log.e("====", "=====year=" + year + "*month=" + month + "*day=" + day + "*hour=" + hour + "*minute=" + minute + "*second=" + second);
        Log.e("====", "======" + String.format("%02x", year));
        Log.e("====", "======" + String.format("%02x", month));
        Log.e("====", "======" + String.format("%02x", day));
        Log.e("====", "======" + String.format("%02x", hour));
        Log.e("====", "======" + String.format("%02x", minute));
        Log.e("====", "======" + String.format("%02x", second));
        return String.format("%02x", year) + String.format("%02x", month) + String.format("%02x", day)
                + String.format("%02x", hour) + String.format("%02x", minute) + String.format("%02x", second);
    }

    public String getKeyStatus() {
        int[] status = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
        int toHexStatus = 0;
        for (int i = 0; i < 24; i++) {
            toHexStatus += status[i] << i;
        }
        //String.format("%08x", xx);
        Log.e("====", "=========toHexStatus=" + String.format("%08x", toHexStatus));
        return String.format("%08x", toHexStatus);
    }

    public void saveAuthCode(String code) {
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putString("auth_code", code);
        ed.commit();
    }

    public String getSavedAuthCode() {
        String authCode = "unknown";
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        authCode = sp.getString("auth_code", "unknown");
        return authCode;
    }

    public String getSavedProvinceAndCity() {
        String provinceAndCityHexString = "01000633";
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        String province = HexStringUtils.intToHexStringProvinceAndCity(Integer.parseInt(sp.getString("province_id", "37")));
        String city = HexStringUtils.intToHexStringProvinceAndCity(Integer.parseInt(sp.getString("city_id", "0200")));
        provinceAndCityHexString = province + city;
        return provinceAndCityHexString;
    }

    public String getSavedManufacturerID() {
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        String manufacturerID = sp.getString("manufacturer_id", "88888888");
        return manufacturerID;
    }

    public String getSavedShopID() {
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        String shopID = sp.getString("shop_id", "77777777777777777777");
        return shopID;
    }

    public String getSavedBoxID() {
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        String boxID = sp.getString("box_id", "010000090911");
        return boxID;
    }

    public String hexStringChecksum(String msg) {
        int check = mBitOperator.getCheckSum4JT808(
                mBCD8421Operater.string2Bcd(msg), 0, (msg.length() / 2));
        //String checkSum = String.format("%02x", Math.abs(check));
        String checkSum = String.format("%02x", check);
        return checkSum;
    }

    public byte[] hexChecksum(String msg) {
        int check = mBitOperator.getCheckSum4JT808(
                mBCD8421Operater.string2Bcd(msg), 0, (msg.length() / 2));
        //String checkSum = String.format("%02x", Math.abs(check));
        //String checkSum = String.format("%02x", check);
        //return checkSum;
        return mBitOperator.integerTo1Bytes(check);
        //return intToByteArray(check);
    }

    public static byte[] intToByteArray(int a) {
        return new byte[]{
                (byte) ((a >> 24) & 0xFF),
                (byte) ((a >> 16) & 0xFF),
                (byte) ((a >> 8) & 0xFF),
                (byte) (a & 0xFF)
        };
    }

    //终端普通应答
    /*private static final int HANDLER_MSG_TYPE_CLIENT_RESPONSE = 0x0001;
    //心跳
    private static final int HANDLER_MSG_TYPE_HEARTBEAT = 0x0002;
    //注销
    private static final int HANDLER_MSG_TYPE_UNREGISTER = 0x0003;
    //注册
    private static final int HANDLER_MSG_TYPE_REGISTER = 0x0100;
    //终端鉴权
    private static final int HANDLER_MSG_TYPE_AUTHENTICATION = 0x0102;
    //借钥匙
    private static final int HANDLER_MSG_TYPE_BORROW = 5;//暂时未定义
    //还钥匙
    private static final int HANDLER_MSG_TYPE_RETURN = 6;//暂时未定义
    //平台普通应答
    private static final int HANDLER_MSG_TYPE_SERVER_RESPONSE = 0x8001;
    //补传分包请求
    private static final int HANDLER_MSG_TYPE_SUB_PAC_REQ = 0x8003;
    //终端注册应答
    private static final int HANDLER_MSG_TYPE_REG_RESPONSE = 0x8100;
    //终端控制
    private static final int HANDLER_MSG_TYPE_SERVER_CONTROL = 0x8105;
    //终端主动上报
    private static final int HANDLER_MSG_TYPE_CLIENT_REQ = 0x0817;
    //设置终端参数
    private static final int HANDLER_MSG_TYPE_CLIENT_SETTINGS = 0x8103;
    //和PC注册机通信获取注册信息
    private static final int HANDLER_MSG_TYPE_ANDROID_TO_PC = 1024;

    private class TcpSendEventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_TYPE_CLIENT_RESPONSE:

                    break;
                case HANDLER_MSG_TYPE_HEARTBEAT:
                    break;
                case HANDLER_MSG_TYPE_UNREGISTER:
                    break;
                case HANDLER_MSG_TYPE_REGISTER:
                    //reg
                    break;
                case HANDLER_MSG_TYPE_AUTHENTICATION:
                    //auth
                    break;
                case HANDLER_MSG_TYPE_BORROW:
                    break;
                case HANDLER_MSG_TYPE_RETURN:
                    break;
                case HANDLER_MSG_TYPE_SERVER_RESPONSE:
                    break;
                case HANDLER_MSG_TYPE_SUB_PAC_REQ:
                    break;
                case HANDLER_MSG_TYPE_REG_RESPONSE:
                    break;
                case HANDLER_MSG_TYPE_SERVER_CONTROL:
                    break;
                case HANDLER_MSG_TYPE_CLIENT_REQ:
                    //24孔上报
                    //7e 081700060200000000150026 0a 000100190123181622 5e 7e
                    //钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间 00 01 00 190123181622
                    break;
                case HANDLER_MSG_TYPE_CLIENT_SETTINGS:
                    break;
                case HANDLER_MSG_TYPE_ANDROID_TO_PC:

                    break;
            }
        }
    }*/


    public void motor01StatusCtrl(boolean on) {
        try {
            FileWriter command = new FileWriter(MOTOR_01_PATH);
            if (on) {
                command.write("on");
            } else {
                command.write("off");
            }
            //command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void motor02StatusCtrl(boolean on) {
        try {
            FileWriter command = new FileWriter(MOTOR_02_PATH);
            if (on) {
                command.write("on");
            } else {
                command.write("off");
            }
            //command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void motor03StatusCtrl(boolean on) {
        try {
            FileWriter command = new FileWriter(MOTOR_03_PATH);
            if (on) {
                command.write("on");
            } else {
                command.write("off");
            }
            //command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void motor04StatusCtrl(boolean on) {
        try {
            FileWriter command = new FileWriter(MOTOR_04_PATH);
            if (on) {
                command.write("on");
            } else {
                command.write("off");
            }
            //command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void motor05StatusCtrl(boolean on) {
        try {
            FileWriter command = new FileWriter(MOTOR_05_PATH);
            if (on) {
                command.write("on");
            } else {
                command.write("off");
            }
            //command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void motor06StatusCtrl(boolean on) {
        try {
            FileWriter command = new FileWriter(MOTOR_06_PATH);
            if (on) {
                command.write("on");
            } else {
                command.write("off");
            }
            //command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void motor07StatusCtrl(boolean on) {
        try {
            FileWriter command = new FileWriter(MOTOR_07_PATH);
            if (on) {
                command.write("on");
            } else {
                command.write("off");
            }
            //command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void motor08StatusCtrl(boolean on) {
        try {
            FileWriter command = new FileWriter(MOTOR_08_PATH);
            if (on) {
                command.write("on");
            } else {
                command.write("off");
            }
            //command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void motor09StatusCtrl(boolean on) {
        try {
            FileWriter command = new FileWriter(MOTOR_09_PATH);
            if (on) {
                command.write("on");
            } else {
                command.write("off");
            }
            //command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void motor10StatusCtrl(boolean on) {
        try {
            FileWriter command = new FileWriter(MOTOR_10_PATH);
            if (on) {
                command.write("on");
            } else {
                command.write("off");
            }
            //command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void switchStatusCtrl(String path, boolean on) {
        try {
            //SWITCH_04_PATH
            FileWriter command = new FileWriter(path);
            if (on) {
                command.write("on");
            } else {
                command.write("off");
                //playFromRawFile(mContext, R.raw.key_insert);
            }
            //command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void allKeysIdClear() {
        try {
            Runtime.getRuntime().exec(ALL_KEYS_ID_CLEAR_COMMAND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void allKeysIdReadReq() {
        try {
            //SWITCH_04_PATH
            FileWriter command = new FileWriter(ALL_KEYS_ID_REQ_PATH);
            command.write("utg");
            //command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String allKeysIdRead() {
        char[] buffer = new char[1024];

        String state = "";
        FileReader file = null;
        try {
            file = new FileReader(ALL_KEYS_ID_PATH);
            int len = file.read(buffer, 0, 1024);
            state = String.valueOf((new String(buffer, 0, len)));
            if (file != null) {
                file.close();
                file = null;
            }
        } catch (Exception e) {
            try {
                if (file != null) {
                    file.close();
                    file = null;
                }
            } catch (IOException io) {
                //Log.e("WifiSpot", "getWifiSpotState fail");
                e.printStackTrace();
            }
        }
        return state;
    }

    public void allKeysSendAuthCode(String code) {
        try {
            //SWITCH_04_PATH
            FileWriter command = new FileWriter(ALL_KEYS_ID_REQ_PATH);
            command.write(code);
            //command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void ledSeriesCtrl(String path, boolean on) {
        try {
            //SWITCH_04_PATH
            FileWriter command = new FileWriter(path);
            if (on) {
                command.write("255");
            } else {
                command.write("0");
                //playFromRawFile(mContext, R.raw.key_insert);
            }
            //command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class DroidToKey01Handler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_KEY_01_MOTOR_ON:
                    motor01StatusCtrl(true);
                    mDroidToKey01Handler.postDelayed(mMotor01OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_01_POWER_ON:
                    switchStatusCtrl(SWITCH_01_PATH, true);
                    mDroidToKey01Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_01_ID_CLEAR, 100);
                    break;
                case HANDLER_MSG_KEY_01_ID_CLEAR:
                    allKeysIdClear();
                    mDroidToKey01Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_01_REQ_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_01_REQ_READ_ID:
                    allKeysIdReadReq();
                    mDroidToKey01Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_01_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_01_READ_ID:
                    IdReadFromKeys = allKeysIdRead();
                    if (IdReadFromKeys.equals("1")) {
                        tmpKeyId = tmpKey01Id;
                    } else if (IdReadFromKeys.equals("2")) {
                        tmpKeyId = tmpKey02Id;
                    } else if (IdReadFromKeys.equals("3")) {
                        tmpKeyId = tmpKey03Id;
                    } else if (IdReadFromKeys.equals("4")) {
                        tmpKeyId = tmpKey04Id;
                    } else if (IdReadFromKeys.equals("5")) {
                        tmpKeyId = tmpKey05Id;
                    } else if (IdReadFromKeys.equals("6")) {
                        tmpKeyId = tmpKey06Id;
                    } else if (IdReadFromKeys.equals("7")) {
                        tmpKeyId = tmpKey07Id;
                    } else if (IdReadFromKeys.equals("8")) {
                        tmpKeyId = tmpKey08Id;
                    } else if (IdReadFromKeys.equals("9")) {
                        tmpKeyId = tmpKey09Id;
                    } else if (IdReadFromKeys.equals("10")) {
                        tmpKeyId = tmpKey10Id;
                    }
                    //拿到id注册
                    /*String ttString = "0100001e" + tmpPhoneId + "0018010006338888888877777777777777777777" + tmpKeyId + "990000000000";
                    int checkint = mBitOperator.getCheckSum4JT808(
                            mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                    try {
                        TcpClient.init().send(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/
                    break;
                case HANDLER_MSG_KEY_01_SEND_AUTH_CODE:
                    if (IdReadFromKeys != null && (!"".equals(IdReadFromKeys))) {
                        allKeysSendAuthCode("==============");
                    }
                    break;
                case HANDLER_MSG_KEY_01_MOTOR_OFF:
                    motor01StatusCtrl(false);
                    mDroidToKey01Handler.removeCallbacks(mMotor01OffIn10S);
                    break;
                case HANDLER_MSG_KEY_01_POWER_OFF:
                    switchStatusCtrl(SWITCH_01_PATH, false);
                    break;
            }
        }
    }

    private class DroidToKey02Handler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_KEY_02_MOTOR_ON:
                    motor02StatusCtrl(true);
                    mDroidToKey02Handler.postDelayed(mMotor02OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_02_POWER_ON:
                    switchStatusCtrl(SWITCH_02_PATH, true);
                    mDroidToKey02Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_02_ID_CLEAR, 100);
                    break;
                case HANDLER_MSG_KEY_02_ID_CLEAR:
                    allKeysIdClear();
                    mDroidToKey02Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_02_REQ_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_02_REQ_READ_ID:
                    allKeysIdReadReq();
                    mDroidToKey02Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_02_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_02_READ_ID:
                    IdReadFromKeys = allKeysIdRead();
                    if (IdReadFromKeys.equals("1")) {
                        tmpKeyId = tmpKey01Id;
                    } else if (IdReadFromKeys.equals("2")) {
                        tmpKeyId = tmpKey02Id;
                    } else if (IdReadFromKeys.equals("3")) {
                        tmpKeyId = tmpKey03Id;
                    } else if (IdReadFromKeys.equals("4")) {
                        tmpKeyId = tmpKey04Id;
                    } else if (IdReadFromKeys.equals("5")) {
                        tmpKeyId = tmpKey05Id;
                    } else if (IdReadFromKeys.equals("6")) {
                        tmpKeyId = tmpKey06Id;
                    } else if (IdReadFromKeys.equals("7")) {
                        tmpKeyId = tmpKey07Id;
                    } else if (IdReadFromKeys.equals("8")) {
                        tmpKeyId = tmpKey08Id;
                    } else if (IdReadFromKeys.equals("9")) {
                        tmpKeyId = tmpKey09Id;
                    } else if (IdReadFromKeys.equals("10")) {
                        tmpKeyId = tmpKey10Id;
                    }
                    //拿到id注册
                    /*String ttString = "0100001e" + tmpPhoneId + "0018010006338888888877777777777777777777" + tmpKeyId + "990000000000";
                    int checkint = mBitOperator.getCheckSum4JT808(
                            mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                    try {
                        TcpClient.init().send(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/
                    break;
                case HANDLER_MSG_KEY_02_SEND_AUTH_CODE:
                    if (IdReadFromKeys != null && (!"".equals(IdReadFromKeys))) {
                        allKeysSendAuthCode("==============");
                    }
                    break;
                case HANDLER_MSG_KEY_02_MOTOR_OFF:
                    motor02StatusCtrl(false);
                    mDroidToKey02Handler.removeCallbacks(mMotor02OffIn10S);
                    break;
                case HANDLER_MSG_KEY_02_POWER_OFF:
                    switchStatusCtrl(SWITCH_02_PATH, false);
                    break;
            }
        }
    }

    private class DroidToKey03Handler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_KEY_03_MOTOR_ON:
                    motor03StatusCtrl(true);
                    mDroidToKey03Handler.postDelayed(mMotor03OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_03_POWER_ON:
                    switchStatusCtrl(SWITCH_03_PATH, true);
                    mDroidToKey03Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_03_ID_CLEAR, 100);
                    break;
                case HANDLER_MSG_KEY_03_ID_CLEAR:
                    allKeysIdClear();
                    mDroidToKey03Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_03_REQ_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_03_REQ_READ_ID:
                    allKeysIdReadReq();
                    mDroidToKey03Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_03_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_03_READ_ID:
                    IdReadFromKeys = allKeysIdRead();
                    if (IdReadFromKeys.equals("1")) {
                        tmpKeyId = tmpKey01Id;
                    } else if (IdReadFromKeys.equals("2")) {
                        tmpKeyId = tmpKey02Id;
                    } else if (IdReadFromKeys.equals("3")) {
                        tmpKeyId = tmpKey03Id;
                    } else if (IdReadFromKeys.equals("4")) {
                        tmpKeyId = tmpKey04Id;
                    } else if (IdReadFromKeys.equals("5")) {
                        tmpKeyId = tmpKey05Id;
                    } else if (IdReadFromKeys.equals("6")) {
                        tmpKeyId = tmpKey06Id;
                    } else if (IdReadFromKeys.equals("7")) {
                        tmpKeyId = tmpKey07Id;
                    } else if (IdReadFromKeys.equals("8")) {
                        tmpKeyId = tmpKey08Id;
                    } else if (IdReadFromKeys.equals("9")) {
                        tmpKeyId = tmpKey09Id;
                    } else if (IdReadFromKeys.equals("10")) {
                        tmpKeyId = tmpKey10Id;
                    }
                    //拿到id注册
                    /*String ttString = "0100001e" + tmpPhoneId + "0018010006338888888877777777777777777777" + tmpKeyId + "990000000000";
                    int checkint = mBitOperator.getCheckSum4JT808(
                            mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                    try {
                        TcpClient.init().send(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/
                    break;
                case HANDLER_MSG_KEY_03_SEND_AUTH_CODE:
                    if (IdReadFromKeys != null && (!"".equals(IdReadFromKeys))) {
                        allKeysSendAuthCode("==============");
                    }
                    break;
                case HANDLER_MSG_KEY_03_MOTOR_OFF:
                    motor03StatusCtrl(false);
                    mDroidToKey03Handler.removeCallbacks(mMotor03OffIn10S);
                    break;
                case HANDLER_MSG_KEY_03_POWER_OFF:
                    switchStatusCtrl(SWITCH_03_PATH, false);
                    break;
            }
        }
    }

    private class DroidToKey04Handler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_KEY_04_MOTOR_ON:
                    motor04StatusCtrl(true);
                    mDroidToKey04Handler.postDelayed(mMotor04OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_04_POWER_ON:
                    switchStatusCtrl(SWITCH_04_PATH, true);
                    mDroidToKey04Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_ID_CLEAR, 100);
                    break;
                case HANDLER_MSG_KEY_04_ID_CLEAR:
                    allKeysIdClear();
                    mDroidToKey04Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_REQ_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_04_REQ_READ_ID:
                    allKeysIdReadReq();
                    mDroidToKey04Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_04_READ_ID:
                    IdReadFromKeys = allKeysIdRead();
                    if (IdReadFromKeys.equals("1")) {
                        tmpKeyId = tmpKey01Id;
                    } else if (IdReadFromKeys.equals("2")) {
                        tmpKeyId = tmpKey02Id;
                    } else if (IdReadFromKeys.equals("3")) {
                        tmpKeyId = tmpKey03Id;
                    } else if (IdReadFromKeys.equals("4")) {
                        tmpKeyId = tmpKey04Id;
                    } else if (IdReadFromKeys.equals("5")) {
                        tmpKeyId = tmpKey05Id;
                    } else if (IdReadFromKeys.equals("6")) {
                        tmpKeyId = tmpKey06Id;
                    } else if (IdReadFromKeys.equals("7")) {
                        tmpKeyId = tmpKey07Id;
                    } else if (IdReadFromKeys.equals("8")) {
                        tmpKeyId = tmpKey08Id;
                    } else if (IdReadFromKeys.equals("9")) {
                        tmpKeyId = tmpKey09Id;
                    } else if (IdReadFromKeys.equals("10")) {
                        tmpKeyId = tmpKey10Id;
                    }
                    //拿到id注册
                    /*String ttString = "0100001e" + tmpPhoneId + "0018010006338888888877777777777777777777" + tmpKeyId + "990000000000";
                    int checkint = mBitOperator.getCheckSum4JT808(
                            mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                    try {
                        TcpClient.init().send(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/
                    break;
                case HANDLER_MSG_KEY_04_SEND_AUTH_CODE:
                    if (IdReadFromKeys != null && (!"".equals(IdReadFromKeys))) {
                        allKeysSendAuthCode("==============");
                    }
                    break;
                case HANDLER_MSG_KEY_04_MOTOR_OFF:
                    motor04StatusCtrl(false);
                    mDroidToKey04Handler.removeCallbacks(mMotor04OffIn10S);
                    break;
                case HANDLER_MSG_KEY_04_POWER_OFF:
                    switchStatusCtrl(SWITCH_04_PATH, false);
                    break;
            }
        }
    }

    private class DroidToKey05Handler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_KEY_05_MOTOR_ON:
                    motor05StatusCtrl(true);
                    mDroidToKey05Handler.postDelayed(mMotor05OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_05_POWER_ON:
                    switchStatusCtrl(SWITCH_05_PATH, true);
                    mDroidToKey05Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_05_ID_CLEAR, 100);
                    break;
                case HANDLER_MSG_KEY_05_ID_CLEAR:
                    allKeysIdClear();
                    mDroidToKey05Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_05_REQ_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_05_REQ_READ_ID:
                    allKeysIdReadReq();
                    mDroidToKey05Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_05_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_05_READ_ID:
                    IdReadFromKeys = allKeysIdRead();
                    if (IdReadFromKeys.equals("1")) {
                        tmpKeyId = tmpKey01Id;
                    } else if (IdReadFromKeys.equals("2")) {
                        tmpKeyId = tmpKey02Id;
                    } else if (IdReadFromKeys.equals("3")) {
                        tmpKeyId = tmpKey03Id;
                    } else if (IdReadFromKeys.equals("4")) {
                        tmpKeyId = tmpKey04Id;
                    } else if (IdReadFromKeys.equals("5")) {
                        tmpKeyId = tmpKey05Id;
                    } else if (IdReadFromKeys.equals("6")) {
                        tmpKeyId = tmpKey06Id;
                    } else if (IdReadFromKeys.equals("7")) {
                        tmpKeyId = tmpKey07Id;
                    } else if (IdReadFromKeys.equals("8")) {
                        tmpKeyId = tmpKey08Id;
                    } else if (IdReadFromKeys.equals("9")) {
                        tmpKeyId = tmpKey09Id;
                    } else if (IdReadFromKeys.equals("10")) {
                        tmpKeyId = tmpKey10Id;
                    }
                    //拿到id注册
                    /*String ttString = "0100001e" + tmpPhoneId + "0018010006338888888877777777777777777777" + tmpKeyId + "990000000000";
                    int checkint = mBitOperator.getCheckSum4JT808(
                            mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                    try {
                        TcpClient.init().send(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/
                    break;
                case HANDLER_MSG_KEY_05_SEND_AUTH_CODE:
                    if (IdReadFromKeys != null && (!"".equals(IdReadFromKeys))) {
                        allKeysSendAuthCode("==============");
                    }
                    break;
                case HANDLER_MSG_KEY_05_MOTOR_OFF:
                    motor05StatusCtrl(false);
                    mDroidToKey05Handler.removeCallbacks(mMotor05OffIn10S);
                    break;
                case HANDLER_MSG_KEY_05_POWER_OFF:
                    switchStatusCtrl(SWITCH_05_PATH, false);
                    break;
            }
        }
    }

    private class DroidToKey06Handler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_KEY_06_MOTOR_ON:
                    motor06StatusCtrl(true);
                    mDroidToKey06Handler.postDelayed(mMotor06OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_06_POWER_ON:
                    switchStatusCtrl(SWITCH_06_PATH, true);
                    mDroidToKey06Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_06_ID_CLEAR, 100);
                    break;
                case HANDLER_MSG_KEY_06_ID_CLEAR:
                    allKeysIdClear();
                    mDroidToKey06Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_06_REQ_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_06_REQ_READ_ID:
                    allKeysIdReadReq();
                    mDroidToKey06Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_06_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_06_READ_ID:
                    IdReadFromKeys = allKeysIdRead();
                    if (IdReadFromKeys.equals("1")) {
                        tmpKeyId = tmpKey01Id;
                    } else if (IdReadFromKeys.equals("2")) {
                        tmpKeyId = tmpKey02Id;
                    } else if (IdReadFromKeys.equals("3")) {
                        tmpKeyId = tmpKey03Id;
                    } else if (IdReadFromKeys.equals("4")) {
                        tmpKeyId = tmpKey04Id;
                    } else if (IdReadFromKeys.equals("5")) {
                        tmpKeyId = tmpKey05Id;
                    } else if (IdReadFromKeys.equals("6")) {
                        tmpKeyId = tmpKey06Id;
                    } else if (IdReadFromKeys.equals("7")) {
                        tmpKeyId = tmpKey07Id;
                    } else if (IdReadFromKeys.equals("8")) {
                        tmpKeyId = tmpKey08Id;
                    } else if (IdReadFromKeys.equals("9")) {
                        tmpKeyId = tmpKey09Id;
                    } else if (IdReadFromKeys.equals("10")) {
                        tmpKeyId = tmpKey10Id;
                    }
                    //拿到id注册
                    /*String ttString = "0100001e" + tmpPhoneId + "0018010006338888888877777777777777777777" + tmpKeyId + "990000000000";
                    int checkint = mBitOperator.getCheckSum4JT808(
                            mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                    try {
                        TcpClient.init().send(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/
                    break;
                case HANDLER_MSG_KEY_06_SEND_AUTH_CODE:
                    if (IdReadFromKeys != null && (!"".equals(IdReadFromKeys))) {
                        allKeysSendAuthCode("==============");
                    }
                    break;
                case HANDLER_MSG_KEY_06_MOTOR_OFF:
                    motor06StatusCtrl(false);
                    mDroidToKey06Handler.removeCallbacks(mMotor06OffIn10S);
                    break;
                case HANDLER_MSG_KEY_06_POWER_OFF:
                    switchStatusCtrl(SWITCH_06_PATH, false);
                    break;
            }
        }
    }

    private class DroidToKey07Handler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_KEY_07_MOTOR_ON:
                    motor07StatusCtrl(true);
                    mDroidToKey07Handler.postDelayed(mMotor07OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_07_POWER_ON:
                    switchStatusCtrl(SWITCH_07_PATH, true);
                    mDroidToKey07Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_07_ID_CLEAR, 100);
                    break;
                case HANDLER_MSG_KEY_07_ID_CLEAR:
                    allKeysIdClear();
                    mDroidToKey07Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_07_REQ_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_07_REQ_READ_ID:
                    allKeysIdReadReq();
                    mDroidToKey07Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_07_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_07_READ_ID:
                    IdReadFromKeys = allKeysIdRead();
                    if (IdReadFromKeys.equals("1")) {
                        tmpKeyId = tmpKey01Id;
                    } else if (IdReadFromKeys.equals("2")) {
                        tmpKeyId = tmpKey02Id;
                    } else if (IdReadFromKeys.equals("3")) {
                        tmpKeyId = tmpKey03Id;
                    } else if (IdReadFromKeys.equals("4")) {
                        tmpKeyId = tmpKey04Id;
                    } else if (IdReadFromKeys.equals("5")) {
                        tmpKeyId = tmpKey05Id;
                    } else if (IdReadFromKeys.equals("6")) {
                        tmpKeyId = tmpKey06Id;
                    } else if (IdReadFromKeys.equals("7")) {
                        tmpKeyId = tmpKey07Id;
                    } else if (IdReadFromKeys.equals("8")) {
                        tmpKeyId = tmpKey08Id;
                    } else if (IdReadFromKeys.equals("9")) {
                        tmpKeyId = tmpKey09Id;
                    } else if (IdReadFromKeys.equals("10")) {
                        tmpKeyId = tmpKey10Id;
                    }
                    //拿到id注册
                    /*String ttString = "0100001e" + tmpPhoneId + "0018010006338888888877777777777777777777" + tmpKeyId + "990000000000";
                    int checkint = mBitOperator.getCheckSum4JT808(
                            mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                    try {
                        TcpClient.init().send(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/
                    break;
                case HANDLER_MSG_KEY_07_SEND_AUTH_CODE:
                    if (IdReadFromKeys != null && (!"".equals(IdReadFromKeys))) {
                        allKeysSendAuthCode("==============");
                    }
                    break;
                case HANDLER_MSG_KEY_07_MOTOR_OFF:
                    motor07StatusCtrl(false);
                    mDroidToKey07Handler.removeCallbacks(mMotor07OffIn10S);
                    break;
                case HANDLER_MSG_KEY_07_POWER_OFF:
                    switchStatusCtrl(SWITCH_07_PATH, false);
                    break;
            }
        }
    }

    private class DroidToKey08Handler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_KEY_08_MOTOR_ON:
                    motor08StatusCtrl(true);
                    mDroidToKey08Handler.postDelayed(mMotor08OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_08_POWER_ON:
                    switchStatusCtrl(SWITCH_08_PATH, true);
                    mDroidToKey08Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_08_ID_CLEAR, 100);
                    break;
                case HANDLER_MSG_KEY_08_ID_CLEAR:
                    allKeysIdClear();
                    mDroidToKey08Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_08_REQ_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_08_REQ_READ_ID:
                    allKeysIdReadReq();
                    mDroidToKey08Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_08_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_08_READ_ID:
                    IdReadFromKeys = allKeysIdRead();
                    if (IdReadFromKeys.equals("1")) {
                        tmpKeyId = tmpKey01Id;
                    } else if (IdReadFromKeys.equals("2")) {
                        tmpKeyId = tmpKey02Id;
                    } else if (IdReadFromKeys.equals("3")) {
                        tmpKeyId = tmpKey03Id;
                    } else if (IdReadFromKeys.equals("4")) {
                        tmpKeyId = tmpKey04Id;
                    } else if (IdReadFromKeys.equals("5")) {
                        tmpKeyId = tmpKey05Id;
                    } else if (IdReadFromKeys.equals("6")) {
                        tmpKeyId = tmpKey06Id;
                    } else if (IdReadFromKeys.equals("7")) {
                        tmpKeyId = tmpKey07Id;
                    } else if (IdReadFromKeys.equals("8")) {
                        tmpKeyId = tmpKey08Id;
                    } else if (IdReadFromKeys.equals("9")) {
                        tmpKeyId = tmpKey09Id;
                    } else if (IdReadFromKeys.equals("10")) {
                        tmpKeyId = tmpKey10Id;
                    }
                    //拿到id注册
                    /*String ttString = "0100001e" + tmpPhoneId + "0018010006338888888877777777777777777777" + tmpKeyId + "990000000000";
                    int checkint = mBitOperator.getCheckSum4JT808(
                            mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                    try {
                        TcpClient.init().send(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/
                    break;
                case HANDLER_MSG_KEY_08_SEND_AUTH_CODE:
                    if (IdReadFromKeys != null && (!"".equals(IdReadFromKeys))) {
                        allKeysSendAuthCode("==============");
                    }
                    break;
                case HANDLER_MSG_KEY_08_MOTOR_OFF:
                    motor08StatusCtrl(false);
                    mDroidToKey08Handler.removeCallbacks(mMotor08OffIn10S);
                    break;
                case HANDLER_MSG_KEY_08_POWER_OFF:
                    switchStatusCtrl(SWITCH_08_PATH, false);
                    break;
            }
        }
    }

    private class DroidToKey09Handler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_KEY_09_MOTOR_ON:
                    motor09StatusCtrl(true);
                    mDroidToKey09Handler.postDelayed(mMotor09OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_09_POWER_ON:
                    switchStatusCtrl(SWITCH_09_PATH, true);
                    mDroidToKey09Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_09_ID_CLEAR, 100);
                    break;
                case HANDLER_MSG_KEY_09_ID_CLEAR:
                    allKeysIdClear();
                    mDroidToKey09Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_09_REQ_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_09_REQ_READ_ID:
                    allKeysIdReadReq();
                    mDroidToKey09Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_09_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_09_READ_ID:
                    IdReadFromKeys = allKeysIdRead();
                    if (IdReadFromKeys.equals("1")) {
                        tmpKeyId = tmpKey01Id;
                    } else if (IdReadFromKeys.equals("2")) {
                        tmpKeyId = tmpKey02Id;
                    } else if (IdReadFromKeys.equals("3")) {
                        tmpKeyId = tmpKey03Id;
                    } else if (IdReadFromKeys.equals("4")) {
                        tmpKeyId = tmpKey04Id;
                    } else if (IdReadFromKeys.equals("5")) {
                        tmpKeyId = tmpKey05Id;
                    } else if (IdReadFromKeys.equals("6")) {
                        tmpKeyId = tmpKey06Id;
                    } else if (IdReadFromKeys.equals("7")) {
                        tmpKeyId = tmpKey07Id;
                    } else if (IdReadFromKeys.equals("8")) {
                        tmpKeyId = tmpKey08Id;
                    } else if (IdReadFromKeys.equals("9")) {
                        tmpKeyId = tmpKey09Id;
                    } else if (IdReadFromKeys.equals("10")) {
                        tmpKeyId = tmpKey10Id;
                    }
                    //拿到id注册
                    /*String ttString = "0100001e" + tmpPhoneId + "0018010006338888888877777777777777777777" + tmpKeyId + "990000000000";
                    int checkint = mBitOperator.getCheckSum4JT808(
                            mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                    try {
                        TcpClient.init().send(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/
                    break;
                case HANDLER_MSG_KEY_09_SEND_AUTH_CODE:
                    if (IdReadFromKeys != null && (!"".equals(IdReadFromKeys))) {
                        allKeysSendAuthCode("==============");
                    }
                    break;
                case HANDLER_MSG_KEY_09_MOTOR_OFF:
                    motor09StatusCtrl(false);
                    mDroidToKey09Handler.removeCallbacks(mMotor09OffIn10S);
                    break;
                case HANDLER_MSG_KEY_09_POWER_OFF:
                    switchStatusCtrl(SWITCH_09_PATH, false);
                    break;
            }
        }
    }

    private class DroidToKey10Handler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_KEY_10_MOTOR_ON:
                    motor10StatusCtrl(true);
                    mDroidToKey10Handler.postDelayed(mMotor10OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_10_POWER_ON:
                    switchStatusCtrl(SWITCH_10_PATH, true);
                    mDroidToKey10Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_10_ID_CLEAR, 100);
                    break;
                case HANDLER_MSG_KEY_10_ID_CLEAR:
                    allKeysIdClear();
                    mDroidToKey10Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_10_REQ_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_10_REQ_READ_ID:
                    allKeysIdReadReq();
                    mDroidToKey10Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_10_READ_ID, 100);
                    break;
                case HANDLER_MSG_KEY_10_READ_ID:
                    IdReadFromKeys = allKeysIdRead();
                    if (IdReadFromKeys.equals("1")) {
                        tmpKeyId = tmpKey01Id;
                    } else if (IdReadFromKeys.equals("2")) {
                        tmpKeyId = tmpKey02Id;
                    } else if (IdReadFromKeys.equals("3")) {
                        tmpKeyId = tmpKey03Id;
                    } else if (IdReadFromKeys.equals("4")) {
                        tmpKeyId = tmpKey04Id;
                    } else if (IdReadFromKeys.equals("5")) {
                        tmpKeyId = tmpKey05Id;
                    } else if (IdReadFromKeys.equals("6")) {
                        tmpKeyId = tmpKey06Id;
                    } else if (IdReadFromKeys.equals("7")) {
                        tmpKeyId = tmpKey07Id;
                    } else if (IdReadFromKeys.equals("8")) {
                        tmpKeyId = tmpKey08Id;
                    } else if (IdReadFromKeys.equals("9")) {
                        tmpKeyId = tmpKey09Id;
                    } else if (IdReadFromKeys.equals("10")) {
                        tmpKeyId = tmpKey10Id;
                    }
                    //拿到id注册
                    /*String ttString = "0100001e" + tmpPhoneId + "0018010006338888888877777777777777777777" + tmpKeyId + "990000000000";
                    int checkint = mBitOperator.getCheckSum4JT808(
                            mBCD8421Operater.string2Bcd(ttString), 0, (ttString.length() / 2));
                    try {
                        TcpClient.init().send(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/
                    break;
                case HANDLER_MSG_KEY_10_SEND_AUTH_CODE:
                    if (IdReadFromKeys != null && (!"".equals(IdReadFromKeys))) {
                        allKeysSendAuthCode("==============");
                    }
                    break;
                case HANDLER_MSG_KEY_10_MOTOR_OFF:
                    motor10StatusCtrl(false);
                    mDroidToKey10Handler.removeCallbacks(mMotor10OffIn10S);
                    break;
                case HANDLER_MSG_KEY_10_POWER_OFF:
                    switchStatusCtrl(SWITCH_10_PATH, false);
                    break;
            }
        }
    }

    private final Runnable playSoundKeyInsert = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.key_insert);
        }
    };

    private final Runnable playSoundKeyOut = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.key_out);
        }
    };

    private final Runnable playSoundNotiRemove = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.notify_key_remove);
        }
    };

    private final Runnable mMotor01OffIn10S = new Runnable() {
        public void run() {
            motor01StatusCtrl(false);
        }
    };
    private final Runnable mMotor02OffIn10S = new Runnable() {
        public void run() {
            motor02StatusCtrl(false);
        }
    };
    private final Runnable mMotor03OffIn10S = new Runnable() {
        public void run() {
            motor03StatusCtrl(false);
        }
    };
    private final Runnable mMotor04OffIn10S = new Runnable() {
        public void run() {
            motor04StatusCtrl(false);
        }
    };
    private final Runnable mMotor05OffIn10S = new Runnable() {
        public void run() {
            motor05StatusCtrl(false);
        }
    };
    private final Runnable mMotor06OffIn10S = new Runnable() {
        public void run() {
            motor06StatusCtrl(false);
        }
    };
    private final Runnable mMotor07OffIn10S = new Runnable() {
        public void run() {
            motor07StatusCtrl(false);
        }
    };
    private final Runnable mMotor08OffIn10S = new Runnable() {
        public void run() {
            motor08StatusCtrl(false);
        }
    };
    private final Runnable mMotor09OffIn10S = new Runnable() {
        public void run() {
            motor09StatusCtrl(false);
        }
    };
    private final Runnable mMotor10OffIn10S = new Runnable() {
        public void run() {
            motor10StatusCtrl(false);
        }
    };

    //InfoUtils.playFromRawFile(this, R.raw.noti_new_ver);
    public static void playFromRawFile(Context context, int rawId) {
        try {
            MediaPlayer player = new MediaPlayer();
            AssetFileDescriptor file = context.getResources().openRawResourceFd(rawId);
            try {
                player.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
                file.close();
                if (!player.isPlaying()) {
                    player.prepare();
                    player.start();
                }
                player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        mediaPlayer.release();
                    }
                });
            } catch (IOException e) {
                player = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isNetworkAvailable(Context context) {
        if (context != null) {
            ConnectivityManager mConnectivityManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
            if (mNetworkInfo != null) {
                return mNetworkInfo.isAvailable();
            }
        }
        return false;
    }
}
