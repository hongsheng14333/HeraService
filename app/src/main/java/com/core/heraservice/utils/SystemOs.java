package com.core.heraservice.utils;

import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import android.util.Log;
public class SystemOs {
    private static final String TAG = "SystemOs";
    public static String getDeviceName() {
        return Build.MODEL;
    }

    public static String getSerialno() {
        return Build.getSerial();
    }
    public static void writeAuthkey(String filePath, String content) {
        FileOutputStream fos = null;
        if (content == null) {
            return;
        }
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                file.createNewFile();
            }

            fos = new FileOutputStream(file);
            fos.write(content.getBytes());
            fos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static String readAuthkey(String filePath) {
        BufferedReader reader = null;
        StringBuilder content = new StringBuilder();

        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            return content.toString();
        } catch (IOException e) {
            Log.e(TAG, "read file fail: " + e.getMessage());
            return null;
        }
    }
}
