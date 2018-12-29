package com.erobbing.tcpsmartkey;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by zhangzhaolei on 2018/12/25.
 */

public class BootCompletedReceiver extends BroadcastReceiver {
    private Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            Intent service = new Intent(context, com.erobbing.tcpsmartkey.service.TcpService.class);
            context.startService(service);
        }
    }
}
