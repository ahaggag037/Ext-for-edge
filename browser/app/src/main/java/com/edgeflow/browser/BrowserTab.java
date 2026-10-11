package com.edgeflow.browser;

import org.mozilla.geckoview.GeckoSession;

final class BrowserTab {
    final String id;
    final boolean privateMode;
    boolean desktopMode;
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

    BrowserTab(String id, boolean privateMode, boolean desktopMode, String title, String url) {
        this.id = id;
        this.privateMode = privateMode;
        this.desktopMode = desktopMode;
        this.title = title == null ? "New tab" : title;
        this.url = url == null ? "about:blank" : url;
        this.lastAccessMs = System.currentTimeMillis();
    }

    String label() {
        String prefix = privateMode ? "Private · " : "";
        String base = title == null || title.trim().isEmpty() ? url : title;
        if (hibernated) base = "❄ " + base;
        return prefix + base;
    }
}
