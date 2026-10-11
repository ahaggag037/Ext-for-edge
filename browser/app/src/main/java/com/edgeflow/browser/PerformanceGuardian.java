package com.edgeflow.browser;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

final class PerformanceGuardian {
    interface Listener {
        void onPressureChanged(Snapshot snapshot);
    }

    static final class Snapshot {
        final long availableBytes;
        final long totalBytes;
        final boolean systemLowMemory;
        final boolean lowRamDevice;
        final int thermalStatus;
        final boolean powerSaveMode;
        final boolean deviceIdleMode;
        final int pressure;
        final long sampledAtMs;

        Snapshot(long availableBytes, long totalBytes, boolean systemLowMemory,
                 boolean lowRamDevice, int thermalStatus, boolean powerSaveMode,
                 boolean deviceIdleMode, int pressure, long sampledAtMs) {
            this.availableBytes = availableBytes;
            this.totalBytes = totalBytes;
            this.systemLowMemory = systemLowMemory;
            this.lowRamDevice = lowRamDevice;
            this.thermalStatus = thermalStatus;
            this.powerSaveMode = powerSaveMode;
            this.deviceIdleMode = deviceIdleMode;
            this.pressure = pressure;
            this.sampledAtMs = sampledAtMs;
        }

        int availablePercent() {
            if (totalBytes <= 0) return -1;
            return (int) Math.max(0, Math.min(100, (availableBytes * 100L) / totalBytes));
        }

        long availableMiB() { return availableBytes / (1024L * 1024L); }
        long totalMiB() { return totalBytes / (1024L * 1024L); }
    }

    static final int PRESSURE_NORMAL = 0;
    static final int PRESSURE_WATCH = 1;
    static final int PRESSURE_HIGH = 2;
    static final int PRESSURE_CRITICAL = 3;

    private final ActivityManager activityManager;
    private final PowerManager powerManager;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean lowRamDevice;
    private Snapshot latest;
    private int trimFloor;

    private final Runnable sampler = new Runnable() {
        @Override public void run() {
            sampleNow();
            long next = latest != null && latest.pressure >= PRESSURE_HIGH ? 1800L : 3000L;
            handler.postDelayed(this, next);
        }
    };

    PerformanceGuardian(Context context, Listener listener) {
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        this.listener = listener;
        this.lowRamDevice = activityManager != null && activityManager.isLowRamDevice();
    }

    void start() {
        handler.removeCallbacks(sampler);
        sampler.run();
    }

    void stop() {
        handler.removeCallbacks(sampler);
    }

    Snapshot latest() {
        return latest;
    }

    void onTrimMemory(int level) {
        if (level >= 80) trimFloor = PRESSURE_CRITICAL;
        else if (level >= 40) trimFloor = PRESSURE_HIGH;
        else if (level >= 10) trimFloor = PRESSURE_WATCH;
        else trimFloor = PRESSURE_NORMAL;
        sampleNow();
        if (level < 10) trimFloor = PRESSURE_NORMAL;
    }

    private void sampleNow() {
        if (activityManager == null) return;
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(mem);
        int thermal = PowerManager.THERMAL_STATUS_NONE;
        boolean powerSave = false;
        boolean deviceIdle = false;
        if (powerManager != null) {
            powerSave = powerManager.isPowerSaveMode();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) deviceIdle = powerManager.isDeviceIdleMode();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) thermal = powerManager.getCurrentThermalStatus();
        }

        int pressure = classify(mem, thermal, powerSave, lowRamDevice);
        pressure = Math.max(pressure, trimFloor);
        Snapshot next = new Snapshot(mem.availMem, mem.totalMem, mem.lowMemory,
                lowRamDevice, thermal, powerSave, deviceIdle, pressure, System.currentTimeMillis());
        boolean changed = latest == null
                || latest.pressure != next.pressure
                || latest.thermalStatus != next.thermalStatus
                || latest.powerSaveMode != next.powerSaveMode
                || latest.deviceIdleMode != next.deviceIdleMode
                || Math.abs(latest.availablePercent() - next.availablePercent()) >= 5;
        latest = next;
        if (changed && listener != null) listener.onPressureChanged(next);
    }

    private int classify(ActivityManager.MemoryInfo mem, int thermal, boolean powerSave, boolean lowRam) {
        int pct = mem.totalMem > 0 ? (int) ((mem.availMem * 100L) / mem.totalMem) : 100;
        long availMiB = mem.availMem / (1024L * 1024L);
        long totalMiB = mem.totalMem / (1024L * 1024L);
        int p = PRESSURE_NORMAL;

        // Percentage is useful across device classes, but absolute headroom prevents
        // a low-memory phone from looking deceptively healthy at the same percentage.
        int watchPct = lowRam || totalMiB <= 4096 ? 30 : 25;
        int highPct = lowRam || totalMiB <= 4096 ? 20 : 16;
        int criticalPct = lowRam || totalMiB <= 4096 ? 13 : 10;
        if (pct <= watchPct || availMiB <= 900) p = PRESSURE_WATCH;
        if (pct <= highPct || availMiB <= 600) p = PRESSURE_HIGH;
        if (pct <= criticalPct || availMiB <= 360 || mem.lowMemory) p = PRESSURE_CRITICAL;

        if (powerSave) p = Math.max(p, PRESSURE_WATCH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (thermal >= PowerManager.THERMAL_STATUS_MODERATE) p = Math.max(p, PRESSURE_WATCH);
            if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) p = Math.max(p, PRESSURE_HIGH);
            if (thermal >= PowerManager.THERMAL_STATUS_CRITICAL) p = PRESSURE_CRITICAL;
        }
        return p;
    }
}
