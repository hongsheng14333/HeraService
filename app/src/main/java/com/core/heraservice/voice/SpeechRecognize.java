package com.core.heraservice.voice;

import static android.os.Looper.getMainLooper;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.idst.nui.AsrResult;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeNuiCallback;
import com.alibaba.idst.nui.KwsResult;
import com.alibaba.idst.nui.NativeNui;
import com.core.heraservice.network.CommonConstant;
import com.core.heraservice.network.DataDef;
import com.core.heraservice.network.WebSocketManager;
import com.core.heraservice.utils.SystemProperty;
import com.core.heraservice.utils.Utils;

import android.media.AudioPlaybackCaptureConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiresApi(api = Build.VERSION_CODES.S)
public class SpeechRecognize  extends TelephonyCallback implements INativeNuiCallback, TelephonyCallback.CallStateListener {
    private static final String TAG = " SpeechRecognizer";

    private String g_appkey = "KfKjXbKWkkefUKpf";
    private String g_token = "";
    private String g_sts_token = "";
    private String g_ak = "LTAI5t7GjkpFLLtPo389RHEF";
    private String g_sk = "jOygVylBOwaSaOhl6ydBeSEPhpdXDA";
    private String g_url = "";
    private Context mContext;
    private String mDebugPath = "";
    private boolean mStopping = false;
    private boolean mInit = false;
    private AudioRecord mAudioRecorder = null;
    private String mRecordingAudioFilePath = "";
    private OutputStream mRecordingAudioFile = null;
    NativeNui nui_instance = new NativeNui();
    private boolean mSaveAudioSwitch = true;
    private String curTaskId = "";
    private TelephonyManager telephonyManager;
    private TelecomManager telecomManager;
    private Thread recordingThread;
    int minBufSize;
    static boolean isRecording = false;
    static private int mRetryCount = 0;
    static private int MAX_RETRY_COUNT = 3;

    private LinkedBlockingQueue<byte[]> tmpAudioQueue = new LinkedBlockingQueue();
    WebSocketManager webSocketManager;

    static private volatile String mVoiceCode = "";
    static private volatile String mSmsCode = "";
    static private volatile String mCurrentSlot = "";
    static private volatile String mCurrentPhoneNum = "";

    @SuppressLint("WrongConstant")
    public SpeechRecognize(Context context) {
        mContext = context;
        doInit();
        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.registerTelephonyCallback(context.getMainExecutor(), this);
        telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String sms_code = intent.getStringExtra("sms_code");
                mSmsCode = sms_code;

                if (webSocketManager != null) {
                    webSocketManager.sendSmsCodeResult(mCurrentSlot, mCurrentPhoneNum, mSmsCode);
                }  else {
                    Log.e(TAG, "webSocketManager is null can not sendSmsCodeResult>>");
                }
            }
        }, new IntentFilter(CommonConstant.RECEIVE_SMS_CODE_ACTION));
    }

    public void setWebSocketManager(WebSocketManager webSocketManager) {
        this.webSocketManager = webSocketManager;
    }

    private void doInit() {
        String asset_path = "";
        mDebugPath = mContext.getExternalCacheDir().getAbsolutePath() + "/debug";
        Utils.createDir(mDebugPath);

        //初始化SDK，注意用户需要在Auth.getTicket中填入相关ID信息才可以使用。
        int ret = nui_instance.initialize(this, genInitParams(asset_path, mDebugPath),
                Constants.LogLevel.LOG_LEVEL_DEBUG, true);
        Log.i(TAG, "doInit result = " + ret);
        if (ret == Constants.NuiResultCode.SUCCESS) {
            mInit = true;
        } else {
            final String msg_text = Utils.getMsgWithErrorCode(ret, "init");
            Log.d(TAG, "doInit fail = " + msg_text);
        }
    }

    private String genInitParams(String workpath, String debugpath) {
        String str = "";
        try {
            //获取账号访问凭证：
            Auth.GetTicketMethod method = Auth.GetTicketMethod.GET_TOKEN_FROM_SERVER_FOR_ONLINE_FEATURES;
            if (!g_appkey.isEmpty()) {
                Auth.setAppKey(g_appkey);
            }
            if (!g_token.isEmpty()) {
                Auth.setToken(g_token);
            }
            if (!g_ak.isEmpty()) {
                Auth.setAccessKey(g_ak);
            }
            if (!g_sk.isEmpty()) {
                Auth.setAccessKeySecret(g_sk);
            }
            Auth.setStsToken(g_sts_token);
            // 此处展示将用户传入账号信息进行交互，实际产品不可以将任何账号信息存储在端侧
            if (!g_appkey.isEmpty()) {
                if (!g_ak.isEmpty() && !g_sk.isEmpty()) {
                    if (g_sts_token.isEmpty()) {
                        method = Auth.GetTicketMethod.GET_ACCESS_IN_CLIENT_FOR_ONLINE_FEATURES;
                    } else {
                        method = Auth.GetTicketMethod.GET_STS_ACCESS_IN_CLIENT_FOR_ONLINE_FEATURES;
                    }
                }
                if (!g_token.isEmpty()) {
                    method = Auth.GetTicketMethod.GET_TOKEN_IN_CLIENT_FOR_ONLINE_FEATURES;
                }
            }
            Log.i(TAG, "Use method:" + method);
            JSONObject object = Auth.getTicket(method);
            while (!object.containsKey("token") && mRetryCount < MAX_RETRY_COUNT) {
                object = Auth.getTicket(method);
                mRetryCount++;
                Log.e(TAG, "Cannot get token retry mRetryCount = " + mRetryCount);
            }
            if (mRetryCount >= MAX_RETRY_COUNT) {
                Log.e(TAG, "getTicket for token retry timeout");
            }

            object.put("device_id", "hera_device_id");
            if (g_url != null && (g_url.isEmpty() || g_url.length() < 1)) {
                g_url = "wss://nls-gateway.cn-shanghai.aliyuncs.com:443/ws/v1"; // 默认
            }
            object.put("url", g_url);

            //当初始化SDK时的save_log参数取值为true时，该参数生效。表示是否保存音频debug，该数据保存在debug目录中，需要确保debug_path有效可写。
            object.put("save_wav", "true");
            //debug目录，当初始化SDK时的save_log参数取值为true时，该目录用于保存中间音频文件。
            object.put("debug_path", debugpath);

            //过滤SDK内部日志通过回调送回到用户层
            object.put("log_track_level", String.valueOf(Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_NONE)));

            // FullMix = 0   // 选用此模式开启本地功能并需要进行鉴权注册
            // FullCloud = 1
            // FullLocal = 2 // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrMix = 3    // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrCloud = 4
            // AsrLocal = 5  // 选用此模式开启本地功能并需要进行鉴权注册
            object.put("service_mode", Constants.ModeAsrCloud); // 必填
            str = object.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // 注意! str中包含ak_id ak_secret token app_key等敏感信息, 实际产品中请勿在Log中输出这类信息！
        Log.i(TAG, "InsideUserContext:" + str);
        return str;
    }

    private String genParams() {
        String params = "";
        try {
            JSONObject nls_config = new JSONObject();

            //参数可根据实际业务进行配置
            //接口说明可见: https://help.aliyun.com/document_detail/173298.html
            //查看 2.开始识别

            // 是否返回中间识别结果，默认值：False。
            nls_config.put("enable_intermediate_result", true);
            // 是否在后处理中添加标点，默认值：False。
            nls_config.put("enable_punctuation_prediction", true);
            nls_config.put("enable_inverse_text_normalization", true);

            nls_config.put("sample_rate", 16000);
            nls_config.put("sr_format", "pcm");

            // DNS解析的超时时间设置(单位ms)，默认5000
            //nls_config.put("dns_timeout", 500);

            nls_config.put("enable_voice_detection", false);

            JSONObject parameters = new JSONObject();

            parameters.put("nls_config", nls_config);
            parameters.put("service_type", Constants.kServiceTypeASR); // 必填

            //如果有HttpDns则可进行设置
//            parameters.put("direct_ip", "1.1.1.1");

            params = parameters.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return params;
    }

    @SuppressLint("WrongConstant")
    private boolean startDialog() {
        if (mAudioRecorder == null) {
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "check record audio permission fail!");
                return false;
            }
            int sampleRate = 16000;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            mAudioRecorder = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build())
                    .setBufferSizeInBytes(minBufSize * 2)
                    .build();
            Log.w(TAG, "AudioRecord new on startDialog...");
        } else {
            Log.w(TAG, "AudioRecord has been new ...");
        }

        //设置相关识别参数，具体参考API文档
        //  initialize()之后startDialog之前调用
        String setParamsString = genParams();
        Log.i(TAG, "nui set params " + setParamsString);
        nui_instance.setParams(setParamsString);
        //开始一句话识别
        int ret = nui_instance.startDialog(Constants.VadMode.TYPE_P2T,
                genDialogParams());
        Log.i(TAG, "start done with " + ret);
        if (ret != 0) {
            final String msg_text = Utils.getMsgWithErrorCode(ret, "start");
            Log.d(TAG, "startDialog fail msg_text = " + msg_text);
        }
        return true;
    }

    private String genDialogParams() {
        String params = "";
        try {
            JSONObject dialog_param = new JSONObject();
            // 运行过程中可以在startDialog时更新临时参数，尤其是更新过期token
            // 注意: 若下一轮对话不再设置参数，则继续使用初始化时传入的参数
            long distance_expire_time_5m = 300;
            dialog_param = Auth.refreshTokenIfNeed(dialog_param, distance_expire_time_5m);

            // 注意: 若需要更换appkey和token，可以直接传入参数
//            dialog_param.put("app_key", "");
//            dialog_param.put("token", "");
            params = dialog_param.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "dialog params: " + params);
        return params;
    }

    @Override
    public void onNuiEventCallback(Constants.NuiEvent event, int resultCode, int arg2, KwsResult kwsResult, AsrResult asrResult) {
        //Log.i(TAG, "event=" + event + " resultCode=" + resultCode);
        if (event == Constants.NuiEvent.EVENT_ASR_STARTED) {
            Log.d(TAG, "onNuiEventCallback EVENT_ASR_STARTED>>>");
            JSONObject jsonObject = JSON.parseObject(asrResult.allResponse);
            JSONObject header = jsonObject.getJSONObject("header");
            curTaskId = header.getString("task_id");
        } else if (event == Constants.NuiEvent.EVENT_ASR_RESULT) {
            mStopping = false;
            JSONObject jsonObject = JSON.parseObject(asrResult.asrResult);
            JSONObject payload = jsonObject.getJSONObject("payload");
            String result = payload.getString("result");
            Log.d(TAG, "onNuiEventCallback onNuiEventCallback result = " + result);

            Pattern pattern = Pattern.compile("(?<!\\d)(\\d{4,6})(?!\\d)");
            Matcher matcher = pattern.matcher(result);
            if (matcher.find()) {
                mVoiceCode = matcher.group(1);
                if (webSocketManager != null) {
                    webSocketManager.sendVoiceCodeResult(mCurrentSlot, mCurrentPhoneNum, mVoiceCode);
                    Log.d(TAG, ">>>received voice code : " + mVoiceCode);
                } else {
                    Log.d(TAG, "webSocketManager is null can not send voicecode>>>");
                }
            }
        } else if (event == Constants.NuiEvent.EVENT_ASR_PARTIAL_RESULT) {
            JSONObject jsonObject = JSON.parseObject(asrResult.asrResult);
            JSONObject payload = jsonObject.getJSONObject("payload");
            String result = payload.getString("result");
            Log.d(TAG, "onNuiEventCallback EVENT_ASR_PARTIAL_RESULT result = " + result);
        } else if (event == Constants.NuiEvent.EVENT_SENTENCE_END) {
            Log.d(TAG, "onNuiEventCallback EVENT_SENTENCE_END result = " + asrResult.asrResult);
        } else if (event == Constants.NuiEvent.EVENT_ASR_ERROR) {
            // asrResult在EVENT_ASR_ERROR中为错误信息，搭配错误码resultCode和其中的task_id更易排查问题，请用户进行记录保存。
            Log.d(TAG, "onNuiEventCallback EVENT_ASR_ERROR result = " + asrResult.asrResult);
            mStopping = false;
        } else if (event == Constants.NuiEvent.EVENT_VAD_START) {
            Log.i(TAG, "onNuiEventCallback  EVENT_VAD_START>>>");
        } else if (event == Constants.NuiEvent.EVENT_VAD_END) {
            Log.i(TAG, "onNuiEventCallback  EVENT_VAD_END>>>");
        } else if (event == Constants.NuiEvent.EVENT_MIC_ERROR) {
            // EVENT_MIC_ERROR表示2s未传入音频数据，请检查录音相关代码、权限或录音模块是否被其他应用占用。
            Log.i(TAG, "onNuiEventCallback  EVENT_MIC_ERROR>>>");
            mStopping = false;
            // 此处也可重新启动录音模块
        } else if (event == Constants.NuiEvent.EVENT_DIALOG_EX) { /* unused */
            Log.i(TAG, "dialog extra message = " + asrResult.asrResult);
        }
    }

    @Override
    public int onNuiNeedAudioData(byte[] buffer, int len) {
        if (mAudioRecorder == null) {
            return -1;
        }
        if (mAudioRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "audio recorder not init");
            return -1;
        }

        // 送入SDK
        int audio_size = mAudioRecorder.read(buffer, 0, len);

        // 音频存储到本地
        if (mSaveAudioSwitch && audio_size > 0) {
            if (mRecordingAudioFile == null) {
                // 音频存储文件未打开，则等获得task_id后打开音频存储文件，否则数据存储到tmpAudioQueue
                if (!curTaskId.isEmpty() && mRecordingAudioFile == null) {
                    try {
                        mRecordingAudioFilePath = mDebugPath + "/" + "sr_task_id_" + curTaskId + ".pcm";
                        Log.i(TAG, "save recorder data into " + mRecordingAudioFilePath);
                        mRecordingAudioFile = new FileOutputStream(mRecordingAudioFilePath, true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    tmpAudioQueue.offer(buffer);
                }
            }
            if (mRecordingAudioFile != null) {
                // 若tmpAudioQueue有存储的音频，先存到音频存储文件中
                if (tmpAudioQueue.size() > 0) {
                    try {
                        // 将未打开recorder前的音频存入文件中
                        byte[] audioData = tmpAudioQueue.take();
                        try {
                            mRecordingAudioFile.write(audioData);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                // 当前音频数据存到音频存储文件
                try {
                    mRecordingAudioFile.write(buffer);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return audio_size;
    }

    @SuppressLint("WrongConstant")
    @Override
    public void onNuiAudioStateChanged(Constants.AudioState state) {
        Log.i(TAG, "onNuiAudioStateChanged");
        if (state == Constants.AudioState.STATE_OPEN) {
            Log.i(TAG, "audio recorder start");
            if (mAudioRecorder == null) {
                //录音初始化，录音参数中格式只支持16bit/单通道，采样率支持8K/16K
                //使用者请根据实际情况选择Android设备的MediaRecorder.AudioSource
                //录音麦克风如何选择,可查看https://developer.android.google.cn/reference/android/media/MediaRecorder.AudioSource
                if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "check record audio permission fail!");
                    return;
                }

                int sampleRate = 16000;
                int channelConfig = AudioFormat.CHANNEL_IN_MONO;
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
                int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

                mAudioRecorder = new AudioRecord.Builder()
                        .setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(audioFormat)
                                .setSampleRate(sampleRate)
                                .setChannelMask(channelConfig)
                                .build())
                        .setBufferSizeInBytes(minBufSize * 2)
                        .build();
                Log.d(TAG, "AudioRecorder new onNuiAudioStateChanged ...");
            }
            if (mAudioRecorder != null) {
                mAudioRecorder.startRecording();
            }
            Log.i(TAG, "audio recorder start done");
        } else if (state == Constants.AudioState.STATE_CLOSE) {
            Log.i(TAG, "audio recorder close");
            if (mAudioRecorder != null) {
                mAudioRecorder.release();
                mAudioRecorder = null;
            }

            try {
                if (mRecordingAudioFile != null) {
                    mRecordingAudioFile.close();
                    mRecordingAudioFile = null;
                    Log.d(TAG, "存储录音音频到: " + mRecordingAudioFilePath);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (state == Constants.AudioState.STATE_PAUSE) {
            Log.i(TAG, "audio recorder pause");
            if (mAudioRecorder != null) {
                mAudioRecorder.stop();
            }

            try {
                if (mRecordingAudioFile != null) {
                    mRecordingAudioFile.close();
                    mRecordingAudioFile = null;
                    Log.d(TAG, "存储录音音频到: " + mRecordingAudioFilePath);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onNuiAudioRMSChanged(float val) {
    }

    @Override
    public void onNuiVprEventCallback(Constants.NuiVprEvent event) {
        Log.i(TAG, "onNuiVprEventCallback event " + event);
    }

    @Override
    public void onCallStateChanged(int state) {
        switch (state) {
            case TelephonyManager.CALL_STATE_OFFHOOK:
                Log.d(TAG, "电话接通/摘机：开始录音");
                startDialog();
                break;

            case TelephonyManager.CALL_STATE_IDLE:
                Log.d(TAG, "电话挂断/空闲：停止录音");
                long ret = nui_instance.stopDialog();
                nui_instance.cancelDialog();
                Log.i(TAG, "cancel dialog " + ret + " end");
                break;

            case TelephonyManager.CALL_STATE_RINGING:
                Log.d(TAG, "正在响铃...");
                new android.os.Handler(getMainLooper()).postDelayed(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        answerCallViaReflection();
                    }
                }, 1500);
                break;
            default:break;
        }
    }

    private void answerCallViaReflection() {
        try {
            // 获取隐藏方法 acceptRingingCall
            Method method = TelecomManager.class.getDeclaredMethod("acceptRingingCall");
            method.setAccessible(true);
            method.invoke(telecomManager);
            Log.d(TAG, "Invoked acceptRingingCall via reflection");
        } catch (Exception e) {
            Log.e(TAG, "Reflection failed", e);
            answerCallViaMediaButton();
        }
    }

    // 模拟按下耳机上的“接听”键
    private void answerCallViaMediaButton() {
        try {
            Log.d(TAG, "Simulating HEADSETHOOK button press...");
            // 发送 KeyEvent.KEYCODE_HEADSETHOOK 按下和抬起事件
            Runtime.getRuntime().exec("input keyevent " + android.view.KeyEvent.KEYCODE_HEADSETHOOK);
        } catch (Exception e) {
            Log.e(TAG, "Media Button simulation failed", e);
        }
    }

    public void startReceiveCode(DataDef.ReceiveCodeData codeData, boolean status) {
        mSmsCode = "";
        mVoiceCode = "";
        if (webSocketManager != null) {
            // 发送切卡成功/失败状态
            webSocketManager.sendSwitchSimResult(codeData.sessionId, codeData.simSlot, codeData.phoneNumber, status);
            mCurrentPhoneNum = codeData.phoneNumber;
            mCurrentSlot = codeData.simSlot;
        }
    }
}
