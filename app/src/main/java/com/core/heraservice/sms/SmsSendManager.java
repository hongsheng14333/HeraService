package com.core.heraservice.sms;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.core.heraservice.BackgroundTaskService;
import com.core.heraservice.SmsSendReceiver;
import com.core.heraservice.network.CommonConstant;
import com.core.heraservice.network.DataDef;
import com.core.heraservice.network.WebSocketManager;
import com.core.heraservice.utils.BatteryHelper;
import com.core.heraservice.utils.NetworkHelper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SmsSendManager implements WebSocketManager.OnSmsSendListener {
    private static final int MAX_RETRY_COUNT = 12;
    private Context mContext;
    private WebSocketManager mWorkManager;
    private BatteryHelper mBatteryHelper;
    private NetworkHelper mNetworkHelper;
    private static final String TAG = "SmsSendManager";
    private static final String SENT_SMS_ACTION = "com.core.heraservice.SMS_SENT_ACTION";
    private static final String DELIVERED_SMS_ACTION = "com.core.heraservice.SMS_DELIVERED_ACTION";
    IntentFilter filter = new IntentFilter(SENT_SMS_ACTION);
    DataDef.SimStatus[] simcarData;
    private static int currentIndex = 0;
    private BackgroundTaskService mBackgroundTaskService;
    private Handler mDelayHandler;
    private HandlerThread mDelaSendyHandlerThread;
    private SmsSendObserver mSmsObserver;

    private void registerSmsObserver(Context context) {
        mSmsObserver = new SmsSendObserver(new Handler(Looper.getMainLooper()), context);
        // notifyForDescendants 必须为 true，以监听到 content://sms/123 这种具体ID的变化
        context.getContentResolver().registerContentObserver(
                Telephony.Sms.CONTENT_URI,
                true,
                mSmsObserver
        );
        Log.d("SmsSendObserver", "SmsObserver 注册成功");
    }


    public SmsSendManager(Context context, WebSocketManager workManager, BackgroundTaskService backgroundTaskService) {
        mContext = context;
        mWorkManager = workManager;
        mBackgroundTaskService = backgroundTaskService;
        mBatteryHelper = new BatteryHelper(context);
        mNetworkHelper = new NetworkHelper(context);

        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                DataDef.SmsSendData smsDat = new DataDef.SmsSendData();
                String number = intent.getStringExtra("number");
                String msg = intent.getStringExtra("msg");
                Log.d(TAG, ">>> number = " + number + " msg = " + msg);
                smsDat.phoneNumber = number;
                smsDat.content = msg;
                sendSms("601133442145", "hhhhhhh");
            }
        }, new IntentFilter("android.intent.action.SEND_SMS"));

        //registerSmsObserver(context);

        mDelaSendyHandlerThread = new HandlerThread("DelaySendThread");
        mDelaSendyHandlerThread.start();
        mDelayHandler = new Handler(mDelaSendyHandlerThread.getLooper());
    }

    @Override
    public void onSmsSend(DataDef.SmsSendData data) {
        //监听短信发送状态
        Intent intentSim = new Intent(CommonConstant.SWITCH_SIMCARD_ACTION);
        new Thread(() -> {
            try {
                //1.获取在线的卡槽号
                for (int i = 0; i < simcarData.length; i++) {
                    if (simcarData[i].status == 1) {
                        // 切到指定SIM卡
                        if (Integer.parseInt(simcarData[i].slot) > 0) {
                            intentSim.putExtra("sim_id", (Integer.parseInt(simcarData[i].slot) - 1) + "");
                            mContext.sendBroadcast(intentSim);
                            Thread.sleep(5000);
                            if (mBackgroundTaskService != null) {
                                if (mBackgroundTaskService.checkSimcardSignal()) {
                                    Log.d(TAG, "check simcard signal start send sms>>>");
                                } else {
                                    Log.d(TAG, "check simcard no signal start send sms maybe fail>>>");
                                }
                            } else {
                                Log.d(TAG, "mBackgroundTaskService is null!");
                            }
                            Thread.sleep(10000);
                            // 发送短信
                            SmsSendReceiver.setSendSmsStatus(false);
                            sendSms(data.phoneNumber, data.content);
                            int count = 0;
                            while (count <= MAX_RETRY_COUNT) {
                                if (SmsSendReceiver.getSendSmsStatus()) {
                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                    break;
                                }
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                count++;
                            }
                            if (count > MAX_RETRY_COUNT && !SmsSendReceiver.getSendSmsStatus()) {
                                Log.d(TAG, "**************** sms send fail ****************");
                                Log.d(TAG, "simcard id: " + simcarData[i].slot);
                                Log.d(TAG, "simcard number: " + simcarData[i].cardNumber);
                                Log.d(TAG, "targent number: " + data.phoneNumber);
                                Log.d(TAG, "send message: " + data.content);
                                Log.d(TAG, "*************************************************");
                                mWorkManager.sendSmsResult(data.smsId, false, "发送失败");
                            } else {
                                Log.d(TAG, "**************** sms send sucess ****************");
                                Log.d(TAG, "simcard id: " + simcarData[i].slot);
                                Log.d(TAG, "simcard number: " + simcarData[i].cardNumber);
                                Log.d(TAG, "targent number: " + data.phoneNumber);
                                Log.d(TAG, "send message: " + data.content);
                                Log.d(TAG, "*************************************************");
                                mWorkManager.sendSmsResult(data.smsId, true, "发送成功");
                            }
                        } else {
                            Log.e(TAG, "invaild simcard slot!!!");
                        }
                    }
                }
            } catch (Exception e) {
                mWorkManager.sendSmsResult(data.smsId, false, e.getMessage());
                Log.e(TAG, "短信发送异常: " + e.getMessage());
            }
        }).start();
    }


    @Override
    public void onTaskPush(DataDef.SmsTaskPushData data) {
        currentIndex = 0;

        //监听短信发送状态
        Intent intentSim = new Intent(CommonConstant.SWITCH_SIMCARD_ACTION);

        new Thread(() -> {
            try {
                //1.获取在线的卡槽号
                int index = 0;
                for (int i = 0; i < simcarData.length; i++) {
                    if (simcarData[i].status == 1) {
                        // 2.切到指定SIM卡
                        if (Integer.parseInt(simcarData[i].slot) > 0) {
                            intentSim.putExtra("sim_id", (Integer.parseInt(simcarData[i].slot) - 1) + "");
                            mContext.sendBroadcast(intentSim);
                            Thread.sleep(5000);

                            if (mBackgroundTaskService != null) {
                                if (mBackgroundTaskService.checkSimcardSignal()) {
                                    Log.d(TAG, "check simcard signal start send sms>>>");
                                } else {
                                    Log.d(TAG, "check simcard no signal start send sms maybe fail>>>");
                                }
                            } else {
                                Log.d(TAG, "mBackgroundTaskService is null!");
                            }

                            Log.d(TAG, ">>>current simlist max = " + data.smsList.size());
                            // 3.开始轮询发送短信
                            for (int j = 0; j < data.maxPerCard; j++) {
                                int count = 0;
                                Log.d(TAG, "#slot = " + i + "start send j = " + j + " targetNumber = " + data.smsList.get((index * data.maxPerCard) + j).phoneNumber +
                                        " content = " + data.smsList.get((index* data.maxPerCard) + j).content);
                                SmsSendReceiver.setSendSmsStatus(false);
                                sendSms(data.smsList.get((index * data.maxPerCard) + j).phoneNumber, data.smsList.get((index * data.maxPerCard) + j).content);
                                while (count <= MAX_RETRY_COUNT) {
                                    if (SmsSendReceiver.getSendSmsStatus()) {
                                        try {
                                            Thread.sleep(1000);
                                        } catch (InterruptedException e) {
                                            throw new RuntimeException(e);
                                        }
                                        break;
                                    }
                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                    count++;
                                }
                                if (count > MAX_RETRY_COUNT && !SmsSendReceiver.getSendSmsStatus()) {
                                    Log.d(TAG, "**************** sms send fail ****************");
                                    Log.d(TAG, "simcard id: " + simcarData[i].slot);
                                    Log.d(TAG, "simcard number: " + simcarData[i].cardNumber);
                                    Log.d(TAG, "targent number: " + data.smsList.get((index * data.maxPerCard) + j).phoneNumber);
                                    Log.d(TAG, "send message: " + data.smsList.get((index * data.maxPerCard) + j).content);
                                    Log.d(TAG, "*************************************************");
                                    mWorkManager.sendSmsResult(data.smsList.get((index * data.maxPerCard) + j).smsId, false, "发送失败");
//                                    int finalIndex = index;
//                                    int finalJ = j;
//                                    mDelayHandler.postDelayed(new Runnable() {
//                                        @Override
//                                        public void run() {
//                                            mWorkManager.sendSmsResult(data.smsList.get((finalIndex * data.maxPerCard) + finalJ).smsId, false, "发送失败");
//                                        }
//                                    }, 8000);
                                } else {
                                    Log.d(TAG, "**************** sms send sucess ****************");
                                    Log.d(TAG, "simcard id: " + simcarData[i].slot);
                                    Log.d(TAG, "simcard number: " + simcarData[i].cardNumber);
                                    Log.d(TAG, "targent number: " + data.smsList.get((index * data.maxPerCard) + j).phoneNumber);
                                    Log.d(TAG, "send message: " + data.smsList.get((index * data.maxPerCard) + j).content);
                                    Log.d(TAG, "*************************************************");
                                    mWorkManager.sendSmsResult(data.smsList.get((index * data.maxPerCard) + j).smsId, true, "发送成功");
//                                    int finalIndex1 = index;
//                                    int finalJ1 = j;
//                                    mDelayHandler.postDelayed(new Runnable() {
//                                        @Override
//                                        public void run() {
//                                            mWorkManager.sendSmsResult(data.smsList.get((finalIndex1 * data.maxPerCard) + finalJ1).smsId, true, "发送成功");
//                                        }
//                                    }, 8000);
                                }
                                Log.d(TAG, "send sms intervalSeconds = " + data.intervalSeconds);
                                Thread.sleep(data.intervalSeconds * 1000);
                            }
                            index++;
                        } else {
                            Log.e(TAG, "invaild simcard slot!!!");
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "短信发送异常: " + e.getMessage());
            }
        }).start();
    }

    private void sendSms(String phoneNumber, String message) {
        try {
            SmsManager smsManager = mContext.getSystemService(SmsManager.class);
            int requestCode = (int) System.currentTimeMillis();
            Intent sentIntent = new Intent(SENT_SMS_ACTION);
            sentIntent.setPackage("com.core.heraservice");
            PendingIntent sentPI = PendingIntent.getBroadcast(mContext, requestCode, sentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

            // 创建送达状态的 PendingIntent (可选)
            /*
            Intent deliveredIntent = new Intent(DELIVERED_SMS_ACTION);
            sentIntent.setPackage("com.core.heraservice");
            PendingIntent deliveredPI = PendingIntent.getBroadcast(mContext, 0, deliveredIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
             */

            // 处理长短信 (超过70个汉字或160字符)
            if (message.length() > 70) {
                ArrayList<String> parts = smsManager.divideMessage(message);

                ArrayList<PendingIntent> sentIntents = new ArrayList<>();
                //ArrayList<PendingIntent> deliveredIntents = new ArrayList<>();

                for (int i = 0; i < parts.size(); i++) {
                    sentIntents.add(sentPI);
                    //deliveredIntents.add(deliveredPI);
                }
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null);
            } else {
                // 发送普通短短信
                smsManager.sendTextMessage(phoneNumber, null, message, sentPI, null);
            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "发送异常: " + e.getMessage());
        }
    }


    /**
     * 获取心跳数据
     */
    public DataDef.HeartbeatData getHeartbeatData(DataDef.SimStatus[] sims, boolean isScaning) {
        DataDef.HeartbeatData heartbeat = new DataDef.HeartbeatData();

        Log.d(TAG, ">>>getHeartbeatData sims = " + sims.toString());

        // SIM卡状态
        heartbeat.sims = Arrays.asList(sims);
        simcarData = sims;

        // 设备状态
        heartbeat.device = new DataDef.DeviceStatus();
        heartbeat.device.battery = mBatteryHelper.getBatteryLevel();
        heartbeat.device.charging = mBatteryHelper.isCharging();
        heartbeat.device.temperature = mBatteryHelper.getTemperature();
        heartbeat.device.networkType = mNetworkHelper.getNetworkType();
        heartbeat.device.scanning = isScaning;
        return heartbeat;
    }
}
