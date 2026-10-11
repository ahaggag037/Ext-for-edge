package com.edgeflow.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

/**
 * Completes the common web prompt surface that a real browser is expected to
 * provide. File selection is delegated back to the activity-specific delegate
 * so Atlas/Aegis keep their existing file-picker flow.
 */
final class BrowserPromptDelegate implements GeckoSession.PromptDelegate {
    private final GeckoSession.PromptDelegate fallback;

    BrowserPromptDelegate(GeckoSession.PromptDelegate fallback) {
        this.fallback = fallback;
    }

    @Override public GeckoResult<PromptResponse> onFilePrompt(
            GeckoSession session, FilePrompt prompt) {
        GeckoResult<PromptResponse> delegated = fallback == null ? null : fallback.onFilePrompt(session, prompt);
        return delegated != null ? delegated : GeckoResult.fromValue(prompt.dismiss());
    }

    @Override public GeckoResult<PromptResponse> onAlertPrompt(
            GeckoSession session, AlertPrompt prompt) {
        Activity activity = ForegroundActivityTracker.current();
        if (activity == null) return delegateOrDismissAlert(session, prompt);
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        new AlertDialog.Builder(activity)
                .setTitle(textOr(prompt.title, "Website message"))
                .setMessage(textOr(prompt.message, ""))
                .setPositiveButton("OK", (d, w) -> complete(result, prompt.dismiss()))
                .setOnCancelListener(d -> complete(result, prompt.dismiss()))
                .show();
        return result;
    }

    @Override public GeckoResult<PromptResponse> onButtonPrompt(
            GeckoSession session, ButtonPrompt prompt) {
        Activity activity = ForegroundActivityTracker.current();
        if (activity == null) {
            GeckoResult<PromptResponse> delegated = fallback == null ? null : fallback.onButtonPrompt(session, prompt);
            return delegated != null ? delegated : GeckoResult.fromValue(prompt.confirm(ButtonPrompt.Type.NEGATIVE));
        }
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        new AlertDialog.Builder(activity)
                .setTitle(textOr(prompt.title, "Confirm"))
                .setMessage(textOr(prompt.message, ""))
                .setPositiveButton("OK", (d, w) -> complete(result, prompt.confirm(ButtonPrompt.Type.POSITIVE)))
                .setNegativeButton("Cancel", (d, w) -> complete(result, prompt.confirm(ButtonPrompt.Type.NEGATIVE)))
                .setOnCancelListener(d -> complete(result, prompt.confirm(ButtonPrompt.Type.NEGATIVE)))
                .show();
        return result;
    }

    @Override public GeckoResult<PromptResponse> onTextPrompt(
            GeckoSession session, TextPrompt prompt) {
        Activity activity = ForegroundActivityTracker.current();
        if (activity == null) {
            GeckoResult<PromptResponse> delegated = fallback == null ? null : fallback.onTextPrompt(session, prompt);
            return delegated != null ? delegated : GeckoResult.fromValue(prompt.dismiss());
        }
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        if (prompt.defaultValue != null) input.setText(prompt.defaultValue);
        new AlertDialog.Builder(activity)
                .setTitle(textOr(prompt.title, "Input"))
                .setMessage(textOr(prompt.message, ""))
                .setView(input)
                .setPositiveButton("OK", (d, w) -> complete(result, prompt.confirm(input.getText().toString())))
                .setNegativeButton("Cancel", (d, w) -> complete(result, prompt.dismiss()))
                .setOnCancelListener(d -> complete(result, prompt.dismiss()))
                .show();
        return result;
    }

    @Override public GeckoResult<PromptResponse> onAuthPrompt(
            GeckoSession session, AuthPrompt prompt) {
        Activity activity = ForegroundActivityTracker.current();
        if (activity == null) {
            GeckoResult<PromptResponse> delegated = fallback == null ? null : fallback.onAuthPrompt(session, prompt);
            return delegated != null ? delegated : GeckoResult.fromValue(prompt.dismiss());
        }
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        fields.setPadding(pad, 0, pad, 0);
        EditText username = new EditText(activity);
        username.setHint("Username");
        username.setSingleLine(true);
        EditText password = new EditText(activity);
        password.setHint("Password");
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        fields.addView(username);
        fields.addView(password);
        new AlertDialog.Builder(activity)
                .setTitle(textOr(prompt.title, "Authentication required"))
                .setMessage(textOr(prompt.message, ""))
                .setView(fields)
                .setPositiveButton("Sign in", (d, w) -> complete(result,
                        prompt.confirm(username.getText().toString(), password.getText().toString())))
                .setNegativeButton("Cancel", (d, w) -> complete(result, prompt.dismiss()))
                .setOnCancelListener(d -> complete(result, prompt.dismiss()))
                .show();
        return result;
    }

    @Override public GeckoResult<PromptResponse> onBeforeUnloadPrompt(
            GeckoSession session, BeforeUnloadPrompt prompt) {
        return confirmAllowOrDeny("Leave this page?",
                "Changes you made may not be saved.",
                () -> prompt.confirm(AllowOrDeny.ALLOW),
                () -> prompt.confirm(AllowOrDeny.DENY),
                fallback == null ? null : fallback.onBeforeUnloadPrompt(session, prompt));
    }

    @Override public GeckoResult<PromptResponse> onRepostConfirmPrompt(
            GeckoSession session, RepostConfirmPrompt prompt) {
        return confirmAllowOrDeny("Resend form data?",
                "Reloading this page needs to resend previously submitted data.",
                () -> prompt.confirm(AllowOrDeny.ALLOW),
                () -> prompt.confirm(AllowOrDeny.DENY),
                fallback == null ? null : fallback.onRepostConfirmPrompt(session, prompt));
    }

    @Override public GeckoResult<PromptResponse> onPopupPrompt(
            GeckoSession session, PopupPrompt prompt) {
        String message = prompt.targetUri == null ? "This site wants to open a popup."
                : "Open popup?\n" + prompt.targetUri;
        return confirmAllowOrDeny("Popup request", message,
                () -> prompt.confirm(AllowOrDeny.ALLOW),
                () -> prompt.confirm(AllowOrDeny.DENY),
                fallback == null ? null : fallback.onPopupPrompt(session, prompt));
    }

    @Override public GeckoResult<PromptResponse> onRedirectPrompt(
            GeckoSession session, RedirectPrompt prompt) {
        return confirmAllowOrDeny("Redirect request",
                "A third-party frame wants to redirect this page.",
                () -> prompt.confirm(AllowOrDeny.ALLOW),
                () -> prompt.confirm(AllowOrDeny.DENY),
                fallback == null ? null : fallback.onRedirectPrompt(session, prompt));
    }

    @Override public GeckoResult<PromptResponse> onSharePrompt(
            GeckoSession session, SharePrompt prompt) {
        Activity activity = ForegroundActivityTracker.current();
        if (activity == null) {
            GeckoResult<PromptResponse> delegated = fallback == null ? null : fallback.onSharePrompt(session, prompt);
            return delegated != null ? delegated : GeckoResult.fromValue(prompt.dismiss());
        }
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            StringBuilder body = new StringBuilder();
            if (prompt.text != null && !prompt.text.isEmpty()) body.append(prompt.text);
            if (prompt.uri != null && !prompt.uri.isEmpty()) {
                if (body.length() > 0) body.append('\n');
                body.append(prompt.uri);
            }
            if (prompt.title != null) share.putExtra(Intent.EXTRA_TITLE, prompt.title);
            share.putExtra(Intent.EXTRA_TEXT, body.toString());
            activity.startActivity(Intent.createChooser(share, textOr(prompt.title, "Share")));
            return GeckoResult.fromValue(prompt.confirm(SharePrompt.Result.SUCCESS));
        } catch (Throwable error) {
            return GeckoResult.fromValue(prompt.confirm(SharePrompt.Result.FAILURE));
        }
    }

    private GeckoResult<PromptResponse> delegateOrDismissAlert(GeckoSession session, AlertPrompt prompt) {
        GeckoResult<PromptResponse> delegated = fallback == null ? null : fallback.onAlertPrompt(session, prompt);
        return delegated != null ? delegated : GeckoResult.fromValue(prompt.dismiss());
    }

    private GeckoResult<PromptResponse> confirmAllowOrDeny(String title, String message,
            ResponseFactory allow, ResponseFactory deny, GeckoResult<PromptResponse> delegated) {
        Activity activity = ForegroundActivityTracker.current();
        if (activity == null) return delegated != null ? delegated : GeckoResult.fromValue(deny.create());
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Allow", (d, w) -> complete(result, allow.create()))
                .setNegativeButton("Block", (d, w) -> complete(result, deny.create()))
                .setOnCancelListener(d -> complete(result, deny.create()))
                .show();
        return result;
    }

    private static void complete(GeckoResult<PromptResponse> result, PromptResponse response) {
        try { result.complete(response); } catch (Throwable ignored) {}
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private interface ResponseFactory { PromptResponse create(); }
}
