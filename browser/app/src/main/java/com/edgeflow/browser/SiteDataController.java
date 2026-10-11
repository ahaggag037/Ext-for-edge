package com.edgeflow.browser;

import android.net.Uri;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.StorageController;

final class SiteDataController {
    interface Callback { void onDone(boolean success, String message); }

    static void clearCurrentSite(GeckoRuntime runtime, String url, Callback callback) {
        String host = host(url);
        if (runtime == null || host.isEmpty()) {
            callback.onDone(false, "No current site");
            return;
        }
        runtime.getStorageController()
                .clearDataFromHost(host, StorageController.ClearFlags.SITE_DATA)
                .accept(v -> callback.onDone(true, "Site data cleared for " + host),
                        e -> callback.onDone(false, "Could not clear site data"));
    }

    static void clearCurrentPermissions(GeckoRuntime runtime, String url, Callback callback) {
        String host = host(url);
        if (runtime == null || host.isEmpty()) {
            callback.onDone(false, "No current site");
            return;
        }
        runtime.getStorageController()
                .clearDataFromHost(host, StorageController.ClearFlags.PERMISSIONS)
                .accept(v -> callback.onDone(true, "Permissions reset for " + host),
                        e -> callback.onDone(false, "Could not reset permissions"));
    }

    static void clearCaches(GeckoRuntime runtime, Callback callback) {
        if (runtime == null) {
            callback.onDone(false, "Browser runtime is unavailable");
            return;
        }
        runtime.getStorageController()
                .clearData(StorageController.ClearFlags.ALL_CACHES)
                .accept(v -> callback.onDone(true, "Browser caches cleared"),
                        e -> callback.onDone(false, "Could not clear caches"));
    }

    private static String host(String url) {
        if (url == null) return "";
        try {
            String value = Uri.parse(url).getHost();
            return value == null ? "" : value;
        } catch (Throwable ignored) { return ""; }
    }

    private SiteDataController() {}
}
