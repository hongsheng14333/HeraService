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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.android.stk.IStkCallInterface;
import com.core.heraservice.call.CallphoneManager;
import com.core.heraservice.network.CommonConstant;
import com.core.heraservice.network.DataDef;
import com.core.heraservice.network.WebSocketManager;
import com.core.heraservice.sim.SimCardHelper;
import com.core.heraservice.sim.UssdNumberFetcher;
import com.core.heraservice.sms.SmsSendManager;
import com.core.heraservice.utils.DeviceRegister;
import com.core.heraservice.utils.GlobalDialog;
import com.core.heraservice.utils.GlobalToast;
import com.core.heraservice.utils.SystemOs;
import com.core.heraservice.utils.SystemProperty;
import com.core.heraservice.voice.SpeechRecognize;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.CountDownLatch;

public class BackgroundTaskService extends Service {
    private static final String TAG = "BackgroundTaskService";
    private static final String CHANNEL_ID = "BackgroundServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private WebSocketManager webSocketManager;
    private Handler mDeviceRegisterHandler, mWebsocketHandler, mSimcardScanHandler, mSimcardSwitchHandler;
    private HandlerThread mDeviceRegisterHandlerThread, mWebsocketHandlerThread, mSimcardHandlerThread,
            mSimcardSwitchThread;
    private ConnectivityManager mConnectivityManager;
    private String authKey;
    private String deviceCode;
    private boolean isDeviceRegistered = false;

    private SmsSendManager mSmsManager;
    private CallphoneManager mCallphoneManager;

    private CountDownLatch latch = new CountDownLatch(1);

    private  Context mContext;

    private IStkCallInterface mStkService;
    private SimCardHelper mSimCardHelper;
    private UssdNumberFetcher mUssdNumberFetcher;

    public  DataDef.SimStatus[] mSimStatus = null;
    public  DataDef.SimStatus[] mSimEmpty = null;
    private static final int MAIN_SIMCARD = 0;
    private static int currentSelectSlotID = 0;
    private static final int MAX_RETRY_COUNT = 12;
    private static final int MAX_UUSD_RETRY_COUNT = 50;
    private static final int MAX_UUSD_RETRY_COUNT_FOR_SINGAL_CHECK = 10;
    private static final int SIM_SWITCH_SETTLE_DELAY_MS = 3000;
    private static final int SIM_POLL_INTERVAL_MS = 500;
    private boolean mSimCardReady = false;
    private String mCurrentSimSerailNum = "";
    private String mMainSimcardSerialNo = "";
    private View overlayView;
    private WindowManager windowManager;
    private GlobalToast mGlobalToast;
    private SpeechRecognize mSpeechRecognize;
    private boolean isOverlayVisible = false;

    private static volatile boolean isSimScaning = false;
    public GlobalToast getmGlobalToast() {
        return mGlobalToast;
    }

    public class SimStateReceiver extends BroadcastReceiver {
        private final String ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED";
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                int state = mSimCardHelper.getSimState();
                if (state == TelephonyManager.SIM_STATE_READY || state == TelephonyManager.SIM_STATE_LOADED) {
                    Log.d(TAG, "simcard state change->ready state=" + state);
                    mSimCardReady = true;
                } else {
                    Log.d(TAG, "simcard state change state=" + state);
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

        // 初始化simcard 切换handler
        mSimcardSwitchThread = new HandlerThread("SimSwitchThread");
        mSimcardSwitchThread.start();
        mSimcardSwitchHandler = new Handler(mSimcardSwitchThread.getLooper());

        // 初始化connectmanager
        mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        mSimCardHelper = new SimCardHelper(mContext);
        mUssdNumberFetcher = new UssdNumberFetcher(mContext);
        mGlobalToast = new GlobalToast(mContext);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        mSimStatus = new DataDef.SimStatus[16];
        mSimEmpty = new DataDef.SimStatus[16];
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (mSpeechRecognize == null) {
                        mSpeechRecognize = new SpeechRecognize(mContext);
                    }
                }
            }

            @Override
            public void onLost(Network network) {
                Log.d(TAG, "Network is lost>>>");
            }
        });

        mWebsocketHandler.post(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.S)
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
                    mSmsManager = new SmsSendManager(mContext, webSocketManager, BackgroundTaskService.this);
                    webSocketManager.setOnSmsSendListener(mSmsManager);

                    if (mSpeechRecognize == null) {
                        mSpeechRecognize = new SpeechRecognize(mContext);
                    }
                    mSpeechRecognize.setWebSocketManager(webSocketManager);

                    // 初始化打电话管理类
                    mCallphoneManager = new CallphoneManager(mContext, webSocketManager);

                    webSocketManager.connect();

                    doSimcardScan(2, "");
                } else {
                    Log.d(TAG, "设备注册失败,请检查设备网络");
                }
            }
        });

        // 绑定STK
        //bindToStk();

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
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mStkService = null;
        }
    };

    public void doSimcardScan(int intervalSeconds, String scanId) {
        mSimcardScanHandler.post(new Runnable() {
            @Override
            public void run() {
                int i = 0;
                long startTime, endTime;

                isSimScaning = true;
                Log.d(TAG, ">>> 扫卡开始 isSimScaning = true");
                startTime = System.currentTimeMillis();
                // 切回主卡
                Intent intent = new Intent(CommonConstant.SWITCH_SIMCARD_ACTION);
                intent.putExtra("sim_id", 0);
                mContext.sendBroadcast(intent);
                try {
                    Thread.sleep(2000 + intervalSeconds * 1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                // 开始扫卡
                mContext.registerReceiver(simStateReceiver, filter);
                for (i = 0; i < CommonConstant.MAX_SIM_SLOT; i++) {
                    mSimStatus[i] = new DataDef.SimStatus();
                    mSimEmpty[i] = new DataDef.SimStatus();

                    try {
                        Thread.sleep(SIM_SWITCH_SETTLE_DELAY_MS);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    // 获取切卡前的卡信息，后续用来判断是否真的切到了新卡
                    mCurrentSimSerailNum = normalizeSimSerialNo(mSimCardHelper.getSimSerialNo());
                    Log.d(TAG, "current simcard serialno = " + mCurrentSimSerailNum);

                    // 切换SIM卡
                    intent.putExtra("sim_id", i + "");
                    mContext.sendBroadcast(intent);
                    boolean switchReady = waitForSimSwitchReady(i, mCurrentSimSerailNum);
                    String switchedSerialNo = normalizeSimSerialNo(mSimCardHelper.getSimSerialNo());

                    if (!switchReady) {
                        Log.d(TAG, "switch sim slot timeout or not stable, slotid: " + i + " serial=" + switchedSerialNo);
                        markSlotAsNoSim(i);
                    } else {
                        Log.d(TAG, "switch simcard ready slotid: " + i + " serial=" + switchedSerialNo);
                        if (isLikelyEmptySlot(i, switchedSerialNo)) {
                            Log.d(TAG, "switch simcard judged empty slotid: " + i);
                            markSlotAsNoSim(i);
                        } else {
                            Log.d(TAG, "switch simcard success slotid: " + i);
                            fillSlotInfo(i);
                        }
                    }
                    if (i == 0) {
                        mMainSimcardSerialNo = switchedSerialNo;
                    }
                }
                endTime = System.currentTimeMillis();
                // 发送扫卡结果
                Log.d(TAG, "send simcard scan result!");
                if (scanId.length() >= 1) {
                    webSocketManager.sendSimScanResult(scanId, mSimStatus, (int) ((endTime - startTime) / 1000L));
                }
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (scanId.length() >= 1) {
                    webSocketManager.sendSimScanResult(scanId, mSimStatus, (int) ((endTime - startTime) / 1000L));
                }
                mContext.unregisterReceiver(simStateReceiver);
                isSimScaning = false;
                Log.d(TAG, ">>> 扫卡结束 isSimScaning = false, 耗时=" + ((endTime - startTime) / 1000L) + "秒");
            }
        });
    }

    public void switchSimSlot(DataDef.ReceiveCodeData codeData) {
        String simSlot = codeData.simSlot;
        int slotID = Integer.parseInt(simSlot);
        mSimcardSwitchHandler.post(new Runnable() {
            @Override
            public void run() {
                boolean status = false;
                String currentSerialNo = normalizeSimSerialNo(mSimCardHelper.getSimSerialNo());

                Intent intent = new Intent(CommonConstant.SWITCH_SIMCARD_ACTION);
                intent.putExtra("sim_id", (slotID - 1) + "");
                mContext.sendBroadcast(intent);
                try {
                    Thread.sleep(SIM_SWITCH_SETTLE_DELAY_MS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                // 开始切卡
                mContext.registerReceiver(simStateReceiver, filter);
                boolean switchReady = waitForSimSwitchReady(slotID - 1, currentSerialNo);
                String switchedSerialNo = normalizeSimSerialNo(mSimCardHelper.getSimSerialNo());
                if (!switchReady) {
                    Log.d(TAG, "switchSimSlot timeout, simslot not ready slotid: " + slotID);
                    status = false;
                } else if (isLikelyEmptySlot(slotID - 1, switchedSerialNo)) {
                    Log.d(TAG, "switchSimSlot fail slotid: " + slotID + " not insert simcard");
                    status = false;
                } else {
                    Log.d(TAG, "switchSimSlot success slotid: " + slotID + " serial=" + switchedSerialNo);
                    status = true;
                }
                mSpeechRecognize.startReceiveCode(codeData, status);
                mContext.unregisterReceiver(simStateReceiver);
            }
        });
    }

    private boolean waitForSimSwitchReady(int targetSlotIndex, String previousSerialNo) {
        int count = 0;
        String lastSerialNo = "";
        mSimCardReady = false;

        while (count <= MAX_RETRY_COUNT) {
            String currentSerialNo = normalizeSimSerialNo(mSimCardHelper.getSimSerialNo());
            lastSerialNo = currentSerialNo;
            boolean hasIccCard = mSimCardHelper.hasIccCard();
            int simState = mSimCardHelper.getSimState();

            if (hasIccCard && mSimCardReady && !currentSerialNo.isEmpty()) {
                try {
                    Thread.sleep(2000 + intervalSafeDelay(targetSlotIndex));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                Log.d(TAG, "waitForSimSwitchReady success slot=" + targetSlotIndex + " state=" + simState + " serial=" + currentSerialNo);
                return true;
            }

            if (!hasIccCard && count >= 2) {
                Log.d(TAG, "waitForSimSwitchReady no icc card slot=" + targetSlotIndex + " state=" + simState + " serial=" + currentSerialNo);
                return false;
            }

            if (!previousSerialNo.isEmpty() && previousSerialNo.equals(currentSerialNo) && !mSimCardReady && count >= MAX_RETRY_COUNT / 2) {
                Log.d(TAG, "waitForSimSwitchReady serial unchanged slot=" + targetSlotIndex + " serial=" + currentSerialNo);
            }

            try {
                Thread.sleep(SIM_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            count++;
        }

        Log.d(TAG, "waitForSimSwitchReady timeout slot=" + targetSlotIndex + " serial=" + lastSerialNo);
        return false;
    }

    private boolean isLikelyEmptySlot(int slotIndex, String switchedSerialNo) {
        boolean hasIccCard = mSimCardHelper.hasIccCard();
        int simState = mSimCardHelper.getSimState();
        String operatorName = mSimCardHelper.getOperatorNameSafe();

        if (!hasIccCard) {
            return true;
        }

        if (slotIndex != MAIN_SIMCARD && !mMainSimcardSerialNo.isEmpty() && mMainSimcardSerialNo.equals(switchedSerialNo)) {
            return true;
        }

        if (switchedSerialNo.isEmpty() && operatorName.isEmpty() && simState != TelephonyManager.SIM_STATE_READY && simState != TelephonyManager.SIM_STATE_LOADED) {
            return true;
        }

        return false;
    }

    private void markSlotAsNoSim(int slotIndex) {
        setSimStatusOperator(slotIndex, "NO SIM");
        mSimEmpty[slotIndex].operator = "NO SIM";
        setSimStatusCardNumber(slotIndex, "NO SIM");
        mSimEmpty[slotIndex].cardNumber = "";
        setSimStatusSlot(slotIndex, slotIndex + 1 + "");
        mSimEmpty[slotIndex].slot = slotIndex + 1 + "";
        setSimStatusStatus(slotIndex, 0);
        mSimEmpty[slotIndex].status = 0;
    }

    private void fillSlotInfo(int slotIndex) {
        String operatorName = mSimCardHelper.getOperatorNameSafe();
        setSimStatusOperator(slotIndex, operatorName);
        mSimEmpty[slotIndex].operator = operatorName;

        String phoneNumber = mSimCardHelper.getPhoneNumber();
        if (phoneNumber != null && phoneNumber.length() > 1) {
            Log.d(TAG, "telephony get phonenumber success");
            setSimStatusCardNumber(slotIndex, phoneNumber);
            setSimStatusStatus(slotIndex, 1);
            mSimEmpty[slotIndex].status = 1;
        } else {
            Log.d(TAG, "telephony get phonenumber fail try use USSD get>>");
            String numberByUSSD = getPhoneNumber(operatorName);
            if (numberByUSSD.length() > 1) {
                setSimStatusStatus(slotIndex, 1);
                mSimEmpty[slotIndex].status = 1;
            } else {
                setSimStatusStatus(slotIndex, 0);
                mSimEmpty[slotIndex].status = 0;
            }
            setSimStatusCardNumber(slotIndex, numberByUSSD);
        }

        mSimEmpty[slotIndex].cardNumber = "";
        setSimStatusSlot(slotIndex, slotIndex + 1 + "");
        mSimEmpty[slotIndex].slot = slotIndex + 1 + "";
        Log.d(TAG, "add slot id = " + mSimStatus[slotIndex].slot + " operator = " + mSimStatus[slotIndex].operator + " number = " + mSimStatus[slotIndex].cardNumber +
                " status = " + mSimStatus[slotIndex].status);
    }

    private String normalizeSimSerialNo(String serialNo) {
        return serialNo == null ? "" : serialNo.trim();
    }

    private int intervalSafeDelay(int targetSlotIndex) {
        return 2000;
    }


    public String getPhoneNumber(String name) {
        int count = 0;
        String number = "";
        String code = "*139#";

        SystemProperty.set("sys.phone_number", "");
        if (name != null) {
            if (name.toLowerCase().contains("maxis")) {
                code = "*139#";
            } else if (name.toLowerCase().contains("digi")) {
                code = "*126#";
            } else if (name.toLowerCase().contains("u mobile")) {
                code = "*118*6#";
            } else if (name.toLowerCase().contains("celcom")) {
                code = "*126#";
            } else if (name.toLowerCase().contains("uni")) {
                code = "*123#";
            } else if (name.toLowerCase().contains("viettel")) {
                code = "*098#";
            } else if (name.toLowerCase().contains("vinaphone")) {
                code = "*110#";
            } else if (name.toLowerCase().contains("vietnamobile")) {
                code = "*123#";
            } else if (name.toLowerCase().contains("yettel")) {
                code = "*121#";
            } else if (name.toLowerCase().contains("vodafone")) {
                code = "*138#";
            } else if (name.toLowerCase().contains("atom")) {
                code = "*97#";
            } else if (name.toLowerCase().contains("mtp") || name.toLowerCase().contains("mytel") ||
                    name.toLowerCase().contains("u9")) {
                code = "*124#";
            } else if (name.toLowerCase().contains("bangalink") || name.toLowerCase().contains("airtel") ||
                    name.toLowerCase().contains("grameenphone") || name.toLowerCase().contains("robi")) {
                code = "*121#";
            } else {
                code = "*139#";
            }
        }

        Log.d(TAG, "getPhoneNumberoperator = " + name + " code = " + code);

        while (count <= MAX_UUSD_RETRY_COUNT) {
            try {
                number = mUssdNumberFetcher.sendUssdRequest(code);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (number.length() > 1) {
                number = "+" + number;
                break;
            }
            number = SystemProperty.get("sys.phone_number", "");
            if (number.length() > 1) {
                number = "+" + number;
                break;
            }
            count++;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            Log.d(TAG, "sendUssdRequest fail retry count = " + count);
        }
        if (count >= MAX_UUSD_RETRY_COUNT) {
            Log.w(TAG, "sendUssdRequest retry maxcount still fail, start get by property>>");
        }
        return number;
    }

    public boolean checkSimcardSignal() {
        int count = 0;
        String number = "";
        String name = "";
        String code = "*139#";

        SystemProperty.set("sys.phone_number", "");
        while (count <= MAX_UUSD_RETRY_COUNT_FOR_SINGAL_CHECK) {
            name = mSimCardHelper.getOperatorName();
            if (name != null) {
                if (name.toLowerCase().contains("maxis")) {
                    code = "*139#";
                } else if (name.toLowerCase().contains("digi")) {
                    code = "*126#";
                } else if (name.toLowerCase().contains("u mobile")) {
                    code = "*118*6#";
                } else if (name.toLowerCase().contains("celcom")) {
                    code = "*126#";
                } else if (name.toLowerCase().contains("uni")) {
                    code = "*123#";
                } else if (name.toLowerCase().contains("viettel")) {
                    code = "*098#";
                } else if (name.toLowerCase().contains("vinaphone:")) {
                    code = "*110#";
                } else if (name.toLowerCase().contains("vietnamobile")) {
                    code = "*123#";
                } else if (name.toLowerCase().contains("yettel")) {
                    code = "*121#";
                } else if (name.toLowerCase().contains("vodafone")) {
                    code = "*138#";
                } else if (name.toLowerCase().contains("atom")) {
                    code = "*97#";
                } else if (name.toLowerCase().contains("mtp") || name.toLowerCase().contains("mytel") ||
                        name.toLowerCase().contains("u9")) {
                    code = "*124#";
                } else if (name.toLowerCase().contains("bangalink") || name.toLowerCase().contains("airtel") ||
                        name.toLowerCase().contains("grameenphone") || name.toLowerCase().contains("robi")) {
                    code = "*121#";
                } else {
                    code = "*139#";
                }
            }
            try {
                number = mUssdNumberFetcher.sendUssdRequest(code);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (number.length() > 1) {
                number = "+" + number;
                break;
            }
            count++;
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            Log.d(TAG, "[CheckSignal]sendUssdRequest fail retry count = " + count);
        }
        if (count >= MAX_UUSD_RETRY_COUNT_FOR_SINGAL_CHECK) {
            Log.w(TAG, "[CheckSignal] sendUssdRequest retry maxcount still fail, start get by property>>");
            number = SystemProperty.get("sys.phone_number", "");
        }
        return number.length() > 1 ? true : false;
    }


    public synchronized boolean getDeviceScaningStatus() {
        return isSimScaning;
    }

    public synchronized DataDef.SimStatus[] getmSimCardData() {
        if (isSimScaning) {
            Log.d(TAG, "simcard scaning use none cardnumber data>>>");
            return mSimEmpty;
        } else {
            return mSimStatus;
        }
    }

    public synchronized void setSimStatusOperator(int i, String operator) {
        mSimStatus[i].operator = operator;
    }

    public synchronized void setSimStatusSlot(int i, String slot) {
        mSimStatus[i].slot = slot;
    }

    public synchronized void setSimStatusStatus(int i, int status) {
        mSimStatus[i].status = status;
    }

    public synchronized void setSimStatusCardNumber(int i, String cardNumber) {
        mSimStatus[i].cardNumber = cardNumber;
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
                webSocketManager.startHeartbeatLoop(30, mSmsManager);
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
                WindowManager.LayoutParams.MATCH_PARENT, // WindowManager.LayoutParams.MATCH_PARENT
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );

        //  WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
        //  WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
        // WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

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
                        SystemOs.writeAuthkey(CommonConstant.CARD_KEY_FILE, cardKey);
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

        overlayView.setFocusableInTouchMode(true);
        overlayView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                Log.d(TAG, "overlayView keycode = " + keyCode);
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                    hideInputDialog();
                }
                return false;
            }
        });
    }

    public void hideInputDialog() {
        if (isOverlayVisible && overlayView != null) {
            windowManager.removeView(overlayView);
            isOverlayVisible = false;
            overlayView = null;
        }
    }
}
