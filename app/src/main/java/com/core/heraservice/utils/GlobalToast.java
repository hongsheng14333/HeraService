package com.core.heraservice.utils;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import android.util.*;

public class GlobalToast {
    static final String TAG = "GlobalToast";
    private Context mContext;

    TextView mTipView;
    WindowManager mWindowManager;

    public GlobalToast(Context context) {
        mContext = context;
        //mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    }
    public void showCustomToast(String message) {
        WindowManager windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);

        // 1. 创建布局
        TextView toastView = new TextView(mContext);
        toastView.setText(message);
        toastView.setTextColor(Color.WHITE);
        toastView.setBackgroundColor(Color.BLACK);
        toastView.setPadding(30, 20, 30, 20);
        // 这里你可以加载更复杂的 XML 布局来美化，比如圆角背景

        // 2. 设置 LayoutParams
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        // 关键点：设置 Type 为 APPLICATION_OVERLAY (Android 8.0+)
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        // 设置 Flag：不获取焦点 (不可点击)，保持屏幕常亮等
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        params.format = PixelFormat.TRANSLUCENT;
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = 200; // 距离底部的距离

        // 3. 添加视图
        try {
            windowManager.addView(toastView, params);

            // 4. 定时移除 (模拟 Toast 的短时间显示)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    windowManager.removeView(toastView);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 2000); // 2秒后消失
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
    public void showCustomTip(String message) {
        if (mWindowManager == null) {
            mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        }

        // 1. 创建布局
        mTipView = new TextView(mContext);
        mTipView.setText(message);
        mTipView.setTextColor(Color.WHITE);
        mTipView.setBackgroundColor(Color.BLACK);
        mTipView.setPadding(30, 20, 30, 20);
        // 这里你可以加载更复杂的 XML 布局来美化，比如圆角背景

        // 2. 设置 LayoutParams
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        // 关键点：设置 Type 为 APPLICATION_OVERLAY (Android 8.0+)
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        // 设置 Flag：不获取焦点 (不可点击)，保持屏幕常亮等
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        params.format = PixelFormat.TRANSLUCENT;
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = 200; // 距离底部的距离

        // 3. 添加视图
        try {
            mWindowManager.addView(mTipView, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void hideCustomTip() {
        if (mTipView != null && mWindowManager != null) {
            mWindowManager.removeView(mTipView);
            mTipView = null;
        }
    }*/
}