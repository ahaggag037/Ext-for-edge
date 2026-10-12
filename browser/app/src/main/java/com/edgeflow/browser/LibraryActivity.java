package com.edgeflow.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class LibraryActivity extends Activity {
    private LinearLayout list;
    private TextView title;
    private EditText search;
    private boolean showBookmarks;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        showBookmarks = "bookmarks".equalsIgnoreCase(getIntent().getStringExtra("section"));
        setContentView(buildUi());
        refresh();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showBookmarks = "bookmarks".equalsIgnoreCase(intent.getStringExtra("section"));
        refresh();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 13, 18));
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);

        Button back = button("‹");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setPadding(dp(8), 0, dp(8), 0);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button switcher = button("⇄");
        switcher.setContentDescription("Switch history and bookmarks");
        switcher.setOnClickListener(v -> {
            showBookmarks = !showBookmarks;
            if (search != null) search.setText("");
            refresh();
        });
        top.addView(switcher, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(top);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search local browser library");
        search.setHintTextColor(Color.rgb(120, 135, 155));
        search.setTextColor(Color.WHITE);
        search.setBackgroundColor(Color.rgb(25, 31, 41));
        search.setPadding(dp(12), 0, dp(12), 0);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        searchLp.setMargins(0, dp(4), 0, dp(10));
        root.addView(search, searchLp);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Button clear = button("Clear history");
        clear.setOnClickListener(v -> {
            if (showBookmarks) return;
            new AlertDialog.Builder(this)
                    .setTitle("Clear browsing history?")
                    .setMessage("This removes EdgeFlow's local encrypted history. It does not delete website accounts or cookies.")
                    .setPositiveButton("Clear", (d, w) -> { HistoryStore.clear(this); refresh(); })
                    .setNegativeButton("Cancel", null).show();
        });
        root.addView(clear, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        return root;
    }

    private void refresh() {
        if (list == null || title == null) return;
        list.removeAllViews();
        title.setText(showBookmarks ? "Bookmarks" : "History");
        if (showBookmarks) renderBookmarks(); else renderHistory();
    }

    private String query() {
        return search == null ? "" : search.getText().toString().trim().toLowerCase();
    }

    private void renderBookmarks() {
        String q = query();
        List<BookmarkStore.Entry> entries = BookmarkStore.list(this);
        int shown = 0;
        for (BookmarkStore.Entry e : entries) {
            if (!q.isEmpty() && !e.url.toLowerCase().contains(q)
                    && !e.title.toLowerCase().contains(q)) continue;
            addRow(e.title, e.url, e.savedAt, () -> open(e.url), () -> {
                BookmarkStore.remove(this, e.url);
                Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show();
                refresh();
            });
            if (++shown >= 200) break;
        }
        if (shown == 0) empty(q.isEmpty() ? "No bookmarks yet" : "No matching bookmarks");
    }

    private void renderHistory() {
        String q = query();
        List<HistoryStore.Entry> entries = q.isEmpty()
                ? HistoryStore.list(this)
                : HistoryStore.search(this, q, 200);
        if (entries.isEmpty()) { empty(q.isEmpty() ? "No browsing history yet" : "No matching history"); return; }
        int shown = 0;
        for (HistoryStore.Entry e : entries) {
            if (shown++ >= 200) break;
            String subtitle = e.url + (e.visits > 1 ? "  ·  " + e.visits + " visits" : "");
            addRow(e.title, subtitle, e.visitedAt, () -> open(e.url), () -> {
                BookmarkStore.add(this, e.title, e.url);
                Toast.makeText(this, "Bookmarked", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void addRow(String name, String subtitle, long time, Runnable open, Runnable longAction) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackgroundColor(Color.rgb(18, 22, 29));

        TextView main = new TextView(this);
        main.setText(name == null || name.trim().isEmpty() ? host(subtitle) : name);
        main.setTextColor(Color.WHITE);
        main.setTextSize(15);
        main.setMaxLines(2);
        row.addView(main);

        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextColor(Color.rgb(155, 170, 188));
        sub.setTextSize(12);
        sub.setMaxLines(2);
        row.addView(sub);

        if (time > 0) {
            TextView when = new TextView(this);
            when.setText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(time)));
            when.setTextColor(Color.rgb(110, 125, 145));
            when.setTextSize(10);
            row.addView(when);
        }

        row.setOnClickListener(v -> open.run());
        if (longAction != null) row.setOnLongClickListener(v -> { longAction.run(); return true; });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        list.addView(row, lp);
    }

    private void empty(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(Color.rgb(155, 170, 188));
        view.setTextSize(16);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(16), dp(64), dp(16), dp(64));
        list.addView(view);
    }

    private void open(String url) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url), this, BrowserActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private String host(String url) {
        try { String h = Uri.parse(url).getHost(); return h == null ? url : h; }
        catch (Throwable ignored) { return url == null ? "" : url; }
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.rgb(31, 37, 48));
        return b;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
