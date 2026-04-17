package com.core.heraservice;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SmsSendReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsSendReceiver";
    private static final String EXTRA_SMS_REQUEST_ID = "sms_request_id";
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 90000;

    public static volatile boolean mSendSmsStatus = false;
    private static final Map<String, SendResult> PENDING_RESULTS = new ConcurrentHashMap<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        String requestId = intent != null ? intent.getStringExtra(EXTRA_SMS_REQUEST_ID) : null;
        SendResult sendResult = requestId != null ? PENDING_RESULTS.get(requestId) : null;
        boolean success = false;
        String message;
        switch (getResultCode()) {
            case Activity.RESULT_OK:
                Log.d(TAG, "短信发送成功");
                mSendSmsStatus = true;
                success = true;
                message = "发送成功";
                break;
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                Log.d(TAG, "发送失败: 常规错误");
                mSendSmsStatus = false;
                message = "发送失败: 常规错误";
                break;
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                Log.d(TAG, "发送失败: 无服务");
                mSendSmsStatus = false;
                message = "发送失败: 无服务";
                break;
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                Log.d(TAG, "发送失败: 射频关闭(飞行模式等)");
                mSendSmsStatus = false;
                message = "发送失败: 射频关闭";
                break;
            default:
                Log.d(TAG, "发送失败: 未知错误码 " + getResultCode());
                mSendSmsStatus = false;
                message = "发送失败: 未知错误码 " + getResultCode();
                break;
        }

        boolean completed = completeRequest(sendResult, success, message);
        if (requestId != null && completed) {
            PENDING_RESULTS.remove(requestId);
        }
    }

    public static boolean getSendSmsStatus() {
        return mSendSmsStatus;
    }

    public static void setSendSmsStatus(boolean status) {
        mSendSmsStatus = status;
    }

    public static SendResult registerRequest(String requestId, int partCount) {
        SendResult result = new SendResult(requestId, partCount);
        PENDING_RESULTS.put(requestId, result);
        return result;
    }

    public static void removeRequest(String requestId) {
        PENDING_RESULTS.remove(requestId);
    }

    private static boolean completeRequest(SendResult sendResult, boolean success, String message) {
        if (sendResult == null) {
            return false;
        }
        synchronized (sendResult) {
            if (!success) {
                sendResult.success = false;
                sendResult.message = message;
                sendResult.completed = true;
                sendResult.latch.countDown();
                return true;
            }

            sendResult.remainingParts--;
            if (sendResult.remainingParts <= 0) {
                sendResult.success = true;
                sendResult.message = message;
                sendResult.completed = true;
                sendResult.latch.countDown();
                return true;
            }
        }
        return false;
    }

    public static class SendResult {
        private final String requestId;
        private final CountDownLatch latch = new CountDownLatch(1);
        private int remainingParts;
        private volatile boolean completed;
        private volatile boolean success;
        private volatile String message = "发送超时";

        SendResult(String requestId, int partCount) {
            this.requestId = requestId;
            this.remainingParts = Math.max(1, partCount);
        }

        public boolean await() throws InterruptedException {
            boolean finished = latch.await(DEFAULT_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            completed = finished;
            return finished;
        }

        public boolean isSuccess() {
            return completed && success;
        }

        public String getMessage() {
            return message;
        }

        public String getRequestId() {
            return requestId;
        }
    }
}
