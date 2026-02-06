package com.core.heraservice;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 启动后台服务
        Intent serviceIntent = new Intent(this, BackgroundTaskService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0及以上使用startForegroundService
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // 立即关闭Activity，不显示UI
        finish();
    }
}