package com.edgeflow.browser;

import android.content.Context;
import android.content.SharedPreferences;

final class BrowserPrefs {
    static final int AI_AUTO = 0;
    static final int AI_SMOOTH = 1;
    static final int AI_LIVE = 2;

    private static final String PREFS = "edgeflow_browser_prefs";
    private static final String KEY_AI_PROFILE = "ai_profile";
    private static final String KEY_DATA_SAVER = "data_saver";
    private static final String KEY_SECURE_DNS = "secure_dns";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int getAiProfile(Context context) {
        int value = prefs(context).getInt(KEY_AI_PROFILE, AI_AUTO);
        if (value < AI_AUTO || value > AI_LIVE) return AI_AUTO;
        return value;
    }

    static void setAiProfile(Context context, int profile) {
        int safe = profile < AI_AUTO || profile > AI_LIVE ? AI_AUTO : profile;
        prefs(context).edit().putInt(KEY_AI_PROFILE, safe).apply();
    }

    static String aiProfileName(int profile) {
        if (profile == AI_SMOOTH) return "Max smoothness";
        if (profile == AI_LIVE) return "Live response";
        return "Auto";
    }

    static boolean isDataSaver(Context context) {
        return prefs(context).getBoolean(KEY_DATA_SAVER, false);
    }

    static void setDataSaver(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_DATA_SAVER, enabled).apply();
    }

    static boolean isSecureDns(Context context) {
        return prefs(context).getBoolean(KEY_SECURE_DNS, false);
    }

    static void setSecureDns(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SECURE_DNS, enabled).apply();
    }

    private BrowserPrefs() {}
}
