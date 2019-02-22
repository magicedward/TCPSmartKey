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
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemService;
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
import com.erobbing.tcpsmartkey.util.ShellUtils;

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
    private String mLocationFrequency = "60";
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
    private String tmpPhoneId = "019999999976";//019999999998
    private String tmpKeyId = "000000090901";
    private String currentKeyId = "000000000000";//"000000090901";
    private String currentKeyholeId = "01";
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
    private static final String LED_SERIES_01 = "/sys/class/leds/led-ct-01/brightness";//red
    private static final String LED_SERIES_02 = "/sys/class/leds/led-ct-02/brightness";//blue
    //private static final String LED_SERIES_03 = "/sys/devices/soc.0/gpio-leds.70/leds/led-ct-03/brightness";
    //
    private static final String LED_RED_01 = "/sys/class/leds/led-r-01/brightness";
    private static final String LED_RED_02 = "/sys/class/leds/led-r-02/brightness";
    private static final String LED_RED_03 = "/sys/class/leds/led-r-03/brightness";
    private static final String LED_RED_04 = "/sys/class/leds/led-r-04/brightness";
    private static final String LED_RED_05 = "/sys/class/leds/led-r-05/brightness";
    private static final String LED_RED_06 = "/sys/class/leds/led-r-06/brightness";
    private static final String LED_RED_07 = "/sys/class/leds/led-r-07/brightness";
    private static final String LED_RED_08 = "/sys/class/leds/led-r-08/brightness";
    private static final String LED_RED_09 = "/sys/class/leds/led-r-09/brightness";
    private static final String LED_RED_10 = "/sys/class/leds/led-r-10/brightness";
    private static final String LED_GREEN_01 = "/sys/class/leds/led-g-01/brightness";
    private static final String LED_GREEN_02 = "/sys/class/leds/led-g-02/brightness";
    private static final String LED_GREEN_03 = "/sys/class/leds/led-g-03/brightness";
    private static final String LED_GREEN_04 = "/sys/class/leds/led-g-04/brightness";
    private static final String LED_GREEN_05 = "/sys/class/leds/led-g-05/brightness";
    private static final String LED_GREEN_06 = "/sys/class/leds/led-g-06/brightness";
    private static final String LED_GREEN_07 = "/sys/class/leds/led-g-07/brightness";
    private static final String LED_GREEN_08 = "/sys/class/leds/led-g-08/brightness";
    private static final String LED_GREEN_09 = "/sys/class/leds/led-g-09/brightness";
    private static final String LED_GREEN_10 = "/sys/class/leds/led-g-10/brightness";


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
    private boolean mAuthState = false;

    private static final String ALL_KEYS_ID_PATH = "/sys/devices/virtual/switch/rkeyid";
    private static final String ALL_KEYS_ID_REQ_PATH = "/sys/class/gpio_switch/wkeyid";
    private static final String ALL_KEYS_ID_CLEAR_COMMAND = "cat /sys/devices/virtual/switch/rkeyid";

    private static final String DROID_TO_KEYS_SEND_AUTH_PATH = "/sys/class/gpio_pwm/pwm1";

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

    private static final String WRITE_AUTH_CODE_PATH = "/sys/bus/i2c/devices/6-005b/mcu/se_code";
    private static final String RW_SHOP_ID_PATH = "/sys/bus/i2c/devices/6-005b/mcu/dm_id";
    private static final String READ_KEY_ID_PATH = "/sys/bus/i2c/devices/6-005b/mcu/key_id";
    private static final String RW_KEY_BATTERY_PATH = "/sys/bus/i2c/devices/6-005b/mcu/key_st";

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

    private static final int HANDLER_MSG_LED_SERIES_01_ON = 80;
    private static final int HANDLER_MSG_LED_SERIES_01_OFF = 81;
    private static final int HANDLER_MSG_LED_SERIES_02_ON = 82;
    private static final int HANDLER_MSG_LED_SERIES_02_OFF = 83;
    private static final int HANDLER_MSG_LED_RED_01_ON = 84;
    private static final int HANDLER_MSG_LED_RED_01_OFF = 85;
    private static final int HANDLER_MSG_LED_GREEN_01_ON = 86;
    private static final int HANDLER_MSG_LED_GREEN_01_OFF = 87;
    private static final int HANDLER_MSG_LED_RED_02_ON = 88;
    private static final int HANDLER_MSG_LED_RED_02_OFF = 89;
    private static final int HANDLER_MSG_LED_GREEN_02_ON = 90;
    private static final int HANDLER_MSG_LED_GREEN_02_OFF = 91;
    private static final int HANDLER_MSG_LED_RED_03_ON = 92;
    private static final int HANDLER_MSG_LED_RED_03_OFF = 93;
    private static final int HANDLER_MSG_LED_GREEN_03_ON = 94;
    private static final int HANDLER_MSG_LED_GREEN_03_OFF = 95;
    private static final int HANDLER_MSG_LED_RED_04_ON = 96;
    private static final int HANDLER_MSG_LED_RED_04_OFF = 97;
    private static final int HANDLER_MSG_LED_GREEN_04_ON = 98;
    private static final int HANDLER_MSG_LED_GREEN_04_OFF = 99;
    private static final int HANDLER_MSG_LED_RED_05_ON = 100;
    private static final int HANDLER_MSG_LED_RED_05_OFF = 101;
    private static final int HANDLER_MSG_LED_GREEN_05_ON = 102;
    private static final int HANDLER_MSG_LED_GREEN_05_OFF = 103;
    private static final int HANDLER_MSG_LED_RED_06_ON = 104;
    private static final int HANDLER_MSG_LED_RED_06_OFF = 105;
    private static final int HANDLER_MSG_LED_GREEN_06_ON = 106;
    private static final int HANDLER_MSG_LED_GREEN_06_OFF = 107;
    private static final int HANDLER_MSG_LED_RED_07_ON = 108;
    private static final int HANDLER_MSG_LED_RED_07_OFF = 109;
    private static final int HANDLER_MSG_LED_GREEN_07_ON = 110;
    private static final int HANDLER_MSG_LED_GREEN_07_OFF = 111;
    private static final int HANDLER_MSG_LED_RED_08_ON = 112;
    private static final int HANDLER_MSG_LED_RED_08_OFF = 113;
    private static final int HANDLER_MSG_LED_GREEN_08_ON = 114;
    private static final int HANDLER_MSG_LED_GREEN_08_OFF = 115;
    private static final int HANDLER_MSG_LED_RED_09_ON = 116;
    private static final int HANDLER_MSG_LED_RED_09_OFF = 117;
    private static final int HANDLER_MSG_LED_GREEN_09_ON = 118;
    private static final int HANDLER_MSG_LED_GREEN_09_OFF = 119;
    private static final int HANDLER_MSG_LED_RED_10_ON = 120;
    private static final int HANDLER_MSG_LED_RED_10_OFF = 121;
    private static final int HANDLER_MSG_LED_GREEN_10_ON = 122;
    private static final int HANDLER_MSG_LED_GREEN_10_OFF = 123;

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;

    private DroidToKey01Handler mDroidToKey01Handler;

    private MotorCtrlHandler mMotorCtrlHandler;
    private LedCtrlHandler mLedCtrlHandler;

    /////////////////////////////////////////////////////////////
    private final UEventObserver mBottomSwitch01Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch01Observer", "onUEvent.event.toString()=" + event.toString());
            isKey01BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_01_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_ON, 10);
            if (isKey01BottomOn) {
                //switchStatusCtrl(SWITCH_01_PATH, true);
                ShellUtils.execCommand("echo on > /sys/class/gpio_switch/switch_ct_01", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "01";
                Log.e("=====", "key01--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                //if (false) {
                //if (shopId.contains("ffffffffffffffffffff") || shopId.contains("fafafafafafafafafafa")) {//读到店面id
                if (shopId.contains("ffffffffffffffffffff")) {
                    //switchStatusCtrl(SWITCH_01_PATH, true);
                    Log.e("====", "===========key01reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mLedCtrlHandler.postDelayed(playSoundKeycommunicateErr, 200);
                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_01_MOTOR_ON, 1000);
                } else {
                    Log.e("====", "===key01=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key01=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        mLedCtrlHandler.postDelayed(playSoundKeyBackSucceed, 10);
                        //钥匙扣归还
                        //钥匙扣插入钥匙箱01，钥匙箱主动上报平台。上报指令如下：“钥匙孔编号;钥匙扣ID”
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        Log.e("====", "===============key01-back");
                        //allKeysBatteryLevelWrite("02");//不在此处，在归还收到服务器信息之后
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_01_ON, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_OFF, 10);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key01-back-illegal");
                        mLedCtrlHandler.postDelayed(playSoundKeyBackIllegal, 10);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_01_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_ON, 10);
                    }
                }
                ShellUtils.execCommand("echo off > /sys/class/gpio_switch/switch_ct_01", false);
            } else {
                boolean isLegal = getXmlKeyOutLegal();
                if (isLegal) {
                    //声音提示弹出
                    //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                    //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "01" + "3b" + "00" + "3b" + getTimeHexString()));
                    Log.e("====", "=========key01-normal-out-");
                    mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_01_OFF, 10);
                    setXmlKeyOutLegal(false);
                } else {
                    //  提示非法
                    Log.e("====", "=========key01-illegal-out-");
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                    mLedCtrlHandler.postDelayed(playSoundKeyOutIllegal, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_ON, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_OFF, 10);
                }
                //setXmlKeyOutLegal(false);
            }
        }
    };
    private final UEventObserver mBottomSwitch02Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch02Observer", "onUEvent.event.toString()=" + event.toString());
            isKey02BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            //mDroidToKey04Handler.postDelayed(playSoundKeyInsert, 50);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_02_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_ON, 10);
            if (isKey02BottomOn) {
                //switchStatusCtrl(SWITCH_01_PATH, true);
                ShellUtils.execCommand("echo on > /sys/class/gpio_switch/switch_ct_02", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "02";
                Log.e("=====", "key02--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                //if (false) {
                //if (shopId.contains("ffffffffffffffffffff") || shopId.contains("fafafafafafafafafafa")) {//读到店面id
                if (shopId.contains("ffffffffffffffffffff")) {
                    //switchStatusCtrl(SWITCH_01_PATH, true);
                    Log.e("====", "===========key02reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mLedCtrlHandler.postDelayed(playSoundKeycommunicateErr, 200);
                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_02_MOTOR_ON, 1000);
                } else {
                    Log.e("====", "===key02=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===kwy02=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        mLedCtrlHandler.postDelayed(playSoundKeyBackSucceed, 10);
                        //钥匙扣归还
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "02" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        Log.e("====", "===============key02-back");
                        //allKeysBatteryLevelWrite("02");//不在此处，在归还收到服务器信息之后
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_02_ON, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_OFF, 10);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key01-back-illegal");
                        mLedCtrlHandler.postDelayed(playSoundKeyBackIllegal, 10);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_02_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_ON, 10);
                    }
                }
                ShellUtils.execCommand("echo off > /sys/class/gpio_switch/switch_ct_02", false);
            } else {
                boolean isLegal = getXmlKeyOutLegal();
                if (isLegal) {
                    //声音提示弹出
                    //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                    //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "02" + "3b" + "00" + "3b" + getTimeHexString()));
                    Log.e("====", "=========key02-normal-out-");
                    mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_02_OFF, 10);
                    setXmlKeyOutLegal(false);
                } else {
                    //  提示非法
                    Log.e("====", "=========key02-illegal-out-");
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                    mLedCtrlHandler.postDelayed(playSoundKeyOutIllegal, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_ON, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_OFF, 10);
                }
                //setXmlKeyOutLegal(false);
            }

        }
    };
    private final UEventObserver mBottomSwitch03Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch03Observer", "onUEvent.event.toString()=" + event.toString());
            isKey03BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_03_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_ON, 10);
            if (isKey03BottomOn) {
                //switchStatusCtrl(SWITCH_01_PATH, true);
                ShellUtils.execCommand("echo on > /sys/class/gpio_switch/switch_ct_03", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "03";
                Log.e("=====", "key03--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                //if (false) {
                //if (shopId.contains("ffffffffffffffffffff") || shopId.contains("fafafafafafafafafafa")) {//读到店面id
                if (shopId.contains("ffffffffffffffffffff")) {
                    //switchStatusCtrl(SWITCH_01_PATH, true);
                    Log.e("====", "===========key03reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mLedCtrlHandler.postDelayed(playSoundKeycommunicateErr, 200);
                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_03_MOTOR_ON, 1000);
                } else {
                    Log.e("====", "===key03=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key03=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        mLedCtrlHandler.postDelayed(playSoundKeyBackSucceed, 10);
                        //钥匙扣归还
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "03" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        Log.e("====", "===============key03-back");
                        //allKeysBatteryLevelWrite("02");//不在此处，在归还收到服务器信息之后
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_03_ON, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_OFF, 10);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key03-back-illegal");
                        mLedCtrlHandler.postDelayed(playSoundKeyBackIllegal, 10);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_03_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_ON, 10);
                    }
                }
                ShellUtils.execCommand("echo off > /sys/class/gpio_switch/switch_ct_03", false);
            } else {
                boolean isLegal = getXmlKeyOutLegal();
                if (isLegal) {
                    //声音提示弹出
                    //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                    //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "03" + "3b" + "00" + "3b" + getTimeHexString()));
                    Log.e("====", "=========key03-normal-out-");
                    mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_03_OFF, 10);
                    setXmlKeyOutLegal(false);
                } else {
                    //  提示非法
                    Log.e("====", "=========key03-illegal-out-");
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                    mLedCtrlHandler.postDelayed(playSoundKeyOutIllegal, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_ON, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_OFF, 10);
                }
                //setXmlKeyOutLegal(false);
            }
        }
    };
    private final UEventObserver mBottomSwitch04Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch04Observer", "onUEvent.event.toString()=" + event.toString());
            isKey04BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_04_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_ON, 10);
            if (isKey04BottomOn) {
                //switchStatusCtrl(SWITCH_01_PATH, true);
                ShellUtils.execCommand("echo on > /sys/class/gpio_switch/switch_ct_04", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "04";
                Log.e("=====", "key04--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                //if (false) {
                //if (shopId.contains("ffffffffffffffffffff") || shopId.contains("fafafafafafafafafafa")) {//读到店面id
                if (shopId.contains("ffffffffffffffffffff")) {
                    //switchStatusCtrl(SWITCH_01_PATH, true);
                    Log.e("====", "===========key04reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mLedCtrlHandler.postDelayed(playSoundKeycommunicateErr, 200);
                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_MOTOR_ON, 1000);
                } else {
                    Log.e("====", "===key04=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key04=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        mLedCtrlHandler.postDelayed(playSoundKeyBackSucceed, 10);
                        //钥匙扣归还
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "04" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        Log.e("====", "===============key04-back");
                        //allKeysBatteryLevelWrite("02");//不在此处，在归还收到服务器信息之后
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_04_ON, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_OFF, 10);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key04-back-illegal");
                        mLedCtrlHandler.postDelayed(playSoundKeyBackIllegal, 10);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_ON, 10);
                    }
                }
                ShellUtils.execCommand("echo off > /sys/class/gpio_switch/switch_ct_04", false);
            } else {
                boolean isLegal = getXmlKeyOutLegal();
                if (isLegal) {
                    //声音提示弹出
                    //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                    //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "04" + "3b" + "00" + "3b" + getTimeHexString()));
                    Log.e("====", "=========key04-normal-out-");
                    mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_04_OFF, 10);
                    setXmlKeyOutLegal(false);
                } else {
                    //  提示非法
                    Log.e("====", "=========key04-illegal-out-");
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                    mLedCtrlHandler.postDelayed(playSoundKeyOutIllegal, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_ON, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_OFF, 10);
                }
                //setXmlKeyOutLegal(false);
            }
        }
    };
    private final UEventObserver mBottomSwitch05Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch05Observer", "onUEvent.event.toString()=" + event.toString());
            isKey05BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_05_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_ON, 10);

            if (isKey05BottomOn) {
                //switchStatusCtrl(SWITCH_01_PATH, true);
                ShellUtils.execCommand("echo on > /sys/class/gpio_switch/switch_ct_05", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "05";
                Log.e("=====", "key05--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                //if (false) {
                //if (shopId.contains("ffffffffffffffffffff") || shopId.contains("fafafafafafafafafafa")) {//读到店面id
                if (shopId.contains("ffffffffffffffffffff")) {
                    //switchStatusCtrl(SWITCH_01_PATH, true);
                    Log.e("====", "===========key05reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mLedCtrlHandler.postDelayed(playSoundKeycommunicateErr, 200);
                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_05_MOTOR_ON, 1000);
                } else {
                    Log.e("====", "===key05=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key05=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        mLedCtrlHandler.postDelayed(playSoundKeyBackSucceed, 10);
                        //钥匙扣归还
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "05" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        Log.e("====", "===============key05-back");
                        //allKeysBatteryLevelWrite("02");//不在此处，在归还收到服务器信息之后
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_05_ON, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_OFF, 10);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key05-back-illegal");
                        mLedCtrlHandler.postDelayed(playSoundKeyBackIllegal, 10);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_05_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_ON, 10);
                    }
                }
                ShellUtils.execCommand("echo off > /sys/class/gpio_switch/switch_ct_05", false);
            } else {
                boolean isLegal = getXmlKeyOutLegal();
                if (isLegal) {
                    //声音提示弹出
                    //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                    //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "05" + "3b" + "00" + "3b" + getTimeHexString()));
                    Log.e("====", "=========key05-normal-out-");
                    mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_05_OFF, 10);
                    setXmlKeyOutLegal(false);
                } else {
                    //  提示非法
                    Log.e("====", "=========key05-illegal-out-");
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                    mLedCtrlHandler.postDelayed(playSoundKeyOutIllegal, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_ON, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_OFF, 10);
                }
                //setXmlKeyOutLegal(false);
            }
        }
    };
    private final UEventObserver mBottomSwitch06Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch06Observer", "onUEvent.event.toString()=" + event.toString());
            isKey06BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_06_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_ON, 10);
            if (isKey06BottomOn) {
                //switchStatusCtrl(SWITCH_01_PATH, true);
                ShellUtils.execCommand("echo on > /sys/class/gpio_switch/switch_ct_06", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "06";
                Log.e("=====", "key06--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                //if (false) {
                //if (shopId.contains("ffffffffffffffffffff") || shopId.contains("fafafafafafafafafafa")) {//读到店面id
                if (shopId.contains("ffffffffffffffffffff")) {
                    //switchStatusCtrl(SWITCH_01_PATH, true);
                    Log.e("====", "===========key06reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mLedCtrlHandler.postDelayed(playSoundKeycommunicateErr, 200);
                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_06_MOTOR_ON, 1000);
                } else {
                    Log.e("====", "===key06=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key06=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        mLedCtrlHandler.postDelayed(playSoundKeyBackSucceed, 10);
                        //钥匙扣归还
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "06" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        Log.e("====", "===============key06-back");
                        //allKeysBatteryLevelWrite("02");//不在此处，在归还收到服务器信息之后
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_06_ON, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_OFF, 10);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key06-back-illegal");
                        mLedCtrlHandler.postDelayed(playSoundKeyBackIllegal, 10);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_06_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_ON, 10);
                    }
                }
                ShellUtils.execCommand("echo off > /sys/class/gpio_switch/switch_ct_06", false);
            } else {
                boolean isLegal = getXmlKeyOutLegal();
                if (isLegal) {
                    //声音提示弹出
                    //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                    //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "06" + "3b" + "00" + "3b" + getTimeHexString()));
                    Log.e("====", "=========key06-normal-out-");
                    mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_06_OFF, 10);
                    setXmlKeyOutLegal(false);
                } else {
                    //  提示非法
                    Log.e("====", "=========key06-illegal-out-");
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                    mLedCtrlHandler.postDelayed(playSoundKeyOutIllegal, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_ON, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_OFF, 20);
                }
                //setXmlKeyOutLegal(false);
            }
        }
    };
    private final UEventObserver mBottomSwitch07Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch07Observer", "onUEvent.event.toString()=" + event.toString());
            isKey07BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_07_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_ON, 10);
            if (isKey07BottomOn) {
                //switchStatusCtrl(SWITCH_01_PATH, true);
                ShellUtils.execCommand("echo on > /sys/class/gpio_switch/switch_ct_07", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "07";
                Log.e("=====", "key07--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                //if (false) {
                //if (shopId.contains("ffffffffffffffffffff") || shopId.contains("fafafafafafafafafafa")) {//读到店面id
                if (shopId.contains("ffffffffffffffffffff")) {
                    //switchStatusCtrl(SWITCH_01_PATH, true);
                    Log.e("====", "===========key07reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mLedCtrlHandler.postDelayed(playSoundKeycommunicateErr, 200);
                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_07_MOTOR_ON, 1000);
                } else {
                    Log.e("====", "===key07=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key07=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        mLedCtrlHandler.postDelayed(playSoundKeyBackSucceed, 10);
                        //钥匙扣归还
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "07" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        Log.e("====", "===============key07-back");
                        //allKeysBatteryLevelWrite("02");//不在此处，在归还收到服务器信息之后
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_07_ON, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_OFF, 10);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key07-back-illegal");
                        mLedCtrlHandler.postDelayed(playSoundKeyBackIllegal, 10);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_07_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_ON, 10);
                    }
                }
                ShellUtils.execCommand("echo off > /sys/class/gpio_switch/switch_ct_07", false);
            } else {
                boolean isLegal = getXmlKeyOutLegal();
                if (isLegal) {
                    //声音提示弹出
                    //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                    //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "07" + "3b" + "00" + "3b" + getTimeHexString()));
                    Log.e("====", "=========key07-normal-out-");
                    mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_07_OFF, 10);
                    setXmlKeyOutLegal(false);
                } else {
                    //  提示非法
                    Log.e("====", "=========key07-illegal-out-");
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                    mLedCtrlHandler.postDelayed(playSoundKeyOutIllegal, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_ON, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_OFF, 10);
                }
                //setXmlKeyOutLegal(false);
            }
        }
    };
    private final UEventObserver mBottomSwitch08Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch08Observer", "onUEvent.event.toString()=" + event.toString());
            isKey08BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_08_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_ON, 10);
            if (isKey08BottomOn) {
                //switchStatusCtrl(SWITCH_01_PATH, true);
                ShellUtils.execCommand("echo on > /sys/class/gpio_switch/switch_ct_08", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "08";
                Log.e("=====", "key08--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                //if (false) {
                //if (shopId.contains("ffffffffffffffffffff") || shopId.contains("fafafafafafafafafafa")) {//读到店面id
                if (shopId.contains("ffffffffffffffffffff")) {
                    //switchStatusCtrl(SWITCH_01_PATH, true);
                    Log.e("====", "===========key08reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mLedCtrlHandler.postDelayed(playSoundKeycommunicateErr, 200);
                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_08_MOTOR_ON, 1000);
                } else {
                    Log.e("====", "===key08=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key08=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        mLedCtrlHandler.postDelayed(playSoundKeyBackSucceed, 10);
                        //钥匙扣归还
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "08" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        Log.e("====", "===============key08-back");
                        //allKeysBatteryLevelWrite("02");//不在此处，在归还收到服务器信息之后
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_08_ON, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_OFF, 10);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key08-back-illegal");
                        mLedCtrlHandler.postDelayed(playSoundKeyBackIllegal, 10);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_08_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_ON, 10);
                    }
                }
                ShellUtils.execCommand("echo off > /sys/class/gpio_switch/switch_ct_08", false);
            } else {
                boolean isLegal = getXmlKeyOutLegal();
                if (isLegal) {
                    //声音提示弹出
                    //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                    //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "08" + "3b" + "00" + "3b" + getTimeHexString()));
                    Log.e("====", "=========key08-normal-out-");
                    mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_08_OFF, 10);
                    setXmlKeyOutLegal(false);
                } else {
                    //  提示非法
                    Log.e("====", "=========key08-illegal-out-");
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                    mLedCtrlHandler.postDelayed(playSoundKeyOutIllegal, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_ON, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_OFF, 10);
                }
                //setXmlKeyOutLegal(false);
            }
        }
    };
    private final UEventObserver mBottomSwitch09Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch09Observer", "onUEvent.event.toString()=" + event.toString());
            isKey09BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_09_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_ON, 10);
            if (isKey09BottomOn) {
                //switchStatusCtrl(SWITCH_01_PATH, true);
                ShellUtils.execCommand("echo on > /sys/class/gpio_switch/switch_ct_09", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "09";
                Log.e("=====", "key09--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                //if (false) {
                //if (shopId.contains("ffffffffffffffffffff") || shopId.contains("fafafafafafafafafafa")) {//读到店面id
                if (shopId.contains("ffffffffffffffffffff")) {
                    //switchStatusCtrl(SWITCH_01_PATH, true);
                    Log.e("====", "===========key09reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mLedCtrlHandler.postDelayed(playSoundKeycommunicateErr, 200);
                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_09_MOTOR_ON, 1000);
                } else {
                    Log.e("====", "===key09=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key09=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        mLedCtrlHandler.postDelayed(playSoundKeyBackSucceed, 10);
                        //钥匙扣归还
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "09" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        Log.e("====", "===============key09-back");
                        //allKeysBatteryLevelWrite("02");//不在此处，在归还收到服务器信息之后
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_09_ON, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_OFF, 10);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key09-back-illegal");
                        mLedCtrlHandler.postDelayed(playSoundKeyBackIllegal, 10);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_09_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_ON, 10);
                    }
                }
                ShellUtils.execCommand("echo off > /sys/class/gpio_switch/switch_ct_09", false);
            } else {
                boolean isLegal = getXmlKeyOutLegal();
                if (isLegal) {
                    //声音提示弹出
                    //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                    //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "09" + "3b" + "00" + "3b" + getTimeHexString()));
                    Log.e("====", "=========key09-normal-out-");
                    mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_09_OFF, 10);
                    setXmlKeyOutLegal(false);
                } else {
                    //  提示非法
                    Log.e("====", "=========key09-illegal-out-");
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                    mLedCtrlHandler.postDelayed(playSoundKeyOutIllegal, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_ON, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_OFF, 10);
                }
                //setXmlKeyOutLegal(false);
            }
        }
    };
    private final UEventObserver mBottomSwitch10Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch10Observer", "onUEvent.event.toString()=" + event.toString());
            isKey10BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_10_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_ON, 10);
            if (isKey10BottomOn) {
                //switchStatusCtrl(SWITCH_01_PATH, true);
                ShellUtils.execCommand("echo on > /sys/class/gpio_switch/switch_ct_10", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "10";
                Log.e("=====", "key10--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                //if (false) {
                //if (shopId.contains("ffffffffffffffffffff") || shopId.contains("fafafafafafafafafafa")) {//读到店面id
                if (shopId.contains("ffffffffffffffffffff")) {
                    //switchStatusCtrl(SWITCH_01_PATH, true);
                    Log.e("====", "===========key10reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mLedCtrlHandler.postDelayed(playSoundKeycommunicateErr, 200);
                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_10_MOTOR_ON, 1000);
                } else {
                    Log.e("====", "===key10=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key10=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        mLedCtrlHandler.postDelayed(playSoundKeyBackSucceed, 10);
                        //钥匙扣归还
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "10" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        Log.e("====", "===============key10-back");
                        //allKeysBatteryLevelWrite("02");//不在此处，在归还收到服务器信息之后
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_10_ON, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_OFF, 10);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key10-back-illegal");
                        mLedCtrlHandler.postDelayed(playSoundKeyBackIllegal, 10);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_10_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_ON, 10);
                    }
                }
                ShellUtils.execCommand("echo off > /sys/class/gpio_switch/switch_ct_10", false);
            } else {
                boolean isLegal = getXmlKeyOutLegal();
                if (isLegal) {
                    //声音提示弹出
                    //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                    //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "10" + "3b" + "00" + "3b" + getTimeHexString()));
                    Log.e("====", "=========key10-normal-out-");
                    mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_10_OFF, 10);
                    setXmlKeyOutLegal(false);
                } else {
                    //  提示非法
                    Log.e("====", "=========key10-illegal-out-");
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                    mLedCtrlHandler.postDelayed(playSoundKeyOutIllegal, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_ON, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_OFF, 10);
                }
                //setXmlKeyOutLegal(false);
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
        acquireWakeLock();
        mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        cancelAlarm();
        setAlarm();
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
        registerBatteryStatusReceiver();

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
        mMotorCtrlHandler = new MotorCtrlHandler();
        mLedCtrlHandler = new LedCtrlHandler();
        //绿灯带亮
        //ledSeriesCtrl(on);
        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 100);
    }

    public void acquireWakeLock() {
        if (mWakeLock == null) {
            mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartKey");
            if (mWakeLock != null) {
                mWakeLock.acquire();
            }
        }
    }

    public void releaseWakeLock() {
        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "TcpService.onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "TcpService.onDestroy");
        cancelAlarm();
        setAlarm();
        if (mUsbReceiver != null) {
            unregisterReceiver(mUsbReceiver);
        }
        unregisterPC2DroidReceiver();
        unregisterBatteryStatusReceiver();
        /*if (mNetworkChangeBroadcastReceiver != null) {
            //unregisterReceiver(mNetworkChangeBroadcastReceiver);
        }*/
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
        releaseWakeLock();
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
        Intent intent = new Intent("com.erobbing.tcpsmartkey.alarm");
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent, 0);
        //mAlarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtTime, five, pi);//api 19以后不准确
        mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtTime, pi);
    }

    /**
     * 取消心跳间隔
     */
    public void cancelAlarm() {
        Intent intent = new Intent("com.erobbing.tcpsmartkey.alarm");
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
                        //0：成功/确认；1：失败；2：消息有误；3：不支持；4：报警
                        //鉴权成功
                        //上报
                        switch (mBodyString.substring(8, 10)) {
                            case "00":
                                Log.e("====", "=============auth succeed");
                                //getXmlAuthState();
                                mLedCtrlHandler.postDelayed(playSoundBoxAuthSucceed, 10);
                                setXmlAuthState(true);
                                allKeysStatus();//钥匙孔状态上报
                                switch (currentKeyholeId) {
                                    case "01":
                                        //mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 10);
                                        //mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_OFF, 10);
                                        //mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_OFF, 10);
                                        //mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_01_ON, 10);
                                        break;
                                    case "02":
                                        break;
                                }
                                break;
                            case "01":
                                Log.e("====", "=============auth failed");
                                //需要清除一下之前保存的鉴权码？或者再次鉴权？
                                //服务器会直接断开连接，接下来怎么办？重新连接？
                                clearSavedAuthCode();
                                setXmlAuthState(false);
                                mLedCtrlHandler.postDelayed(playSoundBoxAuthFailed, 10);
                                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_OFF, 10);
                                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 10);
                                break;
                            case "02":
                                break;
                            case "03":
                                break;
                            case "04":
                                break;
                            case "05":
                                Log.e("====", "=============auth failed--unknown");
                                mLedCtrlHandler.postDelayed(playSoundBoxAuthFailed, 10);
                                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_ON, 10);
                                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_OFF, 10);
                                break;
                        }
                        mBodyRespType = "";
                    }
                    if ("0003".equals(mBodyRespType)) {
                        //注销
                        //上报
                        switch (mBodyString.substring(8, 10)) {
                            case "00":
                                Log.e("====", "=============unreg succeed");
                                clearSavedMsg();
                                mLedCtrlHandler.postDelayed(playSoundBoxUnregSucceed, 10);
                                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_ON, 10);
                                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_OFF, 10);
                                //send
                                break;
                            case "01":
                                Log.e("====", "=============unreg failed");
                                mLedCtrlHandler.postDelayed(playSoundBoxUnregFailed, 10);
                                break;
                            case "02":
                                break;
                            case "03":
                                break;
                            case "04":
                                break;
                            case "05":
                                Log.e("====", "=============unreg failed--unknown");
                                break;
                        }
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
                                //currentKeyId;
                                //currentKeyholeId
                                mLedCtrlHandler.postDelayed(playSoundKeyRegSucceed, 10);
                                Log.e("====", "====send to key--currentKeyholeId=" + currentKeyholeId + "----currentKeyId=" + currentKeyId);
                                ShellUtils.execCommand("echo on > /sys/class/gpio_switch/switch_ct_" + currentKeyholeId, false);
                                ShellUtils.execCommand("echo " + getSavedShopID() + " > /sys/bus/i2c/devices/6-005b/mcu/dm_id", false);
                                ShellUtils.execCommand("echo " + authCode + " > /sys/bus/i2c/devices/6-005b/mcu/se_code", false);
                                ShellUtils.execCommand("echo off > /sys/class/gpio_switch/switch_ct_" + currentKeyholeId, false);
                                saveKeyId("key_hole_" + currentKeyholeId, currentKeyId);
                                //allKeysAuthCodeWrite(authCode);
                                //allKeysShopIdWrite(getSavedShopID());
                                saveKeyAuthCode(authCode);
                                switch (currentKeyholeId) {
                                    case "01":
                                        switchStatusCtrl(SWITCH_01_PATH, false);
                                        break;
                                    case "02":
                                        switchStatusCtrl(SWITCH_02_PATH, false);
                                        break;
                                    case "03":
                                        switchStatusCtrl(SWITCH_03_PATH, false);
                                        break;
                                    case "04":
                                        switchStatusCtrl(SWITCH_04_PATH, false);
                                        break;
                                    case "05":
                                        switchStatusCtrl(SWITCH_05_PATH, false);
                                        break;
                                    case "06":
                                        switchStatusCtrl(SWITCH_06_PATH, false);
                                        break;
                                    case "07":
                                        switchStatusCtrl(SWITCH_07_PATH, false);
                                        break;
                                    case "08":
                                        switchStatusCtrl(SWITCH_08_PATH, false);
                                        break;
                                    case "09":
                                        switchStatusCtrl(SWITCH_09_PATH, false);
                                        break;
                                    case "10":
                                        switchStatusCtrl(SWITCH_10_PATH, false);
                                        break;
                                }
                            } else {
                                mLedCtrlHandler.postDelayed(playSoundBoxRegSucceed, 10);
                                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_OFF, 10);
                                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 10);
                                //箱鉴权
                                saveAuthCode(authCode);
                                try {
                                    Thread.sleep(1000);
                                } catch (Exception e) {

                                }
                                sendMessage(getAllBytes(0x0102, mHeadMsgSeqInt, mHeadPhoneNum, authCode));
                            }

                            Log.e("====", "=======MSG_TYPE_REG_RESPONSE--01=authCode=" + authCode);
                            //mLedCtrlHandler.postDelayed(playSoundKeyRegSucceed, 10);

                            break;
                        case "01":
                            //String savedAuthCode = getSavedAuthCode();
                            sendMessage(getAllBytes(0x0102, mHeadMsgSeqInt, mHeadPhoneNum, savedAuthCode));
                            mLedCtrlHandler.postDelayed(playSoundBoxReged, 10);
                            break;
                        case "02":
                            break;
                        case "03":
                            Log.e("====", "========MSG_TYPE_REG_RESPONSE--03=");
                            //sendMessage(getAllBytes(0x0102, mHeadMsgSeqInt, mHeadPhoneNum, savedAuthCode));
                            /*if (allKeysIdRead().contains("ffffffffffffffffffff")) {
                                Log.e("====", "=======03-key-save" + getSavedKeyAuthCode());
                                switchStatusCtrl(SWITCH_01_PATH, true);
                                allKeysAuthCodeWrite(getSavedKeyAuthCode());
                                allKeysShopIdWrite(getSavedShopID());
                                switchStatusCtrl(SWITCH_01_PATH, false);
                            }
                            mLedCtrlHandler.postDelayed(playSoundBoxRegFailed, 10);*/
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
                    //mDroidToKey04Handler.postDelayed(playSoundNotiRemove, 50);
                    //0x10 钥匙箱弹出钥匙扣
                    //String kkId = mBodyString.substring(2, 4);
                    switch (mBodyString.substring(0, 2)) {
                        case "01":
                            //ota
                            mLedCtrlHandler.postDelayed(playSoundBoxUpgrade, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_ON, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_OFF, 10);
                            break;
                        case "02":
                            //mDroidToKey02Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_02_MOTOR_ON, 100);
                            break;
                        case "03":
                            //mDroidToKey03Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_03_MOTOR_ON, 100);
                            break;
                        case "04":
                            //mDroidToKey04Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_MOTOR_ON, 100);
                            break;
                        case "05":
                            //mDroidToKey05Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_05_MOTOR_ON, 100);
                            break;
                        case "06":
                            //mDroidToKey06Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_06_MOTOR_ON, 100);
                            break;
                        case "07":
                            //mDroidToKey07Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_07_MOTOR_ON, 100);
                            break;
                        case "08":
                            //mDroidToKey08Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_08_MOTOR_ON, 100);
                            break;
                        case "09":
                            //mDroidToKey09Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_09_MOTOR_ON, 100);
                            break;
                        case "10":
                            //exam 10 3031 3b 303030303030303930393230
                            String kkId = HexStringUtils.convertASCIIHexToString(mBodyString.substring(2, 6));
                            //钥匙箱弹出钥匙扣。参数之间采用半角分号分隔。指令如下： “钥匙孔编号
                            //;钥匙扣ID” ，若某个参数无值，则放空
                            //mDroidToKey10Handler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_10_MOTOR_ON, 100);
                            String keyHoleId = HexStringUtils.convertASCIIHexToString(mBodyString.substring(2, 6));//7-8->3b(英文分号)
                            String keyId = HexStringUtils.convertASCIIHexToString(mBodyString.substring(8, 32));
                            setXmlKeyOutLegal(true);//合法弹出
                            Log.e("====", "======合法弹出-keyHoleId=" + keyHoleId + "---keyId=" + keyId);
                            mLedCtrlHandler.postDelayed(playSoundNotiRemove, 10);//请取走钥匙扣
                            //motor on
                            switch (keyHoleId) {
                                case "01":
                                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_01_MOTOR_ON, 100);
                                    break;
                                case "02":
                                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_02_MOTOR_ON, 100);
                                    break;
                                case "03":
                                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_03_MOTOR_ON, 100);
                                    break;
                                case "04":
                                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_MOTOR_ON, 100);
                                    break;
                                case "05":
                                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_05_MOTOR_ON, 100);
                                    break;
                                case "06":
                                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_06_MOTOR_ON, 100);
                                    break;
                                case "07":
                                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_07_MOTOR_ON, 100);
                                    break;
                                case "08":
                                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_08_MOTOR_ON, 100);
                                    break;
                                case "09":
                                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_09_MOTOR_ON, 100);
                                    break;
                                case "10":
                                    mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_10_MOTOR_ON, 100);
                                    break;
                            }
                            //mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                            break;
                        case "11":

                            break;
                        case "12":
                            break;
                        case "13":
                            //设置终端连接服务器地址。指令如下： “地址;端口号” ，若某个参数无值，则放空
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
                setXmlBoxPowerState("00");//00正常供电，01电池供电，02异常
                //start socket
                //context.startService(new Intent(context, TcpServer.class));
                //start here get provinceId  cityId  manufacturerId  terminalType?  shopId  terminalId
                //generate TerminalRegInfo
                //sendd();
                //Intent intent = new Intent(this, com.erobbing.adb_config_demo.sdk.service.SdkService.class);
                startService(new Intent(context, com.erobbing.adb_config_demo.sdk.service.SdkService.class));
                //registerReceiver(mPC2DroidReceiver, filter);
                //sendMessage("7E010000180199999999980018010006338888888877777777777777777777019999999998357E");
                if (getXmlAuthState()) {
                    //08--钥匙箱运行状态;钥匙箱电源状态;时间
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "08" + getXmlBoxWorkState() + "3b" + "00" + "3b" + getTimeHexString()));
                }
            } else {
                setXmlBoxPowerState("01");
                //stop socket
                //context.stopService(new Intent(context, TcpServer.class));
                //if (mPC2DroidReceiver != null) {
                //    unregisterReceiver(mPC2DroidReceiver);
                //}
                stopService(new Intent(context, com.erobbing.adb_config_demo.sdk.service.SdkService.class));
                if (getXmlAuthState()) {
                    //08--钥匙箱运行状态;钥匙箱电源状态;时间
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "08" + getXmlBoxWorkState() + "3b" + "01" + "3b" + getTimeHexString()));
                }
            }
        }
    };

    private void registerPC2DroidReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.erobbing.action.PC_TO_DROID");
        filter.addAction("com.erobbing.action.PC_TO_DROID_REG");
        filter.addAction("com.erobbing.action.PC_TO_DROID_UNREG");
        filter.addAction("com.erobbing.tcpsmartkey.alarm");
        filter.addAction("com.erobbing.action.ETHERNET_CHANGE");//yinqi add 20190220
        registerReceiver(mPC2DroidReceiver, filter);
    }

    private void unregisterPC2DroidReceiver() {
        if (mPC2DroidReceiver != null) {
            unregisterReceiver(mPC2DroidReceiver);
        }
    }

    private void registerBatteryStatusReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.erobbing.action.smartkey_battery_level");
        registerReceiver(mBatteryStatusReceiver, filter);
    }

    private void unregisterBatteryStatusReceiver() {
        if (mBatteryStatusReceiver != null) {
            unregisterReceiver(mBatteryStatusReceiver);
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

                //sendMessage(getAllBytes(0x0100, 0x0001, mBodyTerminalID, mBodyProvinceID + mBodyCityID + mBodyManufacturerID + mBodyShopID + mBodyTerminalID));
                //01 99 99 99 99 98
                //7E 0100 0018 019999999998 0018 0100 0633 88888888 77777777777777777777 019999999998 35 7E
                //sendMessage(getAllBytes(0x0100, 0x0018, tmpPhoneId, "0100" + "0633" + "88888888" + "77777777777777777777" + tmpPhoneId));
            }
            if ("com.erobbing.action.PC_TO_DROID_REG".equals(action)) {
                Log.e("====", "=======PC_TO_DROID_REG");
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
                //tmpPhoneId = terminalId;
                Log.d(TAG, "mPC2DroidReceiver.provinceId=" + provinceId
                        + " - cityId=" + cityId
                        + " - manufacturerId=" + manufacturerId
                        + " - shopId=" + shopId
                        + " - terminalId=" + terminalId);
                Log.e("====", "=======provinceID=" + HexStringUtils.intToHexStringProvinceAndCity(provinceId));
                Log.e("====", "=======cityID=" + HexStringUtils.intToHexStringProvinceAndCity(cityId));
                //sendMessage(getAllBytes(0x0100, 0x0001, mBodyTerminalID, mBodyProvinceID + mBodyCityID + mBodyManufacturerID + mBodyShopID + mBodyTerminalID));
                //01 99 99 99 99 98
                //7E 0100 0018 019999999998 0018 0100 0633 88888888 77777777777777777777 019999999998 35 7E
                //sendMessage(getAllBytes(0x0100, 0x0018, tmpPhoneId, HexStringUtils.intToHexStringProvinceAndCity(provinceId) + HexStringUtils.intToHexStringProvinceAndCity(cityId) + mBodyManufacturerID + mBodyShopID + tmpPhoneId));
                sendMessage(getAllBytes(0x0100, 0x0001, tmpPhoneId, "0100" + "0633" + "88888888" + "77777777777777777777" + tmpPhoneId));
            }
            if ("com.erobbing.action.PC_TO_DROID_UNREG".equals(action)) {
                Log.e("====", "=======PC_TO_DROID_UNREG");
                //0x0003;
                //body 00 shop change,01 demaged
                sendMessage(getAllBytes(0x0003, mHeadMsgSeqInt, tmpPhoneId, "00"));
            }
            if ("com.erobbing.tcpsmartkey.alarm".equals(action)) {
                Log.e("====", "==========heart alarm");
                cancelAlarm();
                if (getXmlAuthState()) {
                    sendMessage(getAllBytes(0x0002, mHeadMsgSeqInt, tmpPhoneId, ""));//心跳
                }
                setAlarm();
            } else if ("com.erobbing.action.ETHERNET_CHANGE".equals(action)) {
                String ip = intent.getStringExtra("ip");
                Log.e("====", "==========ethernet change action, ip:" + ip);
                if (ip != null) {
                    if (ip.contains("dhcp")) {
                        SystemService.start("ethernet_start");
                    } else if (ip.contains("stop")) {
                        SystemService.start("ethernet_stop");
                    }
                }
            }
        }
    };

    private BroadcastReceiver mBatteryStatusReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int batteryLevel = intent.getIntExtra("battery_level", 100);
            if ("com.erobbing.action.smartkey_battery_level".equals(action)) {
                Log.e("====", "=======smartkey_battery_level");
                if (batteryLevel == 3) {
                    //shutdown
                    setXmlAuthState(false);//清除鉴权登录状态
                } else {
                    //上报电量
                    sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "07" + getBatteryLevelHexString(batteryLevel) + "3b" + getTimeHexString()));
                }
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
            try {
                sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0c" + String.format("%02x", i + 1) + "3b" + "999999999999"));
                Thread.sleep(200L);
            } catch (Exception e) {
                e.printStackTrace();
            }
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

    public String getBatteryLevelHexString(int level) {
        return String.format("%02x", level);
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

    public void saveKeyId(String keyHoleId, String keyId) {
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(keyHoleId, keyId);
        editor.commit();
    }

    public void clearSavedKeyId(String keyHoleId) {
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(keyHoleId, "");
        editor.commit();
    }

    public void saveAuthCode(String code) {
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("auth_code", code);
        editor.commit();
    }

    public String getSavedAuthCode() {
        String authCode = "unknown";
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        authCode = sp.getString("auth_code", "unknown");
        return authCode;
    }

    public void saveKeyAuthCode(String code) {
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("auth_code_key", code);
        editor.commit();
    }

    public String getSavedKeyAuthCode() {
        String authCode = "unknown";
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        authCode = sp.getString("auth_code_key", "unknown");
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

    public void clearSavedMsg() {
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.clear();
        editor.commit();
    }

    public void clearSavedAuthCode() {
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("auth_code", "unknown");
        editor.commit();
    }

    public boolean getXmlAuthState() {
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        mAuthState = sp.getBoolean("auth_state", false);
        return mAuthState;
    }

    public void setXmlAuthState(boolean authed) {
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("auth_state", authed);
        editor.commit();
    }

    public boolean getXmlKeyOutLegal() {
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        mAuthState = sp.getBoolean("key_out_legal", false);
        return mAuthState;
    }

    public void setXmlKeyOutLegal(boolean isLegal) {
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("key_out_legal", isLegal);
        editor.commit();
    }

    public String getXmlBoxPowerState() {
        //00：正常电源供电 01：电池供电 02：异常
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        String state = sp.getString("box_power_state", "00");
        return state;
    }

    public void setXmlBoxPowerState(String state) {
        //00：正常电源供电 01：电池供电 02：异常
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("box_power_state", state);
        editor.commit();
    }

    public String getXmlBoxWorkState() {
        //00：正常运行 01：异常运行 02：开机中 03：关机中
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        String state = sp.getString("box_work_state", "00");
        return state;
    }

    public void setXmlBoxWorkState(String state) {
        //00：正常运行 01：异常运行 02：开机中 03：关机中
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("box_work_state", state);
        editor.commit();
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

    private String allKeysShopIdRead() {
        char[] buffer = new char[1024];

        String state = "";
        FileReader file = null;
        try {
            //fa-fa-fa...   default  ,key insert default  ff-ff-ff...
            //file = new FileReader("/sys/bus/i2c/devices/6-005b/mcu/se_code");
            file = new FileReader(RW_SHOP_ID_PATH);
            int len = file.read(buffer, 0, 1024);
            String oriString = String.valueOf((new String(buffer, 0, len)));
            //state = String.valueOf((new String(buffer, 0, len)));
            state = reGroupString(oriString);
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

    private String allKeysIdRead() {
        char[] buffer = new char[1024];

        String state = "";
        FileReader file = null;
        try {
            //fa-fa-fa-fa-fa-fa-fa-fa-fa-fa   default
            //file = new FileReader("/sys/bus/i2c/devices/6-005b/mcu/se_code");
            file = new FileReader(READ_KEY_ID_PATH);
            int len = file.read(buffer, 0, 1024);
            String oriString = String.valueOf((new String(buffer, 0, len)));
            //state = String.valueOf((new String(buffer, 0, len)));
            state = reGroupString(oriString);
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

    private String allKeysBatteryLevelRead() {
        char[] buffer = new char[1024];

        String state = "";
        FileReader file = null;
        try {
            //fa   default   有钥匙  ff
            file = new FileReader(RW_KEY_BATTERY_PATH);//0->charge,1->full
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

    public void allKeysBatteryLevelWrite(String cmd) {
        try {
            FileWriter command = new FileWriter(RW_KEY_BATTERY_PATH);
            switch (cmd) {
                case "00":
                    command.write(0);
                    break;
                case "01":
                    command.write(1);
                    break;
                case "02":
                    command.write(2);
                    break;
            }
            //command.write(cmd);//01,clear,02 key-back-normal
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void allKeysAuthCodeWrite(String cmd) {
        try {
            FileWriter command = new FileWriter(WRITE_AUTH_CODE_PATH);
            command.write(cmd);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void allKeysShopIdWrite(String cmd) {
        try {
            FileWriter command = new FileWriter(RW_SHOP_ID_PATH);
            command.write(cmd);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String reGroupString(String str) {
        //String str = "fa-a-fa-fa-a-fa-fa-fa-fa-fa";
        String[] strarray = str.split("-");
        String newString = "";
        for (int i = 0; i < strarray.length; i++) {
            Log.e("====", "=======strarray[]=" + strarray[i]);
            if (strarray[i] != null && strarray[i].length() < 2) {
                strarray[i] = "0" + strarray[i];
            }
            newString += strarray[i];
        }
        Log.e("====", "==========newString=" + newString);
        return newString;
    }

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

    /*private String allKeysIdRead() {
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
    }*/

    public void droidToKeysSendAuthCode(String code) {
        try {
            //SWITCH_04_PATH
            FileWriter command = new FileWriter(DROID_TO_KEYS_SEND_AUTH_PATH);
            command.write(code);
            //command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String droidToKeysReadAuthCode() {
        char[] buffer = new char[1024];

        String state = "";
        FileReader file = null;
        try {
            file = new FileReader(DROID_TO_KEYS_SEND_AUTH_PATH);
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

    private class MotorCtrlHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_KEY_01_MOTOR_ON:
                    motor01StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor01OffIn10S, 6 * 1000);
                    //mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_OFF, 10);
                    //mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_ON, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_01_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_ON, 10);
                    break;
                case HANDLER_MSG_KEY_02_MOTOR_ON:
                    motor02StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor02OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_03_MOTOR_ON:
                    motor03StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor03OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_04_MOTOR_ON:
                    motor04StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor04OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_05_MOTOR_ON:
                    motor05StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor05OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_06_MOTOR_ON:
                    motor06StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor06OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_07_MOTOR_ON:
                    motor07StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor07OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_08_MOTOR_ON:
                    motor08StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor08OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_09_MOTOR_ON:
                    motor09StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor09OffIn10S, 6 * 1000);
                    break;
                case HANDLER_MSG_KEY_10_MOTOR_ON:
                    motor10StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor10OffIn10S, 6 * 1000);
                    break;
            }
        }
    }

    private class LedCtrlHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_LED_SERIES_01_ON:
                    ledSeriesCtrl(LED_SERIES_01, true);
                    break;
                case HANDLER_MSG_LED_SERIES_01_OFF:
                    ledSeriesCtrl(LED_SERIES_01, false);
                    break;
                case HANDLER_MSG_LED_SERIES_02_ON:
                    ledSeriesCtrl(LED_SERIES_02, true);
                    break;
                case HANDLER_MSG_LED_SERIES_02_OFF:
                    ledSeriesCtrl(LED_SERIES_02, false);
                    break;
                case HANDLER_MSG_LED_RED_01_ON:
                    ledSeriesCtrl(LED_RED_01, true);
                    break;
                case HANDLER_MSG_LED_RED_01_OFF:
                    ledSeriesCtrl(LED_RED_01, false);
                    break;
                case HANDLER_MSG_LED_GREEN_01_ON:
                    ledSeriesCtrl(LED_GREEN_01, true);
                    break;
                case HANDLER_MSG_LED_GREEN_01_OFF:
                    ledSeriesCtrl(LED_GREEN_01, false);
                    break;
                case HANDLER_MSG_LED_RED_02_ON:
                    ledSeriesCtrl(LED_RED_02, true);
                    break;
                case HANDLER_MSG_LED_RED_02_OFF:
                    ledSeriesCtrl(LED_RED_02, false);
                    break;
                case HANDLER_MSG_LED_GREEN_02_ON:
                    ledSeriesCtrl(LED_GREEN_02, true);
                    break;
                case HANDLER_MSG_LED_GREEN_02_OFF:
                    ledSeriesCtrl(LED_GREEN_02, false);
                    break;
                case HANDLER_MSG_LED_RED_03_ON:
                    ledSeriesCtrl(LED_RED_03, true);
                    break;
                case HANDLER_MSG_LED_RED_03_OFF:
                    ledSeriesCtrl(LED_RED_03, false);
                    break;
                case HANDLER_MSG_LED_GREEN_03_ON:
                    ledSeriesCtrl(LED_GREEN_03, true);
                    break;
                case HANDLER_MSG_LED_GREEN_03_OFF:
                    ledSeriesCtrl(LED_GREEN_03, false);
                    break;
                case HANDLER_MSG_LED_RED_04_ON:
                    ledSeriesCtrl(LED_RED_04, true);
                    break;
                case HANDLER_MSG_LED_RED_04_OFF:
                    ledSeriesCtrl(LED_RED_04, false);
                    break;
                case HANDLER_MSG_LED_GREEN_04_ON:
                    ledSeriesCtrl(LED_GREEN_04, true);
                    break;
                case HANDLER_MSG_LED_GREEN_04_OFF:
                    ledSeriesCtrl(LED_GREEN_04, false);
                    break;
                case HANDLER_MSG_LED_RED_05_ON:
                    ledSeriesCtrl(LED_RED_05, true);
                    break;
                case HANDLER_MSG_LED_RED_05_OFF:
                    ledSeriesCtrl(LED_RED_05, false);
                    break;
                case HANDLER_MSG_LED_GREEN_05_ON:
                    ledSeriesCtrl(LED_GREEN_05, true);
                    break;
                case HANDLER_MSG_LED_GREEN_05_OFF:
                    ledSeriesCtrl(LED_GREEN_05, false);
                    break;
                case HANDLER_MSG_LED_RED_06_ON:
                    ledSeriesCtrl(LED_RED_06, true);
                    break;
                case HANDLER_MSG_LED_RED_06_OFF:
                    ledSeriesCtrl(LED_RED_06, false);
                    break;
                case HANDLER_MSG_LED_GREEN_06_ON:
                    ledSeriesCtrl(LED_GREEN_06, true);
                    break;
                case HANDLER_MSG_LED_GREEN_06_OFF:
                    ledSeriesCtrl(LED_GREEN_06, false);
                    break;
                case HANDLER_MSG_LED_RED_07_ON:
                    ledSeriesCtrl(LED_RED_07, true);
                    break;
                case HANDLER_MSG_LED_RED_07_OFF:
                    ledSeriesCtrl(LED_RED_07, false);
                    break;
                case HANDLER_MSG_LED_GREEN_07_ON:
                    ledSeriesCtrl(LED_GREEN_07, true);
                    break;
                case HANDLER_MSG_LED_GREEN_07_OFF:
                    ledSeriesCtrl(LED_GREEN_07, false);
                    break;
                case HANDLER_MSG_LED_RED_08_ON:
                    ledSeriesCtrl(LED_RED_08, true);
                    break;
                case HANDLER_MSG_LED_RED_08_OFF:
                    ledSeriesCtrl(LED_RED_08, false);
                    break;
                case HANDLER_MSG_LED_GREEN_08_ON:
                    ledSeriesCtrl(LED_GREEN_08, true);
                    break;
                case HANDLER_MSG_LED_GREEN_08_OFF:
                    ledSeriesCtrl(LED_GREEN_08, false);
                    break;
                case HANDLER_MSG_LED_RED_09_ON:
                    ledSeriesCtrl(LED_RED_09, true);
                    break;
                case HANDLER_MSG_LED_RED_09_OFF:
                    ledSeriesCtrl(LED_RED_09, false);
                    break;
                case HANDLER_MSG_LED_GREEN_09_ON:
                    ledSeriesCtrl(LED_GREEN_09, true);
                    break;
                case HANDLER_MSG_LED_GREEN_09_OFF:
                    ledSeriesCtrl(LED_GREEN_09, false);
                    break;
                case HANDLER_MSG_LED_RED_10_ON:
                    ledSeriesCtrl(LED_RED_10, true);
                    break;
                case HANDLER_MSG_LED_RED_10_OFF:
                    ledSeriesCtrl(LED_RED_10, false);
                    break;
                case HANDLER_MSG_LED_GREEN_10_ON:
                    ledSeriesCtrl(LED_GREEN_10, true);
                    break;
                case HANDLER_MSG_LED_GREEN_10_OFF:
                    ledSeriesCtrl(LED_GREEN_10, false);
                    break;
            }
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
                        //allKeysSendAuthCode("==============");
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

    private final Runnable playSoundKeyOutIllegal = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.key_out_illegal);
        }
    };

    private final Runnable playSoundKeyBackSucceed = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.key_back_succeed);
        }
    };

    private final Runnable playSoundKeyBackIllegal = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.key_back_illegal);
        }
    };

    private final Runnable playSoundNotiRemove = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.notify_key_remove);
        }
    };

    private final Runnable playSoundBoxAuthSucceed = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.box_auth_succeed);
        }
    };

    private final Runnable playSoundBoxAuthFailed = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.box_auth_failed);
        }
    };

    private final Runnable playSoundBoxRegSucceed = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.box_reg_succeed);
        }
    };

    private final Runnable playSoundBoxRegFailed = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.box_reg_faileded);
        }
    };

    private final Runnable playSoundBoxUnregSucceed = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.box_unreg_succeed);
        }
    };

    private final Runnable playSoundBoxUnregFailed = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.box_unreg_failed);
        }
    };

    private final Runnable playSoundBoxReged = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.box_reged);
        }
    };

    private final Runnable playSoundKeyRegSucceed = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.key_reg_succeed);
        }
    };

    private final Runnable playSoundKeyRegFailed = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.key_reg_failed);
        }
    };

    private final Runnable playSoundBoxUpgrade = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.box_upgrade);
        }
    };

    private final Runnable playSoundKeycommunicateErr = new Runnable() {
        public void run() {
            playFromRawFile(mContext, R.raw.key_communicate_err);
        }
    };

    private final Runnable mMotor01OffIn10S = new Runnable() {
        public void run() {
            motor01StatusCtrl(false);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_OFF, 10);
            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_01_ON, 10);
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

    //主动上报消息
    public void ActiveReport(int msgType, String msgCmd, int flowId, String terminalId, String bodyString) {
        //电量百分比
        //BYTE  电量百分比的整数值
        //钥匙箱运行状态 BYTE 0：正常运行 1：异常运行 2：开机中 3：关机中
        //钥匙箱电源状态
        //BYTE 0：正常电源供电 1：电池供电 2：异常
        //钥匙箱网络状态
        //BYTE 0：正常网络通信 1：2G 网络通信 2：异常
        //钥匙孔状态
        //BYTE  0：正常 1 充电异常 2 通信异常 3 电磁阀异常
        //钥匙扣ID  BCD[6]  钥匙扣ID
        //钥匙孔编号  BYTE
        //钥匙箱对应的钥匙孔编号
        //钥匙环状态  BYTE0：关闭 1 打开 2 异常
        switch (msgCmd) {
            case "01"://钥匙扣插入钥匙箱，钥匙箱主动上报平台。上报指令如下“钥匙孔编号;钥匙扣ID”
                break;
            case "05"://钥匙箱暴力破坏报警信息上报。上报指令如下：“时间”
                //getTimeHexString();
                break;
            case "07"://钥匙箱断电后备用电池的电量信息上报,每下降10%上报一次。电量百分比;时间
                break;
            case "08"://钥匙箱电源状态变化信息上报（开机或者关机也上报一次） 。钥匙箱运行状态;钥匙箱电源状态;时间
                break;
            case "09"://钥匙箱网络状态变化信息上报（开机或者关机也上报一次） 。钥匙箱运行状态;钥匙箱网络状态;时间
                break;
            case "0a"://钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次） 。钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                break;
            case "0c"://每次鉴权成功后，钥匙箱需要上报一下钥匙孔的状态。 钥匙孔编号;钥匙扣ID
                break;
        }
        sendMessage(getAllBytes(msgType, flowId, terminalId, bodyString));
    }

    private void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
