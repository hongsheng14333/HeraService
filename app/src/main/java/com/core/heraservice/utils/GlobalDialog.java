package com.core.heraservice.utils;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.core.heraservice.R;

public class GlobalDialog {
    private static volatile GlobalDialog instance;
    private WindowManager windowManager;
    private View expireView;
    private TextView tvMessage;

    public GlobalDialog() {
    }

    public static GlobalDialog getInstance() {
        if (instance == null) {
            synchronized (GlobalDialog.class) {
                if (instance == null) {
                    instance = new GlobalDialog();
                }
            }
        }
        return instance;
    }

    public void show(Context context, String message) {
        if (expireView != null) {
            if (tvMessage != null) {
                tvMessage.setText(message);
            }
            return;
        }

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        LayoutInflater inflater = LayoutInflater.from(context);
        expireView = inflater.inflate(R.layout.view_expire_warning, null);
        tvMessage = expireView.findViewById(R.id.tv_message);

        if (tvMessage != null) {
            tvMessage.setText(message);
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.format = PixelFormat.TRANSLUCENT;

        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        params.gravity = Gravity.CENTER;

        try {
            windowManager.addView(expireView, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 关闭提示框
    public void dismiss() {
        if (windowManager != null && expireView != null) {
            try {
                windowManager.removeView(expireView);
            } catch (Exception e) {
                e.printStackTrace();
            }
            expireView = null;
            tvMessage = null;
        }
    }
}
