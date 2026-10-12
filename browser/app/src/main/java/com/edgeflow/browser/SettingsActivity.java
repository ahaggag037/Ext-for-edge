package com.edgeflow.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(18));
        root.setBackgroundColor(Color.rgb(10, 13, 18));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("‹");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = new TextView(this);
        title.setText("EdgeFlow Settings");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setPadding(dp(10), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(top);

        TextView performance = section("Performance");
        root.addView(performance);

        Button aiMode = button("AI performance · " + BrowserPrefs.aiProfileName(BrowserPrefs.getAiProfile(this)));
        aiMode.setOnClickListener(v -> chooseAiMode(aiMode));
        root.addView(aiMode, row());

        CheckBox dataSaver = check("Data Saver · reduce off-screen media/network cost",
                BrowserPrefs.isDataSaver(this));
        dataSaver.setOnCheckedChangeListener((b, checked) -> BrowserPrefs.setDataSaver(this, checked));
        root.addView(dataSaver, row());

        TextView privacy = section("Privacy & network");
        root.addView(privacy);

        CheckBox secureDns = check("Secure DNS (DoH) · requires browser restart",
                BrowserPrefs.isSecureDns(this));
        secureDns.setOnCheckedChangeListener((b, checked) -> {
            BrowserPrefs.setSecureDns(this, checked);
            Toast.makeText(this, "Secure DNS change applies after EdgeFlow restarts", Toast.LENGTH_SHORT).show();
        });
        root.addView(secureDns, row());

        Button autofill = button("Open Android Autofill settings");
        autofill.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)); }
            catch (Throwable ignored) {
                try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                catch (Throwable ignoredAgain) {}
            }
        });
        root.addView(autofill, row());

        TextView note = new TextView(this);
        note.setText("Nova keeps Secure DNS off by default for maximum compatibility with captive portals, VPNs and carrier networks. Data Saver is also opt-in; device RAM/thermal adaptation remains automatic.");
        note.setTextColor(Color.rgb(145, 160, 180));
        note.setTextSize(12);
        note.setPadding(dp(4), dp(18), dp(4), 0);
        root.addView(note);
        return root;
    }

    private void chooseAiMode(Button button) {
        String[] modes = {"Auto · adaptive", "Max smoothness · aggressive", "Live response · never hide streaming"};
        int current = BrowserPrefs.getAiProfile(this);
        new AlertDialog.Builder(this)
                .setTitle("AI performance")
                .setSingleChoiceItems(modes, current, (dialog, which) -> {
                    BrowserPrefs.setAiProfile(this, which);
                    button.setText("AI performance · " + BrowserPrefs.aiProfileName(which));
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private TextView section(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(115, 170, 255));
        view.setTextSize(13);
        view.setPadding(dp(4), dp(18), dp(4), dp(6));
        return view;
    }

    private CheckBox check(String text, boolean value) {
        CheckBox box = new CheckBox(this);
        box.setText(text);
        box.setTextColor(Color.WHITE);
        box.setTextSize(14);
        box.setChecked(value);
        box.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(115, 170, 255)));
        box.setPadding(dp(8), 0, dp(8), 0);
        return box;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setBackgroundColor(Color.rgb(31, 37, 48));
        return b;
    }

    private LinearLayout.LayoutParams row() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        lp.setMargins(0, 0, 0, dp(8));
        return lp;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
