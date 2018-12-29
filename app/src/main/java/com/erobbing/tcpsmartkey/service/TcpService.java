package com.erobbing.tcpsmartkey.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import com.erobbing.tcpsmartkey.common.TcpManager;

import java.io.IOException;

/**
 * Created by zhangzhaolei on 2018/12/25.
 */

public class TcpService extends Service {
    private static final String TAG = "TcpService";
    private Context mContext;
    private AlarmManager mAlarmManager;
    private String mLocationFrequency = "120000";
    private static final String IP = "172.23.130.2";
    private static final int PORT = 1500;

    private static final String REG_TMP = "7e0100002c0200000000150025002c0133373039363054372d54383038000000000000000000000000003033323931373001d4c142383838387b7e";
    //标识位,消息头尾各一个
    private static final String MSG_FLAG = "7e";

    //reg 注册 head 12byte
    //byte[0-1]   消息ID word(16)
    private String mRegHeadMsgID = "0100";
    //byte[2-3]   消息体属性 word(16)
    private String mRegHeadAttributes = "";
    //bit[0-9]    消息体长度
    private String mRegHeadAttributesBodyLength = "";
    //bit[10-12]  数据加密方式, 此三位都为 0，表示消息体不加密;第 10 位为 1，表示消息体经过 RSA 算法加密,其它保留
    private String mRegHeadAttributesEncryptionType = "";
    //bit[13]   分包
    //          1：消息体卫长消息，进行分包发送处理，具体分包信息由消息包封装项决定
    //          0：则消息头中无消息包封装项字段
    private String mRegHeadAttributesSubPac = "0";
    //bit[14-15]  保留
    private String mRegHeadAttributesLast = "0";

    //byte[4-9]   终端手机号或设备ID bcd[6]
    private String mRegHeadPhoneNum = "000123456789";
    //byte[10-11]     消息流水号 word(16) 按发送顺序从 0 开始循环累加
    private String mRegHeadMsgSeq = "0001";
    //byte[12-15]     消息包封装项
    private String mRegHeadSubPac = "00000000";
    //bit[0-1]   消息包总数(word(16)) 该消息分包后得总包数
    private String mRegHeadSubPacNum = "00";
    //bit[2-3]   包序号(word(16)) 从 1 开始
    private String mRegHeadSubPacSeq = "00";

    //msg reg body
    //byte[0-1]     省域ID  word(16) 标示终端安装车辆所在的省域，0 保留，由平台取默
    //认值。省域 ID 采用 GB/T 2260 中规定的行政区划代 码六位中前两位
    private String mRegBodyProvinceID = "000b";//370  202青岛市南区(0025 00ca)//110108 北京海淀区
    //byte[2-3]     市县域ID  word(16)
    private String mRegBodyCityID = "006c";//北京海淀区
    //byte[4-7]     制造商ID BYTE[4],4个字节，终端制造商编码
    private String mRegBodyManufacturerID = "00000000";
    //byte[8-17] BYTE[10] 10 个字节，此字段暂定10个字节，是终端使用店面的ID
    private String mRegBodyShopID = "00000000000000000000";
    //byte[18-23] BYTE[6] 6个字节,最高两位表示终端类型，00表示钥匙扣，01表示24孔钥匙箱，99表示空,
    //剩余十位数字，制造商自行定义[建议 1位表示批次，2-5 年月  6-10 序列号]，
    private String mRegBodyDeviceID = "011181200001";

    //reg checksum
    private String mRegCheckSum = "00";

    //server Authentication 鉴权
    private String mRegResponseMsgID = "8100";

    //终端普通应答
    private static final String MSG_TYPE_CLIENT_RESPONSE = "0001";
    //心跳
    private static final String MSG_TYPE_HEARTBEAT = "0002";
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
    //终端主动上报
    private static final String MSG_TYPE_CLIENT_REQ = "0817";
    //设置终端参数
    private static final String MSG_TYPE_CLIENT_SETTINGS = "8103";


    public TcpService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        Log.d(TAG, "TcpService.onCreate");
        mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        connect(IP, PORT);
        TcpManager.init().setDisconnectedCallback(new TcpManager.OnServerDisconnectedCallbackBlock() {
            @Override
            public void callback(IOException e) {
                Log.e("====", "=========service=断开连接" + "\n");
            }
        });
        TcpManager.init().setConnectedCallback(new TcpManager.OnServerConnectedCallbackBlock() {
            @Override
            public void callback() {
                Log.e("====", "=========service=连接成功" + "\n");
                //sendMessage(REG_TMP);
            }
        });
        TcpManager.init().setReceivedCallback(new TcpManager.OnReceiveCallbackBlock() {
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
                processData(receicedMessage);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "TcpService.onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "TcpService.onDestroy");
        super.onDestroy();
    }

    public void sendMessage(String msg) {
        if (msg != null) {
            TcpManager.init().send(msg.getBytes());
        }
    }

    public void connect(String ip, int port) {
        if (ip != null && port > 0) {
            TcpManager.init().connect(ip, port);
        }
    }

    public void disconnect() {
        TcpManager.init().disconnect();
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

    public void processData(String data) {
        if (data != null && data != "") {
            String msg = data = data.toLowerCase().replace(MSG_FLAG, "");
            Log.e("====", "==========data.toLowerCase()=" + data.toLowerCase());
            String msgHead = msg.substring(0, 24);//000200000000099787870005
            String msgHeadId = msg.substring(0, 4);//byte[0-1]
            String msgHeadBodyAttr = msg.substring(4, 8);//byte[2-3]
            String msgHeadPhoneNum = msg.substring(8, 20);//byte[4-9]   终端手机号或设备ID bcd[6]
            String msgHeadseq = msg.substring(20, 24);//byte[10-11]
            //byte[12-15] 消息包封装项



            String msgCheckSum = msg.substring(msg.length() - 2, msg.length());
            Log.e("====", "=========msgHead=" + msgHead + "#msgHeadId=" + msgHeadId + "#msgHeadBodyAttr=" + msgHeadBodyAttr
                    + "#msgHeadPhoneNum=" + msgHeadPhoneNum + "#msgHeadseq=" + msgHeadseq + "****msgCheckSum=" + msgCheckSum);
            switch (msgHeadId) {
                //终端普通应答
                case MSG_TYPE_CLIENT_RESPONSE:
                    Log.e("====", "============MSG_TYPE_CLIENT_RESPONSE");
                    break;
                //心跳
                case MSG_TYPE_HEARTBEAT:
                    Log.e("====", "============MSG_TYPE_HEARTBEAT");
                    break;
                //注销
                case MSG_TYPE_UNREGISTER:
                    Log.e("====", "============MSG_TYPE_UNREGISTER");
                    break;
                //注册
                case MSG_TYPE_REGISTER:
                    Log.e("====", "============MSG_TYPE_REGISTER");
                    break;
                //终端鉴权
                case MSG_TYPE_AUTHENTICATION:
                    Log.e("====", "============MSG_TYPE_AUTHENTICATION");
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
                    break;
                //补传分包请求
                case MSG_TYPE_SUB_PAC_REQ:
                    break;
                //终端注册应答
                case MSG_TYPE_REG_RESPONSE:
                    break;
                //终端控制
                case MSG_TYPE_SERVER_CONTROL:
                    break;
                //终端主动上报
                case MSG_TYPE_CLIENT_REQ:
                    break;
                //设置终端参数
                case MSG_TYPE_CLIENT_SETTINGS:
                    break;
            }
        }
    }

    //注册
    private static final int HANDLER_MSG_REGISTER = 0;
    //鉴权
    private static final int HANDLER_MSG_AUTHENTICATION = 1;
    //借钥匙
    private static final int HANDLER_MSG_BORROW = 2;
    //还钥匙
    private static final int HANDLER_MSG_RETURN = 3;
    //注销
    private static final int HANDLER_MSG_UNREGISTER = 4;

    private class TcpEventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_MSG_REGISTER:
                    break;
                case HANDLER_MSG_AUTHENTICATION:
                    break;
                case HANDLER_MSG_BORROW:
                    break;
                case HANDLER_MSG_RETURN:
                    break;
                case HANDLER_MSG_UNREGISTER:
                    break;
            }
        }
    }
}
