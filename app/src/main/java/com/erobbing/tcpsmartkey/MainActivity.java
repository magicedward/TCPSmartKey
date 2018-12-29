package com.erobbing.tcpsmartkey;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.erobbing.tcpsmartkey.common.TcpManager;

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
        TcpManager.init().setDisconnectedCallback(new TcpManager.OnServerDisconnectedCallbackBlock() {
            @Override
            public void callback(IOException e) {
                //textView_receive.setText(textView_receive.getText().toString() + "断开连接" + "\n");
                Log.e("====", "==========断开连接" + "\n");
            }
        });
        TcpManager.init().setConnectedCallback(new TcpManager.OnServerConnectedCallbackBlock() {
            @Override
            public void callback() {
                //textView_receive.setText(textView_receive.getText().toString() + "连接成功" + "\n");
                Log.e("====", "==========连接成功" + "\n");
                //成功之后注册

            }
        });
        TcpManager.init().setReceivedCallback(new TcpManager.OnReceiveCallbackBlock() {
            @Override
            public void callback(String receicedMessage) {
                //textView_receive.setText(textView_receive.getText().toString() + receicedMessage + "\n");
                Log.e("====", "==========receive=" + receicedMessage + "\n");
            }
        });
    }

    public void sendMessage(View view) {
        String msg = editText.getText().toString();
        textView_send.setText(textView_send.getText().toString() + msg + "\n");
        TcpManager.init().send(msg.getBytes());
    }

    public void connect(View view) {
        TcpManager.init().connect("172.23.130.2", 1500);
    }

    public void disconnect(View view) {
        TcpManager.init().disconnect();
    }

    public void clear1(View view) {
        textView_send.setText("");
    }

    public void clear2(View view) {
        textView_receive.setText("");
    }
}
