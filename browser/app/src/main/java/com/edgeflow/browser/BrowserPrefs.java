package com.edgeflow.browser;

import android.content.Context;

final class BrowserPrefs {
    static final int AI_AUTO = 0;
    static final int AI_SMOOTH = 1;
    static final int AI_LIVE = 2;

    private static final String PREFS = "edgeflow_browser_prefs";
    private static final String KEY_AI_PROFILE = "ai_profile";

    static int getAiProfile(Context context) {
        int value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_AI_PROFILE, AI_AUTO);
        if (value < AI_AUTO || value > AI_LIVE) return AI_AUTO;
        return value;
    }

    static void setAiProfile(Context context, int profile) {
        int safe = profile < AI_AUTO || profile > AI_LIVE ? AI_AUTO : profile;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_AI_PROFILE, safe).apply();
    }

    static String aiProfileName(int profile) {
        if (profile == AI_SMOOTH) return "Max smoothness";
        if (profile == AI_LIVE) return "Live response";
        return "Auto";
    }

    private BrowserPrefs() {}
}
