package com.edgeflow.browser;

import android.util.Log;
import org.mozilla.geckoview.GeckoRuntime;

final class EdgeFlowRuntime {
    static final String EXTENSION_LOCATION = "resource://android/assets/edgeflow/";
    static final String EXTENSION_ID = "edgeflow-core@edgeflow.local";
    private static final String TAG = "EdgeFlowRuntime";
    private static boolean requested;

    static synchronized void ensureCoreInstalled(GeckoRuntime runtime) {
        if (requested) return;
        requested = true;
        runtime.getWebExtensionController()
                .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
                .accept(
                        extension -> Log.i(TAG, "EdgeFlow Core ready: " + extension.id),
                        error -> {
                            requested = false;
                            Log.e(TAG, "EdgeFlow Core install failed", error);
                        });
    }

    private EdgeFlowRuntime() {}
}
