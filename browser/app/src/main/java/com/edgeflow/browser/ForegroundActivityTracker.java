package com.edgeflow.browser;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import java.lang.ref.WeakReference;

final class ForegroundActivityTracker implements Application.ActivityLifecycleCallbacks {
    private static ForegroundActivityTracker instance;
    private WeakReference<Activity> current = new WeakReference<>(null);

    static synchronized void install(Application app) {
        if (instance != null || app == null) return;
        instance = new ForegroundActivityTracker();
        app.registerActivityLifecycleCallbacks(instance);
    }

    static Activity current() {
        ForegroundActivityTracker tracker = instance;
        return tracker == null ? null : tracker.current.get();
    }

    @Override public void onActivityResumed(Activity activity) {
        current = new WeakReference<>(activity);
    }

    @Override public void onActivityPaused(Activity activity) {
        Activity active = current.get();
        if (active == activity) current = new WeakReference<>(null);
    }

    @Override public void onActivityDestroyed(Activity activity) {
        Activity active = current.get();
        if (active == activity) current = new WeakReference<>(null);
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

    private ForegroundActivityTracker() {}
}
