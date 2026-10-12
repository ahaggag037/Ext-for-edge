package com.edgeflow.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class WebNotificationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String key = intent.getStringExtra(WebNotificationBridge.EXTRA_KEY);
        if (WebNotificationBridge.ACTION_CLICK.equals(action)) {
            WebNotificationBridge.handleClick(key,
                    intent.getStringExtra(WebNotificationBridge.EXTRA_SOURCE));
        } else if (WebNotificationBridge.ACTION_DISMISS.equals(action)) {
            WebNotificationBridge.handleDismiss(key);
        }
    }
}
