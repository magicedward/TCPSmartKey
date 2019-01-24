package com.erobbing.tcpsmartkey.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.erobbing.tcpsmartkey.common.PackageData;
import com.erobbing.tcpsmartkey.common.TPMSConsts;
import com.erobbing.tcpsmartkey.common.tcpclient.TcpClient;
import com.erobbing.tcpsmartkey.service.codec.MsgDecoder;
import com.erobbing.tcpsmartkey.util.BCD8421Operater;
import com.erobbing.tcpsmartkey.util.BitOperator;
import com.erobbing.tcpsmartkey.util.HexStringUtils;
import com.erobbing.tcpsmartkey.util.JT808ProtocolUtils;

import java.io.IOException;

/**
 * Created by zhangzhaolei on 2018/12/25.
 */

public class TcpService extends Service {
    private static final String TAG = "TcpService";
    private Context mContext;
    private AlarmManager mAlarmManager;
    private String mLocationFrequency = "360000";
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
    //终端主动上报
    private static final String MSG_TYPE_CLIENT_REQ = "0817";
    //设置终端参数
    private static final String MSG_TYPE_CLIENT_SETTINGS = "8103";

    private BitOperator mBitOperator;
    private BCD8421Operater mBCD8421Operater;
    private JT808ProtocolUtils mJT808ProtocolUtils;
    private MsgDecoder mMsgDecoder;
    PackageData mPackageData;
    PackageData.MsgHeader mPackageDataMsgHeader;

    //private BroadcastReceiver mUsbReceiver;


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
        super.onDestroy();
    }

    public void sendMessage(String msg) {
        if (msg != null) {
            TcpClient.init().send(msg.getBytes());
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

            String msgHead = msg.substring(0, 24);//000200000000099787870005 //length=24
            String msgHeadId = msg.substring(0, 4);//byte[0-1]
            String msgHeadBodyAttr = msg.substring(4, 8);//byte[2-3]
            String msgHeadPhoneNum = msg.substring(8, 20);//byte[4-9]   终端手机号或设备ID bcd[6]
            String msgHeadseq = msg.substring(20, 24);//byte[10-11]
            //byte[12-15] 消息包封装项 //一般为空
            //消息体
            if (msg.length() - 2 > 24) {
                String msgBody = msg.substring(24, msg.length() - 2);
                Log.e("====", "========msgBody=" + msgBody);


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

                    sendMessage(HexStringUtils.toHexString(getAllBytes()));
                    //鉴权完毕主动上报钥匙孔状态
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
                    break;
                //补传分包请求
                case MSG_TYPE_SUB_PAC_REQ:
                    break;
                //终端注册应答
                case MSG_TYPE_REG_RESPONSE:
                    Log.e("====", "========MSG_TYPE_REG_RESPONSE");
                    //get auth code
                    //send auth code
                    break;
                //终端控制
                case MSG_TYPE_SERVER_CONTROL:
                    //byte[1] 命令参数格式具体见后面描述，每个字段之间采用半角”;”分隔，每个 STRING 字段先按 GBK 编码处理后再组成消息
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
                              int flowed, int totalsubpackage, int seq)
    //消息体长度，加密方式，分包，电话，流水号，包总数，包序号
    {
        mPackageDataMsgHeader = new PackageData.MsgHeader();
        mPackageDataMsgHeader.setMsgId(TPMSConsts.msg_id_terminal_heart_beat);
        mPackageDataMsgHeader.setMsgBodyLength(msgbodylength);
        mPackageDataMsgHeader.setEncryptionType(encryptiontype);
        mPackageDataMsgHeader.setHasSubPackage(issubpackage);
        mPackageDataMsgHeader.setReservedBit(0);
        mPackageDataMsgHeader.setTerminalPhone(terminalphone);
        mPackageDataMsgHeader.setFlowId(flowed);
        if (issubpackage) {
            mPackageDataMsgHeader.setTotalSubPackage(totalsubpackage);
            mPackageDataMsgHeader.setSubPackageSeq(seq);
        }
    }

    public byte[] getAllBytes() {//所有数据报字节
        BitOperator bitOperator = new BitOperator();
        int bodyPro = generateMsgBodyProps(mPackageDataMsgHeader.getMsgBodyLength(), mPackageDataMsgHeader.getEncryptionType(), mPackageDataMsgHeader.isHasSubPackage(), mPackageDataMsgHeader.getReservedBit());
        byte[] headbytes = generateMsgHeader(mPackageDataMsgHeader.getTerminalPhone(), mPackageDataMsgHeader.getMsgId(), bodyPro, mPackageDataMsgHeader.getFlowId());//mPackageDataMsgHeader.getHeaderbytes();
        byte[] bodybytes = mPackageData.getMsgBodyBytes();

        byte[] check = new byte[]{(byte) (bitOperator.getCheckSum4JT808(headbytes, 0, headbytes.length))};
        byte[] flag = new byte[]{TPMSConsts.pkg_delimiter};
        byte[] sendbytes = bitOperator.concatAll(flag, headbytes, bodybytes, check, flag);
        return sendbytes;
    }

    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!"android.hardware.usb.action.USB_STATE".equals(action)) {
                return;
            }
            boolean connected = intent.getBooleanExtra("connected", false);
            Toast.makeText(context, "connected=" + connected, Toast.LENGTH_SHORT).show();
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.erobbing.action.PC_TO_DROID");

            if (connected) {
                //start socket
                //context.startService(new Intent(context, TcpServer.class));
                //start here get provinceId  cityId  manufacturerId  terminalType?  shopId  terminalId
                //generate TerminalRegInfo
                //sendd();
                //Intent intent = new Intent(this, com.erobbing.adb_config_demo.sdk.service.SdkService.class);
                startService(new Intent(context, com.erobbing.adb_config_demo.sdk.service.SdkService.class));
                registerReceiver(mPC2DroidReceiver, filter);
                //sendMessage("7E010000180199999999980018010006338888888877777777777777777777019999999998357E");
            } else {
                //stop socket
                //context.stopService(new Intent(context, TcpServer.class));
                unregisterReceiver(mPC2DroidReceiver);
                stopService(new Intent(context, com.erobbing.adb_config_demo.sdk.service.SdkService.class));
            }
        }
    };

    private BroadcastReceiver mPC2DroidReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.erobbing.action.PC_TO_DROID".equals(action)) {
                String provinceId = intent.getStringExtra("province_id");
                String cityId = intent.getStringExtra("city_id");
                String manufacturerId = intent.getStringExtra("manufacturer_id");
                String shopId = intent.getStringExtra("shop_id");
                String terminalId = intent.getStringExtra("box_id");
                Log.d(TAG, "mPC2DroidReceiver.provinceId=" + provinceId
                        + " - cityId=" + cityId
                        + " - manufacturerId=" + manufacturerId
                        + " - shopId=" + shopId
                        + " - terminalId=" + terminalId);
            }
        }
    };

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


        sendMessage(HexStringUtils.toHexString(getAllBytes()));
        //sendMessage("123");
    }

    //终端普通应答
    private static final int HANDLER_MSG_TYPE_CLIENT_RESPONSE = 0x0001;
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
    }
}
