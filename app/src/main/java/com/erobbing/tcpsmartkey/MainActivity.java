package com.erobbing.tcpsmartkey;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.erobbing.tcpsmartkey.common.tcpclient.TcpClient;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    public EditText editText;
    public TextView textView_send;
    public TextView textView_receive;

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
        TcpClient.init().setDisconnectedCallback(new TcpClient.OnServerDisconnectedCallbackBlock() {
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
        });


        //serverThread = new ServerThread();
        //serverThread.start();


    }

    public void sendMessage(View view) {
        String msg = "7e000200000200000000150003327e";//editText.getText().toString();
        String msg1 = "7E010000180199999999980018010006338888888877777777777777777777019999999998357E";
        //7E8100000D0199999999980001001800724B4370794E524F364D1A7E
        textView_send.setText("");
        textView_send.setText(textView_send.getText().toString() + msg + "\n");
        TcpClient.init().send(msg1.getBytes());
    }

    public void connect(View view) {
        TcpClient.init().connect("172.23.130.2", 1500);
        Log.e("====", "============connect(View view)");
    }

    public void disconnect(View view) {
        TcpClient.init().disconnect();
    }

    public void clear1(View view) {
        textView_send.setText("");
    }

    public void clear2(View view) {
        textView_receive.setText("");
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

    /*class ServerThread extends Thread {

        boolean isLoop = true;

        public void setIsLoop(boolean isLoop) {
            this.isLoop = isLoop;
        }

        @Override
        public void run() {
            Log.d("====", "socket running");

            ServerSocket serverSocket = null;
            try {
                serverSocket = new ServerSocket(10086);
                while (isLoop) {
                    Socket socket = serverSocket.accept();

                    Log.d("====", "socket accept");

                    DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                    DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                    while (isLoop) {
                        outputStream.writeUTF("test data");
                        Log.d("====", "send data");
                        Thread.sleep(1000);
                    }
                    socket.close();
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Log.d("====", "socket destory");

                if (serverSocket != null) {
                    try {
                        serverSocket.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }*/
}
