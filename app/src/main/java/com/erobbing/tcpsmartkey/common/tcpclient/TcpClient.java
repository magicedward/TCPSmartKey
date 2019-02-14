package com.erobbing.tcpsmartkey.common.tcpclient;

import android.util.Log;

import com.erobbing.sdk.local_config.LocalConfigParser;
import com.erobbing.sdk.model.DiagControl;
import com.erobbing.sdk.parse.ParserListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Created by zhangzhaolei on 2018/12/25.
 */

public class TcpClient implements ParserListener {
    private static TcpClient instance;
    private static final String TAG = "TcpClient";
    //    Socket
    private Socket socket;
    //    IP地址
    private String ipAddress;
    //    端口号
    private int port;
    private Thread thread;
    //    Socket输出流
    private OutputStream outputStream;
    //    Socket输入流
    private InputStream inputStream;
    //    连接回调
    private OnServerConnectedCallbackBlock connectedCallback;
    //    断开连接回调(连接失败)
    private OnServerDisconnectedCallbackBlock disconnectedCallback;
    //    接收信息回调
    private OnReceiveCallbackBlock receivedCallback;

    //    构造函数私有化
    private TcpClient() {
        super();
        mLocalConfigParser = new LocalConfigParser();
        mLocalConfigParser.setListener(this);
    }

    private LocalConfigParser mLocalConfigParser;

    //    提供一个全局的静态方法
    public static TcpClient init() {//sharedCenter
        if (instance == null) {
            synchronized (TcpClient.class) {
                if (instance == null) {
                    instance = new TcpClient();
                }
            }
        }
        return instance;
    }

    /**
     * 通过IP地址(域名)和端口进行连接
     *
     * @param ipAddress IP地址(域名)
     * @param port      端口
     */
    public void connect(final String ipAddress, final int port) {

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new Socket(ipAddress, port);
                    socket.setTcpNoDelay(true);
                    socket.setSoTimeout(60 * 1000);//设置超时时间
                    if (isConnected()) {
                        TcpClient.init().ipAddress = ipAddress;
                        TcpClient.init().port = port;
                        if (connectedCallback != null) {
                            connectedCallback.callback();
                        }
                        outputStream = socket.getOutputStream();
                        inputStream = socket.getInputStream();
                        receive();
                        Log.i(TAG, "连接成功");
                    } else {
                        Log.i(TAG, "连接失败");
                        if (disconnectedCallback != null) {
                            disconnectedCallback.callback(new IOException("连接失败"));
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "连接异常");
                    if (disconnectedCallback != null) {
                        disconnectedCallback.callback(e);
                    }
                }
            }
        });
        thread.start();
    }

    /**
     * 判断是否连接
     */
    public boolean isConnected() {
        return socket.isConnected();
    }

    /**
     * 连接
     */
    public void connect() {
        connect(ipAddress, port);
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (isConnected()) {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                socket.close();
                if (socket.isClosed()) {
                    if (disconnectedCallback != null) {
                        disconnectedCallback.callback(new IOException("断开连接"));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 接收数据
     */
    public void receive() {
        while (isConnected()) {
            try {
                /**得到的是16进制数，需要进行解析*/
                byte[] bt = new byte[1024];
//                获取接收到的字节和字节数
                int length = inputStream.read(bt);
                if (length > 0) {
//                获取正确的字节
                    byte[] bs = new byte[length];
                    System.arraycopy(bt, 0, bs, 0, length);

                    //String str = new String(bs, "UTF-8");//不用这个，乱码
                    String str = bytes2HexString(bs);
                    //Log.e("====", "======TcpClient.receive=" + str);
                    try {
                        //mLocalConfigParser.parse(bs);
                    } catch (Exception e) {
                        Log.e("====", "===========mLocalConfigParser-err", e);
                    }

                    if (str != null) {
                        if (receivedCallback != null) {
                            //receivedCallback.callback(str);
                            Log.e("====", "=======str=" + str);
                            searchAllSubString(str.toLowerCase());
                        }
                    }
                    Log.i(TAG, "接收成功");
                } else {
                    Log.i(TAG, "接收出错");
                    disconnect();
                }
                //Log.i(TAG, "接收成功");
            } catch (IOException e) {
                Log.i(TAG, "接收失败");
            }
        }
    }

    public static String bytes2HexString(byte[] b) {
        String r = "";

        for (int i = 0; i < b.length; i++) {
            String hex = Integer.toHexString(b[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            r += hex.toUpperCase();
        }

        return r;
    }

    /**
     * 发送数据
     *
     * @param data 数据
     */
    public void send(final byte[] data) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (socket != null) {
                    try {
                        outputStream.write(data);
                        outputStream.flush();
                        Log.i(TAG, "发送成功");
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.i(TAG, "发送失败");
                    }
                } else {
                    connect();
                }
            }
        }).start();

    }

    /**
     * 回调声明
     */
    public interface OnServerConnectedCallbackBlock {
        void callback();
    }

    public interface OnServerDisconnectedCallbackBlock {
        void callback(IOException e);
    }

    public interface OnReceiveCallbackBlock {
        void callback(String receicedMessage);
    }

    public void setConnectedCallback(OnServerConnectedCallbackBlock connectedCallback) {
        this.connectedCallback = connectedCallback;
    }

    public void setDisconnectedCallback(OnServerDisconnectedCallbackBlock disconnectedCallback) {
        this.disconnectedCallback = disconnectedCallback;
    }

    public void setReceivedCallback(OnReceiveCallbackBlock receivedCallback) {
        this.receivedCallback = receivedCallback;
    }

    /**
     * 移除回调
     */
    private void removeCallback() {
        connectedCallback = null;
        disconnectedCallback = null;
        receivedCallback = null;
    }

    @Override
    public void onGotPackage(DiagControl dc) {
        Log.e("====", "=====DiagControl=" + dc.getPhoneNum());
        String recMsg = bytes2HexString(dc.toByteArray());
        if (recMsg != null) {
            if (receivedCallback != null) {
                receivedCallback.callback(recMsg);
            }
        }
    }

    private void searchAllSubString(String recString) {
        if (recString != null) {
            //String sss = "7e000200000200000000150003327e7e333333333e7e7e4444444444f7e";
            String key = "7e";
            int a = recString.indexOf(key);//first index
            int count = 0;
            ArrayList<Integer> list = new ArrayList<Integer>();
            while (a != -1) {
                Log.e("====", "====a=" + a + "\t");
                Log.e("====", "====count=" + ++count);
                list.add(a);
                a = recString.indexOf(key, a + 1);//the next index from first index
            }
            int subStrCount = list.size() / 2;
            Log.e("====", "==========while end--list.size=" + list.size());
            for (int i = 0; i < subStrCount; i++) {
                //String subString = sss.substring(list.get(i * 2) + 2, list.get(i * 2 + 1) - 2);
                String subString = recString.substring(list.get(i * 2), list.get(i * 2 + 1) + 2);
                Log.e("====", "=========subString=" + subString);
                receivedCallback.callback(subString);
            }
        }
    }
}
