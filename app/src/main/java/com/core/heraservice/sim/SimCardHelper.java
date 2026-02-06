package com.core.heraservice.sim;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

public class SimCardHelper {
    private static final String TAG = "SimCardHelper";
    private final Context mContext;
    private final TelephonyManager telephonyManager;

    public SimCardHelper(Context context) {
        mContext = context;
        this.telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }


    /**
     * 获取SIM卡运营商
     */
    public String getOperatorName() {
        return telephonyManager.getSimOperatorName();
    }

    /**
     * 检查SIM卡是否可用
     */
    public boolean isSimAvailable() {
        return telephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY;
    }

    /**
     * 获取手机号码
     */
    public String getPhoneNumber() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(mContext, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(mContext, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "getPhoneNumber check READ_SMS permission fail");
            return "";
        }
        return telephonyManager.getLine1Number();
    }

    public String getSimSerialNo() {
        return telephonyManager.getSimSerialNumber();
    }

    public int getSimState() {
        return telephonyManager.getSimState();
    }
}
