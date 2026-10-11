package com.edgeflow.browser;

import org.mozilla.geckoview.GeckoSession;

final class BrowserTab {
    final String id;
    final boolean privateMode;
    boolean desktopMode;
    boolean compatibilityMode;
    String title;
    String url;
    String sessionState;
    GeckoSession session;
    boolean hibernated;
    long lastAccessMs;
    boolean canGoBack;
    boolean canGoForward;
    boolean generating;
    boolean shielded;
    int pain;
    int turnCount;
    long lastPageStartMs;
    long lastFcpMs;
    boolean loading;
    boolean lastLoadSucceeded = true;
    int lastErrorCode;
    int lastErrorCategory;

    BrowserTab(String id, boolean privateMode, boolean desktopMode, String title, String url) {
        this.id = id;
        this.privateMode = privateMode;
        this.desktopMode = desktopMode;
        this.title = title == null ? "New tab" : title;
        this.url = url == null ? "about:blank" : url;
        this.compatibilityMode = SitePolicy.shouldDefaultCompatibility(this.url);
        this.lastAccessMs = System.currentTimeMillis();
    }

    String label() {
        String prefix = privateMode ? "Private · " : "";
        String base = title == null || title.trim().isEmpty() ? url : title;
        if (compatibilityMode) base = "↔ " + base;
        if (hibernated) base = "❄ " + base;
        return prefix + base;
    }
}
