package com.edgeflow.browser;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import java.util.concurrent.ConcurrentHashMap;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebNotification;
import org.mozilla.geckoview.WebNotificationDelegate;

final class WebNotificationBridge {
    static final String ACTION_CLICK = "com.edgeflow.browser.WEB_NOTIFICATION_CLICK";
    static final String ACTION_DISMISS = "com.edgeflow.browser.WEB_NOTIFICATION_DISMISS";
    static final String EXTRA_KEY = "edgeflow.notification.key";
    static final String EXTRA_SOURCE = "edgeflow.notification.source";
    private static final String CHANNEL_ID = "web_notifications";
    private static final ConcurrentHashMap<String, WebNotification> ACTIVE = new ConcurrentHashMap<>();
    private static Context app;

    static void install(Context context, GeckoRuntime runtime) {
        if (context == null || runtime == null) return;
        app = context.getApplicationContext();
        NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Website notifications", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Notifications requested by websites opened in EdgeFlow Browser");
            manager.createNotificationChannel(channel);
        }
        runtime.setWebNotificationDelegate(new WebNotificationDelegate() {
            @Override public void onShowNotification(WebNotification notification) {
                show(notification);
            }

            @Override public void onCloseNotification(WebNotification notification) {
                close(notification);
            }
        });
    }

    private static void show(WebNotification web) {
        if (app == null || web == null) return;
        if (web.privateBrowsing) {
            web.dismiss();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            web.dismiss();
            return;
        }
        NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            web.dismiss();
            return;
        }

        String key = key(web);
        ACTIVE.put(key, web);
        Intent click = new Intent(app, WebNotificationReceiver.class)
                .setAction(ACTION_CLICK)
                .putExtra(EXTRA_KEY, key)
                .putExtra(EXTRA_SOURCE, web.source);
        Intent dismiss = new Intent(app, WebNotificationReceiver.class)
                .setAction(ACTION_DISMISS)
                .putExtra(EXTRA_KEY, key);
        int request = key.hashCode();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getBroadcast(app, request, click, flags);
        PendingIntent deleteIntent = PendingIntent.getBroadcast(app, request ^ 0x51e4, dismiss, flags);

        String title = web.title == null || web.title.trim().isEmpty() ? host(web.origin) : web.title;
        String text = web.text == null ? "" : web.text;
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(app, CHANNEL_ID)
                : new Notification.Builder(app);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setContentIntent(contentIntent)
                .setDeleteIntent(deleteIntent)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE);
        if (web.silent) builder.setSilent(true);
        if (web.requireInteraction) builder.setOngoing(true);
        manager.notify(key, 1, builder.build());
        web.show();
    }

    private static void close(WebNotification web) {
        if (app == null || web == null) return;
        String key = key(web);
        ACTIVE.remove(key);
        NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(key, 1);
        web.dismiss();
    }

    static void handleClick(String key, String source) {
        WebNotification web = key == null ? null : ACTIVE.remove(key);
        if (web != null) {
            try { web.click(); } catch (Throwable ignored) {}
            try { web.dismiss(); } catch (Throwable ignored) {}
        }
        if (app != null && source != null && (source.startsWith("http://") || source.startsWith("https://"))) {
            try {
                Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(source), app, BrowserActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                app.startActivity(open);
            } catch (Throwable ignored) {}
        }
    }

    static void handleDismiss(String key) {
        WebNotification web = key == null ? null : ACTIVE.remove(key);
        if (web != null) {
            try { web.dismiss(); } catch (Throwable ignored) {}
        }
    }

    private static String key(WebNotification web) {
        String origin = web.origin == null ? "" : web.origin;
        String tag = web.tag == null ? "" : web.tag;
        String source = web.source == null ? "" : web.source;
        return Integer.toHexString((origin + "|" + tag + "|" + source).hashCode());
    }

    private static String host(String value) {
        try {
            String host = Uri.parse(value).getHost();
            return host == null || host.isEmpty() ? "Website" : host;
        } catch (Throwable ignored) { return "Website"; }
    }

    private WebNotificationBridge() {}
}
