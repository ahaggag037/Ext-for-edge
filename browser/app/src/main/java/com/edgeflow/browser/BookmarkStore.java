package com.edgeflow.browser;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class BookmarkStore {
    private static final String PREFS = "edgeflow_bookmarks_v1";
    private static final String KEY_DATA = "data";
    private static final int MAX = 500;

    static final class Entry {
        final String title;
        final String url;
        final long savedAt;
        Entry(String title, String url, long savedAt) {
            this.title = title == null ? "" : title;
            this.url = url == null ? "" : url;
            this.savedAt = savedAt;
        }
    }

    static synchronized List<Entry> list(Context context) {
        ArrayList<Entry> out = new ArrayList<>();
        String raw = loadRaw(context);
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length() && out.size() < MAX; i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                String url = o.optString("url", "");
                if (url.isEmpty()) continue;
                out.add(new Entry(o.optString("title", ""), url, o.optLong("savedAt", 0L)));
            }
        } catch (Throwable ignored) {}
        out.sort(Comparator.comparingLong((Entry e) -> e.savedAt).reversed());
        return Collections.unmodifiableList(out);
    }

    static synchronized boolean contains(Context context, String url) {
        if (url == null || url.isEmpty()) return false;
        for (Entry e : list(context)) if (url.equals(e.url)) return true;
        return false;
    }

    static synchronized void add(Context context, String title, String url) {
        if (url == null || url.trim().isEmpty()) return;
        ArrayList<Entry> entries = new ArrayList<>(list(context));
        entries.removeIf(e -> url.equals(e.url));
        entries.add(0, new Entry(title, url, System.currentTimeMillis()));
        if (entries.size() > MAX) entries.subList(MAX, entries.size()).clear();
        save(context, entries);
    }

    static synchronized void remove(Context context, String url) {
        if (url == null) return;
        ArrayList<Entry> entries = new ArrayList<>(list(context));
        entries.removeIf(e -> url.equals(e.url));
        save(context, entries);
    }

    private static String loadRaw(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String encrypted = prefs.getString(KEY_DATA, "");
        String raw = SecureStateCodec.decrypt(encrypted);
        return raw == null || raw.isEmpty() ? "[]" : raw;
    }

    private static void save(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        try {
            for (Entry e : entries) {
                JSONObject o = new JSONObject();
                o.put("title", e.title);
                o.put("url", e.url);
                o.put("savedAt", e.savedAt);
                array.put(o);
            }
        } catch (Throwable ignored) {}
        String encrypted = SecureStateCodec.encrypt(array.toString());
        if (encrypted.isEmpty() && array.length() > 0) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_DATA, encrypted).apply();
    }

    private BookmarkStore() {}
}
