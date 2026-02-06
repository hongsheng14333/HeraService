package com.core.heraservice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Boot completed received, starting service");
        String action = intent.getAction();
        if (action == null) {
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // 启动后台服务
            Intent serviceIntent = new Intent(context, BackgroundTaskService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}