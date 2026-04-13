package com.core.heraservice.sms;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.Telephony;
import android.util.Log;

public class SmsSendObserver extends ContentObserver {
    private static final String TAG = "SmsSendObserver";
    private final Context mContext;

    // 记录上一条处理过的短信ID，防止系统数据库多次更新导致重复回调
    private long lastProcessedId = -1;

    public SmsSendObserver(Handler handler, Context context) {
        super(handler);
        this.mContext = context;
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);

        // 每次数据库变化时，查询最新的一条短信记录
        Uri smsUri = Telephony.Sms.CONTENT_URI;
        String[] projection = new String[]{
                Telephony.Sms._ID,
                Telephony.Sms.TYPE,
                Telephony.Sms.ADDRESS
        };

        // 按时间倒序查询最新的一条
        try (Cursor cursor = mContext.getContentResolver().query(
                smsUri, projection, null, null, Telephony.Sms.DATE + " DESC LIMIT 1")) {

            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID));
                int type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE));
                String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));

                // 只有当这条短信是新出现的，且状态发生变化时才处理
                if (id != lastProcessedId) {
                    if (type == Telephony.Sms.MESSAGE_TYPE_SENT) { // type = 2
                        Log.i(TAG, "检测到短信发送成功! ID: " + id + ", 号码: " + address);
                        lastProcessedId = id;
                        // TODO: 在这里执行发送成功后的业务逻辑

                    } else if (type == Telephony.Sms.MESSAGE_TYPE_FAILED) { // type = 5
                        Log.e(TAG, "检测到短信发送失败! ID: " + id + ", 号码: " + address);
                        lastProcessedId = id;
                        // TODO: 处理发送失败逻辑
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "查询短信数据库异常", e);
        }
    }
}
