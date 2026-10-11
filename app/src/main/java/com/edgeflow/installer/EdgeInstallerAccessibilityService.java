package com.edgeflow.installer;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
    private boolean tappedVersion = false;
    private boolean sessionToastShown = false;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!InstallSession.active(this)) return;
        CharSequence pkgCs = event.getPackageName();
        String pkg = pkgCs == null ? "" : pkgCs.toString();
        String target = InstallSession.target(this);
        if (!pkg.equals(target) && !FILE_PICKERS.contains(pkg)) return;
        if (!sessionToastShown) {
            sessionToastShown = true;
            Toast.makeText(this, "EdgeFlow installer assistant is active for this install session only.", Toast.LENGTH_SHORT).show();
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
        if (clickAny(root, "Add extension", "Add", "Install", "OK", "إضافة الإضافة", "إضافة", "تثبيت", "موافق")) {
            InstallSession.finish(this, "INSTALL_CONFIRMED");
            Toast.makeText(this, "EdgeFlow install confirmation sent.", Toast.LENGTH_LONG).show();
            return;
        }

        if (clickAny(root, "Choose .crx file", "Choose crx file", "Choose CRX file", "اختيار ملف .crx", "اختيار ملف CRX")) {
            InstallSession.mark(this, "WAITING_FOR_FILE");
            return;
        }

        if (clickAny(root, "Extension install by crx", "Extension Install by CRX", "Install extension by crx", "تثبيت الإضافة عبر crx", "تثبيت ملحق عبر crx")) {
            InstallSession.mark(this, "OPENED_CRX_INSTALLER");
            return;
        }

        if (clickAny(root, "Developer options", "Developer Options", "خيارات المطور", "خيارات المطورين")) {
            InstallSession.mark(this, "OPENED_DEVELOPER_OPTIONS");
            return;
        }

        if (!tappedVersion) {
            AccessibilityNodeInfo version = findVersionNode(root);
            if (version != null) {
                tappedVersion = true;
                InstallSession.mark(this, "ENABLING_DEVELOPER_OPTIONS");
                AccessibilityNodeInfo clickTarget = clickableAncestor(version);
                for (int i = 0; i < 7; i++) {
                    final int delay = i * 170;
                    handler.postDelayed(() -> clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK), delay);
                }
                handler.postDelayed(() -> {
                    try { performGlobalAction(GLOBAL_ACTION_BACK); } catch (Exception ignored) {}
                }, 1500);
                return;
            }
        }

        if (clickAny(root, "About Microsoft Edge", "About Edge", "حول Microsoft Edge", "حول Edge")) {
            InstallSession.mark(this, "OPENED_ABOUT");
            return;
        }

        if (clickAny(root, "Settings", "الإعدادات")) {
            InstallSession.mark(this, "OPENED_SETTINGS");
            return;
        }

        clickAny(root, "Menu", "More", "More options", "القائمة", "المزيد", "المزيد من الخيارات");
    }

    private void handleFilePicker(AccessibilityNodeInfo root) {
        if (clickAny(root, CrxStore.FILE_NAME, "edgeflow-v3.1.0.crx")) {
            InstallSession.mark(this, "FILE_SELECTED");
            return;
        }
        if (clickAny(root, "EdgeFlow")) return;
        clickAny(root, "Downloads", "Download", "التنزيلات", "تنزيلات");
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
            if ((!t.isEmpty() && (t.equals(needle) || t.contains(needle))) || (!d.isEmpty() && (d.equals(needle) || d.contains(needle)))) {
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
            if (text != null) {
                String s = text.toString();
                if (VERSION.matcher(s).matches()) return n;
            }
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
