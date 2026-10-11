package com.edgeflow.browser;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

public final class BrowserActivity extends Activity {
    private static final String HOME = "https://chatgpt.com/";
    private static GeckoRuntime runtime;

    private GeckoSession session;
    private GeckoView geckoView;
    private EditText address;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());

        if (runtime == null) {
            runtime = GeckoRuntime.create(this);
        }
        EdgeFlowRuntime.ensureCoreInstalled(runtime);

        session = new GeckoSession();
        session.setContentDelegate(new GeckoSession.ContentDelegate() {});
        session.open(runtime);
        geckoView.setSession(session);

        String initial = HOME;
        Intent intent = getIntent();
        Uri data = intent == null ? null : intent.getData();
        if (data != null && ("http".equalsIgnoreCase(data.getScheme()) || "https".equalsIgnoreCase(data.getScheme()))) {
            initial = data.toString();
        }
        address.setText(initial);
        session.loadUri(initial);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(14, 17, 22));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(4), dp(4), dp(4), dp(4));
        toolbar.setBackgroundColor(Color.rgb(18, 22, 29));

        Button back = toolbarButton("‹");
        back.setContentDescription("Back");
        back.setOnClickListener(v -> { if (session != null) session.goBack(); });
        toolbar.addView(back, fixed(dp(46), dp(48)));

        Button forward = toolbarButton("›");
        forward.setContentDescription("Forward");
        forward.setOnClickListener(v -> { if (session != null) session.goForward(); });
        toolbar.addView(forward, fixed(dp(46), dp(48)));

        Button reload = toolbarButton("↻");
        reload.setContentDescription("Reload");
        reload.setOnClickListener(v -> { if (session != null) session.reload(); });
        toolbar.addView(reload, fixed(dp(46), dp(48)));

        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(14);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.rgb(130, 140, 155));
        address.setHint("Search or enter address");
        address.setBackgroundColor(Color.rgb(31, 37, 48));
        address.setPadding(dp(12), 0, dp(12), 0);
        address.setSelectAllOnFocus(false);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate(address.getText().toString());
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        addressParams.setMargins(dp(4), 0, dp(4), 0);
        toolbar.addView(address, addressParams);

        Button go = toolbarButton("Go");
        go.setTextSize(12);
        go.setOnClickListener(v -> navigate(address.getText().toString()));
        toolbar.addView(go, fixed(dp(52), dp(48)));

        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        geckoView = new GeckoView(this);
        root.addView(geckoView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void navigate(String raw) {
        if (session == null) return;
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return;

        String target;
        if (value.startsWith("http://") || value.startsWith("https://")) {
            target = value;
        } else if (value.contains(" ") || !value.contains(".")) {
            target = "https://www.google.com/search?q=" + Uri.encode(value);
        } else {
            target = "https://" + value;
        }
        address.setText(target);
        session.loadUri(target);
    }

    private Button toolbarButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(20);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private LinearLayout.LayoutParams fixed(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (session != null) {
            session.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }
        super.onDestroy();
    }
}
