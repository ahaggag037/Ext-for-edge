package com.edgeflow.browser;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SessionStore {
    private static final String PREFS = "edgeflow_browser_state_v2";
    private static final String KEY_TABS = "tabs";
    private static final String KEY_SELECTED = "selected";
    private static final int MAX_PERSISTED_TABS = 12;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    static final class Snapshot {
        final List<BrowserTab> tabs;
        final int selected;
        Snapshot(List<BrowserTab> tabs, int selected) {
            this.tabs = tabs;
            this.selected = selected;
        }
    }

    static void save(Context context, List<BrowserTab> tabs, int selectedIndex) {
        JSONArray array = new JSONArray();
        int persistedSelected = 0;
        int persistedIndex = 0;
        for (int i = 0; i < tabs.size() && array.length() < MAX_PERSISTED_TABS; i++) {
            BrowserTab tab = tabs.get(i);
            if (tab.privateMode) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("id", tab.id);
                o.put("title", tab.title == null ? "" : tab.title);
                o.put("url", tab.url == null ? "about:blank" : tab.url);
                o.put("desktop", tab.desktopMode);
                o.put("state", tab.sessionState == null ? "" : tab.sessionState);
                array.put(o);
                if (i == selectedIndex) persistedSelected = persistedIndex;
                persistedIndex++;
            } catch (Exception ignored) {}
        }

        final String plaintextSnapshot = array.toString();
        final int selectedSnapshot = persistedSelected;
        final int tabCount = array.length();
        final Context app = context.getApplicationContext();
        IO.execute(() -> {
            String encrypted = SecureStateCodec.encrypt(plaintextSnapshot);
            if (encrypted.isEmpty() && tabCount > 0) {
                // Fail closed: never persist potentially sensitive Gecko session state as plaintext.
                return;
            }
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TABS, encrypted)
                    .putInt(KEY_SELECTED, selectedSnapshot)
                    .commit();
        });
    }

    static Snapshot load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_TABS, "");
        String raw = SecureStateCodec.decrypt(stored);
        if (raw == null || raw.isEmpty()) raw = "[]";
        ArrayList<BrowserTab> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length() && out.size() < MAX_PERSISTED_TABS; i++) {
                JSONObject o = array.getJSONObject(i);
                BrowserTab tab = new BrowserTab(
                        o.optString("id", UUID.randomUUID().toString()),
                        false,
                        o.optBoolean("desktop", false),
                        o.optString("title", "Restored tab"),
                        o.optString("url", "https://chatgpt.com/"));
                String state = o.optString("state", "");
                tab.sessionState = state.isEmpty() ? null : state;
                tab.hibernated = true;
                out.add(tab);
            }
        } catch (Exception ignored) {
            out.clear();
        }
        int selected = prefs.getInt(KEY_SELECTED, 0);
        if (selected < 0 || selected >= out.size()) selected = 0;
        return new Snapshot(out, selected);
    }

    private SessionStore() {}
}
