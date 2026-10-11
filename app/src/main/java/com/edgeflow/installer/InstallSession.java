package com.edgeflow.installer;

import android.content.Context;
import android.content.SharedPreferences;

final class InstallSession {
    static final String PREFS = "edgeflow_install_session";
    static final long MAX_AGE_MS = 8L * 60L * 1000L;

    static void start(Context context, String targetPackage, String uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("active", true)
                .putLong("started_at", System.currentTimeMillis())
                .putString("target_package", targetPackage)
                .putString("crx_uri", uri)
                .putString("state", "STARTED")
                .apply();
    }

    static void mark(Context context, String state) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("state", state).apply();
    }

    static void finish(Context context, String state) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("active", false)
                .putString("state", state)
                .putLong("finished_at", System.currentTimeMillis())
                .apply();
    }

    static boolean active(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("active", false)) return false;
        long age = System.currentTimeMillis() - p.getLong("started_at", 0L);
        if (age < 0 || age > MAX_AGE_MS) {
            finish(context, "TIMED_OUT");
            return false;
        }
        return true;
    }

    static String target(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("target_package", EdgeTargets.CANARY);
    }

    static String state(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("state", "IDLE");
    }

    private InstallSession() {}
}
