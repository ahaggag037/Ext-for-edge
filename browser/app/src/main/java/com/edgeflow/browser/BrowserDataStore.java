package com.edgeflow.browser;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class BrowserDataStore extends SQLiteOpenHelper {
    interface ResultCallback<T> { void onResult(T value); }

    static final class Entry {
        final String url;
        final String title;
        final long timestamp;
        Entry(String url, String title, long timestamp) {
            this.url = url;
            this.title = title == null || title.trim().isEmpty() ? url : title;
            this.timestamp = timestamp;
        }
        String label() { return title + "\n" + url; }
    }

    private static final String DB_NAME = "edgeflow_browser.db";
    private static final int DB_VERSION = 1;
    private static BrowserDataStore instance;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    static synchronized BrowserDataStore get(Context context) {
        if (instance == null) instance = new BrowserDataStore(context.getApplicationContext());
        return instance;
    }

    private BrowserDataStore(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history (url TEXT PRIMARY KEY, title TEXT, last_visit INTEGER NOT NULL, visits INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE bookmarks (url TEXT PRIMARY KEY, title TEXT, created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX history_recent ON history(last_visit DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    void recordVisit(String url, String title) {
        if (!isWebUrl(url)) return;
        final long now = System.currentTimeMillis();
        io.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                Cursor c = db.rawQuery("SELECT visits FROM history WHERE url=?", new String[]{url});
                int visits = c.moveToFirst() ? c.getInt(0) + 1 : 1;
                c.close();
                ContentValues v = new ContentValues();
                v.put("url", url);
                v.put("title", safeTitle(title, url));
                v.put("last_visit", now);
                v.put("visits", visits);
                db.insertWithOnConflict("history", null, v, SQLiteDatabase.CONFLICT_REPLACE);
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        });
    }

    void updateTitle(String url, String title) {
        if (!isWebUrl(url) || title == null || title.trim().isEmpty()) return;
        io.execute(() -> {
            ContentValues v = new ContentValues();
            v.put("title", title.trim());
            getWritableDatabase().update("history", v, "url=?", new String[]{url});
            getWritableDatabase().update("bookmarks", v, "url=?", new String[]{url});
        });
    }

    void recentHistory(int limit, ResultCallback<List<Entry>> callback) {
        io.execute(() -> {
            ArrayList<Entry> out = new ArrayList<>();
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT url,title,last_visit FROM history ORDER BY last_visit DESC LIMIT ?",
                    new String[]{String.valueOf(Math.max(1, Math.min(200, limit)))});
            while (c.moveToNext()) out.add(new Entry(c.getString(0), c.getString(1), c.getLong(2)));
            c.close();
            main.post(() -> callback.onResult(out));
        });
    }

    void bookmarks(ResultCallback<List<Entry>> callback) {
        io.execute(() -> {
            ArrayList<Entry> out = new ArrayList<>();
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT url,title,created FROM bookmarks ORDER BY created DESC", null);
            while (c.moveToNext()) out.add(new Entry(c.getString(0), c.getString(1), c.getLong(2)));
            c.close();
            main.post(() -> callback.onResult(out));
        });
    }

    void isBookmarked(String url, ResultCallback<Boolean> callback) {
        io.execute(() -> {
            Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM bookmarks WHERE url=? LIMIT 1", new String[]{url});
            boolean yes = c.moveToFirst();
            c.close();
            main.post(() -> callback.onResult(yes));
        });
    }

    void toggleBookmark(String url, String title, ResultCallback<Boolean> callback) {
        if (!isWebUrl(url)) { main.post(() -> callback.onResult(false)); return; }
        io.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            Cursor c = db.rawQuery("SELECT 1 FROM bookmarks WHERE url=? LIMIT 1", new String[]{url});
            boolean exists = c.moveToFirst();
            c.close();
            boolean nowBookmarked;
            if (exists) {
                db.delete("bookmarks", "url=?", new String[]{url});
                nowBookmarked = false;
            } else {
                ContentValues v = new ContentValues();
                v.put("url", url);
                v.put("title", safeTitle(title, url));
                v.put("created", System.currentTimeMillis());
                db.insertWithOnConflict("bookmarks", null, v, SQLiteDatabase.CONFLICT_REPLACE);
                nowBookmarked = true;
            }
            boolean result = nowBookmarked;
            main.post(() -> callback.onResult(result));
        });
    }

    void clearHistory(ResultCallback<Boolean> callback) {
        io.execute(() -> {
            getWritableDatabase().delete("history", null, null);
            main.post(() -> callback.onResult(true));
        });
    }

    private static boolean isWebUrl(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }

    private static String safeTitle(String title, String url) {
        return title == null || title.trim().isEmpty() ? url : title.trim();
    }
}
