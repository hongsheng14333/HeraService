package com.core.heraservice.sim;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.TelephonyManager;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UssdNumberFetcher {
    private static final String TAG = "UssdNumberFetcher";
    private final TelephonyManager telephonyManager;
    private Context mContext;

    public UssdNumberFetcher(Context context) {
        mContext = context;
        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    public  String extractPhoneNumberFromText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        // 匹配各种格式的电话号码
        String regex = "(?:(?:\\+|00)\\d{1,3}[-\\s]?)?\\d{7,15}";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String phone = matcher.group();
            // 清理电话号码中的非数字字符（保留开头的+号）
            return phone.replaceAll("[^\\d+]", "");
        }
        return "";
    }


    @RequiresApi(api = Build.VERSION_CODES.P)
    public String sendUssdRequest(String ussdCode) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final String[] number = {""};

        TelephonyManager.UssdResponseCallback ussdCallback = new TelephonyManager.UssdResponseCallback() {
            @Override
            public void onReceiveUssdResponse(TelephonyManager telephonyManager, String request, CharSequence response) {
                String responseStr = (response != null) ? response.toString() : "";
                Log.d(TAG, "Response received: " + responseStr);
                number[0] = extractPhoneNumberFromText(responseStr);
                latch.countDown();
            }

            @Override
            public void onReceiveUssdResponseFailed(TelephonyManager telephonyManager, String request, int failureCode) {
                String error = "USSD request failed with code: " + failureCode;
                Log.e(TAG, error);
                number[0] = "";
                latch.countDown();

            }
        };

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return "";
        }
        telephonyManager.sendUssdRequest(ussdCode, ussdCallback, null);
        boolean awaitSuccess = latch.await(15, TimeUnit.SECONDS);
        if (!awaitSuccess) {
            Log.w(TAG, "sendUssdRequest timeout!!");
            return "";
        }
        return number[0];
    }

    private String parsePhoneNumberFromUssdResponse(String ussdResponse) {
        // 中国移动 *139# 的返回格式示例（实际上应用拿不到这个文本）：
        // "尊敬的客户，您的手机号码是：13800138000。"
        // 这只是示例，实际需要复杂的字符串匹配和正则表达式
        if (ussdResponse == null || ussdResponse.isEmpty()) {
            return null;
        }

        // 非常简单的数字提取（实际场景需要更健壮的解析）
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d{11}");
        java.util.regex.Matcher matcher = pattern.matcher(ussdResponse);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
}
