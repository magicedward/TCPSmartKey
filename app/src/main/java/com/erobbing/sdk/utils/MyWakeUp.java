package com.erobbing.sdk.utils;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

public class MyWakeUp {
    public static void wakeupUsingAlarm(Context context) {

        // 鍚姩alaram
        //		Intent intent = new Intent(context, AlarmBroadcastReceiver.class);
        //		intent.setAction(AlarmBroadcastReceiver.WAKEUP_ACTION_NAME);
        //
        //		PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        //
        //		AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        //
        //		alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), sender);
        wakeLock(context, 5000);
    }

    public static void wakeupAutoRelease(Context context, int delay_milsec) {
        try {
            //releaseDelay(wakeLock(context), delay_milsec);
            PowerManager.WakeLock wl = wakeLock(context, delay_milsec);
        } catch (Exception e) {
            Log.i("MyWakeUp", "MyWakeUp - autoRelease: " + e.getMessage());
        }
    }

    public static void releaseDelay(final PowerManager.WakeLock wl, int milseconds) {
        try {
            if (wl == null) {
                Log.i("MyWakeUp", "releaseDelay: wl is null");
                return;
            }

            if (milseconds <= 0) {
                wl.release();
            } else {
                new Timer().schedule(new TimerTask() {

                    @Override
                    public void run() {
                        try {
                            wl.release();
                        } catch (Exception e) {
                            Log.d("MyWakeUp", "no release: " + e.getMessage());
                        }
                    }
                }, milseconds);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static final PowerManager.WakeLock wakeLock(Context context) {
        return wakeLock(context, 0);
    }

    /**
     * 鑷姩鍏抽棴浼戠湢閿�,
     *
     * @param _timeoutMs 姣
     */
    public static final PowerManager.WakeLock wakeLock(Context context, long _timeoutMs) {
        try {
            // 鑾峰彇鐢垫簮绠＄悊鍣ㄥ璞�
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

            // 鐐逛寒灞忓箷
            //            final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP
            //					| PowerManager.ON_AFTER_RELEASE | PowerManager.SCREEN_DIM_WAKE_LOCK, "NetCamera");

			/* quhuabo: 2017-02-20 鍙栨秷鐐逛寒灞忓箷 */
            final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NetCamera");
            wl.setReferenceCounted(false);

            wl.acquire(_timeoutMs);

            MyLogger.jLog().i("鍚姩淇濇寔鍞ら啋锛坵akeup锛夛紝timeout_MS: " + _timeoutMs);
            return wl;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 鑷姩鍏抽棴浼戠湢閿�
     */
    public static final PowerManager.WakeLock wakeLockScreen(Context context, long _timeout) {
        try {
            // 鑾峰彇鐢垫簮绠＄悊鍣ㄥ璞�
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

            // 鐐逛寒灞忓箷
            final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP
                    | PowerManager.ON_AFTER_RELEASE | PowerManager.SCREEN_DIM_WAKE_LOCK, "NetCamera");

			/* quhuabo: 2017-02-20 鍙栨秷鐐逛寒灞忓箷 */
            //			final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NetCamera");
            wl.setReferenceCounted(false);

            wl.acquire(_timeout);

            MyLogger.jLog().i("鍚姩淇濇寔鍞ら啋锛坵akeup锛夛紝timeout: " + _timeout);
            return wl;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}