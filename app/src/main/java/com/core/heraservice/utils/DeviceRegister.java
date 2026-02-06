package com.core.heraservice.utils;

import android.util.Log;

import com.core.heraservice.network.CommonConstant;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;

public class DeviceRegister {
    private static final String TAG = "DeviceRegister";
    // 注册设备方法
    public static JSONObject registerActive(String serverUrl, String deviceName, String username, String cardKey) throws JSONException {
        String url = serverUrl + "/api/public/device/activate";
        HttpURLConnection connection = null;

        Log.d(TAG, "registerDevice url = " + url) ;
        try {
            // 1. 创建URL连接
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();

            // 2. 设置请求方法
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");

            // 3. 设置超时时间
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            // 4. 允许输入输出
            connection.setDoOutput(true);
            connection.setDoInput(true);

            // 5. 构建请求体
            String requestBody = String.format("{\"deviceName\":\"%s\", \"activationCode\":\"%s\", \"username\":\"%s\"}",
                    deviceName, cardKey, username);
            Log.d(TAG, "registerDevice requestBody = " + requestBody);

            // 6. 发送请求
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // 7. 获取响应码
            int responseCode = connection.getResponseCode();

            // 8. 读取响应
            InputStream inputStream;
            if (responseCode >= 200 && responseCode < 300) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, "utf-8"))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }

            // 9. 解析JSON响应
            return new JSONObject(response.toString());

        } catch (Exception e) {
            JSONObject error = new JSONObject();
            try {
                error.put("code", -1);
                error.put("msg", "请求失败: " + e.getMessage());
            } catch (JSONException ex) {
                throw new RuntimeException(ex);
            }
            Log.e(TAG, "registerDevice error = " + error.toString());
            return error;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}