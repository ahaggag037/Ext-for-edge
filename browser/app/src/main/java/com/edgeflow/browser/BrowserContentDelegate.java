package com.edgeflow.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.view.PointerIcon;
import java.util.ArrayList;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.SlowScriptResponse;
import org.mozilla.geckoview.WebResponse;

/** Adds real-browser long-press actions and slow-script recovery while preserving Aegis delegates. */
final class BrowserContentDelegate implements GeckoSession.ContentDelegate {
    private final GeckoSession.ContentDelegate fallback;

    BrowserContentDelegate(GeckoSession.ContentDelegate fallback) {
        this.fallback = fallback;
    }

    @Override public void onTitleChange(GeckoSession s, String title) {
        if (fallback != null) fallback.onTitleChange(s, title);
    }
    @Override public void onPreviewImage(GeckoSession s, String url) {
        if (fallback != null) fallback.onPreviewImage(s, url);
    }
    @Override public void onFocusRequest(GeckoSession s) {
        if (fallback != null) fallback.onFocusRequest(s);
    }
    @Override public void onCloseRequest(GeckoSession s) {
        if (fallback != null) fallback.onCloseRequest(s);
    }
    @Override public void onFullScreen(GeckoSession s, boolean full) {
        if (fallback != null) fallback.onFullScreen(s, full);
    }
    @Override public void onMetaViewportFitChange(GeckoSession s, String fit) {
        if (fallback != null) fallback.onMetaViewportFitChange(s, fit);
    }
    @Override public void onExternalResponse(GeckoSession s, WebResponse response) {
        if (fallback != null) fallback.onExternalResponse(s, response);
    }
    @Override public void onCrash(GeckoSession s) {
        if (fallback != null) fallback.onCrash(s);
    }
    @Override public void onKill(GeckoSession s) {
        if (fallback != null) fallback.onKill(s);
    }
    @Override public void onFirstComposite(GeckoSession s) {
        if (fallback != null) fallback.onFirstComposite(s);
    }
    @Override public void onFirstContentfulPaint(GeckoSession s) {
        if (fallback != null) fallback.onFirstContentfulPaint(s);
    }
    @Override public void onPaintStatusReset(GeckoSession s) {
        if (fallback != null) fallback.onPaintStatusReset(s);
    }
    @Override public void onPointerIconChange(GeckoSession s, PointerIcon icon) {
        if (fallback != null) fallback.onPointerIconChange(s, icon);
    }
    @Override public void onWebAppManifest(GeckoSession s, JSONObject manifest) {
        if (fallback != null) fallback.onWebAppManifest(s, manifest);
    }
    @Override public void onShowDynamicToolbar(GeckoSession s) {
        if (fallback != null) fallback.onShowDynamicToolbar(s);
    }
    @Override public void onHideDynamicToolbar(GeckoSession s) {
        if (fallback != null) fallback.onHideDynamicToolbar(s);
    }

    @Override public GeckoResult<SlowScriptResponse> onSlowScript(GeckoSession s, String file) {
        GeckoResult<SlowScriptResponse> delegated = fallback == null ? null : fallback.onSlowScript(s, file);
        if (delegated != null) return delegated;
        Activity activity = ForegroundActivityTracker.current();
        if (activity == null) return GeckoResult.fromValue(SlowScriptResponse.CONTINUE);
        GeckoResult<SlowScriptResponse> result = new GeckoResult<>();
        String display = file == null || file.trim().isEmpty() ? "This page" : file;
        new AlertDialog.Builder(activity)
                .setTitle("Page is slowing down")
                .setMessage(display + " has been running a script for a long time.")
                .setPositiveButton("Continue", (d, w) -> complete(result, SlowScriptResponse.CONTINUE))
                .setNegativeButton("Stop script", (d, w) -> complete(result, SlowScriptResponse.STOP))
                .setOnCancelListener(d -> complete(result, SlowScriptResponse.CONTINUE))
                .show();
        return result;
    }

    @Override public void onContextMenu(GeckoSession session, int x, int y,
            GeckoSession.ContentDelegate.ContextElement element) {
        if (element == null) return;
        String link = clean(element.linkUri);
        String media = clean(element.srcUri);
        if (link.isEmpty() && media.isEmpty()) {
            if (fallback != null) fallback.onContextMenu(session, x, y, element);
            return;
        }
        Activity activity = ForegroundActivityTracker.current();
        if (activity == null) {
            if (fallback != null) fallback.onContextMenu(session, x, y, element);
            return;
        }

        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        if (!link.isEmpty()) {
            labels.add("Open link in new tab");
            actions.add(() -> openInNewTab(activity, link));
            labels.add("Copy link");
            actions.add(() -> copy(activity, "Link", link));
            labels.add("Share link");
            actions.add(() -> share(activity, element.linkText, link));
        }
        if (!media.isEmpty() && !media.equals(link)) {
            String noun = element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE ? "image"
                    : element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO ? "video"
                    : element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO ? "audio" : "media";
            labels.add("Open " + noun + " in new tab");
            actions.add(() -> openInNewTab(activity, media));
            labels.add("Copy " + noun + " URL");
            actions.add(() -> copy(activity, "Media URL", media));
            labels.add("Share " + noun + " URL");
            actions.add(() -> share(activity, element.title, media));
        }

        new AlertDialog.Builder(activity)
                .setTitle(contextTitle(element))
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which >= 0 && which < actions.size()) actions.get(which).run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void openInNewTab(Activity activity, String url) {
        try {
            Intent intent = new Intent(activity, AegisActivity.class)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        } catch (Throwable ignored) {}
    }

    private static void copy(Activity activity, String label, String value) {
        try {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        } catch (Throwable ignored) {}
    }

    private static void share(Activity activity, String title, String value) {
        try {
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, value);
            if (title != null && !title.isEmpty()) send.putExtra(Intent.EXTRA_TITLE, title);
            activity.startActivity(Intent.createChooser(send, "Share"));
        } catch (Throwable ignored) {}
    }

    private static String contextTitle(GeckoSession.ContentDelegate.ContextElement e) {
        if (e.linkText != null && !e.linkText.trim().isEmpty()) return e.linkText.trim();
        if (e.title != null && !e.title.trim().isEmpty()) return e.title.trim();
        if (e.altText != null && !e.altText.trim().isEmpty()) return e.altText.trim();
        return "Page item";
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static void complete(GeckoResult<SlowScriptResponse> result, SlowScriptResponse value) {
        try { result.complete(value); } catch (Throwable ignored) {}
    }
}
