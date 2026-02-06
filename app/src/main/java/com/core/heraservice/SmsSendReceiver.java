package com.core.heraservice;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.util.Log;

public class SmsSendReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsSendReceiver";

    public static volatile boolean mSendSmsStatus = false;
    @Override
    public void onReceive(Context context, Intent intent) {
        switch (getResultCode()) {
            case Activity.RESULT_OK:
                Log.d(TAG, "短信发送成功");
                mSendSmsStatus = true;
                break;
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                Log.d(TAG, "发送失败: 常规错误");
                mSendSmsStatus = false;
                break;
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                Log.d(TAG, "发送失败: 无服务");
                mSendSmsStatus = false;
                break;
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                Log.d(TAG, "发送失败: 射频关闭(飞行模式等)");
                mSendSmsStatus = false;
                break;
            default:
                Log.d(TAG, "发送失败: 未知错误码 " + getResultCode());
                mSendSmsStatus = false;
                break;
        }
    }

    public static boolean getSendSmsStatus() {
        return mSendSmsStatus;
    }

    public static void setSendSmsStatus(boolean status) {
        mSendSmsStatus = status;
    }
}
