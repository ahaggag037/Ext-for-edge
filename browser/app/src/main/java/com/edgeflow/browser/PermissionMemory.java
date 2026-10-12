package com.edgeflow.browser;

import android.content.Context;
import android.content.SharedPreferences;
import org.mozilla.geckoview.GeckoSession;

final class PermissionMemory {
    private static final String PREFS = "edgeflow_site_permissions";

    static Integer read(Context context, GeckoSession.PermissionDelegate.ContentPermission permission) {
        if (context == null || permission == null || !rememberable(permission.permission)) return null;
        String host = SitePolicy.host(permission.uri);
        if (host.isEmpty()) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = key(host, permission.permission);
        if (!prefs.contains(key)) return null;
        int value = prefs.getInt(key, Integer.MIN_VALUE);
        return value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                || value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY ? value : null;
    }

    static void write(Context context, GeckoSession.PermissionDelegate.ContentPermission permission, int value) {
        if (context == null || permission == null || !rememberable(permission.permission)) return;
        String host = SitePolicy.host(permission.uri);
        if (host.isEmpty()) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(key(host, permission.permission), value).apply();
    }

    static void clearForHost(Context context, String uri) {
        String host = SitePolicy.host(uri);
        if (context == null || host.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        for (String existing : prefs.getAll().keySet()) {
            if (existing.startsWith(host + "|")) edit.remove(existing);
        }
        edit.apply();
    }

    private static boolean rememberable(int permission) {
        return permission == GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION
                || permission == GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION
                || permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE
                || permission == GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS;
    }

    private static String key(String host, int permission) { return host + "|" + permission; }
    private PermissionMemory() {}
}
