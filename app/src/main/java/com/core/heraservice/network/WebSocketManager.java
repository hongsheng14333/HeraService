package com.core.heraservice.network;

import static com.core.heraservice.network.CommonConstant.HTTP_URL;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import okhttp3.*;
import okio.ByteString;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.core.heraservice.BackgroundTaskService;
import com.core.heraservice.network.DataDef;
import com.core.heraservice.utils.GlobalToast;
import com.google.gson.Gson;

public class WebSocketManager {
    private static final String TAG = "WebSocketManager";
    private static final long RECONNECT_INTERVAL = 5000; // 重连间隔
    private volatile boolean shouldReconnect = true;

    private OkHttpClient client;
    private WebSocket webSocket;
    private Request request;
    private String wsUrl;
    private String deviceCode;
    private String authKey;
    private boolean isConnected = false;
    private WebSocketListener listener;
    private final Gson gson = new Gson();
    private OnSmsSendListener smsSendListener;
    private ScheduledExecutorService heartbeatExecutor;
    private BackgroundTaskService mBackgroundTaskService;

    public interface WebSocketListener {
        void onConnected();
        void onDisconnected(int code, String reason);
        void onError(Throwable t);
    }

    public interface OnSmsSendListener {
        void onSmsSend(DataDef.SmsSendData data);
        void onTaskPush(DataDef.SmsTaskPushData data);
    }

    public WebSocketManager(BackgroundTaskService tasKService, String url, String deviceCode, String authKey, WebSocketListener listener) {
        this.mBackgroundTaskService = tasKService;
        this.wsUrl = url;
        this.deviceCode = deviceCode;
        this.authKey = authKey;
        this.listener = listener;
        initClient();
    }

    private void initClient() {
        client = new OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS) // 心跳保活
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public void connect() {
        try {
            String wsUrl = HTTP_URL.replace("http://", "ws://").replace("https://", "wss://")
                    + "/ws/device/heartbeat?deviceCode=" + deviceCode + "&authKey=" + authKey;
            Log.d(TAG, "ws connect url = " + wsUrl);
            request = new Request.Builder().url(wsUrl).build();

            webSocket = client.newWebSocket(request, new okhttp3.WebSocketListener() {
                @Override
                public void onOpen(okhttp3.WebSocket webSocket, Response response) {
                    Log.d(TAG, "WebSocket connect success");
                    isConnected = true;
                    shouldReconnect = true;
                    if (listener != null) {
                        listener.onConnected();
                    }
                }

                @Override
                public void onMessage(okhttp3.WebSocket webSocket, String text) {
                    Log.d(TAG, "onMessage: " + text);
                    // 处理不同类型的消息
                    handleMessage(text);
                }

                @Override
                public void onClosing(okhttp3.WebSocket webSocket, int code, String reason) {
                    Log.d(TAG, "websocket connect closing: " + code + " - " + reason);
                    isConnected = false;
                }

                @Override
                public void onClosed(okhttp3.WebSocket webSocket, int code, String reason) {
                    Log.d(TAG, "websocket closed: " + code + " - " + reason);
                    isConnected = false;
                    if (listener != null) {
                        listener.onDisconnected(code, reason);
                    }
                }

                @Override
                public void onFailure(okhttp3.WebSocket webSocket, Throwable t, Response response) {
                    Log.e(TAG, "连接失败: " + t.getMessage());
                    isConnected = false;
                    if (listener != null) {
                        listener.onError(t);
                    }
                    if (response != null) {
                        handleConnectionError(response);
                    }
                    // 自动重连
                    scheduleReconnect();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "连接异常: " + e.getMessage());
        }
    }

    private void handleConnectionError(Response response) {
        try {
            int code = response.code();
            String body = response.body() != null ? response.body().string() : "";

            if (code == 401) {
                Log.d(TAG, "❌ 设备认证失败，请检查 deviceCode 和 authKey");
            } else if (code == 403) {
                JSONObject error = new JSONObject(body);
                String msg = error.getString("msg");
                if (msg.contains("未激活")) {
                    Log.d(TAG, "❌ 设备未激活！");
                } else if (msg.contains("已过期")) {
                    Log.d(TAG, "❌ 激活码已过期！");

                    if (error.has("expiresAt")) {
                        String expiresAt = error.getString("expiresAt");
                        Log.d(TAG,"过期时间: " + expiresAt);
                    }
                    if (error.has("remainingDays")) {
                        int remainingDays = error.getInt("remainingDays");
                        Log.d(TAG,"剩余天数: " + remainingDays);
                    }
                    Log.d(TAG,"请联系管理员续期或使用新的激活码");
                    if (null != mBackgroundTaskService && null != mBackgroundTaskService.getmGlobalToast()) {
                        mBackgroundTaskService.getmGlobalToast().showCustomToast("请联系管理员续期或使用新的激活码");
                    }
                }
                Log.d(TAG,"⚠️ 已停止自动重连，请处理激活问题后手动重新连接");
                if (null != mBackgroundTaskService && null != mBackgroundTaskService.getmGlobalToast()) {
                    mBackgroundTaskService.getmGlobalToast().showCustomToast("⚠️ 已停止自动重连，请处理激活问题后手动重新连接");
                }
                stopReconnecting();
            }

        } catch (Exception e) {
            System.err.println("处理错误响应时异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopReconnecting() {
        shouldReconnect = false;
    }


    /**
     * 启动心跳循环
     */
    public void startHeartbeatLoop(int intervalSeconds, DataDef.HeartbeatData heartbeat) {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (isConnected) {
                try {
                    sendHeartbeat(heartbeat);
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onError(e);
                    }
                }
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * 发送心跳数据
     */
    public void sendHeartbeat(DataDef.HeartbeatData heartbeat) {
        if (!isConnected || webSocket == null) return;

        DataDef.DeviceMessage message = new DataDef.DeviceMessage();
        message.type = "HEARTBEAT";
        message.data = gson.toJsonTree(heartbeat);
        message.timestamp = System.currentTimeMillis() / 1000;

        webSocket.send(gson.toJson(message));
    }

    public void setOnSmsSendListener(OnSmsSendListener listener) {
        this.smsSendListener = listener;
    }

    /**
     * 发送短信结果
     */
    public void sendSmsResult(long smsId, boolean success, String errorMessage) {
        if (!isConnected || webSocket == null) return;

        DataDef.SmsResultData result = new DataDef.SmsResultData();
        result.smsId = smsId;
        result.status = success ? 2 : 3;
        result.errorMessage = errorMessage != null ? errorMessage : "";

        DataDef.DeviceMessage message = new DataDef.DeviceMessage();
        message.type = "SMS_RESULT";
        message.data = gson.toJsonTree(result);
        message.timestamp = System.currentTimeMillis() / 1000;

        webSocket.send(gson.toJson(message));
    }

    // 处理收到的消息
    private void handleMessage(String message) {
        try {
            DataDef.ServerMessage msg = gson.fromJson(message, DataDef.ServerMessage.class);
            if (msg == null) return;

            switch (msg.type) {
                case "SMS_SEND":
                    DataDef.SmsSendData smsData = gson.fromJson(msg.data, DataDef.SmsSendData.class);
                    if (smsSendListener != null && smsData != null) {
                        smsSendListener.onSmsSend(smsData);
                    }
                    break;
                case "SMS_TASK_PUSH":
                    DataDef.SmsTaskPushData pushData = gson.fromJson(msg.data, DataDef.SmsTaskPushData.class);
                    if (pushData != null) {
                        Log.d(TAG, "[任务] 收到推送任务: 任务ID=" + pushData.taskId +
                                ", 任务名=" + pushData.taskName +
                                ", 发送模式=" + pushData.sendMode +
                                ", 短信数量=" + (pushData.smsList != null ? pushData.smsList.size() : 0) +
                                ", 间隔=" + pushData.intervalSeconds + "秒" +
                                ", 每卡上限=" + pushData.maxPerCard);
                        if (smsSendListener != null) {
                            smsSendListener.onTaskPush(pushData);
                        }
                    }
                case "ping":
                    break;
                default:
                    Log.d(TAG, "unknow type : " + msg.type);
                    break;
            }
        } catch (Exception e) {
            if (listener != null) {
                listener.onError(e);
            }
        }
    }

    // 自动重连
    private void scheduleReconnect() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isConnected && shouldReconnect) {
                Log.d(TAG, "尝试重新连接...");
                connect();
            }
        }, RECONNECT_INTERVAL);
    }

    // 断开连接
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "client disconnect>>>");
        }
        isConnected = false;
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdown();
        }
    }

    public boolean isConnected() {
        return isConnected;
    }
}