package com.edgeflow.installer;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class EdgeInstallerAccessibilityService extends AccessibilityService {
    private static final Pattern VERSION = Pattern.compile(".*\\b\\d{2,3}\\.\\d+\\.\\d+\\.\\d+\\b.*");
    private static final Set<String> FILE_PICKERS = new HashSet<>(Arrays.asList(
            "com.android.documentsui",
            "com.huawei.hidisk",
            "com.huawei.filemanager",
            "com.android.fileexplorer"
    ));

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastActionAt = 0L;
    private boolean versionTapSequenceRunning = false;
    private boolean sessionToastShown = false;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!InstallSession.active(this)) return;

        CharSequence pkgCs = event.getPackageName();
        String pkg = pkgCs == null ? "" : pkgCs.toString();
        String target = InstallSession.target(this);
        if (!pkg.equals(target) && !FILE_PICKERS.contains(pkg)) return;

        if (!sessionToastShown) {
            sessionToastShown = true;
            Toast.makeText(this,
                    "EdgeFlow Install Assistant is active for this install session only.",
                    Toast.LENGTH_SHORT).show();
        }

        if (System.currentTimeMillis() - lastActionAt < 220) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            if (FILE_PICKERS.contains(pkg)) {
                handleFilePicker(root);
            } else {
                handleEdge(root);
            }
        } finally {
            root.recycle();
        }
    }

    private void handleEdge(AccessibilityNodeInfo root) {
        String state = InstallSession.state(this);

        // Never click Edge's final security confirmation. Stop automation and leave
        // the final consent action visible to the user.
        if (state.equals("FILE_SELECTED") || state.equals("WAITING_FOR_EDGE_CONFIRMATION")) {
            if (hasAny(root,
                    "Add extension", "Confirm extension", "Install extension",
                    "إضافة الإضافة", "تأكيد الإضافة", "تثبيت الإضافة")) {
                InstallSession.finish(this, "AWAITING_USER_CONFIRMATION");
                Toast.makeText(this,
                        "EdgeFlow is ready. Please review Edge's prompt and tap the final confirmation yourself.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        switch (state) {
            case "STARTED":
                if (clickAny(root, "Menu", "More", "More options", "القائمة", "المزيد", "المزيد من الخيارات")) {
                    InstallSession.mark(this, "MENU_OPENED");
                }
                break;

            case "MENU_OPENED":
                if (clickAny(root, "Settings", "الإعدادات")) {
                    InstallSession.mark(this, "OPENED_SETTINGS");
                }
                break;

            case "OPENED_SETTINGS":
                if (clickAny(root, "About Microsoft Edge", "About Edge", "حول Microsoft Edge", "حول Edge")) {
                    InstallSession.mark(this, "OPENED_ABOUT");
                } else {
                    scrollForward(root);
                }
                break;

            case "OPENED_ABOUT":
                if (!versionTapSequenceRunning && findVersionNode(root) != null) {
                    versionTapSequenceRunning = true;
                    InstallSession.mark(this, "ENABLING_DEVELOPER_OPTIONS");
                    scheduleFreshVersionTaps();
                } else if (!versionTapSequenceRunning) {
                    scrollForward(root);
                }
                break;

            case "ENABLING_DEVELOPER_OPTIONS":
                // Delayed taps are handled by scheduleFreshVersionTaps().
                break;

            case "DEVELOPER_ENABLED":
                if (clickAny(root, "Developer options", "Developer Options", "خيارات المطور", "خيارات المطورين")) {
                    InstallSession.mark(this, "OPENED_DEVELOPER_OPTIONS");
                } else {
                    scrollForward(root);
                }
                break;

            case "OPENED_DEVELOPER_OPTIONS":
                if (clickAny(root,
                        "Extension install by crx", "Extension Install by CRX", "Install extension by crx",
                        "تثبيت الإضافة عبر crx", "تثبيت ملحق عبر crx")) {
                    InstallSession.mark(this, "OPENED_CRX_INSTALLER");
                } else {
                    scrollForward(root);
                }
                break;

            case "OPENED_CRX_INSTALLER":
                if (clickAny(root,
                        "Choose .crx file", "Choose crx file", "Choose CRX file",
                        "اختيار ملف .crx", "اختيار ملف CRX")) {
                    InstallSession.mark(this, "WAITING_FOR_FILE");
                }
                break;

            default:
                break;
        }
    }

    private void handleFilePicker(AccessibilityNodeInfo root) {
        String state = InstallSession.state(this);
        if (!state.equals("WAITING_FOR_FILE")) return;

        if (clickAny(root, CrxStore.FILE_NAME, "edgeflow-mobile-performance-v3.1.0.crx")) {
            InstallSession.mark(this, "FILE_SELECTED");
            return;
        }
        if (clickAny(root, "EdgeFlow")) return;
        if (!clickAny(root, "Downloads", "Download", "التنزيلات", "تنزيلات")) {
            scrollForward(root);
        }
    }

    private void scheduleFreshVersionTaps() {
        for (int i = 0; i < 7; i++) {
            handler.postDelayed(this::tapVersionOnceUsingFreshWindow, i * 180L);
        }
        handler.postDelayed(() -> {
            if (!InstallSession.active(this)) return;
            try {
                performGlobalAction(GLOBAL_ACTION_BACK);
            } catch (Exception ignored) {
            }
            versionTapSequenceRunning = false;
            InstallSession.mark(this, "DEVELOPER_ENABLED");
            lastActionAt = System.currentTimeMillis();
        }, 1600L);
    }

    private void tapVersionOnceUsingFreshWindow() {
        if (!InstallSession.active(this)) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            AccessibilityNodeInfo version = findVersionNode(root);
            if (version == null) return;
            AccessibilityNodeInfo clickTarget = clickableAncestor(version);
            if (clickTarget != null) {
                clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                lastActionAt = System.currentTimeMillis();
            }
        } finally {
            root.recycle();
        }
    }

    private boolean hasAny(AccessibilityNodeInfo root, String... labels) {
        for (String label : labels) {
            if (findByLabel(root, label) != null) return true;
        }
        return false;
    }

    private boolean clickAny(AccessibilityNodeInfo root, String... labels) {
        for (String label : labels) {
            AccessibilityNodeInfo node = findByLabel(root, label);
            if (node != null) {
                AccessibilityNodeInfo clickTarget = clickableAncestor(node);
                if (clickTarget != null && clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    lastActionAt = System.currentTimeMillis();
                    return true;
                }
            }
        }
        return false;
    }

    private boolean scrollForward(AccessibilityNodeInfo root) {
        if (System.currentTimeMillis() - lastActionAt < 500) return false;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            if (n.isScrollable() && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                lastActionAt = System.currentTimeMillis();
                return true;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.addLast(child);
            }
        }
        return false;
    }

    private AccessibilityNodeInfo findByLabel(AccessibilityNodeInfo root, String wanted) {
        String needle = normalize(wanted);
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            CharSequence text = n.getText();
            CharSequence desc = n.getContentDescription();
            String t = normalize(text == null ? "" : text.toString());
            String d = normalize(desc == null ? "" : desc.toString());
            if ((!t.isEmpty() && (t.equals(needle) || t.contains(needle))) ||
                    (!d.isEmpty() && (d.equals(needle) || d.contains(needle)))) {
                return n;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.addLast(child);
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findVersionNode(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            CharSequence text = n.getText();
            if (text != null && VERSION.matcher(text.toString()).matches()) return n;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.addLast(child);
            }
        }
        return null;
    }

    private AccessibilityNodeInfo clickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cur = node;
        for (int i = 0; i < 6 && cur != null; i++) {
            if (cur.isClickable()) return cur;
            cur = cur.getParent();
        }
        return node;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    @Override public void onInterrupt() {
    }
}
