package com.erobbing.tcpsmartkey.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * 用于适配CtChat的辅助服务
 * 主要功能：
 * playText(...)
 */

public class MyTtsService extends Service implements TextToSpeech.OnInitListener {
    private static String TAG = "MyTtsService";
    private TextToSpeech textToSpeech;
    private Handler mHandler = new Handler();

    public MyTtsService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        textToSpeech = new TextToSpeech(this, this, "com.iflytek.tts"); // 参数Context,TextToSpeech.OnInitListener
    }

    public static void startWithPlay(Context context, String str) {
        Log.d(TAG, "play text: " + str);
        Intent i = new Intent(context, MyTtsService.class);
        i.putExtra("play", str);
        context.startService(i);
    }

    public static void startWithPlay(Context context, int strID) {
        String str = context.getString(strID);
        startWithPlay(context, str);
    }

    /*public static void startWithPlay(int strID) {
        Context context = ChatApplication.getContext();
        String str = context.getString(strID);
        startWithPlay(context, str);
    }

    public static void startWithPlay(String str) {
        Context context = ChatApplication.getContext();
        startWithPlay(context, str);
    }*/

    private String mText;
    private Runnable playTextRunnable = new Runnable() {
        @Override
        public void run() {
            //if (GlobalConfig.SPEAK) {
            textToSpeech.speak(mText, TextToSpeech.QUEUE_ADD, null);
            //}
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && intent.hasExtra("play")) {
            mText = intent.getStringExtra("play");
            intent.removeExtra("play");
            mHandler.removeCallbacks(playTextRunnable);
            mHandler.postDelayed(playTextRunnable, 800);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.CHINA);
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "tts数据丢失或不支持");
                //Toast.makeText(this, "数据丢失或不支持", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDestroy() {
        textToSpeech.stop(); // 不管是否正在朗读TTS都被打断
        textToSpeech.shutdown(); // 关闭，释放资源
        super.onDestroy();
    }
}
