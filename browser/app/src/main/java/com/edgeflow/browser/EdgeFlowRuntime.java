package com.edgeflow.browser;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;

final class EdgeFlowRuntime {
    static final String EXTENSION_LOCATION = "resource://android/assets/edgeflow/";
    static final String EXTENSION_ID = "edgeflow-core@edgeflow.local";
    static final String NATIVE_APP = "edgeflow.browser";
    private static final String TAG = "EdgeFlowRuntime";

    interface NativeBridge {
        JSONObject onMessage(GeckoSession session, JSONObject message);
    }

    private static GeckoRuntime runtime;
    private static WebExtension coreExtension;

    static synchronized GeckoRuntime getOrCreate(Context context) {
        if (runtime != null) return runtime;

        int antiTracking = ContentBlocking.AntiTracking.DEFAULT
                | ContentBlocking.AntiTracking.FINGERPRINTING
                | ContentBlocking.AntiTracking.CRYPTOMINING;

        ContentBlocking.Settings blocking = new ContentBlocking.Settings.Builder()
                .antiTracking(antiTracking)
                .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
                .enhancedTrackingProtectionCategory(ContentBlocking.EtpCategory.STANDARD)
                .contentBlockingDatabase(true)
                .cookiePurging(true)
                .emailTrackerBlockingPrivateMode(true)
                .bounceTrackingProtectionMode(
                        ContentBlocking.BounceTrackingProtectionMode.BOUNCE_TRACKING_PROTECTION_MODE_ENABLED)
                .queryParameterStrippingEnabled(true)
                .queryParameterStrippingPrivateBrowsingEnabled(true)
                .build();
        blocking.setSafeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT);

        GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                .contentBlocking(blocking)
                .fissionEnabled(true)
                .lowMemoryDetection(true)
                .forceUserScalableEnabled(true)
                .inputAutoZoomEnabled(true)
                .allowInsecureConnections(GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE)
                .webManifest(true)
                .globalPrivacyControlEnabled(true)
                .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
                .consoleOutput(false)
                .debugLogging(false)
                .build();

        // Aegis runtime tuning: browser-engine features rather than page hacks.
        settings.setWebContentIsolationStrategy(GeckoRuntimeSettings.STRATEGY_ISOLATE_HIGH_VALUE);
        settings.setParallelMarkingEnabled(true);
        settings.setFetchPriorityEnabled(true);
        settings.setCookieBehaviorOptInPartitioning(true);
        settings.setCookieBehaviorOptInPartitioningPBM(true);
        settings.setFingerprintingProtectionPrivateBrowsing(true);

        Context app = context.getApplicationContext();
        runtime = GeckoRuntime.create(app, settings);
        runtime.setWebNotificationDelegate(new WebNotificationController(app));
        runtime.warmUp();
        ensureCoreInstalled(runtime);
        return runtime;
    }

    static synchronized void ensureCoreInstalled(GeckoRuntime runtime) {
        runtime.getWebExtensionController()
                .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
                .accept(
                        extension -> {
                            coreExtension = extension;
                            Log.i(TAG, "EdgeFlow Core ready: " + extension.id);
                        },
                        error -> Log.e(TAG, "EdgeFlow Core install failed", error));
    }

    static void attachBridge(GeckoSession session, NativeBridge bridge) {
        if (session == null || bridge == null || runtime == null) return;
        runtime.getWebExtensionController()
                .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
                .accept(extension -> {
                    coreExtension = extension;
                    session.getWebExtensionController().setMessageDelegate(
                            extension,
                            new WebExtension.MessageDelegate() {
                                @Override
                                public GeckoResult<Object> onMessage(String nativeApp, Object message,
                                                                     WebExtension.MessageSender sender) {
                                    if (!NATIVE_APP.equals(nativeApp)
                                            || sender == null
                                            || sender.session != session
                                            || !(message instanceof JSONObject)) {
                                        return GeckoResult.fromValue(null);
                                    }
                                    JSONObject response = bridge.onMessage(session, (JSONObject) message);
                                    return GeckoResult.fromValue(response);
                                }
                            },
                            NATIVE_APP);
                }, error -> Log.e(TAG, "Could not attach EdgeFlow native bridge", error));
    }

    static GeckoRuntime peek() {
        return runtime;
    }

    private EdgeFlowRuntime() {}
}
