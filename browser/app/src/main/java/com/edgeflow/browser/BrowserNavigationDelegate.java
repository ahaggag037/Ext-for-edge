package com.edgeflow.browser;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebRequestError;
import java.util.List;

/** Adds safe external-app routing while preserving the activity's full navigation delegate. */
final class BrowserNavigationDelegate implements GeckoSession.NavigationDelegate {
    private final GeckoSession.NavigationDelegate fallback;

    BrowserNavigationDelegate(GeckoSession.NavigationDelegate fallback) {
        this.fallback = fallback;
    }

    @Override public GeckoResult<AllowOrDeny> onLoadRequest(
            GeckoSession session, LoadRequest request) {
        if (request != null && maybeOpenExternal(request)) {
            return GeckoResult.fromValue(AllowOrDeny.DENY);
        }
        return fallback == null ? null : fallback.onLoadRequest(session, request);
    }

    @Override public GeckoResult<AllowOrDeny> onSubframeLoadRequest(
            GeckoSession session, LoadRequest request) {
        return fallback == null ? null : fallback.onSubframeLoadRequest(session, request);
    }

    @Override public void onLocationChange(GeckoSession session, String url,
            List<GeckoSession.PermissionDelegate.ContentPermission> permissions,
            Boolean hasUserGesture) {
        if (fallback != null) fallback.onLocationChange(session, url, permissions, hasUserGesture);
    }

    @Override public void onCanGoBack(GeckoSession session, boolean canGoBack) {
        if (fallback != null) fallback.onCanGoBack(session, canGoBack);
    }

    @Override public void onCanGoForward(GeckoSession session, boolean canGoForward) {
        if (fallback != null) fallback.onCanGoForward(session, canGoForward);
    }

    @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession session, String uri) {
        return fallback == null ? null : fallback.onNewSession(session, uri);
    }

    @Override public GeckoResult<String> onLoadError(
            GeckoSession session, String uri, WebRequestError error) {
        return fallback == null ? null : fallback.onLoadError(session, uri, error);
    }

    private boolean maybeOpenExternal(LoadRequest request) {
        if (request.uri == null || request.uri.isEmpty()) return false;
        Uri uri;
        try { uri = Uri.parse(request.uri); }
        catch (Throwable ignored) { return false; }
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        scheme = scheme.toLowerCase();
        if ("http".equals(scheme) || "https".equals(scheme)
                || "about".equals(scheme) || "data".equals(scheme)
                || "blob".equals(scheme) || "file".equals(scheme)) return false;

        // Never let background redirects silently launch another app. A direct
        // navigation from EdgeFlow or an active user gesture is required.
        if (!request.hasUserGesture && !request.isDirectNavigation) return false;
        if (!("mailto".equals(scheme) || "tel".equals(scheme) || "sms".equals(scheme)
                || "smsto".equals(scheme) || "geo".equals(scheme)
                || "market".equals(scheme) || "intent".equals(scheme))) return false;

        Activity activity = ForegroundActivityTracker.current();
        if (activity == null) return false;
        try {
            Intent intent;
            if ("intent".equals(scheme)) {
                intent = Intent.parseUri(request.uri, Intent.URI_INTENT_SCHEME);
                // Browser fallbacks are handled in Gecko; do not silently launch a
                // browser_fallback_url from an untrusted intent URI ourselves.
                intent.removeExtra("browser_fallback_url");
            } else {
                intent = new Intent(Intent.ACTION_VIEW, uri);
            }
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            intent.setSelector(null);
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException ignored) {
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
