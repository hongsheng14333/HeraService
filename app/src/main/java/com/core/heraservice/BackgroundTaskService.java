package com.core.heraservice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.android.stk.IStkCallInterface;
import com.core.heraservice.network.CommonConstant;
import com.core.heraservice.network.DataDef;
import com.core.heraservice.network.WebSocketManager;
import com.core.heraservice.sim.SimCardHelper;
import com.core.heraservice.sim.UssdNumberFetcher;
import com.core.heraservice.sms.SmsSendManager;
import com.core.heraservice.utils.DeviceRegister;
import com.core.heraservice.utils.GlobalToast;
import com.core.heraservice.utils.SystemOs;
import com.core.heraservice.utils.SystemProperty;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.CountDownLatch;

public class BackgroundTaskService extends Service {
    private static final String TAG = "BackgroundTaskService";
    private static final String CHANNEL_ID = "BackgroundServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private WebSocketManager webSocketManager;
    private Handler mDeviceRegisterHandler, mWebsocketHandler, mSimcardScanHandler;
    private HandlerThread mDeviceRegisterHandlerThread, mWebsocketHandlerThread, mSimcardHandlerThread;
    private ConnectivityManager mConnectivityManager;
    private String authKey;
    private String deviceCode;
    private boolean isDeviceRegistered = false;

    private SmsSendManager mSmsManager;

    private CountDownLatch latch = new CountDownLatch(1);

    private  Context mContext;

    private IStkCallInterface mStkService;
    private SimCardHelper mSimCardHelper;
    private UssdNumberFetcher mUssdNumberFetcher;

    public  DataDef.SimStatus[] mSimStatus = null;
    private static final int MAIN_SIMCARD = 0;
    private static int currentSelectSlotID = 0;
    private static final int MAX_RETRY_COUNT = 12;
    private static final int MAX_UUSD_RETRY_COUNT = 3;
    private boolean mSimCardReady = false;
    private String mCurrentSimSerailNum = "";
    private String mMainSimcardSerialNo = "";
    private View overlayView;
    private WindowManager windowManager;
    private GlobalToast mGlobalToast;
    private boolean isOverlayVisible = false;

    public GlobalToast getmGlobalToast() {
        return mGlobalToast;
    }

    public class SimStateReceiver extends BroadcastReceiver {
        private final String ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED";
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                int state = mSimCardHelper.getSimState();
                if (state == TelephonyManager.SIM_STATE_READY) {
                    Log.d(TAG, "simcard state change->ready>>>");
                    mSimCardReady = true;
                }
            }
        }
    }
    SimStateReceiver simStateReceiver = new SimStateReceiver();
    IntentFilter filter = new IntentFilter("android.intent.action.SIM_STATE_CHANGED");


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        createNotificationChannel();

        mContext = BackgroundTaskService.this;

        deviceCode = "DEV_" + SystemOs.getDeviceName().replace(" ", "") + "_" + SystemOs.getSerialno();

        // 初始化设备注册handler
        mDeviceRegisterHandlerThread = new HandlerThread("DeviceRegisterThread");
        mDeviceRegisterHandlerThread.start();
        mDeviceRegisterHandler = new Handler(mDeviceRegisterHandlerThread.getLooper());

        // 初始化websocket连接handler
        mWebsocketHandlerThread = new HandlerThread("WebsocketThread");
        mWebsocketHandlerThread.start();
        mWebsocketHandler = new Handler(mWebsocketHandlerThread.getLooper());

        // 初始化simcard 扫描handler
        mSimcardHandlerThread = new HandlerThread("SimscanThread");
        mSimcardHandlerThread.start();
        mSimcardScanHandler = new Handler(mSimcardHandlerThread.getLooper());

        // 初始化connectmanager
        mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        mSimCardHelper = new SimCardHelper(mContext);
        mUssdNumberFetcher = new UssdNumberFetcher(mContext);
        mGlobalToast = new GlobalToast(mContext);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        mSimStatus = new DataDef.SimStatus[16];
    }

    private void initHttpRequest() {
        mDeviceRegisterHandler.post(new Runnable() {
            @Override
            public void run() {
                SystemProperty.set("ctl.start", "set_enforce");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                File key = new File(CommonConstant.AUTH_KEY_FILE);
                if (!key.exists()) {
                    Log.d(TAG, "authkey is not exist need register>>>");
                    showInputDialog();

                } else {
                    authKey = SystemOs.readAuthkey(CommonConstant.AUTH_KEY_FILE);
                    Log.d(TAG, "device have register complete read authkey = " + authKey);
                    isDeviceRegistered = true;
                    latch.countDown();
                }
                //SystemProperty.set("ctl.start", "clear_enforce");
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);

        mConnectivityManager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.d(TAG, "Network is available>>>");
                initHttpRequest();
            }

            @Override
            public void onLost(Network network) {
                Log.d(TAG, "Network is lost>>>");
            }
        });

        mWebsocketHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (isDeviceRegistered) {
                    Log.d(TAG, "device registered start websocket connect>>>");

                    // 初始化websocket
                    initWebSocket(deviceCode, authKey);

                    // 初始化短信管理类
                    mSmsManager = new SmsSendManager(mContext, webSocketManager);

                    webSocketManager.setOnSmsSendListener(mSmsManager);
                    webSocketManager.connect();
                } else {
                    Log.d(TAG, "设备注册失败,请检查设备网络");
                }
            }
        });

        // 绑定STK
        bindToStk();

        return START_STICKY;
    }

    void startAppByPackage(String packageName) {
        PackageManager pm = mContext.getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(launchIntent);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // 获取到 AIDL 接口实例
            mStkService = IStkCallInterface.Stub.asInterface(service);
            Log.d(TAG, "Connected to STK!");
            doSimcardScan();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mStkService = null;
        }
    };

    void doSimcardScan() {
        mSimcardScanHandler.post(new Runnable() {
            @Override
            public void run() {
                int i = 0;
                mContext.registerReceiver(simStateReceiver, filter);
                Intent intent = new Intent(CommonConstant.SWITCH_SIMCARD_ACTION);
                for (i = 0; i < CommonConstant.MAX_SIM_SLOT; i++) {
                    int count = 0;

                    mSimStatus[i] = new DataDef.SimStatus();

                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    mSimCardReady = false;

                    // 获取当前SIM卡IMEI
                    mCurrentSimSerailNum = mSimCardHelper.getSimSerialNo();
                    Log.d(TAG, "current simcard serialno = " + mCurrentSimSerailNum);

                    // 切换SIM卡
                    intent.putExtra("sim_id", i + "");
                    mContext.sendBroadcast(intent);
                    while (count <= MAX_RETRY_COUNT) {
                        if (mSimCardReady) {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            Log.d(TAG, "simcard ready new imei  = " + mSimCardHelper.getSimSerialNo());
                            break;
                        }
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        count++;
                    }
                    if (count > MAX_RETRY_COUNT) {
                        Log.d(TAG, "switch sim slot timeout, simslot not change slotid: " + i);
                        mSimStatus[i].operator = mSimCardHelper.getOperatorName();
                        if ((mSimCardHelper.getPhoneNumber()).length() > 1) {
                            Log.d(TAG, "telephony get phonenumber success！");
                            mSimStatus[i].cardNumber = mSimCardHelper.getPhoneNumber();
                        } else {
                            Log.d(TAG, "telephony get phonenumber fail try use USSD get>>");
                            mSimStatus[i].cardNumber = getPhoneNumber();
                        }
                        mSimStatus[i].slot = i + 1 + "";
                        mSimStatus[i].status = 1;
                    } else {
                        if ((mMainSimcardSerialNo.equals(mSimCardHelper.getSimSerialNo()))) {
                            Log.d(TAG, "switch simcard fail slotid: " + i + " not insert simcard");
                            mSimStatus[i].operator = "NO SIM";
                            mSimStatus[i].cardNumber = "NO SIM";
                            mSimStatus[i].slot = i + 1 + "";
                            mSimStatus[i].status = 0;
                        } else {
                            Log.d(TAG, "switch simcard success slotid: " + i);
                            mSimStatus[i].operator = mSimCardHelper.getOperatorName();
                            mSimStatus[i].cardNumber = mSimCardHelper.getPhoneNumber();
                            if ((mSimCardHelper.getPhoneNumber()).length() > 1) {
                                Log.d(TAG, "telephony get phonenumber success！");
                                mSimStatus[i].cardNumber = mSimCardHelper.getPhoneNumber();
                            } else {
                                Log.d(TAG, "telephony get phonenumber fail try use USSD get>>");
                                mSimStatus[i].cardNumber = getPhoneNumber();
                            }

                            mSimStatus[i].slot = i + 1 + "";
                            mSimStatus[i].status = 1;
                            Log.d(TAG, "add slot id = " + mSimStatus[i].slot + " operator = " + mSimStatus[i].operator + " number = " + mSimStatus[i].cardNumber);
                        }
                    }
                    if (i == 0) {
                        mMainSimcardSerialNo = mSimCardHelper.getSimSerialNo();
                    }
                }
                mContext.unregisterReceiver(simStateReceiver);
            }
        });
    }

    private String getPhoneNumber() {
        int count = 0;
        String number = "";
        while (count <= MAX_UUSD_RETRY_COUNT) {
            try {
                number = mUssdNumberFetcher.sendUssdRequest("*139#");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (number.length() > 1) {
                number = "+" + number;
                break;
            }
            count++;
            Log.d(TAG, "sendUssdRequest fail retry count = " + count);
        }
        if (count >= MAX_UUSD_RETRY_COUNT) {
            Log.w(TAG, "sendUssdRequest retry maxcount still fail!");
        }
        return number;
    }

    public DataDef.SimStatus[] getmSimCardData() {
        return mSimStatus;
    }

    private void bindToStk() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.android.stk", "com.android.stk.StkAppService"));
        intent.setAction("com.android.stk.BIND_STK");
        // 绑定服务
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");

        // 尝试重启服务
        Intent restartIntent = new Intent(this, BackgroundTaskService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent);
        } else {
            startService(restartIntent);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Background Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Channel for Background Service");
            serviceChannel.setSound(null, null);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("后台服务运行中")
                .setContentText("服务正在后台运行...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)
                .setSound(null);
        return builder.build();
    }

    private void initWebSocket(String deviceCode, String authKey) {
        webSocketManager = new WebSocketManager(BackgroundTaskService.this, CommonConstant.WEB_SOCKET, deviceCode, authKey,
                new WebSocketManager.WebSocketListener() {
            @Override
            public void onConnected() {
                Log.d(TAG, "initWebSocket onConnected>>>");
                //启动心跳循环
                webSocketManager.startHeartbeatLoop(30, mSmsManager.getHeartbeatData(mSimStatus));
            }
            @Override
            public void onDisconnected(int code, String reason) {
                Log.d(TAG, "initWebSocket onDisconnected>>>");
            }

            @Override
            public void onError(Throwable t) {
                Log.d(TAG, "initWebSocket onError: " + t.getMessage());
            }
        });
    }

    public void showInputDialog() {
        if (isOverlayVisible) return;

        overlayView = LayoutInflater.from(mContext).inflate(R.layout.global_input_dialog, null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT, // 改为MATCH_PARENT
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        EditText etUsername = overlayView.findViewById(R.id.et_username);
        EditText etCardKey = overlayView.findViewById(R.id.et_card_key);
        Button btnConfirm = overlayView.findViewById(R.id.btn_confirm);

        btnConfirm.setOnClickListener(v -> {
            String username = etUsername.getText().toString();
            String cardKey = etCardKey.getText().toString();

            Log.d(TAG, "showInputDialog name = " + username + " cardKey = " + cardKey);

            if (username == null || cardKey == null || username.length() < 1 || cardKey.length() < 1) {
                Log.d(TAG, "showInputDialog name or cardkey is null");
                mGlobalToast.showCustomToast("用户名和卡密不能为空");
                return;
            }

            JSONObject response = null;
            try {
                response = DeviceRegister.registerActive(CommonConstant.HTTP_URL,
                        deviceCode, username, cardKey);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            if (response != null) {
                try {
                    int code = response.getInt("code");
                    if (200 == code) {
                        Log.d(TAG, "**************** device active sucess ****************");
                        JSONObject data = response.getJSONObject("data");
                        Log.d(TAG, "deviceID: " + data.getString("deviceId"));
                        Log.d(TAG, "tenantId: " + data.getString("tenantId"));
                        Log.d(TAG, "deviceCode: " + data.getString("deviceCode"));
                        Log.d(TAG, "authKey: " + data.getString("authKey"));
                        Log.d(TAG, "expiresAt: " + data.getString("expiresAt"));
                        Log.d(TAG, "remainingDays: " + data.getString("remainingDays"));
                        Log.d(TAG, "message: " + data.getString("message"));
                        Log.d(TAG, "********************************************************");
                        SystemOs.writeAuthkey(CommonConstant.AUTH_KEY_FILE, data.getString("authKey"));
                        isDeviceRegistered = true;
                        authKey = SystemOs.readAuthkey(CommonConstant.AUTH_KEY_FILE);
                        Log.d(TAG, "device register complete read authkey = " + authKey);
                        mGlobalToast.showCustomToast("激活成功");
                        latch.countDown();
                        hideInputDialog();
                    } else {
                        mGlobalToast.showCustomToast("激活时失败, 设备异常");
                        String errorMsg = response.getString("message");
                        Log.d(TAG, "device  activate fail error msg = " + errorMsg);
                    }
                } catch (JSONException e) {
                    mGlobalToast.showCustomToast("激活时失败,服务器访问异常");
                    throw new RuntimeException(e);
                }
            } else {
                mGlobalToast.showCustomToast("激活时失败,请检查手机网络环境");
            }
        });

        windowManager.addView(overlayView, params);
        isOverlayVisible = true;
    }

    public void hideInputDialog() {
        if (isOverlayVisible && overlayView != null) {
            windowManager.removeView(overlayView);
            isOverlayVisible = false;
            overlayView = null;
        }
    }
}