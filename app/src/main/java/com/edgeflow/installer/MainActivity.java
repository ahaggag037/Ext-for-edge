package com.edgeflow.installer;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.accessibility.AccessibilityManager;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_STORAGE = 41;
    private TextView status;
    private TextView detail;
    private EdgeTargets.InstalledEdge selectedEdge;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
        if (InstallSession.active(this) && isAccessibilityEnabled() && selectedEdge != null) {
            launchEdge(selectedEdge.packageName);
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(24), dp(20), dp(32));
        box.setBackgroundColor(Color.rgb(14, 17, 22));
        scroll.addView(box);

        TextView title = text("EdgeFlow Installer", 28, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(title);
        TextView subtitle = text("One-tap assisted installation for EdgeFlow on Microsoft Edge Android", 15, Color.rgb(185, 195, 210));
        subtitle.setPadding(0, dp(6), 0, dp(18));
        box.addView(subtitle);

        status = text("Checking…", 17, Color.WHITE);
        status.setPadding(dp(14), dp(14), dp(14), dp(14));
        status.setBackgroundColor(Color.rgb(25, 31, 40));
        box.addView(status, matchWrap());

        detail = text("", 14, Color.rgb(190, 200, 214));
        detail.setPadding(0, dp(14), 0, dp(12));
        box.addView(detail);

        Button install = button("Install EdgeFlow automatically");
        install.setOnClickListener(v -> startInstall());
        box.addView(install, matchWrap());

        Button assistant = button("Enable / check Install Assistant");
        assistant.setOnClickListener(v -> openAccessibilitySettings());
        box.addView(assistant, matchWrap());

        Button copy = button("Copy verified CRX to Downloads");
        copy.setOnClickListener(v -> exportOnly());
        box.addView(copy, matchWrap());

        Button open = button("Open best Edge channel");
        open.setOnClickListener(v -> {
            if (selectedEdge == null) Toast.makeText(this, "No supported Edge channel detected.", Toast.LENGTH_LONG).show();
            else launchEdge(selectedEdge.packageName);
        });
        box.addView(open, matchWrap());

        Button managed = button("Managed-device policy status");
        managed.setOnClickListener(v -> showManagedStatus());
        box.addView(managed, matchWrap());

        TextView note = text(
                "How it works:\n\n" +
                "• The app verifies the bundled EdgeFlow CRX by SHA-256 before exporting it.\n" +
                "• On a normal personal phone, Android/Edge still require a one-time Accessibility permission and Edge's own install confirmation.\n" +
                "• The assistant only acts during an explicit install session, only in Edge/file-picker windows, and expires automatically after 8 minutes.\n" +
                "• Managed devices can use Edge policy deployment; that route requires a real MDM/device-owner configuration and a hosted update endpoint.\n\n" +
                "Extension ID: oeehabgdmlomabhpcbbdfjlehpbnfljp\nVersion: 3.1.0",
                13, Color.rgb(160, 171, 187));
        note.setPadding(0, dp(22), 0, 0);
        box.addView(note);
        return scroll;
    }

    private void refresh() {
        List<EdgeTargets.InstalledEdge> edges = EdgeTargets.installed(this);
        selectedEdge = edges.isEmpty() ? null : edges.get(0);
        StringBuilder sb = new StringBuilder();
        if (edges.isEmpty()) {
            status.setText("No Microsoft Edge channel detected");
            status.setTextColor(Color.rgb(255, 170, 80));
            sb.append("Install Edge Canary or Beta for the most reliable private-CRX route.");
        } else {
            EdgeTargets.InstalledEdge e = selectedEdge;
            boolean supportedPolicy = EdgeTargets.majorAtLeast(e.versionName, 149);
            status.setText("Ready: " + e.label + " " + e.versionName);
            status.setTextColor(Color.rgb(125, 230, 170));
            sb.append("Preferred target: ").append(e.packageName).append('\n');
            sb.append("Edge 149+ policy capability: ").append(supportedPolicy ? "available by version" : "not available by version").append('\n');
            sb.append("Install Assistant: ").append(isAccessibilityEnabled() ? "enabled" : "not enabled").append('\n');
            sb.append("Session: ").append(InstallSession.state(this));
        }
        detail.setText(sb.toString());
    }

    private void startInstall() {
        if (selectedEdge == null) {
            Toast.makeText(this, "Install Edge Canary or Beta first, then return here.", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        try {
            CrxStore.Result result = CrxStore.export(this);
            InstallSession.start(this, selectedEdge.packageName, result.uri.toString());
            Toast.makeText(this, "Verified CRX prepared in Downloads/EdgeFlow.", Toast.LENGTH_LONG).show();
            if (!isAccessibilityEnabled()) {
                openAccessibilitySettings();
            } else {
                launchEdge(selectedEdge.packageName);
            }
            refresh();
        } catch (Exception e) {
            Toast.makeText(this, "Could not prepare EdgeFlow: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void exportOnly() {
        try {
            CrxStore.Result result = CrxStore.export(this);
            Toast.makeText(this, "CRX exported and verified: " + result.sha256.substring(0, 12) + "…", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openAccessibilitySettings() {
        Toast.makeText(this, "Enable 'EdgeFlow Install Assistant', then return. It only acts during the current install session.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void launchEdge(String packageName) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            Toast.makeText(this, "Could not launch " + packageName, Toast.LENGTH_LONG).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        String expected = getPackageName() + "/" + EdgeInstallerAccessibilityService.class.getName();
        for (AccessibilityServiceInfo info : enabled) {
            if (info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) continue;
            String id = info.getResolveInfo().serviceInfo.packageName + "/" + info.getResolveInfo().serviceInfo.name;
            if (id.equals(expected) || id.endsWith("/" + EdgeInstallerAccessibilityService.class.getName())) return true;
        }
        String raw = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return raw != null && raw.contains(EdgeInstallerAccessibilityService.class.getName());
    }

    private void showManagedStatus() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        boolean owner = dpm != null && (dpm.isDeviceOwnerApp(getPackageName()) || dpm.isProfileOwnerApp(getPackageName()));
        String msg = owner
                ? "This app is a Device/Profile Owner. The silent Edge policy route can be activated once a valid EdgeFlow update endpoint is configured."
                : "This is a normal personal-device install. Android does not allow an ordinary APK to write Edge managed policies silently. The assisted CRX route is the correct path here.";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startInstall();
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setMinHeight(dp(52));
        b.setOnFocusChangeListener((v, hasFocus) -> {});
        LinearLayout.LayoutParams p = matchWrap();
        p.topMargin = dp(10);
        b.setLayoutParams(p);
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
