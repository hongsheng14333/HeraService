package com.core.heraservice.call;

import static android.os.Looper.getMainLooper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telecom.DisconnectCause;
import android.telecom.InCallService;
import android.telecom.TelecomManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.core.heraservice.BackgroundTaskService;
import com.core.heraservice.network.WebSocketManager;
import java.lang.reflect.Method;
import android.telephony.PhoneStateListener;
import android.telephony.PreciseCallState;

@RequiresApi(api = Build.VERSION_CODES.S)
public class CallphoneManager {
    private static final String TAG = "CallphoneManager";
    private static final int LISTEN_PRECISE_CALL_STATE = 0x00000800;
    private Context mContext;
    private TelephonyManager telephonyManager;
    private TelecomManager telecomManager;
    private WebSocketManager mWebSocketManager;

    public CallphoneManager(Context context, WebSocketManager webSocketManager) {
        mContext = context;
        mWebSocketManager = webSocketManager;
        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);

        TestReceiver testReceiver = new TestReceiver();
        IntentFilter filter = new IntentFilter("android.intent.action.CALL_PHONE");
        mContext.registerReceiver(testReceiver, filter);

        telephonyManager.registerTelephonyCallback(context.getMainExecutor(), new PhoneCallStateCallback());
    }

    public class PhoneCallStateCallback extends TelephonyCallback implements TelephonyCallback.PreciseCallStateListener {
        @Override
        public void onPreciseCallStateChanged(@NonNull PreciseCallState callState) {
            // 这里能拿到比普通 PhoneStateListener 更详细的状态
            int backgroundState = callState.getBackgroundCallState();
            int foregroundState = callState.getForegroundCallState();
            int ringingState = callState.getRingingCallState();

            Log.i("CallMonitor", "Fore: " + foregroundState + ", Back: " + backgroundState + ", Ring: " + ringingState);

            // 判断逻辑
            switch (foregroundState) {
                case PreciseCallState.PRECISE_CALL_STATE_DIALING:
                    Log.i("CallMonitor", "正在拨号...");
                    break;
                case PreciseCallState.PRECISE_CALL_STATE_ALERTING:
                    Log.i("CallMonitor", "对方正在响铃 (Alerting)");
                    break;
                case PreciseCallState.PRECISE_CALL_STATE_ACTIVE:
                    Log.i("CallMonitor", "通话已接通 (Active)");
                    break;
                case PreciseCallState.PRECISE_CALL_STATE_DISCONNECTED:
                    Log.i("CallMonitor", "通话已断开");
                    // 这里同样需要配合 disconnect cause 来判断是否空号
                    int cause = callState.getDisconnectCause();
                    int preciseCause = callState.getPreciseDisconnectCause(); // 更精准的原因

                    if (preciseCause == 1) { // 具体的常量值需查阅源码，通常对应 INVALID_NUMBER
                        Log.e("CallMonitor", "可能是空号");
                    }
                    break;
            }
        }
    }


    public class TestReceiver extends BroadcastReceiver {
        private final String ACTION_CALL_PHONE = "android.intent.action.CALL_PHONE";
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_CALL_PHONE.equals(intent.getAction())) {
                callPhoneNumber(mContext, "13202043599");
            }
        }
    }

    public void callPhoneNumber(Context context, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Log.e(TAG, "phonenumber is null");
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + phoneNumber));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void endCallViaReflection() {
        try {
            // 获取隐藏方法 acceptRingingCall
            Method method = TelecomManager.class.getDeclaredMethod("endCall");
            method.setAccessible(true);
            method.invoke(telecomManager);
            Log.d(TAG, "Invoked endCallViaReflection via reflection");
        } catch (Exception e) {
            Log.e(TAG, "Reflection failed", e);
        }
    }
}
