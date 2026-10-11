package com.edgeflow.browser;

import android.content.Context;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class BrowserPrefs {
    static final int AI_AUTO = 0;
    static final int AI_SMOOTH = 1;
    static final int AI_LIVE = 2;

    static final int DATA_AUTO = 0;
    static final int DATA_SAVER = 1;
    static final int DATA_FULL = 2;

    static final int SEARCH_GOOGLE = 0;
    static final int SEARCH_DDG = 1;
    static final int SEARCH_BRAVE = 2;

    private static final String PREFS = "edgeflow_browser_prefs";
    private static final String KEY_AI_PROFILE = "ai_profile";
    private static final String KEY_DATA_MODE = "data_mode";
    private static final String KEY_SEARCH = "search_engine";
    private static final String KEY_COMPAT_HOSTS = "compat_hosts";

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

    static int getDataMode(Context context) {
        int value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_DATA_MODE, DATA_AUTO);
        if (value < DATA_AUTO || value > DATA_FULL) return DATA_AUTO;
        return value;
    }

    static void setDataMode(Context context, int mode) {
        int safe = mode < DATA_AUTO || mode > DATA_FULL ? DATA_AUTO : mode;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_DATA_MODE, safe).apply();
    }

    static String dataModeName(int mode) {
        if (mode == DATA_SAVER) return "Saver";
        if (mode == DATA_FULL) return "Full quality";
        return "Auto";
    }

    static int getSearchEngine(Context context) {
        int value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_SEARCH, SEARCH_GOOGLE);
        if (value < SEARCH_GOOGLE || value > SEARCH_BRAVE) return SEARCH_GOOGLE;
        return value;
    }

    static void setSearchEngine(Context context, int engine) {
        int safe = engine < SEARCH_GOOGLE || engine > SEARCH_BRAVE ? SEARCH_GOOGLE : engine;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_SEARCH, safe).apply();
    }

    static String searchEngineName(int engine) {
        if (engine == SEARCH_DDG) return "DuckDuckGo";
        if (engine == SEARCH_BRAVE) return "Brave Search";
        return "Google";
    }

    static String searchUrl(Context context, String query) {
        int engine = getSearchEngine(context);
        String encoded = android.net.Uri.encode(query == null ? "" : query);
        if (engine == SEARCH_DDG) return "https://duckduckgo.com/?q=" + encoded;
        if (engine == SEARCH_BRAVE) return "https://search.brave.com/search?q=" + encoded;
        return "https://www.google.com/search?q=" + encoded;
    }

    static Set<String> compatibilityHosts(Context context) {
        Set<String> stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_COMPAT_HOSTS, Collections.emptySet());
        return new HashSet<>(stored == null ? Collections.emptySet() : stored);
    }

    static boolean isCompatibilityHost(Context context, String host) {
        if (host == null || host.isEmpty()) return false;
        return compatibilityHosts(context).contains(host.toLowerCase());
    }

    static void setCompatibilityHost(Context context, String host, boolean enabled) {
        if (host == null || host.isEmpty()) return;
        Set<String> hosts = compatibilityHosts(context);
        String key = host.toLowerCase();
        if (enabled) hosts.add(key); else hosts.remove(key);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_COMPAT_HOSTS, hosts).apply();
    }

    private BrowserPrefs() {}
}
