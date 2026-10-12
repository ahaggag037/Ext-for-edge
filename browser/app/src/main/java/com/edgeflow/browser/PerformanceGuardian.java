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
        final int thermalStatus;
        final boolean powerSave;
        final boolean deviceIdle;
        final int pressure;
        final long sampledAtMs;

        Snapshot(long availableBytes, long totalBytes, boolean systemLowMemory,
                 int thermalStatus, boolean powerSave, boolean deviceIdle,
                 int pressure, long sampledAtMs) {
            this.availableBytes = availableBytes;
            this.totalBytes = totalBytes;
            this.systemLowMemory = systemLowMemory;
            this.thermalStatus = thermalStatus;
            this.powerSave = powerSave;
            this.deviceIdle = deviceIdle;
            this.pressure = pressure;
            this.sampledAtMs = sampledAtMs;
        }

        int availablePercent() {
            if (totalBytes <= 0) return -1;
            return (int) Math.max(0, Math.min(100, (availableBytes * 100L) / totalBytes));
        }
    }

    static final int PRESSURE_NORMAL = 0;
    static final int PRESSURE_WATCH = 1;
    static final int PRESSURE_HIGH = 2;
    static final int PRESSURE_CRITICAL = 3;

    private final ActivityManager activityManager;
    private final PowerManager powerManager;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Snapshot latest;
    private int trimFloor;

    private final Runnable sampler = new Runnable() {
        @Override public void run() {
            sampleNow();
            long delay = latest != null && latest.pressure >= PRESSURE_HIGH ? 1800L : 3500L;
            handler.postDelayed(this, delay);
        }
    };

    PerformanceGuardian(Context context, Listener listener) {
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        this.listener = listener;
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
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(mem);
        int thermal = PowerManager.THERMAL_STATUS_NONE;
        boolean powerSave = false;
        boolean deviceIdle = false;
        if (powerManager != null) {
            powerSave = powerManager.isPowerSaveMode();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                deviceIdle = powerManager.isDeviceIdleMode();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                thermal = powerManager.getCurrentThermalStatus();
            }
        }

        int pressure = classify(mem, thermal, powerSave, deviceIdle);
        pressure = Math.max(pressure, trimFloor);
        Snapshot next = new Snapshot(mem.availMem, mem.totalMem, mem.lowMemory,
                thermal, powerSave, deviceIdle, pressure, System.currentTimeMillis());
        boolean changed = latest == null
                || latest.pressure != next.pressure
                || latest.thermalStatus != next.thermalStatus
                || latest.powerSave != next.powerSave
                || latest.deviceIdle != next.deviceIdle
                || Math.abs(latest.availablePercent() - next.availablePercent()) >= 5;
        latest = next;
        if (changed && listener != null) listener.onPressureChanged(next);
    }

    private int classify(ActivityManager.MemoryInfo mem, int thermal,
                         boolean powerSave, boolean deviceIdle) {
        int pct = mem.totalMem > 0 ? (int) ((mem.availMem * 100L) / mem.totalMem) : 100;
        int p = PRESSURE_NORMAL;
        if (pct <= 25) p = PRESSURE_WATCH;
        if (pct <= 16) p = PRESSURE_HIGH;
        if (pct <= 10 || mem.lowMemory) p = PRESSURE_CRITICAL;

        if (powerSave) p = Math.max(p, PRESSURE_WATCH);
        if (deviceIdle) p = Math.max(p, PRESSURE_HIGH);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (thermal >= PowerManager.THERMAL_STATUS_MODERATE) p = Math.max(p, PRESSURE_WATCH);
            if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) p = Math.max(p, PRESSURE_HIGH);
            if (thermal >= PowerManager.THERMAL_STATUS_CRITICAL) p = PRESSURE_CRITICAL;
        }
        return p;
    }
}
