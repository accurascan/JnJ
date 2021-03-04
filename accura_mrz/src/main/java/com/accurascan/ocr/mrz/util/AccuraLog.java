package com.accurascan.ocr.mrz.util;

import android.util.Log;

import androidx.annotation.Keep;

@Keep
public class AccuraLog {
    private static boolean DEBUG = false;

    public static boolean isLogEnable() {
        return DEBUG;
    }

    public static void enableLogs(boolean isLogEnable) {
        AccuraLog.DEBUG = isLogEnable;
    }

    public static void loge(String tag, String s) {
        try {
            if (isLogEnable()) {
                Log.d("AccuraLog."+tag, "" + s);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
