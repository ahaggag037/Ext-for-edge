package com.edgeflow.browser;

import android.content.Context;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

final class HistoryBridge {
    static void attach(Context context, GeckoSession session) {
        if (context == null || session == null) return;
        final Context app = context.getApplicationContext();
        session.setHistoryDelegate(new GeckoSession.HistoryDelegate() {
            @Override
            public GeckoResult<Boolean> onVisited(GeckoSession s, String url,
                                                   String lastVisitedURL, int flags) {
                boolean topLevel = (flags & GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL) != 0;
                boolean error = (flags & GeckoSession.HistoryDelegate.VISIT_UNRECOVERABLE_ERROR) != 0;
                boolean redirectSource = (flags & GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE) != 0
                        || (flags & GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT) != 0;
                boolean privateMode = s.getSettings().getUsePrivateMode();
                if (topLevel && !error && !redirectSource && !privateMode && url != null) {
                    HistoryStore.record(app, "", url, false);
                }
                return GeckoResult.fromValue(Boolean.TRUE);
            }
        });
    }

    private HistoryBridge() {}
}
