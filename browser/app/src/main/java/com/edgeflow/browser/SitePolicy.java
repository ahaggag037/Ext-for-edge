package com.edgeflow.browser;

import android.net.Uri;

final class SitePolicy {
    static boolean isChatGptFamily(String raw) {
        String host = host(raw);
        return host.equals("chatgpt.com")
                || host.endsWith(".chatgpt.com")
                || host.equals("openai.com")
                || host.endsWith(".openai.com");
    }

    static boolean isTrustedOpenAiAsset(String raw) {
        String host = host(raw);
        return isChatGptFamily(raw)
                || host.equals("oaistatic.com")
                || host.endsWith(".oaistatic.com")
                || host.equals("oaiusercontent.com")
                || host.endsWith(".oaiusercontent.com");
    }

    static boolean shouldDefaultCompatibility(String raw) {
        return isChatGptFamily(raw);
    }

    static String host(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        try {
            Uri uri = Uri.parse(raw);
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private SitePolicy() {}
}
