package com.core.heraservice.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

public class NetworkHelper {
    private static final String TAG = "NetworkHelper";
    private final Context mContext;

    public NetworkHelper(Context context) {
        this.mContext = context;
    }

    /**
     * 获取网络类型
     */
    public String getNetworkType() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        if (activeNetwork == null || !activeNetwork.isConnected()) {
            return "无网络";
        }

        if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
            return "WiFi";
        }

        if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
            TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.READ_PHONE_STATE) !=
                    PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "getNetworkType check permission fail");
                return "移动网络";
            }
            int networkType = tm.getNetworkType();

            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_LTE:
                    return "4G";
                case TelephonyManager.NETWORK_TYPE_NR:
                    return "5G";
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_UMTS:
                    return "3G";
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_GPRS:
                    return "2G";
                default:
                    return "移动网络";
            }
        }
        return "未知";
    }
}
