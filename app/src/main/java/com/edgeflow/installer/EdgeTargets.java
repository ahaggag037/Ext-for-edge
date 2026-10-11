package com.edgeflow.installer;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class EdgeTargets {
    static final String STABLE = "com.microsoft.emmx";
    static final String BETA = "com.microsoft.emmx.beta";
    static final String DEV = "com.microsoft.emmx.dev";
    static final String CANARY = "com.microsoft.emmx.canary";

    static final List<String> PREFERENCE = Arrays.asList(CANARY, BETA, DEV, STABLE);

    static final class InstalledEdge {
        final String packageName;
        final String label;
        final String versionName;
        final long versionCode;

        InstalledEdge(String packageName, String label, String versionName, long versionCode) {
            this.packageName = packageName;
            this.label = label;
            this.versionName = versionName;
            this.versionCode = versionCode;
        }
    }

    static List<InstalledEdge> installed(Context context) {
        PackageManager pm = context.getPackageManager();
        List<InstalledEdge> out = new ArrayList<>();
        for (String pkg : PREFERENCE) {
            try {
                PackageInfo info = pm.getPackageInfo(pkg, 0);
                CharSequence appLabel = info.applicationInfo == null ? pkg : pm.getApplicationLabel(info.applicationInfo);
                long code = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
                out.add(new InstalledEdge(pkg, String.valueOf(appLabel), info.versionName == null ? "?" : info.versionName, code));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return out;
    }

    static InstalledEdge best(Context context) {
        List<InstalledEdge> all = installed(context);
        return all.isEmpty() ? null : all.get(0);
    }

    static boolean majorAtLeast(String version, int required) {
        try {
            String first = version == null ? "" : version.split("\\.")[0];
            return Integer.parseInt(first) >= required;
        } catch (Exception ignored) {
            return false;
        }
    }

    private EdgeTargets() {}
}
