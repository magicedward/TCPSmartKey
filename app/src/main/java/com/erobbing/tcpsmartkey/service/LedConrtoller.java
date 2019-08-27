package com.erobbing.tcpsmartkey.service;

import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.util.Log;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;


public class LedConrtoller {
    private static final String TAG = "LedConrtoller";
    private static final String RED_LED_ON = "com.android.redled_on";
    private static final String GREEN_LED_ON = "com.android.greenled_on";
    private static final String BLUE_LED_ON = "com.android.blueled_on";
    private static final String LED_OFF = "com.android.led_off";
    private static Timer mTimer;
    private static TimerTask mTimerTask;
    public static final String PROP_LED_ENABLE = "ro.sys.led_enable";
    public static boolean LED_ENABLE = SystemProperties.getBoolean(PROP_LED_ENABLE, false);
    private static Object mLock = new Object();
    //private static MyLogger mLog = MyLogger.jLog();
    private static boolean DEBUG = false;


    private static class GreenLedBlinkTask extends TimerTask {

        private Context mContext;
        private String mLedGreenPath;
        private long mCycleTime;

        public GreenLedBlinkTask(Context context, long cycleTime) {
            mContext = context;
            mCycleTime = cycleTime;
        }

        public GreenLedBlinkTask(Context context, String path, long cycleTime) {
            mContext = context;
            mLedGreenPath = path;
            mCycleTime = cycleTime;
        }

        @Override
        public void run() {
            synchronized (mLock) {
                GreenLedCtrl(mLedGreenPath, true);
                try {
                    Thread.sleep(mCycleTime / 2);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                GreenLedCtrl(mLedGreenPath, false);
            }
        }
    }

    private static class RedBlueBlinkAlternateTask extends TimerTask {

        private Context mContext;
        private long mCycleTime;

        public RedBlueBlinkAlternateTask(Context context, long cycleTime) {
            mContext = context;
            mCycleTime = cycleTime;
        }

        @Override
        public void run() {
            synchronized (mLock) {
                RedLedOn(mContext);
                try {
                    Thread.sleep(mCycleTime / 2);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                BlueLedOn(mContext);
            }

        }
    }

    public static void resetTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        if (mTimerTask != null) {
            mTimerTask.cancel();
            mTimerTask = null;
        }
    }

    //关闭灯
    public static void closeLed(Context context) {
        synchronized (mLock) {
            if (DEBUG) {
                Log.d(TAG, "close leds.");
            }
            Intent intent = new Intent(LED_OFF);
            context.sendBroadcast(intent);
        }
    }

    //设备正常休眠期间,绿灯常亮
    public static void GreenLedOn(Context context) {
        synchronized (mLock) {
            if (DEBUG) {
                Log.d(TAG, "Turn on green led.");
            }
            //yinqi mod, turn off light indicator to save power 20181219
            Intent intent = new Intent(GREEN_LED_ON);
            context.sendBroadcast(intent);
        }

    }

    public static void GreenLedCtrl(String path, boolean on) {
        synchronized (mLock) {
            if (DEBUG) {
                Log.d(TAG, "Turn on green led.");
            }
            try {
                FileWriter command = new FileWriter(path);
                if (on) {
                    command.write("255");
                } else {
                    command.write("0");
                }
                command.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //蓝灯常亮
    public static void BlueLedOn(Context context) {
        synchronized (mLock) {
            if (DEBUG) {
                Log.d(TAG, "Turn on blue led.");
            }
            Intent intent = new Intent(BLUE_LED_ON);
            context.sendBroadcast(intent);
        }

    }

    //通信失败或网络异常，红灯常亮
    public static void RedLedOn(Context context) {
        synchronized (mLock) {
            if (DEBUG) {
                Log.d(TAG, "Turn on red led.");
            }
            Intent intent = new Intent(RED_LED_ON);
            context.sendBroadcast(intent);
        }

    }

    //上传图片、视频期间,绿灯闪烁，频率为1HZ,周期为1S;上传视频，绿灯闪烁，频率0.5HZ，周期为2S
    public static void GreenLedBlink(Context context, long cycleTime) {
        if (DEBUG) {
            Log.d(TAG, "Green led blink, cycleTime= " + cycleTime);
        }
        mTimer = new Timer();
        mTimerTask = new GreenLedBlinkTask(context, cycleTime);
        mTimer.scheduleAtFixedRate(mTimerTask, 0, cycleTime);
    }

    //程序刚启动连接服务器时，红蓝交替
    public static void RedBlueBlinkAlternate(Context context) {
        if (DEBUG) {
            Log.d(TAG, "Red blue blink alternate");
        }
        long cycleTime = 1000;
        mTimer = new Timer();
        mTimerTask = new RedBlueBlinkAlternateTask(context, cycleTime);
        mTimer.scheduleAtFixedRate(mTimerTask, 0, cycleTime);
    }
}
