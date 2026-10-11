package com.edgeflow.browser;

import org.mozilla.geckoview.GeckoSession;

final class PermissionPolicy {
    static Integer autoDecision(GeckoSession.PermissionDelegate.ContentPermission permission) {
        if (permission == null) return GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY;
        if (permission.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                || permission.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) {
            return permission.value;
        }

        switch (permission.permission) {
            case GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE:
                return GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW;
            case GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE:
                return GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW;
            case GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS:
                if (SitePolicy.isTrustedOpenAiAsset(permission.uri)
                        && (permission.thirdPartyOrigin == null
                        || SitePolicy.isTrustedOpenAiAsset(permission.thirdPartyOrigin))) {
                    return GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW;
                }
                return null;
            case GeckoSession.PermissionDelegate.PERMISSION_TRACKING:
                return GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY;
            default:
                return null;
        }
    }

    static String label(int permission) {
        switch (permission) {
            case GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION:
                return "Location";
            case GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION:
                return "Notifications";
            case GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE:
                return "Persistent storage";
            case GeckoSession.PermissionDelegate.PERMISSION_XR:
                return "XR / immersive content";
            case GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE:
                return "Silent autoplay";
            case GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE:
                return "Audio autoplay";
            case GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS:
                return "Protected media";
            case GeckoSession.PermissionDelegate.PERMISSION_TRACKING:
                return "Tracking exception";
            case GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS:
                return "Cross-site storage access";
            default:
                return "Site capability";
        }
    }

    private PermissionPolicy() {}
}
