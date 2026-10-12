package com.edgeflow.browser;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import org.mozilla.geckoview.GeckoView;

public final class EdgeFlowApplication extends Application implements Application.ActivityLifecycleCallbacks {
    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    private void enableAutofill(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        View root = activity.getWindow().getDecorView();
        GeckoView view = findGeckoView(root);
        if (view != null && !view.getAutofillEnabled()) view.setAutofillEnabled(true);
    }

    private GeckoView findGeckoView(View view) {
        if (view instanceof GeckoView) return (GeckoView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            GeckoView found = findGeckoView(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityResumed(Activity activity) { enableAutofill(activity); }
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
