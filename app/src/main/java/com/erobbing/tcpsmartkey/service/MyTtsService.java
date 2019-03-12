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
    private long mInterval = 3000;
    private int mCount = 3;
    private String mIntervalText = "";
    private Context mContext;
    private final Object mPlayLock = new Object();

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
        mContext = this;
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
            //synchronized (mPlayLock) {
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

    private Runnable playTextIntervalRunnable = new Runnable() {
        @Override
        public void run() {
            mCount--;
            synchronized (mPlayLock) {
                if (mCount > 0) {
                    //textToSpeech.speak(mText, TextToSpeech.QUEUE_ADD, null);
                    startWithPlay(mContext, mIntervalText);
                    mHandler.postDelayed(playTextIntervalRunnable, mInterval);
                } else {
                    mHandler.removeCallbacks(playTextIntervalRunnable);
                }
            }
        }
    };

    public static void playTextInterval(final Context context, final long interval, final int count, final String text) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < count; i++) {
                    startWithPlay(context, text);
                    try {
                        Thread.sleep(interval);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public static void playTextInterval(final Context context, final long interval, final int count, final int textId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < count; i++) {
                    startWithPlay(context, textId);
                    try {
                        Thread.sleep(interval);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }
}
