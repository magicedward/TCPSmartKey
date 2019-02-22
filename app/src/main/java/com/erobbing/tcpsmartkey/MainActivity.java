package com.erobbing.tcpsmartkey;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.erobbing.smartkey.aidl.IMotorControl;
import com.erobbing.tcpsmartkey.common.tcpclient.TcpClient;
import com.erobbing.tcpsmartkey.service.codec.MsgEncoder;
import com.erobbing.tcpsmartkey.util.BCD8421Operater;
import com.erobbing.tcpsmartkey.util.BitOperator;
import com.erobbing.tcpsmartkey.util.JT808ProtocolUtils;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    public EditText editText;
    public TextView textView_send;
    public TextView textView_receive;

    private BitOperator mBitOperator;
    private BCD8421Operater mBCD8421Operater;
    private JT808ProtocolUtils mJT808ProtocolUtils;
    private MsgEncoder mMsgEncoder;

    private static final String IP = "140.143.7.147";//"192.168.0.175";//"140.143.7.147";//"172.23.130.2";
    private static final int PORT = 20048;//20048;//1500;

    private String tmpPhoneId = "010000090910";
    private String tmpKeyId = "000000090901";

    private IMotorControl iMotorControl;
    //private IMotorControl.Stub iMotorControl;
    private boolean serviceConnected;

    private static final String LED_SERIES_01 = "/sys/class/leds/led-ct-01/brightness";
    //private static final String LED_SERIES_01 = "/sys/devices/soc.0/gpio-leds.70/leds/led-ct-01/brightness";
    private static final String LED_SERIES_02 = "/sys/devices/soc.0/gpio-leds.70/leds/led-ct-02/brightness";
    private static final String LED_SERIES_03 = "/sys/devices/soc.0/gpio-leds.70/leds/led-ct-03/brightness";
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editText = (EditText) findViewById(R.id.send_editText);
        textView_send = (TextView) findViewById(R.id.send_textView);
        textView_receive = (TextView) findViewById(R.id.receive_textView);
        textView_send.setMovementMethod(ScrollingMovementMethod.getInstance());
        textView_receive.setMovementMethod(ScrollingMovementMethod.getInstance());
        Intent service = new Intent(this, com.erobbing.tcpsmartkey.service.TcpService.class);
        startService(service);
        /*TcpClient.init().setDisconnectedCallback(new TcpClient.OnServerDisconnectedCallbackBlock() {
            @Override
            public void callback(IOException e) {
                //textView_receive.setText(textView_receive.getText().toString() + "断开连接" + "\n");
                Log.e("====", "==========断开连接" + "\n");
            }
        });
        TcpClient.init().setConnectedCallback(new TcpClient.OnServerConnectedCallbackBlock() {
            @Override
            public void callback() {
                //textView_receive.setText(textView_receive.getText().toString() + "连接成功" + "\n");
                Log.e("====", "==========连接成功" + "\n");
                //成功之后注册

            }
        });
        TcpClient.init().setReceivedCallback(new TcpClient.OnReceiveCallbackBlock() {
            @Override
            public void callback(String receicedMessage) {
                //textView_receive.setText(textView_receive.getText().toString() + receicedMessage + "\n");
                Log.e("====", "==========receive=" + receicedMessage + "\n");
            }
        });*/


        //serverThread = new ServerThread();
        //serverThread.start();
        mBitOperator = new BitOperator();
        mBCD8421Operater = new BCD8421Operater();
        mJT808ProtocolUtils = new JT808ProtocolUtils();
        mMsgEncoder = new MsgEncoder();
        //bindService();
        //patternMatch();
        //countStr();
        String sss = "7e000200000200000000150003327e7e333333333e7e7e4444444444f7e";
        //searchAllSubString(sss);
        //reGroupString();
        //allKeysIdpoweron();
        //allKeysIdWrite();
        Log.e("====", "=======main-allKeysIdRead()=" + allKeysIdRead());
        Log.e("====", "=========convertStringToHex=" + convertHexToString("303030303030303930393230"));
    }

    public String convertStringToHex(String str) {
        char[] chars = str.toCharArray();
        StringBuffer hex = new StringBuffer();
        for (int i = 0; i < chars.length; i++) {
            hex.append(Integer.toHexString((int) chars[i]));
        }
        return hex.toString();
    }

    public String convertHexToString(String hex) {
        StringBuilder sb = new StringBuilder();
        StringBuilder temp = new StringBuilder();
        //49204c6f7665204a617661 split into two characters 49, 20, 4c...
        for (int i = 0; i < hex.length() - 1; i += 2) {
            //grab the hex in pairs
            String output = hex.substring(i, (i + 2));
            //convert hex to decimal
            int decimal = Integer.parseInt(output, 16);
            //convert the decimal to character
            sb.append((char) decimal);
            temp.append(decimal);
        }
        return sb.toString();
    }

    public void sendMessage(View view) {
        String msg = "7e000200000200000000150003327e";//editText.getText().toString();
        String msg1 = "7E010000180199999999980018010006338888888877777777777777777777019999999798357E";
        String ttString = "0102000A019999999991001936345A33627742544A70";
        /*int checkint = mBitOperator.getCheckSum4JT808(
                mBCD8421Operater.string2Bcd("000200000200000000150003"), 0, (ttString.length() / 2));
        Log.e("====", "=========activity.checkint=" + checkint);
        try {
            if (ttString != null) {
                TcpClient.init().send(mMsgEncoder.doEncode(hexString2Intger(ttString), checkint));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }*/


        //7E8100000D0199999999980001001800724B4370794E524F364D1A7E
        textView_send.setText("");
        textView_send.setText(textView_send.getText().toString() + msg + "\n");
        //TcpClient.init().send(msg1.getBytes());
        //Log.e("====", "======aa=" + (5 << 1) + "--" + (5 >> 1));
        //computeKeyStatus();
        //getTimeHexString();
        Log.e("====", "======getTimeHex=" + getTimeHexString());
    }

    byte[] hexString2Intger(String str) {
        byte[] byteTarget = new byte[str.length() / 2];
        for (int i = 0; i < str.length() / 2; ++i)
            byteTarget[i] = (byte) (Integer.parseInt(str.substring(i * 2, i * 2 + 2), 16) & 0xff);
        return byteTarget;
    }

    public String getTimeHexString() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat format = new SimpleDateFormat("yy-MM-dd hh:mm:ss");
        String time = format.format(date);
        Log.e("====", "======time=" + time);//19-01-26 02:35:00
        Calendar calendar = Calendar.getInstance();
        int year = Integer.parseInt((calendar.get(Calendar.YEAR) + "").substring(2, 4));
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DATE);
        int hour = calendar.get(Calendar.HOUR);
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

    public String computeKeyStatus() {
        int[] status = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
        int toHexStatus = 0;
        for (int i = 0; i < 24; i++) {
            toHexStatus += status[i] << i;
        }
        //String.format("%08x", xx);
        Log.e("====", "=======toHexStatus=" + String.format("%08x", toHexStatus));
        return String.format("%08x", toHexStatus);
    }

    public void connect(View view) {
        TcpClient.init().connect(IP, PORT);
        //TcpClient.init().connect("140.143.7.147", 20048);
        //TcpClient.init().connect("192.168.0.175", 20048);
        Log.e("====", "============connect(View view)");
    }

    public void disconnect(View view) {
        TcpClient.init().disconnect();
    }

    public void clear1(View view) {
        /*textView_send.setText("");
        String msg = "010000180199999999980018010006338888888877777777777777777777019999999598";
        int check = mBitOperator.getCheckSum4JT808(
                mBCD8421Operater.string2Bcd(msg), 0, (msg.length() / 2));
        String checkSum = String.format("%02x", check);
        Log.e("====", "===========mainactivity.checkSum=" + checkSum);*/
        //motor01StatusCtrl(true);
        //ledSeriesCtrl(LED_SERIES_01, true);
        //allKeysIdpoweron();
        //allKeysIdWrite();
        //ledSeriesCtrl("/sys/class/leds/led-ct-01/brightness", true);
        //ShellUtils.execCommand("echo on > /sys/class/gpio_switch/switch_ct_01", false);
        //ShellUtils.execCommand("echo 77777777777777777777 > /sys/bus/i2c/devices/6-005b/mcu/dm_id", false);
        //ShellUtils.execCommand("echo 4266496677454c4a566c > /sys/bus/i2c/devices/6-005b/mcu/se_code", false);
        //ShellUtils.execCommand("echo off > /sys/class/gpio_switch/switch_ct_01", false);
        //switchStatusCtrl(SWITCH_01_PATH, true);
        //allKeysAuthCodeWrite("4266496677454c4a566c");
        //switchStatusCtrl(SWITCH_01_PATH, false);
    }

    private void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void clear2(View view) {
        textView_receive.setText("");
        //motor01StatusCtrl(false);
        //ledSeriesCtrl(LED_SERIES_01, false);
        //clearAuthCode();
        //ledSeriesCtrl("/sys/class/leds/led-ct-01/brightness", false);
        Log.e("====", "=======main-allKeysIdRead()=" + allKeysIdRead() + "----allKeysAuthCodeRead=" + allKeysAuthCodeRead());
    }

    public void motor01StatusCtrl(boolean on) {
        try {
            FileWriter command = new FileWriter("/sys/class/gpio_switch/motor_ct_01");
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

    public void allKeysIdpoweron() {
        try {
            //SWITCH_04_PATH
            FileWriter command = new FileWriter("/sys/class/gpio_switch/switch_ct_01");
            command.write("on");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void allKeysIdWrite() {
        try {
            //SWITCH_04_PATH
            FileWriter command = new FileWriter("/sys/bus/i2c/devices/6-005b/mcu/dm_id");
            command.write("77777777777777777777");
            //command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void switchStatusCtrl(String path, boolean on) {
        try {
            FileWriter command = new FileWriter(path);
            if (on) {
                command.write("on");
            } else {
                command.write("off");
            }
            command.write("\n");
            command.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void allKeysAuthCodeWrite(String cmd) {
        try {
            FileWriter command = new FileWriter(WRITE_AUTH_CODE_PATH);
            command.write(cmd);
            command.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void allKeysShopIdWrite(String cmd) {
        try {
            FileWriter command = new FileWriter(RW_SHOP_ID_PATH);
            command.write(cmd);
            command.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String allKeysIdRead() {
        char[] buffer = new char[1024];

        String state = "";
        FileReader file = null;
        try {
            //fa-fa-fa-fa-fa-fa-fa-fa-fa-fa   default
            //file = new FileReader("/sys/bus/i2c/devices/6-005b/mcu/se_code");
            file = new FileReader("/sys/bus/i2c/devices/6-005b/mcu/dm_id");
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

    private String allKeysAuthCodeRead() {
        char[] buffer = new char[1024];

        String state = "";
        FileReader file = null;
        try {
            //fa-fa-fa-fa-fa-fa-fa-fa-fa-fa   default
            //file = new FileReader("/sys/bus/i2c/devices/6-005b/mcu/se_code");
            file = new FileReader("/sys/bus/i2c/devices/6-005b/mcu/se_code");
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

    public void clearAuthCode() {
        SharedPreferences sp = this.getSharedPreferences("config", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putString("auth_code", "unknown");
        ed.commit();
    }


    //ServerThread serverThread;

    Handler handler = new Handler() {

        @SuppressLint("NewApi")
        @Override
        public void handleMessage(Message msg) {
            Toast.makeText(getApplicationContext(), msg.getData().getString("MSG", "Toast"), Toast.LENGTH_SHORT).show();
        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        //serverThread.setIsLoop(false);
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            iMotorControl = IMotorControl.Stub.asInterface(service);
            serviceConnected = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceConnected = false;
        }
    };

    private void bindService() {
        Intent intent = new Intent();
        intent.setPackage("com.erobbing.smartkey.aidl");
        intent.setAction("com.erobbing.action.smartkey.aidl");
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void patternMatch() {
        /*Matcher matcher = Pattern.compile("(?<=(<!log>))[\\w\\W]*(?=(<\\?log>))")
                .matcher("<!log>22222222222\n222222222222222<?log>");

        while (matcher.find()) {
            System.out.println(matcher.group(0));
            Log.e("====", "==========matcher.group(0)=" + matcher.group(0));
        }*/
        /*String log = "<!log>111111111111111111111111111111111111111<?log>";
        Pattern log_ptn = Pattern.compile("<!log>([\\s\\S]+)<\\?log>");
        Matcher log_m = log_ptn.matcher(log);
        while (log_m.find()) {
            System.out.println("log = " + log_m.group(1));
        }*/
        String log = "7e000200000200000000150003327e7e333333333e7e7e4444444444f7e";
        Pattern log_ptn = Pattern.compile("7e([\\s\\S]+)7e");
        Matcher log_m = log_ptn.matcher(log);
        while (log_m.find()) {
            //System.out.println("log = " + log_m.group(1));
            Log.e("====", "==========log_m.group(1)=" + log_m.group(1));
        }
    }

    public void countStr() {
        String sss = "7e000200000200000000150003327e7e333333333e7e7e4444444444f7e";
        int in = sss.indexOf("7e");
        int lastin = sss.lastIndexOf("7e");
        Log.e("====", "====in=" + in + "--lastin=" + lastin);
    }

    private void searchAllSubString(String recString) {
        if (recString != null) {
            //String sss = "7e000200000200000000150003327e7e333333333e7e7e4444444444f7e";
            String key = "7e";
            int a = recString.indexOf(key);//*第一个出现的索引位置
            int count = 0;
            ArrayList<Integer> list = new ArrayList<Integer>();
            while (a != -1) {
                Log.e("====", "====a=" + a + "\t");
                Log.e("====", "====count=" + ++count);
                list.add(a);
                a = recString.indexOf(key, a + 1);//*从这个索引往后开始第一个出现的位置
            }
            int subStrCount = list.size() / 2;
            Log.e("====", "==========while end--list.size=" + list.size());
            for (int i = 0; i < subStrCount; i++) {
                //String subString = sss.substring(list.get(i * 2) + 2, list.get(i * 2 + 1) - 2);
                String subString = recString.substring(list.get(i * 2), list.get(i * 2 + 1) + 2);
                Log.e("====", "=========subString=" + subString);
            }
        }
    }
}
