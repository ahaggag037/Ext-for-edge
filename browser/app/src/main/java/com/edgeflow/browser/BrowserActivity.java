package com.edgeflow.browser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;

public final class BrowserActivity extends Activity implements PerformanceGuardian.Listener {
    private static final String HOME = "https://chatgpt.com/";
    private static final int REQ_FILE = 7001;
    private static final int REQ_ANDROID_PERMISSIONS = 7002;

    private final ArrayList<BrowserTab> tabs = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private GeckoRuntime runtime;
    private GeckoView geckoView;
    private LinearLayout toolbar;
    private EditText address;
    private TextView status;
    private Button tabsButton;
    private int currentIndex = -1;
    private PerformanceGuardian guardian;

    private GeckoSession.PromptDelegate.FilePrompt pendingFilePrompt;
    private GeckoResult<GeckoSession.PromptDelegate.PromptResponse> pendingFileResult;
    private GeckoSession.PermissionDelegate.Callback pendingPermissionCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());

        runtime = EdgeFlowRuntime.getOrCreate(this);
        guardian = new PerformanceGuardian(this, this);

        SessionStore.Snapshot snapshot = SessionStore.load(this);
        tabs.addAll(snapshot.tabs);
        if (tabs.isEmpty()) {
            tabs.add(new BrowserTab(UUID.randomUUID().toString(), false, false, "ChatGPT", HOME));
        }
        currentIndex = Math.min(Math.max(snapshot.selected, 0), tabs.size() - 1);

        String external = externalUrl(getIntent());
        if (external != null) {
            BrowserTab first = tabs.get(currentIndex);
            first.url = external;
            first.sessionState = null;
        }
        switchTo(currentIndex);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (guardian != null) guardian.start();
    }

    @Override
    protected void onStop() {
        persistState();
        if (guardian != null) guardian.stop();
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String url = externalUrl(intent);
        if (url != null) newTab(false, url);
    }

    private String externalUrl(Intent intent) {
        if (intent == null) return null;
        Uri data = intent.getData();
        if (data == null) return null;
        String scheme = data.getScheme();
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                ? data.toString() : null;
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 13, 18));

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(2), dp(2), dp(2), dp(2));
        toolbar.setBackgroundColor(Color.rgb(18, 22, 29));

        Button back = toolbarButton("‹");
        back.setContentDescription("Back");
        back.setOnClickListener(v -> goBack());
        toolbar.addView(back, fixed(dp(42), dp(48)));

        Button forward = toolbarButton("›");
        forward.setContentDescription("Forward");
        forward.setOnClickListener(v -> {
            BrowserTab tab = currentTab();
            if (tab != null && tab.session != null && tab.canGoForward) tab.session.goForward();
        });
        toolbar.addView(forward, fixed(dp(42), dp(48)));

        Button reload = toolbarButton("↻");
        reload.setContentDescription("Reload");
        reload.setOnClickListener(v -> {
            BrowserTab tab = currentTab();
            if (tab != null && tab.session != null) tab.session.reload();
        });
        toolbar.addView(reload, fixed(dp(42), dp(48)));

        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(14);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.rgb(130, 140, 155));
        address.setHint("Search or enter address");
        address.setBackgroundColor(Color.rgb(31, 37, 48));
        address.setPadding(dp(12), 0, dp(12), 0);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate(address.getText().toString());
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        addressParams.setMargins(dp(3), 0, dp(3), 0);
        toolbar.addView(address, addressParams);

        tabsButton = toolbarButton("1");
        tabsButton.setTextSize(14);
        tabsButton.setContentDescription("Tabs");
        tabsButton.setOnClickListener(v -> showTabs());
        toolbar.addView(tabsButton, fixed(dp(44), dp(48)));

        Button menu = toolbarButton("⋮");
        menu.setContentDescription("EdgeFlow menu");
        menu.setOnClickListener(v -> showMenu());
        toolbar.addView(menu, fixed(dp(42), dp(48)));

        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextColor(Color.rgb(160, 180, 200));
        status.setTextSize(10);
        status.setSingleLine(true);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(8), dp(1), dp(8), dp(2));
        status.setBackgroundColor(Color.rgb(13, 17, 23));
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(20)));

        geckoView = new GeckoView(this);
        geckoView.setViewBackend(GeckoView.BACKEND_SURFACE_VIEW);
        geckoView.coverUntilFirstPaint(Color.rgb(10, 13, 18));
        root.addView(geckoView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private BrowserTab currentTab() {
        return currentIndex >= 0 && currentIndex < tabs.size() ? tabs.get(currentIndex) : null;
    }

    private void ensureSession(BrowserTab tab) {
        if (tab.session != null && tab.session.isOpen()) return;

        GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
                .usePrivateMode(tab.privateMode)
                .useTrackingProtection(true)
                .suspendMediaWhenInactive(true)
                .userAgentMode(tab.desktopMode
                        ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                        : GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .viewportMode(tab.desktopMode
                        ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                        : GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                .build();

        GeckoSession session = new GeckoSession(settings);
        tab.session = session;
        tab.hibernated = false;
        configureSession(tab, session);
        session.open(runtime);
        EdgeFlowRuntime.attachBridge(session, (origin, message) -> onExtensionMessage(tab, origin, message));

        if (tab.sessionState != null && !tab.sessionState.isEmpty()) {
            try {
                GeckoSession.SessionState restored = GeckoSession.SessionState.fromString(tab.sessionState);
                if (restored != null) session.restoreState(restored);
                else session.loadUri(tab.url == null ? HOME : tab.url);
            } catch (Throwable ignored) {
                session.loadUri(tab.url == null ? HOME : tab.url);
            }
        } else {
            session.loadUri(tab.url == null ? HOME : tab.url);
        }
    }

    private void configureSession(BrowserTab tab, GeckoSession session) {
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onLocationChange(GeckoSession s, String url,
                    List<GeckoSession.PermissionDelegate.ContentPermission> perms,
                    Boolean hasUserGesture) {
                if (url != null) tab.url = url;
                if (tab == currentTab() && url != null && !address.hasFocus()) address.setText(url);
                persistState();
            }

            @Override public void onCanGoBack(GeckoSession s, boolean value) { tab.canGoBack = value; }
            @Override public void onCanGoForward(GeckoSession s, boolean value) { tab.canGoForward = value; }

            @Override
            public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) {
                BrowserTab child = createTab(false, uri == null ? HOME : uri);
                ensureSession(child);
                return GeckoResult.fromValue(child.session);
            }
        });

        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession s, String url) {
                tab.lastPageStartMs = System.currentTimeMillis();
                if (url != null) tab.url = url;
                updateHud();
            }

            @Override public void onPageStop(GeckoSession s, boolean success) {
                updateHud();
                persistState();
            }

            @Override
            public void onSessionStateChange(GeckoSession s, GeckoSession.SessionState state) {
                tab.sessionState = state.toString();
                if (!tab.privateMode) persistState();
            }
        });

        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onTitleChange(GeckoSession s, String title) {
                if (title != null && !title.trim().isEmpty()) tab.title = title;
                updateHud();
            }

            @Override public void onFirstContentfulPaint(GeckoSession s) {
                tab.lastFcpMs = Math.max(0L, System.currentTimeMillis() - tab.lastPageStartMs);
                updateHud();
            }

            @Override public void onCrash(GeckoSession s) { recoverTab(tab); }
            @Override public void onKill(GeckoSession s) { recoverTab(tab); }

            @Override public void onFullScreen(GeckoSession s, boolean fullScreen) {
                setBrowserFullscreen(fullScreen);
            }
        });

        session.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override
            public GeckoResult<GeckoSession.PromptDelegate.PromptResponse> onFilePrompt(
                    GeckoSession s, GeckoSession.PromptDelegate.FilePrompt prompt) {
                return launchFilePrompt(prompt);
            }
        });

        session.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override
            public void onAndroidPermissionsRequest(GeckoSession s, String[] permissions,
                    GeckoSession.PermissionDelegate.Callback callback) {
                requestAndroidPermissions(permissions, callback);
            }

            @Override
            public GeckoResult<Integer> onContentPermissionRequest(GeckoSession s,
                    GeckoSession.PermissionDelegate.ContentPermission permission) {
                GeckoResult<Integer> result = new GeckoResult<>();
                permission.notifyShown();
                String host;
                try { host = Uri.parse(permission.uri).getHost(); }
                catch (Throwable ignored) { host = permission.uri; }
                new AlertDialog.Builder(BrowserActivity.this)
                        .setTitle("Site permission")
                        .setMessage((host == null ? "This site" : host) + " is requesting browser permission.")
                        .setPositiveButton("Allow", (d, w) -> result.complete(
                                GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW))
                        .setNegativeButton("Deny", (d, w) -> result.complete(
                                GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY))
                        .setOnCancelListener(d -> result.complete(
                                GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY))
                        .show();
                return result;
            }

            @Override
            public void onMediaPermissionRequest(GeckoSession s, String uri,
                    GeckoSession.PermissionDelegate.MediaSource[] video,
                    GeckoSession.PermissionDelegate.MediaSource[] audio,
                    GeckoSession.PermissionDelegate.MediaCallback callback) {
                String host;
                try { host = Uri.parse(uri).getHost(); }
                catch (Throwable ignored) { host = uri; }
                new AlertDialog.Builder(BrowserActivity.this)
                        .setTitle("Camera / microphone")
                        .setMessage((host == null ? "This site" : host) + " wants to use media devices.")
                        .setPositiveButton("Allow", (d, w) -> callback.grant(
                                video != null && video.length > 0 ? video[0] : null,
                                audio != null && audio.length > 0 ? audio[0] : null))
                        .setNegativeButton("Deny", (d, w) -> callback.reject())
                        .setOnCancelListener(d -> callback.reject())
                        .show();
            }
        });
    }

    private JSONObject onExtensionMessage(BrowserTab tab, GeckoSession origin, JSONObject message) {
        if (tab.session != origin) return new JSONObject();
        String type = message.optString("type", "");
        if ("telemetry".equals(type)) {
            tab.generating = message.optBoolean("generating", false);
            tab.shielded = message.optBoolean("shielded", false);
            tab.pain = message.optInt("pain", 0);
            tab.turnCount = message.optInt("turns", 0);
            if (tab == currentTab()) updateHud();
        }

        PerformanceGuardian.Snapshot snap = guardian == null ? null : guardian.latest();
        int pressure = snap == null ? PerformanceGuardian.PRESSURE_NORMAL : snap.pressure;
        int coldKeep = pressure >= PerformanceGuardian.PRESSURE_CRITICAL ? 1
                : pressure >= PerformanceGuardian.PRESSURE_HIGH ? 2
                : pressure >= PerformanceGuardian.PRESSURE_WATCH ? 4 : 6;
        int shieldThreshold = pressure >= PerformanceGuardian.PRESSURE_CRITICAL ? 2
                : pressure >= PerformanceGuardian.PRESSURE_HIGH ? 3
                : pressure >= PerformanceGuardian.PRESSURE_WATCH ? 4 : 5;
        JSONObject out = new JSONObject();
        try {
            out.put("pressure", pressure);
            out.put("thermal", snap == null ? 0 : snap.thermalStatus);
            out.put("availablePercent", snap == null ? -1 : snap.availablePercent());
            out.put("coldKeep", coldKeep);
            out.put("shieldThreshold", shieldThreshold);
            out.put("aggressive", pressure >= PerformanceGuardian.PRESSURE_HIGH);
        } catch (Exception ignored) {}
        return out;
    }

    private void switchTo(int index) {
        if (index < 0 || index >= tabs.size()) return;
        BrowserTab old = currentTab();
        if (old != null && old.session != null) {
            old.session.setFocused(false);
            old.session.setActive(false);
            old.session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT);
        }
        if (geckoView.getSession() != null) geckoView.releaseSession();

        currentIndex = index;
        BrowserTab tab = tabs.get(index);
        ensureSession(tab);
        tab.lastAccessMs = System.currentTimeMillis();
        tab.session.setActive(true);
        tab.session.setFocused(true);
        tab.session.setPriorityHint(GeckoSession.PRIORITY_HIGH);
        geckoView.setSession(tab.session);
        address.setText(tab.url == null ? HOME : tab.url);
        updateHud();
        persistState();
    }

    private BrowserTab createTab(boolean privateMode, String url) {
        BrowserTab tab = new BrowserTab(UUID.randomUUID().toString(), privateMode, false,
                privateMode ? "Private tab" : "New tab", url == null ? HOME : url);
        tabs.add(tab);
        updateHud();
        return tab;
    }

    private void newTab(boolean privateMode, String url) {
        BrowserTab tab = createTab(privateMode, url);
        switchTo(tabs.indexOf(tab));
    }

    private void closeCurrentTab() {
        if (tabs.isEmpty()) return;
        BrowserTab tab = currentTab();
        if (geckoView.getSession() != null) geckoView.releaseSession();
        if (tab != null && tab.session != null) {
            tab.session.close();
            tab.session = null;
        }
        tabs.remove(currentIndex);
        if (tabs.isEmpty()) tabs.add(new BrowserTab(UUID.randomUUID().toString(), false, false, "ChatGPT", HOME));
        currentIndex = Math.min(currentIndex, tabs.size() - 1);
        switchTo(currentIndex);
    }

    private void showTabs() {
        String[] labels = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) labels[i] = (i == currentIndex ? "● " : "") + tabs.get(i).label();
        new AlertDialog.Builder(this)
                .setTitle("Tabs · " + tabs.size())
                .setItems(labels, (d, which) -> switchTo(which))
                .setPositiveButton("New", (d, w) -> newTab(false, HOME))
                .setNeutralButton("Private", (d, w) -> newTab(true, HOME))
                .setNegativeButton("Close current", (d, w) -> closeCurrentTab())
                .show();
    }

    private void showMenu() {
        BrowserTab tab = currentTab();
        boolean desktop = tab != null && tab.desktopMode;
        String[] items = {
                "New tab",
                "New private tab",
                "Tabs",
                desktop ? "Use mobile site" : "Use desktop site",
                "Find in page",
                "Share page",
                "Copy URL",
                "Hibernate background tabs",
                "Performance details"
        };
        new AlertDialog.Builder(this)
                .setTitle("EdgeFlow Hyperion")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: newTab(false, HOME); break;
                        case 1: newTab(true, HOME); break;
                        case 2: showTabs(); break;
                        case 3: toggleDesktopMode(); break;
                        case 4: findInPage(); break;
                        case 5: shareCurrent(); break;
                        case 6: copyCurrentUrl(); break;
                        case 7: hibernateBackgroundTabs(); break;
                        case 8: showPerformanceDetails(); break;
                    }
                }).show();
    }

    private void toggleDesktopMode() {
        BrowserTab tab = currentTab();
        if (tab == null || tab.session == null) return;
        tab.desktopMode = !tab.desktopMode;
        tab.session.getSettings().setUserAgentMode(tab.desktopMode
                ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                : GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
        tab.session.getSettings().setViewportMode(tab.desktopMode
                ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                : GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        tab.session.reload();
        persistState();
    }

    private void findInPage() {
        BrowserTab tab = currentTab();
        if (tab == null || tab.session == null) return;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Find in page")
                .setView(input)
                .setPositiveButton("Find", (d, w) -> {
                    String q = input.getText().toString();
                    if (q.isEmpty()) return;
                    tab.session.getFinder().setDisplayFlags(GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL);
                    tab.session.getFinder().find(q, GeckoSession.FINDER_FIND_FORWARD).accept(result -> {
                        if (result != null) toast(result.found
                                ? "Match " + result.current + (result.total >= 0 ? " of " + result.total : "")
                                : "No match");
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareCurrent() {
        BrowserTab tab = currentTab();
        if (tab == null || tab.url == null) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, tab.url);
        startActivity(Intent.createChooser(share, "Share page"));
    }

    private void copyCurrentUrl() {
        BrowserTab tab = currentTab();
        if (tab == null || tab.url == null) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("URL", tab.url));
        toast("URL copied");
    }

    private void hibernateBackgroundTabs() {
        for (int i = 0; i < tabs.size(); i++) {
            if (i == currentIndex) continue;
            requestHibernate(tabs.get(i));
        }
    }

    private void requestHibernate(BrowserTab tab) {
        if (tab.session == null || tab.hibernated) return;
        GeckoSession session = tab.session;
        session.setFocused(false);
        session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT);
        session.setActive(false);
        session.flushSessionState();
        session.containsFormData().accept(hasData -> {
            if (Boolean.TRUE.equals(hasData)) return;
            main.postDelayed(() -> {
                if (tab.session != session || tab == currentTab()) return;
                if (tab.sessionState == null || tab.sessionState.isEmpty()) return;
                session.close();
                tab.session = null;
                tab.hibernated = true;
                updateHud();
                persistState();
            }, 250L);
        }, error -> {});
    }

    private void recoverTab(BrowserTab tab) {
        if (tab.session != null && geckoView.getSession() == tab.session) geckoView.releaseSession();
        tab.session = null;
        tab.hibernated = true;
        if (tab == currentTab()) {
            main.postDelayed(() -> {
                ensureSession(tab);
                tab.session.setActive(true);
                tab.session.setFocused(true);
                tab.session.setPriorityHint(GeckoSession.PRIORITY_HIGH);
                geckoView.setSession(tab.session);
                toast("Tab recovered after content-process restart");
            }, 150L);
        }
    }

    @Override
    public void onPressureChanged(PerformanceGuardian.Snapshot snapshot) {
        updateHud();
        if (snapshot.pressure >= PerformanceGuardian.PRESSURE_HIGH) hibernateBackgroundTabs();
    }

    @Override
    public void onTrimMemory(int level) {
        if (guardian != null) guardian.onTrimMemory(level);
        if (level >= 20) hibernateBackgroundTabs();
        super.onTrimMemory(level);
    }

    private GeckoResult<GeckoSession.PromptDelegate.PromptResponse> launchFilePrompt(
            GeckoSession.PromptDelegate.FilePrompt prompt) {
        if (pendingFileResult != null) pendingFileResult.complete(pendingFilePrompt.dismiss());
        pendingFilePrompt = prompt;
        pendingFileResult = new GeckoResult<>();

        Intent intent = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.FOLDER
                ? new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                : new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
        if (prompt.type != GeckoSession.PromptDelegate.FilePrompt.Type.FOLDER) {
            String[] mime = prompt.mimeTypes;
            intent.setType(mime != null && mime.length == 1 ? mime[0] : "*/*");
            if (mime != null && mime.length > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, mime);
            if (prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
        }
        try { startActivityForResult(intent, REQ_FILE); }
        catch (Throwable error) {
            pendingFileResult.complete(prompt.dismiss());
            clearFilePrompt();
        }
        return pendingFileResult;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE || pendingFilePrompt == null || pendingFileResult == null) return;
        try {
            if (resultCode != RESULT_OK || data == null) {
                pendingFileResult.complete(pendingFilePrompt.dismiss());
            } else if (data.getClipData() != null) {
                ClipData clip = data.getClipData();
                Uri[] uris = new Uri[clip.getItemCount()];
                for (int i = 0; i < clip.getItemCount(); i++) uris[i] = clip.getItemAt(i).getUri();
                pendingFileResult.complete(pendingFilePrompt.confirm(getApplicationContext(), uris));
            } else if (data.getData() != null) {
                pendingFileResult.complete(pendingFilePrompt.confirm(getApplicationContext(), data.getData()));
            } else {
                pendingFileResult.complete(pendingFilePrompt.dismiss());
            }
        } catch (Throwable error) {
            try { pendingFileResult.complete(pendingFilePrompt.dismiss()); } catch (Throwable ignored) {}
        } finally {
            clearFilePrompt();
        }
    }

    private void clearFilePrompt() {
        pendingFilePrompt = null;
        pendingFileResult = null;
    }

    private void requestAndroidPermissions(String[] permissions,
            GeckoSession.PermissionDelegate.Callback callback) {
        if (permissions == null || permissions.length == 0) {
            callback.grant();
            return;
        }
        ArrayList<String> missing = new ArrayList<>();
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
        }
        if (missing.isEmpty()) {
            callback.grant();
            return;
        }
        if (pendingPermissionCallback != null) pendingPermissionCallback.reject();
        pendingPermissionCallback = callback;
        requestPermissions(missing.toArray(new String[0]), REQ_ANDROID_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_ANDROID_PERMISSIONS || pendingPermissionCallback == null) return;
        boolean granted = grantResults.length > 0;
        for (int value : grantResults) granted &= value == PackageManager.PERMISSION_GRANTED;
        if (granted) pendingPermissionCallback.grant();
        else pendingPermissionCallback.reject();
        pendingPermissionCallback = null;
    }

    private void navigate(String raw) {
        BrowserTab tab = currentTab();
        if (tab == null || tab.session == null) return;
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return;
        String target;
        if (value.startsWith("http://") || value.startsWith("https://")) target = value;
        else if (value.contains(" ") || !value.contains(".")) {
            target = "https://www.google.com/search?q=" + Uri.encode(value);
        } else target = "https://" + value;
        tab.url = target;
        address.setText(target);
        tab.session.loadUri(target);
    }

    private void goBack() {
        BrowserTab tab = currentTab();
        if (tab == null || tab.session == null) return;
        tab.session.processBackPressed().accept(consumed -> {
            if (!Boolean.TRUE.equals(consumed) && tab.canGoBack) tab.session.goBack();
        });
    }

    private void setBrowserFullscreen(boolean fullScreen) {
        toolbar.setVisibility(fullScreen ? View.GONE : View.VISIBLE);
        status.setVisibility(fullScreen ? View.GONE : View.VISIBLE);
        Window window = getWindow();
        if (fullScreen) {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    private void showPerformanceDetails() {
        PerformanceGuardian.Snapshot snap = guardian == null ? null : guardian.latest();
        BrowserTab tab = currentTab();
        StringBuilder text = new StringBuilder();
        if (snap != null) {
            text.append("Available RAM: ").append(snap.availablePercent()).append("%\n")
                    .append("Pressure: ").append(pressureName(snap.pressure)).append("\n")
                    .append("Thermal: ").append(snap.thermalStatus).append("\n");
        }
        text.append("Tabs: ").append(tabs.size()).append("\n");
        int frozen = 0;
        for (BrowserTab t : tabs) if (t.hibernated) frozen++;
        text.append("Hibernated: ").append(frozen).append("\n");
        if (tab != null) {
            text.append("ChatGPT turns: ").append(tab.turnCount).append("\n")
                    .append("Render pressure: ").append(tab.pain).append("\n")
                    .append("Stream Shield: ").append(tab.shielded ? "active" : "idle").append("\n")
                    .append("Last first paint: ").append(tab.lastFcpMs).append(" ms");
        }
        new AlertDialog.Builder(this).setTitle("Performance HUD")
                .setMessage(text.toString()).setPositiveButton("OK", null).show();
    }

    private void updateHud() {
        if (status == null || tabsButton == null) return;
        tabsButton.setText(String.valueOf(tabs.size()));
        PerformanceGuardian.Snapshot snap = guardian == null ? null : guardian.latest();
        BrowserTab tab = currentTab();
        String ram = snap == null || snap.availablePercent() < 0 ? "RAM ?" : "RAM " + snap.availablePercent() + "%";
        String pressure = snap == null ? "" : " · " + pressureName(snap.pressure);
        String ai = tab != null && tab.shielded ? " · ⚡ Shield" : tab != null && tab.generating ? " · AI streaming" : "";
        String priv = tab != null && tab.privateMode ? " · Private" : "";
        status.setText("Hyperion · " + ram + pressure + ai + priv);
    }

    private String pressureName(int p) {
        if (p >= PerformanceGuardian.PRESSURE_CRITICAL) return "critical";
        if (p >= PerformanceGuardian.PRESSURE_HIGH) return "high";
        if (p >= PerformanceGuardian.PRESSURE_WATCH) return "watch";
        return "normal";
    }

    private void persistState() {
        if (!tabs.isEmpty()) SessionStore.save(this, tabs, Math.max(0, currentIndex));
    }

    private Button toolbarButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(20);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private LinearLayout.LayoutParams fixed(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    @Override
    protected void onDestroy() {
        persistState();
        if (guardian != null) guardian.stop();
        if (geckoView != null && geckoView.getSession() != null) geckoView.releaseSession();
        for (BrowserTab tab : tabs) {
            if (tab.session != null) {
                tab.session.setActive(false);
                tab.session.close();
                tab.session = null;
            }
        }
        super.onDestroy();
    }
}
