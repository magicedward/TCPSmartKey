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
import android.net.EthernetManager;
import android.net.IpConfiguration;
import android.net.IpConfiguration.IpAssignment;
import android.net.LinkAddress;
import android.net.NetworkInfo;
import android.net.StaticIpConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemService;
import android.os.UEventObserver;
import android.util.Log;
import android.widget.Toast;

import android.net.DhcpResults;
import android.net.EthernetManager;
//import android.net.IEthernetServiceListener;
import android.net.EthernetManager.Listener;
import android.net.InterfaceConfiguration;
import android.net.IpConfiguration;
import android.net.IpConfiguration.IpAssignment;
import android.net.IpConfiguration.ProxySettings;
import android.net.StaticIpConfiguration;
import android.net.LinkAddress;

import java.io.FileInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.lang.reflect.Constructor;

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
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static android.R.attr.level;
import static com.android.internal.R.id.hour;
import static com.android.internal.R.id.minute;
import static com.erobbing.tcpsmartkey.util.ShellUtils.execCommand;

/**
 * Created by zhangzhaolei on 2018/12/25.
 */

public class TcpService extends Service {
    private static final String TAG = "TcpService";
    private Context mContext;
    private AlarmManager mAlarmManager;
    private String mHeartFrequency = "60";
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
    private String mHeadPhoneNum = "019999999978";
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
    private String unRegKeyhole = "";

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
    private String tmpPhoneId = "019999999979";//019999999998
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
    private static final long LED_SLOW_BLINK_TIME = 3000;
    private static final long LED_FAST_BLINK_TIME = 200;
    private static final long LED_BLINK_SLEEP_TIME = 100;
    private int fastBlinkCount01 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
    private int fastBlinkCount02 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
    private int fastBlinkCount03 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
    private int fastBlinkCount04 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
    private int fastBlinkCount05 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
    private int fastBlinkCount06 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
    private int fastBlinkCount07 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
    private int fastBlinkCount08 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
    private int fastBlinkCount09 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
    private int fastBlinkCount10 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;

    private static final long KEY_BAT_NULL_CHECK_TIME = 6000;
    private static final int KEY_BAT_NULL_CHECK_COUNT = 3;
    private int mKey01BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
    private int mKey02BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
    private int mKey03BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
    private int mKey04BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
    private int mKey05BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
    private int mKey06BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
    private int mKey07BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
    private int mKey08BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
    private int mKey09BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
    private int mKey10BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;

    private static final long MOTOR_AUTO_OFF_TIME = 10 * 1000;

    private boolean isKey01On = false;
    private boolean isKey01MiddleOn = false;
    private boolean isKey01BottomOn = false;
    private boolean isKey01BottomStatus = false;

    private boolean isKey02On = false;
    private boolean isKey02MiddleOn = false;
    private boolean isKey02BottomOn = false;
    private boolean isKey02BottomStatus = false;

    private boolean isKey03On = false;
    private boolean isKey03MiddleOn = false;
    private boolean isKey03BottomOn = false;
    private boolean isKey03BottomStatus = false;

    private boolean isKey04On = false;
    private boolean isKey04MiddleOn = false;
    private boolean isKey04BottomOn = false;
    private boolean isKey04BottomStatus = false;

    private boolean isKey05On = false;
    private boolean isKey05MiddleOn = false;
    private boolean isKey05BottomOn = false;
    private boolean isKey05BottomStatus = false;

    private boolean isKey06On = false;
    private boolean isKey06MiddleOn = false;
    private boolean isKey06BottomOn = false;
    private boolean isKey06BottomStatus = false;

    private boolean isKey07On = false;
    private boolean isKey07MiddleOn = false;
    private boolean isKey07BottomOn = false;
    private boolean isKey07BottomStatus = false;

    private boolean isKey08On = false;
    private boolean isKey08MiddleOn = false;
    private boolean isKey08BottomOn = false;
    private boolean isKey08BottomStatus = false;

    private boolean isKey09On = false;
    private boolean isKey09MiddleOn = false;
    private boolean isKey09BottomOn = false;
    private boolean isKey09BottomStatus = false;

    private boolean isKey10On = false;
    private boolean isKey10MiddleOn = false;
    private boolean isKey10BottomOn = false;
    private boolean isKey10BottomStatus = false;

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

    private static final int HANDLER_MSG_TCP_DISCONNECT = 124;

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;

    private DroidToKey01Handler mDroidToKey01Handler;

    private MotorCtrlHandler mMotorCtrlHandler;
    private LedCtrlHandler mLedCtrlHandler;
    private NetworkStatusHandler mNetworkStatusHandler;
    private KeyPlugInHandler mKeyPlugInHandler;
    private KeyBatNullCheckHandler mKeyBatNullCheckHandler;

    ConnectivityManager mConnectivityManager;
    private boolean mConnected = false;
    private EthernetManager mEthernetManager;
    private boolean isKey01CommunicateErr = false;
    private boolean isKey02CommunicateErr = false;
    private boolean isKey03CommunicateErr = false;
    private boolean isKey04CommunicateErr = false;
    private boolean isKey05CommunicateErr = false;
    private boolean isKey06CommunicateErr = false;
    private boolean isKey07CommunicateErr = false;
    private boolean isKey08CommunicateErr = false;
    private boolean isKey09CommunicateErr = false;
    private boolean isKey10CommunicateErr = false;

    private boolean isKey01Illegal_back = false;
    private boolean isKey02Illegal_back = false;
    private boolean isKey03Illegal_back = false;
    private boolean isKey04Illegal_back = false;
    private boolean isKey05Illegal_back = false;
    private boolean isKey06Illegal_back = false;
    private boolean isKey07Illegal_back = false;
    private boolean isKey08Illegal_back = false;
    private boolean isKey09Illegal_back = false;
    private boolean isKey10Illegal_back = false;
    private boolean mEthernetConnected = false;
    private final Object mKeyCommunicationLock = new Object();
    private boolean isOTAUpgradeBegin = false;

    private final UEventObserver mMiddleSwitch01Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mMiddleSwitch01Observer", "onUEvent.event.toString()=" + event.toString());
            isKey01MiddleOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            if (isKey01MiddleOn) {

            } else {
                if (isKey01BottomStatus) {
                    mLedCtrlHandler.removeCallbacks(mGreenLed01fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed01SlowBlinkRunnable);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_01_OFF, 10);
                    boolean isLegal = getXmlKeyOutLegal();
                    if (isLegal) {
                        //声音提示弹出
                        //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                        //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "01" + "3b" + "00" + "3b" + getTimeHexString()));
                        Log.e("====", "=========key01-normal-out-");
                        //mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                        playVoice(R.string.tts_key_out);
                        setXmlKeyOutLegal(false);
                    } else {
                        //  提示非法
                        Log.e("====", "=========key01-illegal-out-");
                        if (!isKey01CommunicateErr && !isKey01Illegal_back) {
                            playVoice(R.string.tts_key_out_illegal);
                        }
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                        // TODO: 2019/2/28 上报服务器 done
                    }
                    isKey01CommunicateErr = false;
                    isKey01Illegal_back = false;
                    clearSavedKeyId("key_hole_01");
                }
                isKey01BottomStatus = false;
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

            } else {
                if (isKey02BottomStatus) {
                    mLedCtrlHandler.removeCallbacks(mGreenLed02fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed02SlowBlinkRunnable);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_02_OFF, 10);
                    boolean isLegal = getXmlKeyOutLegal();
                    if (isLegal) {
                        //声音提示弹出
                        //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                        //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "02" + "3b" + "00" + "3b" + getTimeHexString()));
                        Log.e("====", "=========key02-normal-out-");
                        //mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                        playVoice(R.string.tts_key_out);
                        setXmlKeyOutLegal(false);
                    } else {
                        //  提示非法
                        Log.e("====", "=========key02-illegal-out-");
                        if (!isKey02CommunicateErr && !isKey02Illegal_back) {
                            playVoice(R.string.tts_key_out_illegal);
                        }
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                        // TODO: 2019/2/28 上报服务器 done
                    }
                    isKey02CommunicateErr = false;
                    isKey02Illegal_back = false;
                    clearSavedKeyId("key_hole_02");
                }
                isKey02BottomStatus = false;
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

            } else {
                if (isKey03BottomStatus) {
                    mLedCtrlHandler.removeCallbacks(mGreenLed03fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed03SlowBlinkRunnable);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_03_OFF, 10);
                    boolean isLegal = getXmlKeyOutLegal();
                    if (isLegal) {
                        //声音提示弹出
                        //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                        //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "03" + "3b" + "00" + "3b" + getTimeHexString()));
                        Log.e("====", "=========key03-normal-out-");
                        //mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                        playVoice(R.string.tts_key_out);
                        setXmlKeyOutLegal(false);
                    } else {
                        //  提示非法
                        Log.e("====", "=========key03-illegal-out-");
                        if (!isKey03CommunicateErr && !isKey03Illegal_back) {
                            playVoice(R.string.tts_key_out_illegal);
                        }
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                        // TODO: 2019/2/28 上报服务器 done
                    }
                    isKey03CommunicateErr = false;
                    isKey03Illegal_back = false;
                    clearSavedKeyId("key_hole_03");
                }
                isKey03BottomStatus = false;
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

            } else {
                if (isKey04BottomStatus) {
                    mLedCtrlHandler.removeCallbacks(mGreenLed04fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed04SlowBlinkRunnable);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_04_OFF, 10);
                    boolean isLegal = getXmlKeyOutLegal();
                    if (isLegal) {
                        //声音提示弹出
                        //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                        //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "04" + "3b" + "00" + "3b" + getTimeHexString()));
                        Log.e("====", "=========key04-normal-out-");
                        //mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                        playVoice(R.string.tts_key_out);
                        setXmlKeyOutLegal(false);
                    } else {
                        //  提示非法
                        Log.e("====", "=========key04-illegal-out-");
                        if (!isKey04CommunicateErr && !isKey04Illegal_back) {
                            playVoice(R.string.tts_key_out_illegal);
                        }
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                        // TODO: 2019/2/28 上报服务器 done
                    }
                    isKey04CommunicateErr = false;
                    isKey04Illegal_back = false;
                    clearSavedKeyId("key_hole_04");
                }
                isKey04BottomStatus = false;
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

            } else {
                if (isKey05BottomStatus) {
                    mLedCtrlHandler.removeCallbacks(mGreenLed05fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed05SlowBlinkRunnable);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_05_OFF, 10);
                    boolean isLegal = getXmlKeyOutLegal();
                    if (isLegal) {
                        //声音提示弹出
                        //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                        //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "05" + "3b" + "00" + "3b" + getTimeHexString()));
                        Log.e("====", "=========key05-normal-out-");
                        //mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                        playVoice(R.string.tts_key_out);
                        setXmlKeyOutLegal(false);
                    } else {
                        //  提示非法
                        Log.e("====", "=========key05-illegal-out-");
                        if (!isKey05CommunicateErr && !isKey05Illegal_back) {
                            playVoice(R.string.tts_key_out_illegal);
                        }
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                        // TODO: 2019/2/28 上报服务器 done
                    }
                    isKey05CommunicateErr = false;
                    isKey05Illegal_back = false;
                    clearSavedKeyId("key_hole_05");
                }
                isKey05BottomStatus = false;
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

            } else {
                if (isKey06BottomStatus) {
                    mLedCtrlHandler.removeCallbacks(mGreenLed06fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed06SlowBlinkRunnable);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_06_OFF, 10);
                    boolean isLegal = getXmlKeyOutLegal();
                    if (isLegal) {
                        //声音提示弹出
                        //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                        //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "06" + "3b" + "00" + "3b" + getTimeHexString()));
                        Log.e("====", "=========key06-normal-out-");
                        //mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                        playVoice(R.string.tts_key_out);
                        setXmlKeyOutLegal(false);
                    } else {
                        //  提示非法
                        Log.e("====", "=========key06-illegal-out-");
                        if (!isKey06CommunicateErr && !isKey06Illegal_back) {
                            playVoice(R.string.tts_key_out_illegal);
                        }
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                        // TODO: 2019/2/28 上报服务器 done
                    }
                    isKey06CommunicateErr = false;
                    isKey06Illegal_back = false;
                    clearSavedKeyId("key_hole_06");
                }
                isKey06BottomStatus = false;
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

            } else {
                if (isKey07BottomStatus) {
                    mLedCtrlHandler.removeCallbacks(mGreenLed07fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed07SlowBlinkRunnable);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_07_OFF, 10);
                    boolean isLegal = getXmlKeyOutLegal();
                    if (isLegal) {
                        //声音提示弹出
                        //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                        //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "07" + "3b" + "00" + "3b" + getTimeHexString()));
                        Log.e("====", "=========key07-normal-out-");
                        //mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                        playVoice(R.string.tts_key_out);
                        setXmlKeyOutLegal(false);
                    } else {
                        //  提示非法
                        Log.e("====", "=========key07-illegal-out-");
                        if (!isKey07CommunicateErr && !isKey07Illegal_back) {
                            playVoice(R.string.tts_key_out_illegal);
                        }
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                        // TODO: 2019/2/28 上报服务器 done
                    }
                    isKey07CommunicateErr = false;
                    isKey07Illegal_back = false;
                    clearSavedKeyId("key_hole_07");
                }
                isKey07BottomStatus = false;
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

            } else {
                if (isKey08BottomStatus) {
                    mLedCtrlHandler.removeCallbacks(mGreenLed08fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed08SlowBlinkRunnable);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_08_OFF, 10);
                    boolean isLegal = getXmlKeyOutLegal();
                    if (isLegal) {
                        //声音提示弹出
                        //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                        //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "08" + "3b" + "00" + "3b" + getTimeHexString()));
                        Log.e("====", "=========key08-normal-out-");
                        //mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                        playVoice(R.string.tts_key_out);
                        setXmlKeyOutLegal(false);
                    } else {
                        //  提示非法
                        Log.e("====", "=========key08-illegal-out-");
                        if (!isKey08CommunicateErr && !isKey08Illegal_back) {
                            playVoice(R.string.tts_key_out_illegal);
                        }
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                        // TODO: 2019/2/28 上报服务器 done
                    }
                    isKey08CommunicateErr = false;
                    isKey08Illegal_back = false;
                    clearSavedKeyId("key_hole_08");
                }
                isKey08BottomStatus = false;
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

            } else {
                if (isKey09BottomStatus) {
                    mLedCtrlHandler.removeCallbacks(mGreenLed09fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed09SlowBlinkRunnable);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_09_OFF, 10);
                    boolean isLegal = getXmlKeyOutLegal();
                    if (isLegal) {
                        //声音提示弹出
                        //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                        //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "09" + "3b" + "00" + "3b" + getTimeHexString()));
                        Log.e("====", "=========key09-normal-out-");
                        //mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                        playVoice(R.string.tts_key_out);
                        setXmlKeyOutLegal(false);
                    } else {
                        //  提示非法
                        Log.e("====", "=========key09-illegal-out-");
                        if (!isKey09CommunicateErr && !isKey09Illegal_back) {
                            playVoice(R.string.tts_key_out_illegal);
                        }
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                        // TODO: 2019/2/28 上报服务器 done
                    }
                    isKey09CommunicateErr = false;
                    isKey09Illegal_back = false;
                    clearSavedKeyId("key_hole_09");
                }
                isKey09BottomStatus = false;
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

            } else {
                if (isKey10BottomStatus) {
                    mLedCtrlHandler.removeCallbacks(mGreenLed10fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed10SlowBlinkRunnable);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_10_OFF, 10);
                    boolean isLegal = getXmlKeyOutLegal();
                    if (isLegal) {
                        //声音提示弹出
                        //钥匙箱钥匙孔状态变化信息上报（开机或者关机也上报一次）,钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
                        //钥匙孔状态：0：正常 1 充电异常 2 通信异常 3 电磁阀异常
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + getXmlBoxWorkState() + "3b" + "10" + "3b" + "00" + "3b" + getTimeHexString()));
                        Log.e("====", "=========key10-normal-out-");
                        //mLedCtrlHandler.postDelayed(playSoundKeyOut, 10);
                        playVoice(R.string.tts_key_out);
                        setXmlKeyOutLegal(false);
                    } else {
                        //  提示非法
                        Log.e("====", "=========key10-illegal-out-");
                        if (!isKey10CommunicateErr && !isKey10Illegal_back) {
                            playVoice(R.string.tts_key_out_illegal);
                        }
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "05" + getTimeHexString()));
                        // TODO: 2019/2/28 上报服务器 done
                    }
                    isKey10CommunicateErr = false;
                    isKey10Illegal_back = false;
                    clearSavedKeyId("key_hole_10");
                }
                isKey10BottomStatus = false;
            }
        }
    };

    /////////////////////////////////////////////////////////////
    private final UEventObserver mBottomSwitch01Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch01Observer", "onUEvent.event.toString()=" + event.toString());
            isKey01BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            isKey01BottomStatus = true;
            if (isKey01BottomOn) {
                mKeyPlugInHandler.postDelayed(mKey01PlugInRunnable, 1500);
            } else {
                mKeyPlugInHandler.removeCallbacks(mKey01PlugInRunnable);
            }
        }
    };
    private final UEventObserver mBottomSwitch02Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch02Observer", "onUEvent.event.toString()=" + event.toString());
            isKey02BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            isKey02BottomStatus = true;
            if (isKey02BottomOn) {
                mKeyPlugInHandler.postDelayed(mKey02PlugInRunnable, 1500);
            } else {
                mKeyPlugInHandler.removeCallbacks(mKey02PlugInRunnable);
            }
        }
    };
    private final UEventObserver mBottomSwitch03Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch03Observer", "onUEvent.event.toString()=" + event.toString());
            isKey03BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            isKey03BottomStatus = true;
            if (isKey03BottomOn) {
                mKeyPlugInHandler.postDelayed(mKey03PlugInRunnable, 1500);
            } else {
                mKeyPlugInHandler.removeCallbacks(mKey03PlugInRunnable);
            }
        }
    };
    private final UEventObserver mBottomSwitch04Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch04Observer", "onUEvent.event.toString()=" + event.toString());
            isKey04BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            isKey04BottomStatus = true;
            if (isKey04BottomOn) {
                mKeyPlugInHandler.postDelayed(mKey04PlugInRunnable, 1500);
            } else {
                mKeyPlugInHandler.removeCallbacks(mKey04PlugInRunnable);
            }
        }
    };
    private final UEventObserver mBottomSwitch05Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch05Observer", "onUEvent.event.toString()=" + event.toString());
            isKey05BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            isKey05BottomStatus = true;
            if (isKey05BottomOn) {
                mKeyPlugInHandler.postDelayed(mKey05PlugInRunnable, 1500);
            } else {
                mKeyPlugInHandler.removeCallbacks(mKey05PlugInRunnable);
            }
        }
    };
    private final UEventObserver mBottomSwitch06Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch06Observer", "onUEvent.event.toString()=" + event.toString());
            isKey06BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            isKey06BottomStatus = true;
            if (isKey06BottomOn) {
                mKeyPlugInHandler.postDelayed(mKey06PlugInRunnable, 1500);
            } else {
                mKeyPlugInHandler.removeCallbacks(mKey06PlugInRunnable);
            }
        }
    };
    private final UEventObserver mBottomSwitch07Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch07Observer", "onUEvent.event.toString()=" + event.toString());
            isKey07BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            isKey07BottomStatus = true;
            if (isKey07BottomOn) {
                mKeyPlugInHandler.postDelayed(mKey07PlugInRunnable, 1500);
            } else {
                mKeyPlugInHandler.removeCallbacks(mKey07PlugInRunnable);
            }
        }
    };
    private final UEventObserver mBottomSwitch08Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch08Observer", "onUEvent.event.toString()=" + event.toString());
            isKey08BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            isKey08BottomStatus = true;
            if (isKey08BottomOn) {
                mKeyPlugInHandler.postDelayed(mKey08PlugInRunnable, 1500);
            } else {
                mKeyPlugInHandler.removeCallbacks(mKey08PlugInRunnable);
            }
        }
    };
    private final UEventObserver mBottomSwitch09Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch09Observer", "onUEvent.event.toString()=" + event.toString());
            isKey09BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            isKey09BottomStatus = true;
            if (isKey09BottomOn) {
                mKeyPlugInHandler.postDelayed(mKey09PlugInRunnable, 1500);
            } else {
                mKeyPlugInHandler.removeCallbacks(mKey09PlugInRunnable);
            }
        }
    };
    private final UEventObserver mBottomSwitch10Observer = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Log.d("mBottomSwitch10Observer", "onUEvent.event.toString()=" + event.toString());
            isKey10BottomOn = "0".equals(event.get("SWITCH_STATE"));//按下是0，弹起是1
            isKey10BottomStatus = true;
            if (isKey10BottomOn) {
                mKeyPlugInHandler.postDelayed(mKey10PlugInRunnable, 1500);
            } else {
                mKeyPlugInHandler.removeCallbacks(mKey10PlugInRunnable);
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
        tmpPhoneId = getSavedBoxID();
        acquireWakeLock();
        mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        cancelAlarm();
        setAlarm();
        startTTS();
        //connect(IP, PORT);
        TcpClient.init().setDisconnectedCallback(new TcpClient.OnServerDisconnectedCallbackBlock() {
            @Override
            public void callback(IOException e) {
                Log.e("====", "=========service=断开连接" + "\n");
                mConnected = false;
                if (!isOTAUpgradeBegin) {
                    playVoice(R.string.tts_network_disconnected);
                }
                //mLedCtrlHandler.removeCallbacks(mGreenLed01fastBlinkRunnable);
            }
        });
        TcpClient.init().setConnectedCallback(new TcpClient.OnServerConnectedCallbackBlock() {
            @Override
            public void callback() {
                Log.e("====", "=========service=连接成功" + "\n");
                mConnected = true;
                playVoice(R.string.tts_network_connected);
                if (getSavedAuthCode().length() == 20) {
                    sleep(1500);
                    sendMessage(getAllBytes(0x0102, mHeadMsgSeqInt, tmpPhoneId, getSavedAuthCode()));
                }
                //mLedCtrlHandler.postDelayed(mGreenLed01fastBlinkRunnable, LED_FAST_BLINK_TIME);
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
        registerNetworkReceiver();
        registerTimeReceiver();

        //IntentFilter networkFilter = new IntentFilter();
        //networkFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        //registerReceiver(mNetworkChangeBroadcastReceiver, networkFilter);

        if (new File(KEY_INT01_PATH).exists()) {
            mMiddleSwitch01Observer.startObserving(KEY_INT01_STATE_MATCH);
        }
        if (new File(KEY_INT02_PATH).exists()) {
            mMiddleSwitch02Observer.startObserving(KEY_INT02_STATE_MATCH);
        }
        if (new File(KEY_INT03_PATH).exists()) {
            mMiddleSwitch03Observer.startObserving(KEY_INT03_STATE_MATCH);
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
            mMiddleSwitch07Observer.startObserving(KEY_INT07_STATE_MATCH);
        }
        if (new File(KEY_INT08_PATH).exists()) {
            mMiddleSwitch08Observer.startObserving(KEY_INT08_STATE_MATCH);
        }
        if (new File(KEY_INT09_PATH).exists()) {
            //mMiddleSwitch09Observer.startObserving(KEY_INT09_STATE_MATCH);
        }
        if (new File(KEY_INT10_PATH).exists()) {
            mMiddleSwitch10Observer.startObserving(KEY_INT10_STATE_MATCH);
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
            //mBottomSwitch04Observer.startObserving(KEY0_INT_04_STATE_MATCH);
        }
        if (new File(KEY0_INT_05_PATH).exists()) {
            //mBottomSwitch05Observer.startObserving(KEY0_INT_05_STATE_MATCH);
        }
        if (new File(KEY0_INT_06_PATH).exists()) {
            //mBottomSwitch06Observer.startObserving(KEY0_INT_06_STATE_MATCH);
        }
        if (new File(KEY0_INT_07_PATH).exists()) {
            mBottomSwitch07Observer.startObserving(KEY0_INT_07_STATE_MATCH);
        }
        if (new File(KEY0_INT_08_PATH).exists()) {
            mBottomSwitch08Observer.startObserving(KEY0_INT_08_STATE_MATCH);
        }
        if (new File(KEY0_INT_09_PATH).exists()) {
            //mBottomSwitch09Observer.startObserving(KEY0_INT_09_STATE_MATCH);
        }
        if (new File(KEY0_INT_10_PATH).exists()) {
            mBottomSwitch10Observer.startObserving(KEY0_INT_10_STATE_MATCH);
        }
        mDroidToKey01Handler = new DroidToKey01Handler();
        mMotorCtrlHandler = new MotorCtrlHandler();
        mLedCtrlHandler = new LedCtrlHandler();
        mNetworkStatusHandler = new NetworkStatusHandler();
        mKeyPlugInHandler = new KeyPlugInHandler();
        mKeyBatNullCheckHandler = new KeyBatNullCheckHandler();
        //绿灯带亮
        //ledSeriesCtrl(on);
        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 100);

        mEthernetManager = (EthernetManager) getSystemService("ethernet");
        if (null != mEthernetManager) {
            mEthernetManager.addListener(mEthernetLisener);
        }
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
        unRegisterNetworkReceiver();
        unRegisterTimeReceiver();
        mMiddleSwitch01Observer.stopObserving();
        mMiddleSwitch02Observer.stopObserving();
        mMiddleSwitch03Observer.stopObserving();
        mMiddleSwitch04Observer.stopObserving();
        mMiddleSwitch05Observer.stopObserving();
        mMiddleSwitch06Observer.stopObserving();
        mMiddleSwitch07Observer.stopObserving();
        mMiddleSwitch08Observer.stopObserving();
        mMiddleSwitch09Observer.stopObserving();
        mMiddleSwitch10Observer.stopObserving();
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
            //mConnected = TcpClient.init().isConnected();
        }
    }

    public void disconnect() {
        TcpClient.init().disconnect();
        //mConnected = false;
    }

    /**
     * 设定心跳间隔
     */
    public void setAlarm() {
        long triggerAtTime = SystemClock.elapsedRealtime() + Long.valueOf(mHeartFrequency) * 1000;
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
                                //mLedCtrlHandler.postDelayed(playSoundBoxAuthSucceed, 10);
                                playVoice(R.string.tts_box_auth_succeed);
                                setXmlAuthState(true);
                                //上报一次电池电量
                                sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "07" + getBatteryLevelHexString(getCurrentBoxBatteryCapacity()) + "3b" + getTimeHexString()));
                                sleep(200);
                                allKeysStatus();//钥匙孔状态上报
                                sleep(200);

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
                                //mLedCtrlHandler.postDelayed(playSoundBoxAuthFailed, 10);
                                playVoice(R.string.tts_box_auth_failed);
                                //mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_OFF, 10);
                                //mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 10);
                                break;
                            case "02":
                                break;
                            case "03":
                                break;
                            case "04":
                                break;
                            case "05":
                                Log.e("====", "=============auth failed--unknown");
                                //mLedCtrlHandler.postDelayed(playSoundBoxAuthFailed, 10);
                                playVoice(R.string.tts_box_auth_failed);
                                setXmlAuthState(false);
                                //mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_ON, 10);
                                //mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_OFF, 10);
                                break;
                        }
                        mBodyRespType = "";
                    }
                    if ("0002".equals(mBodyRespType)) {
                        switch (mBodyString.substring(8, 10)) {
                            case "00":
                                Log.e("====", "=============heart response succeed");
                                break;
                            case "01":
                                Log.e("====", "=============heart response failed");
                                break;
                            case "02":
                                break;
                            case "03":
                                break;
                            case "04":
                                break;
                        }
                    }
                    if ("0003".equals(mBodyRespType)) {
                        //箱子注销
                        //上报
                        switch (mBodyString.substring(8, 10)) {
                            case "00":
                                Log.e("====", "=============unreg succeed");
                                clearSavedMsg();
                                //mLedCtrlHandler.postDelayed(playSoundBoxUnregSucceed, 10);
                                playVoice(R.string.tts_box_unreg_succeed);
                                //mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_ON, 10);
                                //mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_OFF, 10);
                                mNetworkStatusHandler.sendEmptyMessageDelayed(HANDLER_MSG_TCP_DISCONNECT, 2000);
                                // TODO: 2019/2/26 done
                                break;
                            case "01":
                                Log.e("====", "=============unreg failed");
                                //mLedCtrlHandler.postDelayed(playSoundBoxUnregFailed, 10);
                                playVoice(R.string.tts_box_unreg_failed);
                                break;
                            case "02":
                                break;
                            case "03":
                                break;
                            case "04":
                                break;
                            case "05":
                                Log.e("====", "=============unreg failed--unknown");
                                playVoice(R.string.tts_box_unreg_failed);
                                break;
                        }
                        mBodyRespType = "";
                    }
                    if ("0004".equals(mBodyRespType)) {
                        //扣注销
                        switch (mBodyString.substring(8, 10)) {
                            case "00":
                                setXmlKeyOutLegal(true);//合法弹出
                                String unRegedKeyId = getSavedKeyId(unRegKeyhole);
                                Log.e("====", "=============key unreg succeed---unRegKeyhole=" + unRegKeyhole + "----unRegedKeyId=" + unRegedKeyId);
                                //清空key的鉴权码，店面id
                                boolean eraseSucceed = eraseKeyInfo(unRegKeyhole);
                                if (eraseSucceed) {
                                    Log.e("====", "======注销弹出-unRegKeyhole=" + unRegKeyhole + "---unregkeyId=" + getSavedKeyId(unRegKeyhole));
                                    playVoice(String.format(getResources().getString(R.string.tts_key_unreg_succeed), unRegKeyhole));
                                    switch (unRegKeyhole) {
                                        case "01":
                                            mLedCtrlHandler.removeCallbacks(mGreenLed01SlowBlinkRunnable);
                                            mLedCtrlHandler.removeCallbacks(mGreenLed01fastBlinkRunnable);
                                            mLedCtrlHandler.postDelayed(mGreenLed01fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                            break;
                                        case "02":
                                            mLedCtrlHandler.removeCallbacks(mGreenLed02SlowBlinkRunnable);
                                            mLedCtrlHandler.removeCallbacks(mGreenLed02fastBlinkRunnable);
                                            mLedCtrlHandler.postDelayed(mGreenLed02fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                            break;
                                        case "03":
                                            mLedCtrlHandler.removeCallbacks(mGreenLed03SlowBlinkRunnable);
                                            mLedCtrlHandler.removeCallbacks(mGreenLed03fastBlinkRunnable);
                                            mLedCtrlHandler.postDelayed(mGreenLed03fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                            break;
                                        case "04":
                                            mLedCtrlHandler.removeCallbacks(mGreenLed04SlowBlinkRunnable);
                                            mLedCtrlHandler.removeCallbacks(mGreenLed04fastBlinkRunnable);
                                            mLedCtrlHandler.postDelayed(mGreenLed04fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                            break;
                                        case "05":
                                            mLedCtrlHandler.removeCallbacks(mGreenLed05SlowBlinkRunnable);
                                            mLedCtrlHandler.removeCallbacks(mGreenLed05fastBlinkRunnable);
                                            mLedCtrlHandler.postDelayed(mGreenLed05fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                            break;
                                        case "06":
                                            mLedCtrlHandler.removeCallbacks(mGreenLed06SlowBlinkRunnable);
                                            mLedCtrlHandler.removeCallbacks(mGreenLed06fastBlinkRunnable);
                                            mLedCtrlHandler.postDelayed(mGreenLed06fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                            break;
                                        case "07":
                                            mLedCtrlHandler.removeCallbacks(mGreenLed07SlowBlinkRunnable);
                                            mLedCtrlHandler.removeCallbacks(mGreenLed07fastBlinkRunnable);
                                            mLedCtrlHandler.postDelayed(mGreenLed07fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                            break;
                                        case "08":
                                            mLedCtrlHandler.removeCallbacks(mGreenLed08SlowBlinkRunnable);
                                            mLedCtrlHandler.removeCallbacks(mGreenLed08fastBlinkRunnable);
                                            mLedCtrlHandler.postDelayed(mGreenLed08fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                            break;
                                        case "09":
                                            mLedCtrlHandler.removeCallbacks(mGreenLed09SlowBlinkRunnable);
                                            mLedCtrlHandler.removeCallbacks(mGreenLed09fastBlinkRunnable);
                                            mLedCtrlHandler.postDelayed(mGreenLed09fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                            break;
                                        case "10":
                                            mLedCtrlHandler.removeCallbacks(mGreenLed10SlowBlinkRunnable);
                                            mLedCtrlHandler.removeCallbacks(mGreenLed10fastBlinkRunnable);
                                            mLedCtrlHandler.postDelayed(mGreenLed10fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                            break;
                                    }
                                    keyOutMotorOn(unRegKeyhole);
                                } else {
                                    playVoice(R.string.tts_key_erase_failed);
                                    Log.e("====", "============tts_key_erase_failed");
                                }
                                break;
                            case "01":
                                Log.e("====", "=============key unreg failed");
                                playVoice(R.string.tts_key_unreg_failed);
                                break;
                            case "02":
                                break;
                            case "03":
                                break;
                            case "04":
                                break;
                            case "05":
                                Log.e("====", "=============key unreg failed--unknown");
                                playVoice(R.string.tts_key_unreg_failed);
                                break;
                        }
                        mBodyRespType = "";
                        unRegKeyhole = "";
                    }
                    if ("0817".equals(mBodyRespType)) {
                        switch (mBodyString.substring(8, 10)) {
                            case "00":
                                Log.e("====", "=============0817 response succeed");
                                break;
                            case "01":
                                Log.e("====", "=============0817 response failed");
                                break;
                            case "02":
                                break;
                            case "03":
                                break;
                            case "04":
                                break;
                        }
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
                    //boolean hasAuthCode = ("".equals(savedAuthCode) || "unknown".equals(savedAuthCode)) ? false : true;
                    boolean hasAuthCode = (savedAuthCode.length() >= 20);
                    switch (result) {
                        case "00":
                            String authCode = mBodyString.substring(6, 26);
                            if (hasAuthCode) {
                                //扣鉴权
                                //发送鉴权码给扣
                                //currentKeyId;
                                //currentKeyholeId
                                //mLedCtrlHandler.postDelayed(playSoundKeyRegSucceed, 10);
                                playVoice(R.string.tts_key_reg_succeed);
                                Log.e("====", "====send to key--currentKeyholeId=" + currentKeyholeId + "----currentKeyId=" + currentKeyId);
                                synchronized (mKeyCommunicationLock) {
                                    execCommand("echo on > /sys/class/gpio_switch/switch_ct_" + currentKeyholeId, false);
                                    execCommand("echo " + getSavedShopID() + " > /sys/bus/i2c/devices/6-005b/mcu/dm_id", false);
                                    execCommand("echo " + authCode + " > /sys/bus/i2c/devices/6-005b/mcu/se_code", false);
                                    execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                                    execCommand("echo off > /sys/class/gpio_switch/switch_ct_" + currentKeyholeId, false);
                                }
                                saveKeyAuthCode(authCode);
                                /*switch (currentKeyholeId) {
                                    case "01":
                                        //switchStatusCtrl(SWITCH_01_PATH, false);
                                        //mLedCtrlHandler.postDelayed(mGreenLed01SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
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
                                }*/
                            } else {
                                //mLedCtrlHandler.postDelayed(playSoundBoxRegSucceed, 10);
                                playVoice(R.string.tts_box_reg_succeed);
                                //mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_01_OFF, 10);
                                //mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_SERIES_02_ON, 10);
                                //箱鉴权
                                saveAuthCode(authCode);
                                sleep(1000);
                                sendMessage(getAllBytes(0x0102, mHeadMsgSeqInt, tmpPhoneId, authCode));
                            }

                            Log.e("====", "=======MSG_TYPE_REG_RESPONSE--01=authCode=" + authCode);
                            //mLedCtrlHandler.postDelayed(playSoundKeyRegSucceed, 10);

                            break;
                        case "01":
                            //String savedAuthCode = getSavedAuthCode();
                            sleep(1000);
                            //sendMessage(getAllBytes(0x0102, mHeadMsgSeqInt, tmpPhoneId, savedAuthCode));
                            //mLedCtrlHandler.postDelayed(playSoundBoxReged, 10);
                            if (hasAuthCode) {
                                playVoice(R.string.tts_key_reged);
                            } else {
                                playVoice(R.string.tts_box_reged);
                            }
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
                            //mLedCtrlHandler.postDelayed(playSoundBoxUpgrade, 10);
                            playVoice(R.string.tts_box_upgrade);
                            sleep(2000);
                            Intent otaIntent = new Intent("com.qualcomm.update.start_update_after");
                            otaIntent.putExtra("start", true);
                            mContext.sendBroadcast(otaIntent);
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
                            //playVoice(String.format(getResources().getString(R.string.tts_notify_key_remove), keyHoleId));
                            //motor on
                            //keyOutMotorOn(keyHoleId);
                            switch (keyHoleId) {
                                case "01":
                                    if (isKey01MiddleOn) {
                                        playVoice(String.format(getResources().getString(R.string.tts_notify_key_remove), "01"));
                                        keyOutMotorOn("01");
                                        mLedCtrlHandler.removeCallbacks(mGreenLed01SlowBlinkRunnable);
                                        mLedCtrlHandler.removeCallbacks(mGreenLed01fastBlinkRunnable);
                                        mLedCtrlHandler.postDelayed(mGreenLed01fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                    }
                                    break;
                                case "02":
                                    if (isKey02MiddleOn) {
                                        playVoice(String.format(getResources().getString(R.string.tts_notify_key_remove), "02"));
                                        keyOutMotorOn("02");
                                        mLedCtrlHandler.removeCallbacks(mGreenLed02SlowBlinkRunnable);
                                        mLedCtrlHandler.removeCallbacks(mGreenLed02fastBlinkRunnable);
                                        mLedCtrlHandler.postDelayed(mGreenLed02fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                    }
                                    break;
                                case "03":
                                    if (isKey03MiddleOn) {
                                        playVoice(String.format(getResources().getString(R.string.tts_notify_key_remove), "03"));
                                        keyOutMotorOn("03");
                                        mLedCtrlHandler.removeCallbacks(mGreenLed03SlowBlinkRunnable);
                                        mLedCtrlHandler.removeCallbacks(mGreenLed03fastBlinkRunnable);
                                        mLedCtrlHandler.postDelayed(mGreenLed03fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                    }
                                    break;
                                case "04":
                                    if (isKey04MiddleOn) {
                                        playVoice(String.format(getResources().getString(R.string.tts_notify_key_remove), "04"));
                                        keyOutMotorOn("04");
                                        mLedCtrlHandler.removeCallbacks(mGreenLed04SlowBlinkRunnable);
                                        mLedCtrlHandler.removeCallbacks(mGreenLed04fastBlinkRunnable);
                                        mLedCtrlHandler.postDelayed(mGreenLed04fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                    }
                                    break;
                                case "05":
                                    if (isKey05MiddleOn) {
                                        playVoice(String.format(getResources().getString(R.string.tts_notify_key_remove), "05"));
                                        keyOutMotorOn("05");
                                        mLedCtrlHandler.removeCallbacks(mGreenLed05SlowBlinkRunnable);
                                        mLedCtrlHandler.removeCallbacks(mGreenLed05fastBlinkRunnable);
                                        mLedCtrlHandler.postDelayed(mGreenLed05fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                    }
                                    break;
                                case "06":
                                    if (isKey06MiddleOn) {
                                        playVoice(String.format(getResources().getString(R.string.tts_notify_key_remove), "06"));
                                        keyOutMotorOn("06");
                                        mLedCtrlHandler.removeCallbacks(mGreenLed06SlowBlinkRunnable);
                                        mLedCtrlHandler.removeCallbacks(mGreenLed06fastBlinkRunnable);
                                        mLedCtrlHandler.postDelayed(mGreenLed06fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                    }
                                    break;
                                case "07":
                                    if (isKey07MiddleOn) {
                                        playVoice(String.format(getResources().getString(R.string.tts_notify_key_remove), "07"));
                                        keyOutMotorOn("07");
                                        mLedCtrlHandler.removeCallbacks(mGreenLed07SlowBlinkRunnable);
                                        mLedCtrlHandler.removeCallbacks(mGreenLed07fastBlinkRunnable);
                                        mLedCtrlHandler.postDelayed(mGreenLed07fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                    }
                                    break;
                                case "08":
                                    if (isKey08MiddleOn) {
                                        playVoice(String.format(getResources().getString(R.string.tts_notify_key_remove), "08"));
                                        keyOutMotorOn("08");
                                        mLedCtrlHandler.removeCallbacks(mGreenLed08SlowBlinkRunnable);
                                        mLedCtrlHandler.removeCallbacks(mGreenLed08fastBlinkRunnable);
                                        mLedCtrlHandler.postDelayed(mGreenLed08fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                    }
                                    break;
                                case "09":
                                    if (isKey09MiddleOn) {
                                        playVoice(String.format(getResources().getString(R.string.tts_notify_key_remove), "09"));
                                        keyOutMotorOn("09");
                                        mLedCtrlHandler.removeCallbacks(mGreenLed09SlowBlinkRunnable);
                                        mLedCtrlHandler.removeCallbacks(mGreenLed09fastBlinkRunnable);
                                        mLedCtrlHandler.postDelayed(mGreenLed09fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                    }
                                    break;
                                case "10":
                                    if (isKey10MiddleOn) {
                                        playVoice(String.format(getResources().getString(R.string.tts_notify_key_remove), "10"));
                                        keyOutMotorOn("10");
                                        mLedCtrlHandler.removeCallbacks(mGreenLed10SlowBlinkRunnable);
                                        mLedCtrlHandler.removeCallbacks(mGreenLed10fastBlinkRunnable);
                                        mLedCtrlHandler.postDelayed(mGreenLed10fastBlinkRunnable, LED_FAST_BLINK_TIME);
                                    }
                                    break;
                            }
                            /*switch (keyHoleId) {
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
                            }*/
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

    private BroadcastReceiver mTimeChangeBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_TIME_TICK.equals(action)) {
                Calendar calendar = Calendar.getInstance();
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                int minute = calendar.get(Calendar.MINUTE);
                int second = calendar.get(Calendar.SECOND);
                Log.e("====", "=======ACTION_TIME_TICK.hour=" + hour + "--minute=" + minute + "--second=" + second);
                if (getXmlAuthState()) {
                    if (minute % 10 == 0) {
                        allKeysStatusPowerOnOff();
                    }
                }
            }
        }
    };

    BroadcastReceiver mNetworkChangeBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e("====", "=======mNetworkChangeBroadcastReceiver.action=" + action);
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(
                        Context.CONNECTIVITY_SERVICE);
                NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                if (networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()) {
                    int type2 = networkInfo.getType();
                    String typeName = networkInfo.getTypeName();//MOBILE,WIFI,ETHERNET
                    Log.e("====", "============typeName=" + typeName);
                    Log.e("====", "============type2=" + type2);
                    switch (type2) {
                        case ConnectivityManager.TYPE_MOBILE://移动 网络    2G 3G 4G 都是一样的 实测 mix2s 联通卡
                            Log.d("====", "TYPE_MOBILE=有网络");
                            mNetworkStatusHandler.removeCallbacks(mNetworkOnRunnable);
                            mNetworkStatusHandler.postDelayed(mNetworkOnRunnable, 10000);
                            break;
                        case ConnectivityManager.TYPE_WIFI://wifi网络
                            Log.d("====", "TYPE_WIFI=有网络");
                            mNetworkStatusHandler.removeCallbacks(mNetworkOnRunnable);
                            mNetworkStatusHandler.postDelayed(mNetworkOnRunnable, 10000);
                            break;
                        case ConnectivityManager.TYPE_ETHERNET://网线连接
                            Log.d("====", "TYPE_ETHERNET=有网络");
                            mNetworkStatusHandler.removeCallbacks(mNetworkOnRunnable);
                            mNetworkStatusHandler.postDelayed(mNetworkOnRunnable, 10000);
                            break;
                    }
                } else {// 无网络
                    Log.e("====", "============无网络=");
                    mNetworkStatusHandler.removeCallbacks(mNetworkOffRunnable);
                    mNetworkStatusHandler.postDelayed(mNetworkOffRunnable, 10000);
                }
            } else if ("android.net.conn.INET_CONDITION_ACTION".equals(action)) {
                Log.e("====", "=============INET_CONDITION_ACTION");
                NetworkInfo ni = (NetworkInfo) intent.getExtras().get(ConnectivityManager.EXTRA_NETWORK_INFO);
                Log.e("====", "========ni.getType=" + ni.getType() + "-----ni.getState=" + ni.getState());
                switch (ni.getType()) {
                    /*case ConnectivityManager.TYPE_MOBILE://移动 网络    2G 3G 4G 都是一样的 实测 mix2s 联通卡
                        Log.d("====", "INET_CONDITION_ACTION-TYPE_MOBILE=有网络");
                        mNetworkStatusHandler.removeCallbacks(mNetworkOnRunnable);
                        mNetworkStatusHandler.postDelayed(mNetworkOnRunnable, 10000);
                        break;
                    case ConnectivityManager.TYPE_WIFI://wifi网络
                        Log.d("====", "INET_CONDITION_ACTION-TYPE_WIFI=有网络");
                        mNetworkStatusHandler.removeCallbacks(mNetworkOnRunnable);
                        mNetworkStatusHandler.postDelayed(mNetworkOnRunnable, 10000);
                        break;*/
                    case ConnectivityManager.TYPE_ETHERNET://网线连接(手动插网线)
                        Log.d("====", "INET_CONDITION_ACTION-TYPE_ETHERNET=有网络");
                        mNetworkStatusHandler.removeCallbacks(mNetworkOnRunnable);
                        mNetworkStatusHandler.postDelayed(mNetworkOnRunnable, 10000);
                        break;
                }
                //只能触发连接的事件
                //NetworkInfo ni = (NetworkInfo) intent.getExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                /*NetworkInfo ni = (NetworkInfo) intent.getExtras().get(ConnectivityManager.EXTRA_NETWORK_INFO);
                Log.d(TAG, "network type:" + (int) intent.getExtras().get(ConnectivityManager.EXTRA_NETWORK_TYPE));
                if (null != ni) {
                    Log.d(TAG, "rcv ethernet action, network info:" + ni);
                    mEthernetState = ni.getState();
                    Log.d(TAG, "old state:" + mLastEthernetState + ", new state:" + mEthernetState);
                    if (mEthernetConnected || isEth0Connected()) {
                        Log.i(TAG, "以太网从断开到连接状态");
                        restored = true;
                    } else if (mLastEthernetState == NetworkInfo.State.CONNECTED) {
                        Log.i(TAG, "以太网从连接到断开状态");
                        if (mWifiState == NetworkInfo.State.CONNECTED || mMobileState == NetworkInfo.State.CONNECTED) {
                            Log.i(TAG, "以太网仍然连接状态，执行恢复网络连接操作");
                            restored = true;
                        } else {
                            disconnect = true;
                        }
                    }

                    mLastEthernetState = mEthernetState;
                    if (restored) {
                        Log.i(TAG, "网络连接恢复： " + "以太网连接" + "（data）数据连接");
                        connect(IP, PORT);
                    }

                    if (disconnect) {
                        //断网
                        Log.i(TAG, "网络断开");
                        disconnect();
                    }
                }*/
            }
        }
    };

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

    private void registerTimeReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        registerReceiver(mTimeChangeBroadcastReceiver, filter);
    }

    private void unRegisterTimeReceiver() {
        if (mTimeChangeBroadcastReceiver != null) {
            unregisterReceiver(mTimeChangeBroadcastReceiver);
            mTimeChangeBroadcastReceiver = null;
        }
    }

    private void registerNetworkReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.net.ethernet.ETHERNET_STATE_CHANGED");
        filter.addAction("android.net.ethernet.STATE_CHANGE");
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        filter.addAction("android.net.wifi.STATE_CHANGE");
        filter.addAction("android.net.conn.INET_CONDITION_ACTION");
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(mNetworkChangeBroadcastReceiver, filter);
    }

    private void unRegisterNetworkReceiver() {
        if (mNetworkChangeBroadcastReceiver != null) {
            unregisterReceiver(mNetworkChangeBroadcastReceiver);
            mNetworkChangeBroadcastReceiver = null;
        }
    }

    private void registerPC2DroidReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.erobbing.action.PC_TO_DROID");
        filter.addAction("com.erobbing.action.PC_TO_DROID_REG_BOX");
        filter.addAction("com.erobbing.action.PC_TO_DROID_UNREG_BOX");
        filter.addAction("com.erobbing.action.PC_TO_DROID_UNREG_KEY");
        filter.addAction("com.erobbing.tcpsmartkey.alarm");
        filter.addAction("com.erobbing.action.EXIT_SMARTKEY");
        filter.addAction("com.erobbing.action.SMARTKEY_OTA_TEST");
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
            if ("com.erobbing.action.PC_TO_DROID_REG_BOX".equals(action)) {
                Log.e("====", "=======PC_TO_DROID_REG_BOX");
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
                tmpPhoneId = intent.getStringExtra("box_id");
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
                if (mConnected) {
                    sendMessage(getAllBytes(0x0100, 0x0001, tmpPhoneId, "0100" + "0633" + "88888888" + "77777777777777777777" + tmpPhoneId));
                } else {
                    connect(IP, PORT);
                    sleep(2000);
                    sendMessage(getAllBytes(0x0100, 0x0001, tmpPhoneId, "0100" + "0633" + "88888888" + "77777777777777777777" + tmpPhoneId));
                }
            }
            if ("com.erobbing.action.PC_TO_DROID_UNREG_BOX".equals(action)) {
                Log.e("====", "=======PC_TO_DROID_UNREG_BOX");
                //0x0003;
                //body 00 shop change,01 demaged
                sendMessage(getAllBytes(0x0003, mHeadMsgSeqInt, tmpPhoneId, "00" + tmpPhoneId));
            }
            if ("com.erobbing.action.PC_TO_DROID_UNREG_KEY".equals(action)) {
                Log.e("====", "=======PC_TO_DROID_UNREG_KEY");
                // TODO: 2019/3/3 钥匙扣注销 0x0004 done
                unRegKeyhole = intent.getStringExtra("keynum");
                Log.e("====", "=====UNREG_KEY-receiver-unRegKeyhole=" + unRegKeyhole + "----keyid=" + getSavedKeyId(unRegKeyhole));
                //0：换店 1：损坏
                //终端 ID
                sendMessage(getAllBytes(0x0004, mHeadMsgSeqInt, tmpPhoneId, "00" + getSavedKeyId(unRegKeyhole)));
            }
            if ("com.erobbing.action.EXIT_SMARTKEY".equals(action)) {
                Log.e("====", "==============EXIT_SMARTKEY");
                //stopSelf();
                //Intent stopIntent = new Intent(mContext, TcpService.class);
                //stopService(stopIntent);
                isOTAUpgradeBegin = true;
            }
            if ("com.erobbing.action.SMARTKEY_OTA_TEST".equals(action)) {
                playVoice(R.string.tts_box_upgrade);
                sleep(2000);
                Intent otaIntent = new Intent("com.qualcomm.update.start_update_after");
                otaIntent.putExtra("start", true);
                mContext.sendBroadcast(otaIntent);
            }
            if ("com.erobbing.tcpsmartkey.alarm".equals(action)) {
                Log.e("====", "==========heart alarm");
                cancelAlarm();
                if (getXmlAuthState()) {
                    sendMessage(getAllBytes(0x0002, mHeadMsgSeqInt, tmpPhoneId, ""));//心跳
                }
                setAlarm();
            } else if ("com.erobbing.action.ETHERNET_CHANGE".equals(action)) {
                boolean on = intent.getBooleanExtra("ethernet_switch", false);

                Log.d(TAG, "==========ethernet change action, on:" + on);
                if (on) {
                    Log.d(TAG, "==========ethernet change action---on:");
                    //mEthernetManager = (EthernetManager) context.getSystemService("ethernet");
                    if (null == mEthernetManager) {
                        Log.w(TAG, "system does not support ETHERNET!!!");
                        return;
                    }

                    IpConfiguration config = mEthernetManager.getConfiguration();
                    Log.w(TAG, "============config=" + config);
                    boolean isDhcp = intent.getBooleanExtra("dhcp", false);
                    if (isDhcp) {
                        Log.w(TAG, "============isDhcp=" + isDhcp);
                        //set ipconfiguration now
                        config.ipAssignment = IpAssignment.DHCP;
                    } else {
                        String ip = intent.getStringExtra("ip");
                        String ipmask = intent.getStringExtra("ipmask");//it seems meanless here
                        String gateway = intent.getStringExtra("gateway");
                        String dns1 = intent.getStringExtra("dns1");
                        String dns2 = intent.getStringExtra("dns2");

                        if (null == ip || null == gateway) {
                            Log.w(TAG, "ip or gateway is empty, do nothing");
                            return;
                        }
                        if (ipmask != null && ipmask.contains("255.255.255.0") && !ip.contains("/24")) {
                            ip = ip + "/24";
                        } else if (ipmask != null && ipmask.contains("255.255.0.0") && !ip.contains("/16")) {
                            ip = ip + "/16";
                        }
                        Log.w(TAG, "ip:" + ip + ",ipmask :" + ipmask + ",gateway:" + gateway);
                        config.ipAssignment = IpAssignment.STATIC;
                        StaticIpConfiguration staticIpConfiguration = new StaticIpConfiguration();
                        try {
                            Constructor<?> laConstructor = LinkAddress.class.getConstructor(String.class);
                            Log.w(TAG, "ipaddress");
                            staticIpConfiguration.ipAddress = (LinkAddress) laConstructor.newInstance(ip);
                            Log.w(TAG, "ipaddress 1");
                            staticIpConfiguration.gateway = InetAddress.getByName(gateway);
                            staticIpConfiguration.dnsServers.clear();
                            Log.w(TAG, "ipaddress 2");

                            if (dns1 != null) {
                                staticIpConfiguration.dnsServers.add(InetAddress.getByName(dns1));
                            }
                            if (dns2 != null) {
                                staticIpConfiguration.dnsServers.add(InetAddress.getByName(dns2));
                            }

                            //add default
                            if (null == dns1 && null == dns2) {
                                staticIpConfiguration.dnsServers.add(InetAddress.getByName("8.8.8.8"));
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "set ip failed", e);
                        }
                        config.setStaticIpConfiguration(staticIpConfiguration);
                        Log.d(TAG, " current ethernet config:" + config);
                    }
                    Log.d(TAG, "==========setConfiguration");
                    mEthernetManager.setConfiguration(config);
                    Log.d(TAG, "call service ethernet_start");
                    SystemService.start("ethernet_start");
                } else {
                    Log.d(TAG, "call service ethernet_stop");
                    SystemService.start("ethernet_stop");
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
                    //sendMessage();上报一下状态
                    // TODO: 2019/2/26  done
                    allKeysStatusPowerOnOff();
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
                String keyId = getSavedKeyId(String.format("%02x", i + 1));//default "999999999999"
                Log.e("====", "======getSavedKeyId=" + keyId);
                sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0c" + String.format("%02x", i + 1) + "3b" + keyId));
                Thread.sleep(200L);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void allKeysStatusPowerOnOff() {
        //time
        //0x0817
        String msgId = "0817";
        String headerO = "0008" + tmpPhoneId;
        //String flowId = "0019";
        int flowId = 20;
        //String bodyString = "0a00010013011a023912";
        //String bodyString = "0a000100" + getTimeHexString();
        String bodyString = "0c" + "00" + "999999999999";
        //钥匙箱运行状态;钥匙孔编号;钥匙孔状态;时间
        //钥匙孔状态0：正常 1 充电异常 2 通信异常 3 电磁阀异常 4 其他异常
        for (int i = 0; i < 24; i++) {
            try {
                String keyId = getSavedKeyId(String.format("%02x", i + 1));//default "999999999999"
                Log.e("====", "======getSavedKeyId=" + keyId);
                sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "0a" + "00" + "3b" + String.format("%02x", i + 1) + "3b" + "00" + "3b" + getTimeHexString()));
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

    public int getCurrentBoxBatteryCapacity() {
        String levelString = "100";
        levelString = ShellUtils.execCommand("cat /sys/class/power_supply/battery/capacity", false, true).successMsg;
        Log.e("====", "=============getCurrentBoxBatteryCapacity");
        return Integer.parseInt(levelString);
    }

    public boolean getCurrentKeyBatteryFull(String keyHole) {
        String levelString = "00";
        //0 0 0 0 0 0 B1 B0
        //B0：代表是否充满电 0：未充满 1：充满
        //B1：代表数据是否有效 0：无效 1：有效
        //00 01 10 11   11->3有效满电
        synchronized (mKeyCommunicationLock) {
            ShellUtils.execCommand("echo on > /sys/class/gpio_switch/switch_ct_" + keyHole, false);
            levelString = ShellUtils.execCommand("cat /sys/bus/i2c/devices/6-005b/mcu/key_st", false, true).successMsg;
            ShellUtils.execCommand("echo off > /sys/class/gpio_switch/switch_ct_" + keyHole, false);
        }
        Log.e("====", "=============getCurrentKeyBatteryFull");
        return levelString.contains("3") ? true : false;
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

    public String getSavedKeyId(String keyHoleId) {
        SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
        String keyId = sp.getString("key_hole_" + keyHoleId, "999999999999");
        return keyId;
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

    public boolean eraseKeyInfo(String keyHole) {
        Log.e("====", "==============eraseKeyInfo");
        String levelString = "00";
        synchronized (mKeyCommunicationLock) {
            ShellUtils.execCommand("echo on > /sys/class/gpio_switch/switch_ct_" + keyHole, false);
            levelString = ShellUtils.execCommand("cat /sys/bus/i2c/devices/6-005b/mcu/key_st", false, true).successMsg;
            String dm = ShellUtils.execCommand("cat /sys/bus/i2c/devices/6-005b/mcu/dm_id", false, true).successMsg;
            String se_code = ShellUtils.execCommand("cat /sys/bus/i2c/devices/6-005b/mcu/se_code", false, true).successMsg;
            Log.e("====", "========levelString=" + levelString + "------keyHole=" + keyHole + "----dm=" + dm);
            if (levelString.contains("2") || levelString.contains("3")) {
                //通信正常
                ShellUtils.execCommand("echo ffffffffffffffffffff > /sys/bus/i2c/devices/6-005b/mcu/dm_id", false);
                ShellUtils.execCommand("echo ffffffffffffffffffff > /sys/bus/i2c/devices/6-005b/mcu/se_code", false);
                Log.e("====", "======erase done! ----dm=" + ShellUtils.execCommand("cat /sys/bus/i2c/devices/6-005b/mcu/dm_id", false, true).successMsg
                        + "----se_code=" + ShellUtils.execCommand("cat /sys/bus/i2c/devices/6-005b/mcu/se_code", false, true).successMsg);
                ShellUtils.execCommand("echo off > /sys/class/gpio_switch/switch_ct_" + keyHole, false);
                return true;
            } else {
                //通信异常
                Log.e("====", "=======eraseKeyInfo error");
                ShellUtils.execCommand("echo off > /sys/class/gpio_switch/switch_ct_" + keyHole, false);
                return false;
            }
        }
    }

    private String keyIdRead() {
        Log.e("====", "=======keyIdRead");
        String oriString = execCommand("cat /sys/bus/i2c/devices/6-005b/mcu/key_id", false, true).successMsg;
        Log.d(TAG, "keyIdRead=" + reGroupString(oriString));
        return reGroupString(oriString);
    }

    private String keyShopIdRead() {
        Log.e("====", "=======keyShopIdRead");
        String oriString = execCommand("cat /sys/bus/i2c/devices/6-005b/mcu/dm_id", false, true).successMsg;
        Log.d(TAG, "keyShopIdRead=" + reGroupString(oriString));
        return reGroupString(oriString);
    }

    private String keyBatLeveldRead() {
        Log.e("====", "=======keyBatLeveldRead");
        String oriString = execCommand("cat /sys/bus/i2c/devices/6-005b/mcu/key_st", false, true).successMsg;
        Log.d(TAG, "keyBatLeveldRead=" + reGroupString(oriString));
        return reGroupString(oriString);
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

    public static String read(String fileName) {
        File f = new File(fileName);
        if (!f.exists()) {
            return null;
        }
        FileInputStream fs;
        String result = null;
        try {
            fs = new FileInputStream(f);
            byte[] b = new byte[fs.available()];
            fs.read(b);
            fs.close();
            result = new String(b);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    public static boolean isEth0Connected() {
        String state = read("/sys/class/net/eth0/operstate");
        //Log.v(TAG, "isEth0Connected:" + state);
        if (state != null && (state.contains("unknown") || state.contains("up"))) {
            Log.v(TAG, "eth0 connected");
            return true;
        }

        Log.v(TAG, "eth0 disconnected");
        return false;
    }

    private Listener mEthernetLisener = new Listener() {
        @Override
        public void onAvailabilityChanged(boolean isAvailable) {
            Log.d("====", "ethernet state changed, isvailable:" + isAvailable);
            mEthernetConnected = isAvailable;
            if (isAvailable) {
                Log.e("====", "===============mEthernetLisener-isAvailable");
            } else {
                Log.e("====", "===============mEthernetLisener");
                /*mLastEthernetState = NetworkInfo.State.DISCONNECTED;
                //disconnect();
                if (mWifiState == NetworkInfo.State.CONNECTED || mMobileState == NetworkInfo.State.CONNECTED) {
                    Log.d(TAG, "restore connection from wifi or moible");
                    connect(IP, PORT);
                } else {
                    Log.d(TAG, "disconnect");
                    disconnect();
                }*/
            }
        }
    };

    private class MotorCtrlHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_KEY_01_MOTOR_ON:
                    motor01StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor01OffIn10S, MOTOR_AUTO_OFF_TIME);
                    break;
                case HANDLER_MSG_KEY_02_MOTOR_ON:
                    motor02StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor02OffIn10S, MOTOR_AUTO_OFF_TIME);
                    break;
                case HANDLER_MSG_KEY_03_MOTOR_ON:
                    motor03StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor03OffIn10S, MOTOR_AUTO_OFF_TIME);
                    break;
                case HANDLER_MSG_KEY_04_MOTOR_ON:
                    motor04StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor04OffIn10S, MOTOR_AUTO_OFF_TIME);
                    break;
                case HANDLER_MSG_KEY_05_MOTOR_ON:
                    motor05StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor05OffIn10S, MOTOR_AUTO_OFF_TIME);
                    break;
                case HANDLER_MSG_KEY_06_MOTOR_ON:
                    motor06StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor06OffIn10S, MOTOR_AUTO_OFF_TIME);
                    break;
                case HANDLER_MSG_KEY_07_MOTOR_ON:
                    motor07StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor07OffIn10S, MOTOR_AUTO_OFF_TIME);
                    break;
                case HANDLER_MSG_KEY_08_MOTOR_ON:
                    motor08StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor08OffIn10S, MOTOR_AUTO_OFF_TIME);
                    break;
                case HANDLER_MSG_KEY_09_MOTOR_ON:
                    motor09StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor09OffIn10S, MOTOR_AUTO_OFF_TIME);
                    break;
                case HANDLER_MSG_KEY_10_MOTOR_ON:
                    motor10StatusCtrl(true);
                    mMotorCtrlHandler.postDelayed(mMotor10OffIn10S, MOTOR_AUTO_OFF_TIME);
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

    private class NetworkStatusHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_TCP_DISCONNECT:
                    disconnect();
                    break;
            }
        }
    }

    private class KeyPlugInHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {

            }
        }
    }

    private class KeyBatNullCheckHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {

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

    private final Runnable mNetworkOnRunnable = new Runnable() {
        public void run() {
            connect(IP, PORT);
            Log.e("====", "=======mNetworkOnRunnable");
        }
    };
    private final Runnable mNetworkOffRunnable = new Runnable() {
        public void run() {
            disconnect();
            Log.e("====", "=======mNetworkOffRunnable");
        }
    };

    private final Runnable mGreenLed01SlowBlinkRunnable = new Runnable() {
        public void run() {
            // TODO: 2019/2/27 充满电判断 done
            if (getCurrentKeyBatteryFull("01")) {
                mLedCtrlHandler.removeCallbacks(mGreenLed01fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed01SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_01_ON, 10);
            } else {
                ledSeriesCtrl(LED_GREEN_01, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_01, false);
                mLedCtrlHandler.postDelayed(mGreenLed01SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
            }
        }
    };

    private final Runnable mGreenLed01fastBlinkRunnable = new Runnable() {
        public void run() {
            fastBlinkCount01--;
            if (fastBlinkCount01 > 0) {
                ledSeriesCtrl(LED_GREEN_01, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_01, false);
                mLedCtrlHandler.postDelayed(mGreenLed01fastBlinkRunnable, LED_FAST_BLINK_TIME);
            } else {
                mLedCtrlHandler.removeCallbacks(mGreenLed01fastBlinkRunnable);
                fastBlinkCount01 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
            }
            // TODO: 2019/3/2 快闪出仓时间最多10s，所以快闪灯最多亮10s done
        }
    };

    private final Runnable mGreenLed02SlowBlinkRunnable = new Runnable() {
        public void run() {
            if (getCurrentKeyBatteryFull("02")) {
                mLedCtrlHandler.removeCallbacks(mGreenLed02fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed02SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_02_ON, 10);
            } else {
                ledSeriesCtrl(LED_GREEN_02, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_02, false);
                mLedCtrlHandler.postDelayed(mGreenLed02SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
            }
        }
    };

    private final Runnable mGreenLed02fastBlinkRunnable = new Runnable() {
        public void run() {
            fastBlinkCount02--;
            if (fastBlinkCount02 > 0) {
                ledSeriesCtrl(LED_GREEN_02, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_02, false);
                mLedCtrlHandler.postDelayed(mGreenLed02fastBlinkRunnable, LED_FAST_BLINK_TIME);
            } else {
                mLedCtrlHandler.removeCallbacks(mGreenLed02fastBlinkRunnable);
                fastBlinkCount02 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
            }
        }
    };

    private final Runnable mGreenLed03SlowBlinkRunnable = new Runnable() {
        public void run() {
            if (getCurrentKeyBatteryFull("03")) {
                mLedCtrlHandler.removeCallbacks(mGreenLed03fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed03SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_03_ON, 10);
            } else {
                ledSeriesCtrl(LED_GREEN_03, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_03, false);
                mLedCtrlHandler.postDelayed(mGreenLed03SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
            }
        }
    };

    private final Runnable mGreenLed03fastBlinkRunnable = new Runnable() {
        public void run() {
            fastBlinkCount03--;
            if (fastBlinkCount03 > 0) {
                ledSeriesCtrl(LED_GREEN_03, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_03, false);
                mLedCtrlHandler.postDelayed(mGreenLed03fastBlinkRunnable, LED_FAST_BLINK_TIME);
            } else {
                mLedCtrlHandler.removeCallbacks(mGreenLed03fastBlinkRunnable);
                fastBlinkCount03 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
            }
        }
    };

    private final Runnable mGreenLed04SlowBlinkRunnable = new Runnable() {
        public void run() {
            if (getCurrentKeyBatteryFull("04")) {
                mLedCtrlHandler.removeCallbacks(mGreenLed04fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed04SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_04_ON, 10);
            } else {
                ledSeriesCtrl(LED_GREEN_04, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_04, false);
                mLedCtrlHandler.postDelayed(mGreenLed04SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
            }
        }
    };

    private final Runnable mGreenLed04fastBlinkRunnable = new Runnable() {
        public void run() {
            fastBlinkCount04--;
            if (fastBlinkCount04 > 0) {
                ledSeriesCtrl(LED_GREEN_04, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_04, false);
                mLedCtrlHandler.postDelayed(mGreenLed04fastBlinkRunnable, LED_FAST_BLINK_TIME);
            } else {
                mLedCtrlHandler.removeCallbacks(mGreenLed04fastBlinkRunnable);
                fastBlinkCount04 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
            }
        }
    };

    private final Runnable mGreenLed05SlowBlinkRunnable = new Runnable() {
        public void run() {
            if (getCurrentKeyBatteryFull("05")) {
                mLedCtrlHandler.removeCallbacks(mGreenLed05fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed05SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_05_ON, 10);
            } else {
                ledSeriesCtrl(LED_GREEN_05, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_05, false);
                mLedCtrlHandler.postDelayed(mGreenLed05SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
            }
        }
    };

    private final Runnable mGreenLed05fastBlinkRunnable = new Runnable() {
        public void run() {
            fastBlinkCount05--;
            if (fastBlinkCount05 > 0) {
                ledSeriesCtrl(LED_GREEN_05, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_05, false);
                mLedCtrlHandler.postDelayed(mGreenLed05fastBlinkRunnable, LED_FAST_BLINK_TIME);
            } else {
                mLedCtrlHandler.removeCallbacks(mGreenLed05fastBlinkRunnable);
                fastBlinkCount05 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
            }
        }
    };

    private final Runnable mGreenLed06SlowBlinkRunnable = new Runnable() {
        public void run() {
            if (getCurrentKeyBatteryFull("06")) {
                mLedCtrlHandler.removeCallbacks(mGreenLed06fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed06SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_06_ON, 10);
            } else {
                ledSeriesCtrl(LED_GREEN_06, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_06, false);
                mLedCtrlHandler.postDelayed(mGreenLed06SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
            }
        }
    };

    private final Runnable mGreenLed06fastBlinkRunnable = new Runnable() {
        public void run() {
            fastBlinkCount06--;
            if (fastBlinkCount06 > 0) {
                ledSeriesCtrl(LED_GREEN_06, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_06, false);
                mLedCtrlHandler.postDelayed(mGreenLed06fastBlinkRunnable, LED_FAST_BLINK_TIME);
            } else {
                mLedCtrlHandler.removeCallbacks(mGreenLed06fastBlinkRunnable);
                fastBlinkCount06 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
            }
        }
    };

    private final Runnable mGreenLed07SlowBlinkRunnable = new Runnable() {
        public void run() {
            if (getCurrentKeyBatteryFull("07")) {
                mLedCtrlHandler.removeCallbacks(mGreenLed07fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed07SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_07_ON, 10);
            } else {
                ledSeriesCtrl(LED_GREEN_07, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_07, false);
                mLedCtrlHandler.postDelayed(mGreenLed07SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
            }
        }
    };

    private final Runnable mGreenLed07fastBlinkRunnable = new Runnable() {
        public void run() {
            fastBlinkCount07--;
            if (fastBlinkCount07 > 0) {
                ledSeriesCtrl(LED_GREEN_07, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_07, false);
                mLedCtrlHandler.postDelayed(mGreenLed07fastBlinkRunnable, LED_FAST_BLINK_TIME);
            } else {
                mLedCtrlHandler.removeCallbacks(mGreenLed07fastBlinkRunnable);
                fastBlinkCount07 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
            }
        }
    };

    private final Runnable mGreenLed08SlowBlinkRunnable = new Runnable() {
        public void run() {
            if (getCurrentKeyBatteryFull("08")) {
                mLedCtrlHandler.removeCallbacks(mGreenLed08fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed08SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_08_ON, 10);
            } else {
                ledSeriesCtrl(LED_GREEN_08, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_08, false);
                mLedCtrlHandler.postDelayed(mGreenLed08SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
            }
        }
    };

    private final Runnable mGreenLed08fastBlinkRunnable = new Runnable() {
        public void run() {
            fastBlinkCount08--;
            if (fastBlinkCount08 > 0) {
                ledSeriesCtrl(LED_GREEN_08, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_08, false);
                mLedCtrlHandler.postDelayed(mGreenLed08fastBlinkRunnable, LED_FAST_BLINK_TIME);
            } else {
                mLedCtrlHandler.removeCallbacks(mGreenLed08fastBlinkRunnable);
                fastBlinkCount08 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
            }
        }
    };

    private final Runnable mGreenLed09SlowBlinkRunnable = new Runnable() {
        public void run() {
            if (getCurrentKeyBatteryFull("09")) {
                mLedCtrlHandler.removeCallbacks(mGreenLed09fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed09SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_09_ON, 10);
            } else {
                ledSeriesCtrl(LED_GREEN_09, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_09, false);
                mLedCtrlHandler.postDelayed(mGreenLed09SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
            }
        }
    };

    private final Runnable mGreenLed09fastBlinkRunnable = new Runnable() {
        public void run() {
            fastBlinkCount09--;
            if (fastBlinkCount09 > 0) {
                ledSeriesCtrl(LED_GREEN_09, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_09, false);
                mLedCtrlHandler.postDelayed(mGreenLed09fastBlinkRunnable, LED_FAST_BLINK_TIME);
            } else {
                mLedCtrlHandler.removeCallbacks(mGreenLed09fastBlinkRunnable);
                fastBlinkCount09 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
            }
        }
    };

    private final Runnable mGreenLed10SlowBlinkRunnable = new Runnable() {
        public void run() {
            if (getCurrentKeyBatteryFull("10")) {
                mLedCtrlHandler.removeCallbacks(mGreenLed10fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed10SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_10_ON, 10);
            } else {
                ledSeriesCtrl(LED_GREEN_10, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_10, false);
                mLedCtrlHandler.postDelayed(mGreenLed10SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
            }
        }
    };

    private final Runnable mGreenLed10fastBlinkRunnable = new Runnable() {
        public void run() {
            fastBlinkCount10--;
            if (fastBlinkCount10 > 0) {
                ledSeriesCtrl(LED_GREEN_10, true);
                sleep(LED_BLINK_SLEEP_TIME);
                ledSeriesCtrl(LED_GREEN_10, false);
                mLedCtrlHandler.postDelayed(mGreenLed10fastBlinkRunnable, LED_FAST_BLINK_TIME);
            } else {
                mLedCtrlHandler.removeCallbacks(mGreenLed10fastBlinkRunnable);
                fastBlinkCount10 = (int) MOTOR_AUTO_OFF_TIME / (int) LED_FAST_BLINK_TIME;
            }
        }
    };

    ////
    private final Runnable mKey01PlugInRunnable = new Runnable() {
        public void run() {
            synchronized (mKeyCommunicationLock) {
                execCommand("echo on > /sys/class/gpio_switch/switch_ct_01", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = keyShopIdRead();//allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = keyIdRead();//allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "01";
                saveKeyId("key_hole_01", keyId);
                Log.e("=====", "key01--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                if (shopId.contains("ffffffffffffffffffff")) {
                    Log.e("====", "===========key01reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_01_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_OFF, 10);
                    mLedCtrlHandler.removeCallbacks(mGreenLed01fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed01SlowBlinkRunnable);
                    mLedCtrlHandler.postDelayed(mGreenLed01SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mKeyBatNullCheckHandler.postDelayed(mKey01BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                } else {
                    Log.e("====", "===key01=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key01=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        playVoice(R.string.tts_key_back_succeed);
                        //钥匙扣归还
                        //钥匙扣插入钥匙箱01，钥匙箱主动上报平台。上报指令如下：“钥匙孔编号;钥匙扣ID”
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                        Log.e("====", "===============key01-back");
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_01_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed01fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed01SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed01SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key01-back-illegal");
                        isKey01Illegal_back = true;
                        playVoice(R.string.tts_key_back_illegal);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_01_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_01_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed01fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed01SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_ON, 10);
                        // TODO: 2019/2/27  上报？
                    }
                }
                execCommand("echo off > /sys/class/gpio_switch/switch_ct_01", false);
            }
        }
    };

    private final Runnable mKey02PlugInRunnable = new Runnable() {
        public void run() {
            synchronized (mKeyCommunicationLock) {
                execCommand("echo on > /sys/class/gpio_switch/switch_ct_02", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = keyShopIdRead();//allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = keyIdRead();//allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "02";
                saveKeyId("key_hole_02", keyId);
                Log.e("=====", "key02--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                if (shopId.contains("ffffffffffffffffffff")) {
                    Log.e("====", "===========key02reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_02_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_OFF, 10);
                    mLedCtrlHandler.removeCallbacks(mGreenLed02fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed02SlowBlinkRunnable);
                    mLedCtrlHandler.postDelayed(mGreenLed02SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mKeyBatNullCheckHandler.postDelayed(mKey02BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                } else {
                    Log.e("====", "===key02=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key02=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        playVoice(R.string.tts_key_back_succeed);
                        //钥匙扣归还
                        //钥匙扣插入钥匙箱01，钥匙箱主动上报平台。上报指令如下：“钥匙孔编号;钥匙扣ID”
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "02" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                        Log.e("====", "===============key02-back");
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_02_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed02fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed02SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed02SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key02-back-illegal");
                        isKey02Illegal_back = true;
                        playVoice(R.string.tts_key_back_illegal);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_02_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_02_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed02fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed02SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_ON, 10);
                        // TODO: 2019/2/27  上报？
                    }
                }
                execCommand("echo off > /sys/class/gpio_switch/switch_ct_02", false);
            }
        }
    };

    private final Runnable mKey03PlugInRunnable = new Runnable() {
        public void run() {
            synchronized (mKeyCommunicationLock) {
                execCommand("echo on > /sys/class/gpio_switch/switch_ct_03", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = keyShopIdRead();//allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = keyIdRead();//allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "03";
                saveKeyId("key_hole_03", keyId);
                Log.e("=====", "key03--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                if (shopId.contains("ffffffffffffffffffff")) {
                    Log.e("====", "===========key03reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_03_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_OFF, 10);
                    mLedCtrlHandler.removeCallbacks(mGreenLed03fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed03SlowBlinkRunnable);
                    mLedCtrlHandler.postDelayed(mGreenLed03SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mKeyBatNullCheckHandler.postDelayed(mKey03BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                } else {
                    Log.e("====", "===key03=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key03=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        playVoice(R.string.tts_key_back_succeed);
                        //钥匙扣归还
                        //钥匙扣插入钥匙箱01，钥匙箱主动上报平台。上报指令如下：“钥匙孔编号;钥匙扣ID”
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "03" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                        Log.e("====", "===============key03-back");
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_03_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed03fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed03SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed03SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key03-back-illegal");
                        isKey03Illegal_back = true;
                        playVoice(R.string.tts_key_back_illegal);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_03_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_03_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed03fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed03SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_ON, 10);
                        // TODO: 2019/2/27  上报？
                    }
                }
                execCommand("echo off > /sys/class/gpio_switch/switch_ct_03", false);
            }
        }
    };

    private final Runnable mKey04PlugInRunnable = new Runnable() {
        public void run() {
            synchronized (mKeyCommunicationLock) {
                execCommand("echo on > /sys/class/gpio_switch/switch_ct_04", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = keyShopIdRead();//allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = keyIdRead();//allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "04";
                saveKeyId("key_hole_04", keyId);
                Log.e("=====", "key04--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                if (shopId.contains("ffffffffffffffffffff")) {
                    Log.e("====", "===========key04reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_04_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_OFF, 10);
                    mLedCtrlHandler.removeCallbacks(mGreenLed04fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed04SlowBlinkRunnable);
                    mLedCtrlHandler.postDelayed(mGreenLed04SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mKeyBatNullCheckHandler.postDelayed(mKey04BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                } else {
                    Log.e("====", "===key04=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key04=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        playVoice(R.string.tts_key_back_succeed);
                        //钥匙扣归还
                        //钥匙扣插入钥匙箱01，钥匙箱主动上报平台。上报指令如下：“钥匙孔编号;钥匙扣ID”
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "04" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                        Log.e("====", "===============key04-back");
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_04_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed04fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed04SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed04SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key04-back-illegal");
                        isKey04Illegal_back = true;
                        playVoice(R.string.tts_key_back_illegal);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_04_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed04fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed04SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_ON, 10);
                        // TODO: 2019/2/27  上报？
                    }
                }
                execCommand("echo off > /sys/class/gpio_switch/switch_ct_04", false);
            }
        }
    };

    private final Runnable mKey05PlugInRunnable = new Runnable() {
        public void run() {
            synchronized (mKeyCommunicationLock) {
                execCommand("echo on > /sys/class/gpio_switch/switch_ct_05", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = keyShopIdRead();//allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = keyIdRead();//allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "05";
                saveKeyId("key_hole_05", keyId);
                Log.e("=====", "key05--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                if (shopId.contains("ffffffffffffffffffff")) {
                    Log.e("====", "===========key05reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_05_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_OFF, 10);
                    mLedCtrlHandler.removeCallbacks(mGreenLed05fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed05SlowBlinkRunnable);
                    mLedCtrlHandler.postDelayed(mGreenLed05SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mKeyBatNullCheckHandler.postDelayed(mKey05BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                } else {
                    Log.e("====", "===key05=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key05=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        playVoice(R.string.tts_key_back_succeed);
                        //钥匙扣归还
                        //钥匙扣插入钥匙箱01，钥匙箱主动上报平台。上报指令如下：“钥匙孔编号;钥匙扣ID”
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "05" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                        Log.e("====", "===============key05-back");
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_05_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed05fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed05SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed05SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key05-back-illegal");
                        isKey05Illegal_back = true;
                        playVoice(R.string.tts_key_back_illegal);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_05_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_05_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed05fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed05SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_ON, 10);
                        // TODO: 2019/2/27  上报？
                    }
                }
                execCommand("echo off > /sys/class/gpio_switch/switch_ct_05", false);
            }
        }
    };

    private final Runnable mKey06PlugInRunnable = new Runnable() {
        public void run() {
            synchronized (mKeyCommunicationLock) {
                execCommand("echo on > /sys/class/gpio_switch/switch_ct_06", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = keyShopIdRead();//allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = keyIdRead();//allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "06";
                saveKeyId("key_hole_06", keyId);
                Log.e("=====", "key06--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                if (shopId.contains("ffffffffffffffffffff")) {
                    Log.e("====", "===========key06reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_06_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_OFF, 10);
                    mLedCtrlHandler.removeCallbacks(mGreenLed06fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed06SlowBlinkRunnable);
                    mLedCtrlHandler.postDelayed(mGreenLed06SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mKeyBatNullCheckHandler.postDelayed(mKey06BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                } else {
                    Log.e("====", "===key06=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key06=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        playVoice(R.string.tts_key_back_succeed);
                        //钥匙扣归还
                        //钥匙扣插入钥匙箱01，钥匙箱主动上报平台。上报指令如下：“钥匙孔编号;钥匙扣ID”
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "06" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                        Log.e("====", "===============key06-back");
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_06_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed06fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed06SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed06SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key06-back-illegal");
                        isKey06Illegal_back = true;
                        playVoice(R.string.tts_key_back_illegal);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_06_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_06_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed06fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed06SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_ON, 10);
                        // TODO: 2019/2/27  上报？
                    }
                }
                execCommand("echo off > /sys/class/gpio_switch/switch_ct_06", false);
            }
        }
    };

    private final Runnable mKey07PlugInRunnable = new Runnable() {
        public void run() {
            synchronized (mKeyCommunicationLock) {
                execCommand("echo on > /sys/class/gpio_switch/switch_ct_07", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = keyShopIdRead();//allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = keyIdRead();//allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "07";
                saveKeyId("key_hole_07", keyId);
                Log.e("=====", "key07--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                if (shopId.contains("ffffffffffffffffffff")) {
                    Log.e("====", "===========key07reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_07_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_OFF, 10);
                    mLedCtrlHandler.removeCallbacks(mGreenLed07fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed07SlowBlinkRunnable);
                    mLedCtrlHandler.postDelayed(mGreenLed07SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mKeyBatNullCheckHandler.postDelayed(mKey07BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                } else {
                    Log.e("====", "===key07=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key07=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        playVoice(R.string.tts_key_back_succeed);
                        //钥匙扣归还
                        //钥匙扣插入钥匙箱01，钥匙箱主动上报平台。上报指令如下：“钥匙孔编号;钥匙扣ID”
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "07" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                        Log.e("====", "===============key07-back");
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_07_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed07fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed07SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed07SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key07-back-illegal");
                        isKey07Illegal_back = true;
                        playVoice(R.string.tts_key_back_illegal);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_07_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_07_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed07fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed07SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_ON, 10);
                        // TODO: 2019/2/27  上报？
                    }
                }
                execCommand("echo off > /sys/class/gpio_switch/switch_ct_07", false);
            }
        }
    };

    private final Runnable mKey08PlugInRunnable = new Runnable() {
        public void run() {
            synchronized (mKeyCommunicationLock) {
                execCommand("echo on > /sys/class/gpio_switch/switch_ct_08", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = keyShopIdRead();//allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = keyIdRead();//allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "08";
                saveKeyId("key_hole_08", keyId);
                Log.e("=====", "key08--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                if (shopId.contains("ffffffffffffffffffff")) {
                    Log.e("====", "===========key08reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_08_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_OFF, 10);
                    mLedCtrlHandler.removeCallbacks(mGreenLed08fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed08SlowBlinkRunnable);
                    mLedCtrlHandler.postDelayed(mGreenLed08SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mKeyBatNullCheckHandler.postDelayed(mKey08BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                } else {
                    Log.e("====", "===key08=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key08=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        playVoice(R.string.tts_key_back_succeed);
                        //钥匙扣归还
                        //钥匙扣插入钥匙箱01，钥匙箱主动上报平台。上报指令如下：“钥匙孔编号;钥匙扣ID”
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "08" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                        Log.e("====", "===============key08-back");
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_08_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed08fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed08SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed08SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key08-back-illegal");
                        isKey08Illegal_back = true;
                        playVoice(R.string.tts_key_back_illegal);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_08_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_08_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed08fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed08SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_ON, 10);
                        // TODO: 2019/2/27  上报？
                    }
                }
                execCommand("echo off > /sys/class/gpio_switch/switch_ct_08", false);
            }
        }
    };

    private final Runnable mKey09PlugInRunnable = new Runnable() {
        public void run() {
            synchronized (mKeyCommunicationLock) {
                execCommand("echo on > /sys/class/gpio_switch/switch_ct_09", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = keyShopIdRead();//allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = keyIdRead();//allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "09";
                saveKeyId("key_hole_09", keyId);
                Log.e("=====", "key09--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                if (shopId.contains("ffffffffffffffffffff")) {
                    Log.e("====", "===========key09reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_09_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_OFF, 10);
                    mLedCtrlHandler.removeCallbacks(mGreenLed09fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed09SlowBlinkRunnable);
                    mLedCtrlHandler.postDelayed(mGreenLed09SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mKeyBatNullCheckHandler.postDelayed(mKey09BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                } else {
                    Log.e("====", "===key09=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key09=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        playVoice(R.string.tts_key_back_succeed);
                        //钥匙扣归还
                        //钥匙扣插入钥匙箱01，钥匙箱主动上报平台。上报指令如下：“钥匙孔编号;钥匙扣ID”
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "09" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                        Log.e("====", "===============key09-back");
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_09_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed09fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed09SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed09SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key09-back-illegal");
                        isKey09Illegal_back = true;
                        playVoice(R.string.tts_key_back_illegal);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_09_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_09_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed09fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed09SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_ON, 10);
                        // TODO: 2019/2/27  上报？
                    }
                }
                execCommand("echo off > /sys/class/gpio_switch/switch_ct_09", false);
            }
        }
    };

    private final Runnable mKey10PlugInRunnable = new Runnable() {
        public void run() {
            synchronized (mKeyCommunicationLock) {
                execCommand("echo on > /sys/class/gpio_switch/switch_ct_10", false);
                //当钥匙扣插入钥匙箱时候，如果钥匙箱读到钥匙扣的ID 和 钥匙扣的店面 ID 判断一下，是否 等于 钥匙箱的店面 ID， 如果相等， 启动电磁阀， 锁住 钥匙扣，上报 0817 + 01的命令
                //如果店面ID 不一致，闪灯， 提示非法归还
                //如果没有读到店面 ID， 等信息，  启动注册流程
                String shopId = keyShopIdRead();//allKeysShopIdRead();
                String savedShopId = getSavedShopID();
                String keyId = keyIdRead();//allKeysIdRead();
                currentKeyId = keyId;
                currentKeyholeId = "10";
                saveKeyId("key_hole_10", keyId);
                Log.e("=====", "key10--shopId-read=" + shopId + "-----keyId-read=" + keyId);
                if (shopId.contains("ffffffffffffffffffff")) {
                    Log.e("====", "===========key10reg=allKeysShopIdRead()=" + allKeysShopIdRead());
                    //钥匙扣注册
                    String proAndCity = "01000633";//getSavedProvinceAndCity();
                    String manufacturerID = "88888888";//getSavedManufacturerID();
                    String shopID = "77777777777777777777";//getSavedShopID();
                    //getSavedBoxID();
                    sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_10_OFF, 10);
                    mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_OFF, 10);
                    mLedCtrlHandler.removeCallbacks(mGreenLed10fastBlinkRunnable);
                    mLedCtrlHandler.removeCallbacks(mGreenLed10SlowBlinkRunnable);
                    mLedCtrlHandler.postDelayed(mGreenLed10SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                } else if (shopId.contains("fafafafafafafafafafa")) {
                    mKeyBatNullCheckHandler.postDelayed(mKey10BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                } else {
                    Log.e("====", "===key10=savedShopId=" + savedShopId + "-length=" + savedShopId.length());
                    Log.e("====", "===key10=shopId=" + shopId + "-length=" + shopId.length());
                    if (shopId.contains(savedShopId)) {
                        playVoice(R.string.tts_key_back_succeed);
                        //钥匙扣归还
                        //钥匙扣插入钥匙箱01，钥匙箱主动上报平台。上报指令如下：“钥匙孔编号;钥匙扣ID”
                        //sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + tmpKey01Id));
                        sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "10" + "3b" + keyId));
                        //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                        execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                        Log.e("====", "===============key10-back");
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_10_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed10fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed10SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed10SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else {
                        //报警，非法归还
                        Log.e("====", "===============key10-back-illegal");
                        isKey10Illegal_back = true;
                        playVoice(R.string.tts_key_back_illegal);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_10_MOTOR_ON, 100);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_10_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed10fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed10SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_ON, 10);
                        // TODO: 2019/2/27  上报？
                    }
                }
                execCommand("echo off > /sys/class/gpio_switch/switch_ct_10", false);
            }
        }
    };

    private final Runnable mKey01MiddleInRunnable = new Runnable() {
        public void run() {

        }
    };

    private final Runnable mKey02MiddleInRunnable = new Runnable() {
        public void run() {

        }
    };

    private final Runnable mKey03MiddleInRunnable = new Runnable() {
        public void run() {

        }
    };

    private final Runnable mKey04MiddleInRunnable = new Runnable() {
        public void run() {

        }
    };

    private final Runnable mKey05MiddleInRunnable = new Runnable() {
        public void run() {

        }
    };

    private final Runnable mKey06MiddleInRunnable = new Runnable() {
        public void run() {

        }
    };

    private final Runnable mKey07MiddleInRunnable = new Runnable() {
        public void run() {

        }
    };

    private final Runnable mKey08MiddleInRunnable = new Runnable() {
        public void run() {

        }
    };

    private final Runnable mKey09MiddleInRunnable = new Runnable() {
        public void run() {

        }
    };

    private final Runnable mKey10MiddleInRunnable = new Runnable() {
        public void run() {

        }
    };

    private final Runnable mKey01BatNull2BootRunnable = new Runnable() {
        public void run() {
            mKey01BatNullCheckCount--;
            if (mKey01BatNullCheckCount > 0) {
                // TODO: 2019/2/28 done
                synchronized (mKeyCommunicationLock) {
                    execCommand("echo on > /sys/class/gpio_switch/switch_ct_01", false);
                    String keyId = keyIdRead();
                    String shopId = keyShopIdRead();
                    String batLevel = keyBatLeveldRead();
                    boolean keyDataValid = batLevel.contains("02") || batLevel.contains("03");
                    Log.e("====", "==========mKey01BatNull2BootRunnable.keyId=" + keyId + "***shopId=" + shopId + "***batLevel=" + batLevel);
                    String savedShopId = getSavedShopID();
                    saveKeyId("key_hole_01", keyId);
                    //currentKeyId = keyId;
                    //currentKeyholeId = "01";
                    if (shopId.contains("ffffffffffffffffffff") && keyDataValid) {
                        //钥匙扣注册
                        String proAndCity = "01000633";//getSavedProvinceAndCity();
                        String manufacturerID = "88888888";//getSavedManufacturerID();
                        String shopID = "77777777777777777777";//getSavedShopID();
                        //getSavedBoxID();
                        sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                        mKeyBatNullCheckHandler.removeCallbacks(mKey01BatNull2BootRunnable);
                        mKey01BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_01_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed01fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed01SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed01SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else if (shopId.contains("fafafafafafafafafafa")) {
                        //mKeyBatNullCheckHandler.postDelayed(mKey01BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                        mKeyBatNullCheckHandler.removeCallbacks(mKey01BatNull2BootRunnable);
                        mKey01BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        playVoice(R.string.tts_key_communicate_err);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_01_MOTOR_ON, 1000);
                        isKey01CommunicateErr = true;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_01_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed01fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed01SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_ON, 10);
                    } else if (keyId.contains("00ffffffffff")) {
                        mKeyBatNullCheckHandler.postDelayed(mKey01BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                    } else {
                        if (shopId.contains(savedShopId)) {
                            playVoice(R.string.tts_key_back_succeed);
                            //钥匙扣归还
                            sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "01" + "3b" + keyId));
                            //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                            execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_01_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed01fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed01SlowBlinkRunnable);
                            mLedCtrlHandler.postDelayed(mGreenLed01SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                        } else {
                            //报警，非法归还
                            isKey01Illegal_back = true;
                            playVoice(R.string.tts_key_back_illegal);
                            mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_01_MOTOR_ON, 100);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_01_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed01fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed01SlowBlinkRunnable);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_ON, 10);
                            // TODO: 2019/2/28 需要上报服务器？
                        }
                        mKeyBatNullCheckHandler.removeCallbacks(mKey01BatNull2BootRunnable);
                        mKey01BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                    }
                    execCommand("echo of > /sys/class/gpio_switch/switch_ct_01", false);
                }
            } else {
                mKeyBatNullCheckHandler.removeCallbacks(mKey01BatNull2BootRunnable);
                mKey01BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                playVoice(R.string.tts_key_communicate_err);
                mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_01_MOTOR_ON, 1000);
                isKey01CommunicateErr = true;
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_01_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_OFF, 10);
                mLedCtrlHandler.removeCallbacks(mGreenLed01fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed01SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_01_ON, 10);
            }
        }
    };

    private final Runnable mKey02BatNull2BootRunnable = new Runnable() {
        public void run() {
            mKey02BatNullCheckCount--;
            if (mKey02BatNullCheckCount > 0) {
                // TODO: 2019/2/28 done
                synchronized (mKeyCommunicationLock) {
                    execCommand("echo on > /sys/class/gpio_switch/switch_ct_02", false);
                    String keyId = keyIdRead();
                    String shopId = keyShopIdRead();
                    String batLevel = keyBatLeveldRead();
                    boolean keyDataValid = batLevel.contains("02") || batLevel.contains("03");
                    Log.e("====", "==========mKey02BatNull2BootRunnable.keyId=" + keyId + "***shopId=" + shopId + "***batLevel=" + batLevel);
                    String savedShopId = getSavedShopID();
                    saveKeyId("key_hole_02", keyId);
                    //currentKeyId = keyId;
                    //currentKeyholeId = "01";
                    if (shopId.contains("ffffffffffffffffffff") && keyDataValid) {
                        //钥匙扣注册
                        String proAndCity = "01000633";//getSavedProvinceAndCity();
                        String manufacturerID = "88888888";//getSavedManufacturerID();
                        String shopID = "77777777777777777777";//getSavedShopID();
                        //getSavedBoxID();
                        sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                        mKeyBatNullCheckHandler.removeCallbacks(mKey02BatNull2BootRunnable);
                        mKey02BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_02_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed02fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed02SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed02SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else if (shopId.contains("fafafafafafafafafafa")) {
                        //mKeyBatNullCheckHandler.postDelayed(mKey01BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                        mKeyBatNullCheckHandler.removeCallbacks(mKey02BatNull2BootRunnable);
                        mKey02BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        playVoice(R.string.tts_key_communicate_err);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_02_MOTOR_ON, 1000);
                        isKey02CommunicateErr = true;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_02_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed02fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed02SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_ON, 10);
                    } else if (keyId.contains("00ffffffffff")) {
                        mKeyBatNullCheckHandler.postDelayed(mKey02BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                    } else {
                        if (shopId.contains(savedShopId)) {
                            playVoice(R.string.tts_key_back_succeed);
                            //钥匙扣归还
                            sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "02" + "3b" + keyId));
                            //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                            execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_02_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed02fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed02SlowBlinkRunnable);
                            mLedCtrlHandler.postDelayed(mGreenLed02SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                        } else {
                            //报警，非法归还
                            isKey02Illegal_back = true;
                            playVoice(R.string.tts_key_back_illegal);
                            mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_02_MOTOR_ON, 100);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_02_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed02fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed02SlowBlinkRunnable);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_ON, 10);
                            // TODO: 2019/2/28 需要上报服务器？
                        }
                        mKeyBatNullCheckHandler.removeCallbacks(mKey02BatNull2BootRunnable);
                        mKey02BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                    }
                    execCommand("echo of > /sys/class/gpio_switch/switch_ct_02", false);
                }
            } else {
                mKeyBatNullCheckHandler.removeCallbacks(mKey02BatNull2BootRunnable);
                mKey02BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                playVoice(R.string.tts_key_communicate_err);
                mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_02_MOTOR_ON, 1000);
                isKey02CommunicateErr = true;
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_02_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_OFF, 10);
                mLedCtrlHandler.removeCallbacks(mGreenLed02fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed02SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_02_ON, 10);
            }
        }
    };

    private final Runnable mKey03BatNull2BootRunnable = new Runnable() {
        public void run() {
            mKey03BatNullCheckCount--;
            if (mKey03BatNullCheckCount > 0) {
                // TODO: 2019/2/28 done
                synchronized (mKeyCommunicationLock) {
                    execCommand("echo on > /sys/class/gpio_switch/switch_ct_03", false);
                    String keyId = keyIdRead();
                    String shopId = keyShopIdRead();
                    String batLevel = keyBatLeveldRead();
                    boolean keyDataValid = batLevel.contains("02") || batLevel.contains("03");
                    Log.e("====", "==========mKey03BatNull2BootRunnable.keyId=" + keyId + "***shopId=" + shopId + "***batLevel=" + batLevel);
                    String savedShopId = getSavedShopID();
                    saveKeyId("key_hole_03", keyId);
                    //currentKeyId = keyId;
                    //currentKeyholeId = "01";
                    if (shopId.contains("ffffffffffffffffffff") && keyDataValid) {
                        //钥匙扣注册
                        String proAndCity = "01000633";//getSavedProvinceAndCity();
                        String manufacturerID = "88888888";//getSavedManufacturerID();
                        String shopID = "77777777777777777777";//getSavedShopID();
                        //getSavedBoxID();
                        sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                        mKeyBatNullCheckHandler.removeCallbacks(mKey03BatNull2BootRunnable);
                        mKey03BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_03_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed03fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed03SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed03SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else if (shopId.contains("fafafafafafafafafafa")) {
                        //mKeyBatNullCheckHandler.postDelayed(mKey01BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                        mKeyBatNullCheckHandler.removeCallbacks(mKey03BatNull2BootRunnable);
                        mKey03BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        playVoice(R.string.tts_key_communicate_err);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_03_MOTOR_ON, 1000);
                        isKey03CommunicateErr = true;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_03_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed03fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed03SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_ON, 10);
                    } else if (keyId.contains("00ffffffffff")) {
                        mKeyBatNullCheckHandler.postDelayed(mKey03BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                    } else {
                        if (shopId.contains(savedShopId)) {
                            playVoice(R.string.tts_key_back_succeed);
                            //钥匙扣归还
                            sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "03" + "3b" + keyId));
                            //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                            execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_03_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed03fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed03SlowBlinkRunnable);
                            mLedCtrlHandler.postDelayed(mGreenLed03SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                        } else {
                            //报警，非法归还
                            isKey03Illegal_back = true;
                            playVoice(R.string.tts_key_back_illegal);
                            mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_03_MOTOR_ON, 100);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_03_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed03fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed03SlowBlinkRunnable);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_ON, 10);
                            // TODO: 2019/2/28 需要上报服务器？
                        }
                        mKeyBatNullCheckHandler.removeCallbacks(mKey03BatNull2BootRunnable);
                        mKey03BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                    }
                    execCommand("echo of > /sys/class/gpio_switch/switch_ct_03", false);
                }
            } else {
                mKeyBatNullCheckHandler.removeCallbacks(mKey03BatNull2BootRunnable);
                mKey03BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                playVoice(R.string.tts_key_communicate_err);
                mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_03_MOTOR_ON, 1000);
                isKey03CommunicateErr = true;
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_03_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_OFF, 10);
                mLedCtrlHandler.removeCallbacks(mGreenLed03fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed03SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_03_ON, 10);
            }
        }
    };

    private final Runnable mKey04BatNull2BootRunnable = new Runnable() {
        public void run() {
            mKey04BatNullCheckCount--;
            if (mKey04BatNullCheckCount > 0) {
                // TODO: 2019/2/28 done
                synchronized (mKeyCommunicationLock) {
                    execCommand("echo on > /sys/class/gpio_switch/switch_ct_04", false);
                    String keyId = keyIdRead();
                    String shopId = keyShopIdRead();
                    String batLevel = keyBatLeveldRead();
                    boolean keyDataValid = batLevel.contains("02") || batLevel.contains("03");
                    Log.e("====", "==========mKey04BatNull2BootRunnable.keyId=" + keyId + "***shopId=" + shopId + "***batLevel=" + batLevel);
                    String savedShopId = getSavedShopID();
                    saveKeyId("key_hole_04", keyId);
                    //currentKeyId = keyId;
                    //currentKeyholeId = "01";
                    if (shopId.contains("ffffffffffffffffffff") && keyDataValid) {
                        //钥匙扣注册
                        String proAndCity = "01000633";//getSavedProvinceAndCity();
                        String manufacturerID = "88888888";//getSavedManufacturerID();
                        String shopID = "77777777777777777777";//getSavedShopID();
                        //getSavedBoxID();
                        sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                        mKeyBatNullCheckHandler.removeCallbacks(mKey04BatNull2BootRunnable);
                        mKey04BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_04_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed04fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed04SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed04SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else if (shopId.contains("fafafafafafafafafafa")) {
                        //mKeyBatNullCheckHandler.postDelayed(mKey01BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                        mKeyBatNullCheckHandler.removeCallbacks(mKey04BatNull2BootRunnable);
                        mKey04BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        playVoice(R.string.tts_key_communicate_err);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_MOTOR_ON, 1000);
                        isKey04CommunicateErr = true;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_04_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed04fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed04SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_ON, 10);
                    } else if (keyId.contains("00ffffffffff")) {
                        mKeyBatNullCheckHandler.postDelayed(mKey04BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                    } else {
                        if (shopId.contains(savedShopId)) {
                            playVoice(R.string.tts_key_back_succeed);
                            //钥匙扣归还
                            sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "04" + "3b" + keyId));
                            //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                            execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_04_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed04fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed04SlowBlinkRunnable);
                            mLedCtrlHandler.postDelayed(mGreenLed04SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                        } else {
                            //报警，非法归还
                            isKey04Illegal_back = true;
                            playVoice(R.string.tts_key_back_illegal);
                            mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_MOTOR_ON, 100);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_04_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed04fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed04SlowBlinkRunnable);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_ON, 10);
                            // TODO: 2019/2/28 需要上报服务器？
                        }
                        mKeyBatNullCheckHandler.removeCallbacks(mKey04BatNull2BootRunnable);
                        mKey04BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                    }
                    execCommand("echo of > /sys/class/gpio_switch/switch_ct_04", false);
                }
            } else {
                mKeyBatNullCheckHandler.removeCallbacks(mKey04BatNull2BootRunnable);
                mKey04BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                playVoice(R.string.tts_key_communicate_err);
                mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_04_MOTOR_ON, 1000);
                isKey04CommunicateErr = true;
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_04_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_OFF, 10);
                mLedCtrlHandler.removeCallbacks(mGreenLed04fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed04SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_04_ON, 10);
            }
        }
    };

    private final Runnable mKey05BatNull2BootRunnable = new Runnable() {
        public void run() {
            mKey05BatNullCheckCount--;
            if (mKey05BatNullCheckCount > 0) {
                // TODO: 2019/2/28 done
                synchronized (mKeyCommunicationLock) {
                    execCommand("echo on > /sys/class/gpio_switch/switch_ct_05", false);
                    String keyId = keyIdRead();
                    String shopId = keyShopIdRead();
                    String batLevel = keyBatLeveldRead();
                    boolean keyDataValid = batLevel.contains("02") || batLevel.contains("03");
                    Log.e("====", "==========mKey05BatNull2BootRunnable.keyId=" + keyId + "***shopId=" + shopId + "***batLevel=" + batLevel);
                    String savedShopId = getSavedShopID();
                    saveKeyId("key_hole_05", keyId);
                    //currentKeyId = keyId;
                    //currentKeyholeId = "01";
                    if (shopId.contains("ffffffffffffffffffff") && keyDataValid) {
                        //钥匙扣注册
                        String proAndCity = "01000633";//getSavedProvinceAndCity();
                        String manufacturerID = "88888888";//getSavedManufacturerID();
                        String shopID = "77777777777777777777";//getSavedShopID();
                        //getSavedBoxID();
                        sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                        mKeyBatNullCheckHandler.removeCallbacks(mKey05BatNull2BootRunnable);
                        mKey05BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_05_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed05fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed05SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed05SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else if (shopId.contains("fafafafafafafafafafa")) {
                        //mKeyBatNullCheckHandler.postDelayed(mKey01BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                        mKeyBatNullCheckHandler.removeCallbacks(mKey05BatNull2BootRunnable);
                        mKey05BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        playVoice(R.string.tts_key_communicate_err);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_05_MOTOR_ON, 1000);
                        isKey05CommunicateErr = true;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_05_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed05fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed05SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_ON, 10);
                    } else if (keyId.contains("00ffffffffff")) {
                        mKeyBatNullCheckHandler.postDelayed(mKey05BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                    } else {
                        if (shopId.contains(savedShopId)) {
                            playVoice(R.string.tts_key_back_succeed);
                            //钥匙扣归还
                            sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "05" + "3b" + keyId));
                            //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                            execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_05_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed05fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed05SlowBlinkRunnable);
                            mLedCtrlHandler.postDelayed(mGreenLed05SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                        } else {
                            //报警，非法归还
                            isKey05Illegal_back = true;
                            playVoice(R.string.tts_key_back_illegal);
                            mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_05_MOTOR_ON, 100);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_05_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed05fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed05SlowBlinkRunnable);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_ON, 10);
                            // TODO: 2019/2/28 需要上报服务器？
                        }
                        mKeyBatNullCheckHandler.removeCallbacks(mKey05BatNull2BootRunnable);
                        mKey05BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                    }
                    execCommand("echo of > /sys/class/gpio_switch/switch_ct_05", false);
                }
            } else {
                mKeyBatNullCheckHandler.removeCallbacks(mKey05BatNull2BootRunnable);
                mKey05BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                playVoice(R.string.tts_key_communicate_err);
                mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_05_MOTOR_ON, 1000);
                isKey05CommunicateErr = true;
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_05_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_OFF, 10);
                mLedCtrlHandler.removeCallbacks(mGreenLed05fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed05SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_05_ON, 10);
            }
        }
    };

    private final Runnable mKey06BatNull2BootRunnable = new Runnable() {
        public void run() {
            mKey06BatNullCheckCount--;
            if (mKey06BatNullCheckCount > 0) {
                // TODO: 2019/2/28 done
                synchronized (mKeyCommunicationLock) {
                    execCommand("echo on > /sys/class/gpio_switch/switch_ct_06", false);
                    String keyId = keyIdRead();
                    String shopId = keyShopIdRead();
                    String batLevel = keyBatLeveldRead();
                    boolean keyDataValid = batLevel.contains("02") || batLevel.contains("03");
                    Log.e("====", "==========mKey06BatNull2BootRunnable.keyId=" + keyId + "***shopId=" + shopId + "***batLevel=" + batLevel);
                    String savedShopId = getSavedShopID();
                    saveKeyId("key_hole_06", keyId);
                    //currentKeyId = keyId;
                    //currentKeyholeId = "01";
                    if (shopId.contains("ffffffffffffffffffff") && keyDataValid) {
                        //钥匙扣注册
                        String proAndCity = "01000633";//getSavedProvinceAndCity();
                        String manufacturerID = "88888888";//getSavedManufacturerID();
                        String shopID = "77777777777777777777";//getSavedShopID();
                        //getSavedBoxID();
                        sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                        mKeyBatNullCheckHandler.removeCallbacks(mKey06BatNull2BootRunnable);
                        mKey06BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_06_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed06fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed06SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed06SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else if (shopId.contains("fafafafafafafafafafa")) {
                        //mKeyBatNullCheckHandler.postDelayed(mKey01BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                        mKeyBatNullCheckHandler.removeCallbacks(mKey06BatNull2BootRunnable);
                        mKey06BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        playVoice(R.string.tts_key_communicate_err);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_06_MOTOR_ON, 1000);
                        isKey06CommunicateErr = true;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_06_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed06fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed06SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_ON, 10);
                    } else if (keyId.contains("00ffffffffff")) {
                        mKeyBatNullCheckHandler.postDelayed(mKey06BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                    } else {
                        if (shopId.contains(savedShopId)) {
                            playVoice(R.string.tts_key_back_succeed);
                            //钥匙扣归还
                            sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "06" + "3b" + keyId));
                            //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                            execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_06_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed06fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed06SlowBlinkRunnable);
                            mLedCtrlHandler.postDelayed(mGreenLed06SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                        } else {
                            //报警，非法归还
                            isKey06Illegal_back = true;
                            playVoice(R.string.tts_key_back_illegal);
                            mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_06_MOTOR_ON, 100);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_06_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed06fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed06SlowBlinkRunnable);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_ON, 10);
                            // TODO: 2019/2/28 需要上报服务器？
                        }
                        mKeyBatNullCheckHandler.removeCallbacks(mKey06BatNull2BootRunnable);
                        mKey06BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                    }
                    execCommand("echo of > /sys/class/gpio_switch/switch_ct_06", false);
                }
            } else {
                mKeyBatNullCheckHandler.removeCallbacks(mKey06BatNull2BootRunnable);
                mKey06BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                playVoice(R.string.tts_key_communicate_err);
                mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_06_MOTOR_ON, 1000);
                isKey06CommunicateErr = true;
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_06_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_OFF, 10);
                mLedCtrlHandler.removeCallbacks(mGreenLed06fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed06SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_06_ON, 10);
            }
        }
    };

    private final Runnable mKey07BatNull2BootRunnable = new Runnable() {
        public void run() {
            mKey07BatNullCheckCount--;
            if (mKey07BatNullCheckCount > 0) {
                // TODO: 2019/2/28 done
                synchronized (mKeyCommunicationLock) {
                    execCommand("echo on > /sys/class/gpio_switch/switch_ct_07", false);
                    String keyId = keyIdRead();
                    String shopId = keyShopIdRead();
                    String batLevel = keyBatLeveldRead();
                    boolean keyDataValid = batLevel.contains("02") || batLevel.contains("03");
                    Log.e("====", "==========mKey07BatNull2BootRunnable.keyId=" + keyId + "***shopId=" + shopId + "***batLevel=" + batLevel);
                    String savedShopId = getSavedShopID();
                    saveKeyId("key_hole_07", keyId);
                    //currentKeyId = keyId;
                    //currentKeyholeId = "01";
                    if (shopId.contains("ffffffffffffffffffff") && keyDataValid) {
                        //钥匙扣注册
                        String proAndCity = "01000633";//getSavedProvinceAndCity();
                        String manufacturerID = "88888888";//getSavedManufacturerID();
                        String shopID = "77777777777777777777";//getSavedShopID();
                        //getSavedBoxID();
                        sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                        mKeyBatNullCheckHandler.removeCallbacks(mKey07BatNull2BootRunnable);
                        mKey07BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_07_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed07fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed07SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed07SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else if (shopId.contains("fafafafafafafafafafa")) {
                        //mKeyBatNullCheckHandler.postDelayed(mKey01BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                        mKeyBatNullCheckHandler.removeCallbacks(mKey07BatNull2BootRunnable);
                        mKey07BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        playVoice(R.string.tts_key_communicate_err);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_07_MOTOR_ON, 1000);
                        isKey07CommunicateErr = true;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_07_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed07fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed07SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_ON, 10);
                    } else if (keyId.contains("00ffffffffff")) {
                        mKeyBatNullCheckHandler.postDelayed(mKey07BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                    } else {
                        if (shopId.contains(savedShopId)) {
                            playVoice(R.string.tts_key_back_succeed);
                            //钥匙扣归还
                            sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "07" + "3b" + keyId));
                            //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                            execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_07_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed07fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed07SlowBlinkRunnable);
                            mLedCtrlHandler.postDelayed(mGreenLed07SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                        } else {
                            //报警，非法归还
                            isKey07Illegal_back = true;
                            playVoice(R.string.tts_key_back_illegal);
                            mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_07_MOTOR_ON, 100);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_07_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed07fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed07SlowBlinkRunnable);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_ON, 10);
                            // TODO: 2019/2/28 需要上报服务器？
                        }
                        mKeyBatNullCheckHandler.removeCallbacks(mKey07BatNull2BootRunnable);
                        mKey07BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                    }
                    execCommand("echo of > /sys/class/gpio_switch/switch_ct_07", false);
                }
            } else {
                mKeyBatNullCheckHandler.removeCallbacks(mKey07BatNull2BootRunnable);
                mKey07BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                playVoice(R.string.tts_key_communicate_err);
                mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_07_MOTOR_ON, 1000);
                isKey07CommunicateErr = true;
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_07_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_OFF, 10);
                mLedCtrlHandler.removeCallbacks(mGreenLed07fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed07SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_07_ON, 10);
            }
        }
    };

    private final Runnable mKey08BatNull2BootRunnable = new Runnable() {
        public void run() {
            mKey08BatNullCheckCount--;
            if (mKey08BatNullCheckCount > 0) {
                // TODO: 2019/2/28 done
                synchronized (mKeyCommunicationLock) {
                    execCommand("echo on > /sys/class/gpio_switch/switch_ct_08", false);
                    String keyId = keyIdRead();
                    String shopId = keyShopIdRead();
                    String batLevel = keyBatLeveldRead();
                    boolean keyDataValid = batLevel.contains("02") || batLevel.contains("03");
                    Log.e("====", "==========mKey08BatNull2BootRunnable.keyId=" + keyId + "***shopId=" + shopId + "***batLevel=" + batLevel);
                    String savedShopId = getSavedShopID();
                    saveKeyId("key_hole_08", keyId);
                    //currentKeyId = keyId;
                    //currentKeyholeId = "01";
                    if (shopId.contains("ffffffffffffffffffff") && keyDataValid) {
                        //钥匙扣注册
                        String proAndCity = "01000633";//getSavedProvinceAndCity();
                        String manufacturerID = "88888888";//getSavedManufacturerID();
                        String shopID = "77777777777777777777";//getSavedShopID();
                        //getSavedBoxID();
                        sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                        mKeyBatNullCheckHandler.removeCallbacks(mKey08BatNull2BootRunnable);
                        mKey08BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_08_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed08fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed08SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed08SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else if (shopId.contains("fafafafafafafafafafa")) {
                        //mKeyBatNullCheckHandler.postDelayed(mKey01BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                        mKeyBatNullCheckHandler.removeCallbacks(mKey08BatNull2BootRunnable);
                        mKey08BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        playVoice(R.string.tts_key_communicate_err);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_08_MOTOR_ON, 1000);
                        isKey08CommunicateErr = true;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_08_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed08fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed08SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_ON, 10);
                    } else if (keyId.contains("00ffffffffff")) {
                        mKeyBatNullCheckHandler.postDelayed(mKey08BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                    } else {
                        if (shopId.contains(savedShopId)) {
                            playVoice(R.string.tts_key_back_succeed);
                            //钥匙扣归还
                            sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "08" + "3b" + keyId));
                            //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                            execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_08_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed08fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed08SlowBlinkRunnable);
                            mLedCtrlHandler.postDelayed(mGreenLed08SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                        } else {
                            //报警，非法归还
                            isKey08Illegal_back = true;
                            playVoice(R.string.tts_key_back_illegal);
                            mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_08_MOTOR_ON, 100);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_08_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed08fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed08SlowBlinkRunnable);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_ON, 10);
                            // TODO: 2019/2/28 需要上报服务器？
                        }
                        mKeyBatNullCheckHandler.removeCallbacks(mKey08BatNull2BootRunnable);
                        mKey08BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                    }
                    execCommand("echo of > /sys/class/gpio_switch/switch_ct_08", false);
                }
            } else {
                mKeyBatNullCheckHandler.removeCallbacks(mKey08BatNull2BootRunnable);
                mKey08BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                playVoice(R.string.tts_key_communicate_err);
                mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_08_MOTOR_ON, 1000);
                isKey08CommunicateErr = true;
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_08_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_OFF, 10);
                mLedCtrlHandler.removeCallbacks(mGreenLed08fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed08SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_08_ON, 10);
            }
        }
    };

    private final Runnable mKey09BatNull2BootRunnable = new Runnable() {
        public void run() {
            mKey09BatNullCheckCount--;
            if (mKey09BatNullCheckCount > 0) {
                // TODO: 2019/2/28 done
                synchronized (mKeyCommunicationLock) {
                    execCommand("echo on > /sys/class/gpio_switch/switch_ct_09", false);
                    String keyId = keyIdRead();
                    String shopId = keyShopIdRead();
                    String batLevel = keyBatLeveldRead();
                    boolean keyDataValid = batLevel.contains("02") || batLevel.contains("03");
                    Log.e("====", "==========mKey09BatNull2BootRunnable.keyId=" + keyId + "***shopId=" + shopId + "***batLevel=" + batLevel);
                    String savedShopId = getSavedShopID();
                    saveKeyId("key_hole_09", keyId);
                    //currentKeyId = keyId;
                    //currentKeyholeId = "01";
                    if (shopId.contains("ffffffffffffffffffff") && keyDataValid) {
                        //钥匙扣注册
                        String proAndCity = "01000633";//getSavedProvinceAndCity();
                        String manufacturerID = "88888888";//getSavedManufacturerID();
                        String shopID = "77777777777777777777";//getSavedShopID();
                        //getSavedBoxID();
                        sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                        mKeyBatNullCheckHandler.removeCallbacks(mKey09BatNull2BootRunnable);
                        mKey09BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_09_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed09fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed09SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed09SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else if (shopId.contains("fafafafafafafafafafa")) {
                        //mKeyBatNullCheckHandler.postDelayed(mKey01BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                        mKeyBatNullCheckHandler.removeCallbacks(mKey09BatNull2BootRunnable);
                        mKey09BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        playVoice(R.string.tts_key_communicate_err);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_09_MOTOR_ON, 1000);
                        isKey09CommunicateErr = true;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_09_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed09fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed09SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_ON, 10);
                    } else if (keyId.contains("00ffffffffff")) {
                        mKeyBatNullCheckHandler.postDelayed(mKey09BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                    } else {
                        if (shopId.contains(savedShopId)) {
                            playVoice(R.string.tts_key_back_succeed);
                            //钥匙扣归还
                            sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "09" + "3b" + keyId));
                            //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                            execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_09_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed09fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed09SlowBlinkRunnable);
                            mLedCtrlHandler.postDelayed(mGreenLed09SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                        } else {
                            //报警，非法归还
                            isKey09Illegal_back = true;
                            playVoice(R.string.tts_key_back_illegal);
                            mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_09_MOTOR_ON, 100);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_09_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed09fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed09SlowBlinkRunnable);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_ON, 10);
                            // TODO: 2019/2/28 需要上报服务器？
                        }
                        mKeyBatNullCheckHandler.removeCallbacks(mKey09BatNull2BootRunnable);
                        mKey09BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                    }
                    execCommand("echo of > /sys/class/gpio_switch/switch_ct_09", false);
                }
            } else {
                mKeyBatNullCheckHandler.removeCallbacks(mKey09BatNull2BootRunnable);
                mKey09BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                playVoice(R.string.tts_key_communicate_err);
                mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_09_MOTOR_ON, 1000);
                isKey09CommunicateErr = true;
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_09_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_OFF, 10);
                mLedCtrlHandler.removeCallbacks(mGreenLed09fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed09SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_09_ON, 10);
            }
        }
    };

    private final Runnable mKey10BatNull2BootRunnable = new Runnable() {
        public void run() {
            mKey10BatNullCheckCount--;
            if (mKey10BatNullCheckCount > 0) {
                // TODO: 2019/2/28 done
                synchronized (mKeyCommunicationLock) {
                    execCommand("echo on > /sys/class/gpio_switch/switch_ct_10", false);
                    String keyId = keyIdRead();
                    String shopId = keyShopIdRead();
                    String batLevel = keyBatLeveldRead();
                    boolean keyDataValid = batLevel.contains("02") || batLevel.contains("03");
                    Log.e("====", "==========mKey10BatNull2BootRunnable.keyId=" + keyId + "***shopId=" + shopId + "***batLevel=" + batLevel);
                    String savedShopId = getSavedShopID();
                    saveKeyId("key_hole_10", keyId);
                    //currentKeyId = keyId;
                    //currentKeyholeId = "01";
                    if (shopId.contains("ffffffffffffffffffff") && keyDataValid) {
                        //钥匙扣注册
                        String proAndCity = "01000633";//getSavedProvinceAndCity();
                        String manufacturerID = "88888888";//getSavedManufacturerID();
                        String shopID = "77777777777777777777";//getSavedShopID();
                        //getSavedBoxID();
                        sendMessage(getAllBytes(0x0100, mHeadMsgSeqInt, tmpPhoneId, proAndCity + manufacturerID + shopID + keyId));
                        mKeyBatNullCheckHandler.removeCallbacks(mKey10BatNull2BootRunnable);
                        mKey10BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_10_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed10fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed10SlowBlinkRunnable);
                        mLedCtrlHandler.postDelayed(mGreenLed10SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                    } else if (shopId.contains("fafafafafafafafafafa")) {
                        //mKeyBatNullCheckHandler.postDelayed(mKey01BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                        mKeyBatNullCheckHandler.removeCallbacks(mKey10BatNull2BootRunnable);
                        mKey10BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                        playVoice(R.string.tts_key_communicate_err);
                        mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_10_MOTOR_ON, 1000);
                        isKey10CommunicateErr = true;
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_10_OFF, 10);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_OFF, 10);
                        mLedCtrlHandler.removeCallbacks(mGreenLed10fastBlinkRunnable);
                        mLedCtrlHandler.removeCallbacks(mGreenLed10SlowBlinkRunnable);
                        mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_ON, 10);
                    } else if (keyId.contains("00ffffffffff")) {
                        mKeyBatNullCheckHandler.postDelayed(mKey10BatNull2BootRunnable, KEY_BAT_NULL_CHECK_TIME);
                    } else {
                        if (shopId.contains(savedShopId)) {
                            playVoice(R.string.tts_key_back_succeed);
                            //钥匙扣归还
                            sendMessage(getAllBytes(0x0817, mHeadMsgSeqInt, tmpPhoneId, "01" + "10" + "3b" + keyId));
                            //向钥匙扣st(电量接口)写02,钥匙扣收到之后休眠
                            execCommand("echo 2 > /sys/bus/i2c/devices/6-005b/mcu/key_st", false);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_10_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed10fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed10SlowBlinkRunnable);
                            mLedCtrlHandler.postDelayed(mGreenLed10SlowBlinkRunnable, LED_SLOW_BLINK_TIME);
                        } else {
                            //报警，非法归还
                            isKey10Illegal_back = true;
                            playVoice(R.string.tts_key_back_illegal);
                            mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_10_MOTOR_ON, 100);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_10_OFF, 10);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_OFF, 10);
                            mLedCtrlHandler.removeCallbacks(mGreenLed10fastBlinkRunnable);
                            mLedCtrlHandler.removeCallbacks(mGreenLed10SlowBlinkRunnable);
                            mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_ON, 10);
                            // TODO: 2019/2/28 需要上报服务器？
                        }
                        mKeyBatNullCheckHandler.removeCallbacks(mKey10BatNull2BootRunnable);
                        mKey10BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                    }
                    execCommand("echo of > /sys/class/gpio_switch/switch_ct_10", false);
                }
            } else {
                mKeyBatNullCheckHandler.removeCallbacks(mKey10BatNull2BootRunnable);
                mKey10BatNullCheckCount = KEY_BAT_NULL_CHECK_COUNT;
                playVoice(R.string.tts_key_communicate_err);
                mMotorCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_KEY_10_MOTOR_ON, 1000);
                isKey10CommunicateErr = true;
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_GREEN_10_OFF, 10);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_OFF, 10);
                mLedCtrlHandler.removeCallbacks(mGreenLed10fastBlinkRunnable);
                mLedCtrlHandler.removeCallbacks(mGreenLed10SlowBlinkRunnable);
                mLedCtrlHandler.sendEmptyMessageDelayed(HANDLER_MSG_LED_RED_10_ON, 10);
            }
        }
    };

    /*public boolean keyInfoReadSucceed(String keyHole) {
        ShellUtils.execCommand("echo on > /sys/class/gpio_switch/switch_ct_" + keyHole, false);
        keyIdRead();
        keyShopIdRead();
        keyBatLeveldRead();
        ShellUtils.execCommand("echo of > /sys/class/gpio_switch/switch_ct_" + keyHole, false);
    }*/

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

    private void keyOutMotorOn(String keyHole) {
        switch (keyHole) {
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
    }

    private void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startTTS() {
        Intent ttsService = new Intent(mContext, com.erobbing.tcpsmartkey.service.MyTtsService.class);
        mContext.startService(ttsService);
    }

    private void playVoice(String str) {
        MyTtsService.startWithPlay(mContext, str);
    }

    private void playVoice(int resId) {
        MyTtsService.startWithPlay(mContext, resId);
    }
}
