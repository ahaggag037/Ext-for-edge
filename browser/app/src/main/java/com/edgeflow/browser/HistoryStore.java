package com.edgeflow.browser;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class HistoryStore {
    private static final String PREFS = "edgeflow_history_v1";
    private static final String KEY_DATA = "data";
    private static final int MAX = 800;

    static final class Entry {
        final String title;
        final String url;
        final long visitedAt;
        final int visits;
        Entry(String title, String url, long visitedAt, int visits) {
            this.title = title == null ? "" : title;
            this.url = url == null ? "" : url;
            this.visitedAt = visitedAt;
            this.visits = visits;
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
                out.add(new Entry(o.optString("title", ""), url,
                        o.optLong("visitedAt", 0L), Math.max(1, o.optInt("visits", 1))));
            }
        } catch (Throwable ignored) {}
        out.sort(Comparator.comparingLong((Entry e) -> e.visitedAt).reversed());
        return Collections.unmodifiableList(out);
    }

    static synchronized void record(Context context, String title, String url, boolean privateMode) {
        if (privateMode || url == null || url.trim().isEmpty()) return;
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return;
        ArrayList<Entry> entries = new ArrayList<>(list(context));
        int visits = 1;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (url.equals(e.url)) {
                visits = e.visits + 1;
                entries.remove(i);
                break;
            }
        }
        entries.add(0, new Entry(title, url, System.currentTimeMillis(), visits));
        if (entries.size() > MAX) entries.subList(MAX, entries.size()).clear();
        save(context, entries);
    }

    static synchronized void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    static synchronized List<Entry> search(Context context, String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase();
        ArrayList<Entry> out = new ArrayList<>();
        for (Entry e : list(context)) {
            if (q.isEmpty() || e.url.toLowerCase().contains(q) || e.title.toLowerCase().contains(q)) {
                out.add(e);
                if (out.size() >= Math.max(1, limit)) break;
            }
        }
        return out;
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
                o.put("visitedAt", e.visitedAt);
                o.put("visits", e.visits);
                array.put(o);
            }
        } catch (Throwable ignored) {}
        String encrypted = SecureStateCodec.encrypt(array.toString());
        if (encrypted.isEmpty() && array.length() > 0) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_DATA, encrypted).apply();
    }

    private HistoryStore() {}
}
