package com.edgeflow.browser;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class AiHubActivity extends Activity {
    private static final String[][] SERVICES = {
            {"ChatGPT", "https://chatgpt.com/"},
            {"Claude", "https://claude.ai/"},
            {"Gemini", "https://gemini.google.com/"},
            {"Kimi", "https://www.kimi.com/"},
            {"Perplexity", "https://www.perplexity.ai/"},
            {"Copilot", "https://copilot.microsoft.com/"},
            {"DeepSeek", "https://chat.deepseek.com/"}
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
    }

    private ScrollView buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(10, 13, 18));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(20));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("EdgeFlow AI Hub");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, 0, 0, dp(4));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Open an AI workspace with EdgeFlow's site-specific performance engine.");
        subtitle.setTextColor(Color.rgb(155, 170, 188));
        subtitle.setTextSize(13);
        subtitle.setPadding(0, 0, 0, dp(18));
        root.addView(subtitle);

        for (String[] service : SERVICES) {
            Button button = new Button(this);
            button.setText(service[0]);
            button.setAllCaps(false);
            button.setTextSize(17);
            button.setTextColor(Color.WHITE);
            button.setBackgroundColor(Color.rgb(31, 37, 48));
            button.setOnClickListener(v -> open(service[1]));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
            lp.setMargins(0, 0, 0, dp(10));
            root.addView(button, lp);
        }
        return scroll;
    }

    private void open(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url), this, BrowserActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
