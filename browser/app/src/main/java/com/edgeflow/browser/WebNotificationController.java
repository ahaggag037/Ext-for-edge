package com.edgeflow.browser;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import org.mozilla.geckoview.WebNotification;
import org.mozilla.geckoview.WebNotificationDelegate;

final class WebNotificationController implements WebNotificationDelegate {
    private static final String CHANNEL_DEFAULT = "edgeflow_web";
    private static final String CHANNEL_SILENT = "edgeflow_web_silent";
    private final Context context;
    private final NotificationManager manager;
    private final Handler main = new Handler(Looper.getMainLooper());

    WebNotificationController(Context context) {
        this.context = context.getApplicationContext();
        this.manager = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel normal = new NotificationChannel(
                    CHANNEL_DEFAULT, "Website notifications", NotificationManager.IMPORTANCE_DEFAULT);
            normal.setDescription("Notifications requested by websites in EdgeFlow Browser");
            manager.createNotificationChannel(normal);

            NotificationChannel silent = new NotificationChannel(
                    CHANNEL_SILENT, "Silent website notifications", NotificationManager.IMPORTANCE_LOW);
            silent.setDescription("Silent notifications requested by websites in EdgeFlow Browser");
            silent.setSound(null, null);
            silent.enableVibration(false);
            manager.createNotificationChannel(silent);
        }
    }

    @Override public void onShowNotification(WebNotification web) {
        main.post(() -> showOnMain(web));
    }

    @Override public void onCloseNotification(WebNotification web) {
        main.post(() -> {
            if (manager != null) manager.cancel(idFor(web));
            try { web.dismiss(); } catch (Throwable ignored) {}
        });
    }

    private void showOnMain(WebNotification web) {
        if (manager == null) {
            web.dismiss();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            // Site permission and Android notification permission are separate.
            web.dismiss();
            return;
        }

        String source = web.source != null ? web.source : web.origin;
        Intent open = new Intent(context, AegisActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { open.setData(Uri.parse(source)); } catch (Throwable ignored) {}
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(context, idFor(web), open, flags);

        String channel = web.silent ? CHANNEL_SILENT : CHANNEL_DEFAULT;
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, channel)
                : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(emptyTo(web.title, SitePolicy.host(web.origin)))
                .setContentText(emptyTo(web.text, "Website notification"))
                .setContentIntent(pending)
                .setAutoCancel(!web.requireInteraction)
                .setOnlyAlertOnce(false);
        if (web.silent && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(null);
            builder.setVibrate(null);
        }

        try {
            manager.notify(idFor(web), builder.build());
            web.show();
        } catch (Throwable error) {
            web.dismiss();
        }
    }

    private int idFor(WebNotification web) {
        String key = (web.origin == null ? "" : web.origin) + "|" + (web.tag == null ? "" : web.tag)
                + "|" + (web.title == null ? "" : web.title);
        return 0x40000000 | (key.hashCode() & 0x3fffffff);
    }

    private static String emptyTo(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
