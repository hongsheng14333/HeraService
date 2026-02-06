package com.core.heraservice.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

public class BatteryHelper {
    private final Context context;

    public BatteryHelper(Context context) {
        this.context = context;
    }

    /**
     * 获取电池电量百分比
     */
    public int getBatteryLevel() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryIntent = context.registerReceiver(null, filter);

        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            return (int) (level * 100 / (float) scale);
        }
        return 0;
    }

    /**
     * 是否正在充电
     */
    public boolean isCharging() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryIntent = context.registerReceiver(null, filter);

        if (batteryIntent != null) {
            int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;
        }
        return false;
    }

    /**
     * 获取电池温度（摄氏度）
     */
    public float getTemperature() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryIntent = context.registerReceiver(null, filter);

        if (batteryIntent != null) {
            int temp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            return temp / 10.0f;
        }
        return 0;
    }
}
