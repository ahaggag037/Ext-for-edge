package com.edgeflow.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

/**
 * Common web prompt surface for EdgeFlow. File selection is still delegated to
 * the activity so file uploads keep their existing Android document-picker path.
 */
final class BrowserPromptDelegate implements GeckoSession.PromptDelegate {
    private final GeckoSession.PromptDelegate fallback;

    BrowserPromptDelegate(GeckoSession.PromptDelegate fallback) {
        this.fallback = fallback;
    }

    @Override public GeckoResult<PromptResponse> onFilePrompt(GeckoSession session, FilePrompt prompt) {
        GeckoResult<PromptResponse> delegated = fallback == null ? null : fallback.onFilePrompt(session, prompt);
        return delegated != null ? delegated : GeckoResult.fromValue(prompt.dismiss());
    }

    @Override public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession session, AlertPrompt prompt) {
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

    @Override public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession session, ButtonPrompt prompt) {
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

    @Override public GeckoResult<PromptResponse> onTextPrompt(GeckoSession session, TextPrompt prompt) {
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

    @Override public GeckoResult<PromptResponse> onAuthPrompt(GeckoSession session, AuthPrompt prompt) {
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

    @Override public GeckoResult<PromptResponse> onChoicePrompt(GeckoSession session, ChoicePrompt prompt) {
        Activity activity = ForegroundActivityTracker.current();
        if (activity == null) {
            GeckoResult<PromptResponse> delegated = fallback == null ? null : fallback.onChoicePrompt(session, prompt);
            return delegated != null ? delegated : GeckoResult.fromValue(prompt.dismiss());
        }
        List<ChoicePrompt.Choice> choices = new ArrayList<>();
        flattenChoices(prompt.choices, choices);
        if (choices.isEmpty()) return GeckoResult.fromValue(prompt.dismiss());
        String[] labels = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            ChoicePrompt.Choice c = choices.get(i);
            labels[i] = (c.disabled ? "⛔ " : "") + c.label;
        }

        GeckoResult<PromptResponse> result = new GeckoResult<>();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(textOr(prompt.title, textOr(prompt.message, "Choose")))
                .setOnCancelListener(d -> complete(result, prompt.dismiss()));

        if (prompt.type == ChoicePrompt.Type.MULTIPLE) {
            boolean[] checked = new boolean[choices.size()];
            for (int i = 0; i < choices.size(); i++) checked[i] = choices.get(i).selected && !choices.get(i).disabled;
            builder.setMultiChoiceItems(labels, checked, (d, which, value) -> {
                if (which < 0 || which >= choices.size()) return;
                if (choices.get(which).disabled) {
                    ((AlertDialog) d).getListView().setItemChecked(which, false);
                    checked[which] = false;
                } else checked[which] = value;
            });
            builder.setPositiveButton("OK", (d, w) -> {
                ArrayList<String> selected = new ArrayList<>();
                for (int i = 0; i < choices.size(); i++) {
                    if (checked[i] && !choices.get(i).disabled) selected.add(choices.get(i).id);
                }
                complete(result, prompt.confirm(selected.toArray(new String[0])));
            });
        } else if (prompt.type == ChoicePrompt.Type.SINGLE) {
            int initial = -1;
            for (int i = 0; i < choices.size(); i++) {
                if (choices.get(i).selected && !choices.get(i).disabled) { initial = i; break; }
            }
            final int[] selected = {initial};
            builder.setSingleChoiceItems(labels, initial, (d, which) -> {
                if (which >= 0 && which < choices.size() && !choices.get(which).disabled) selected[0] = which;
            });
            builder.setPositiveButton("OK", (d, w) -> {
                if (selected[0] >= 0) complete(result, prompt.confirm(choices.get(selected[0]).id));
                else complete(result, prompt.dismiss());
            });
        } else {
            builder.setItems(labels, (d, which) -> {
                if (which >= 0 && which < choices.size() && !choices.get(which).disabled) {
                    complete(result, prompt.confirm(choices.get(which).id));
                } else complete(result, prompt.dismiss());
            });
        }
        builder.setNegativeButton("Cancel", (d, w) -> complete(result, prompt.dismiss())).show();
        return result;
    }

    @Override public GeckoResult<PromptResponse> onColorPrompt(GeckoSession session, ColorPrompt prompt) {
        return textValuePrompt(prompt.title, "Color (#RRGGBB)", prompt.defaultValue,
                value -> prompt.confirm(value),
                () -> prompt.dismiss(),
                fallback == null ? null : fallback.onColorPrompt(session, prompt));
    }

    @Override public GeckoResult<PromptResponse> onDateTimePrompt(GeckoSession session, DateTimePrompt prompt) {
        String hint;
        if (prompt.type == DateTimePrompt.Type.DATE) hint = "Date (YYYY-MM-DD)";
        else if (prompt.type == DateTimePrompt.Type.TIME) hint = "Time (HH:MM)";
        else if (prompt.type == DateTimePrompt.Type.MONTH) hint = "Month (YYYY-MM)";
        else if (prompt.type == DateTimePrompt.Type.WEEK) hint = "Week (YYYY-Www)";
        else hint = "Date and time (YYYY-MM-DDTHH:MM)";
        return textValuePrompt(prompt.title, hint, prompt.defaultValue,
                value -> prompt.confirm(value),
                () -> prompt.dismiss(),
                fallback == null ? null : fallback.onDateTimePrompt(session, prompt));
    }

    @Override public GeckoResult<PromptResponse> onFolderUploadPrompt(GeckoSession session, FolderUploadPrompt prompt) {
        String directory = prompt.directoryName == null || prompt.directoryName.isEmpty()
                ? "this folder" : prompt.directoryName;
        return confirmAllowOrDeny("Upload folder?",
                "Allow this site to upload all selected files from " + directory + "?",
                () -> prompt.confirm(AllowOrDeny.ALLOW),
                () -> prompt.confirm(AllowOrDeny.DENY),
                fallback == null ? null : fallback.onFolderUploadPrompt(session, prompt));
    }

    @Override public GeckoResult<PromptResponse> onBeforeUnloadPrompt(GeckoSession session, BeforeUnloadPrompt prompt) {
        return confirmAllowOrDeny("Leave this page?", "Changes you made may not be saved.",
                () -> prompt.confirm(AllowOrDeny.ALLOW), () -> prompt.confirm(AllowOrDeny.DENY),
                fallback == null ? null : fallback.onBeforeUnloadPrompt(session, prompt));
    }

    @Override public GeckoResult<PromptResponse> onRepostConfirmPrompt(GeckoSession session, RepostConfirmPrompt prompt) {
        return confirmAllowOrDeny("Resend form data?", "Reloading this page needs to resend previously submitted data.",
                () -> prompt.confirm(AllowOrDeny.ALLOW), () -> prompt.confirm(AllowOrDeny.DENY),
                fallback == null ? null : fallback.onRepostConfirmPrompt(session, prompt));
    }

    @Override public GeckoResult<PromptResponse> onPopupPrompt(GeckoSession session, PopupPrompt prompt) {
        String message = prompt.targetUri == null ? "This site wants to open a popup." : "Open popup?\n" + prompt.targetUri;
        return confirmAllowOrDeny("Popup request", message,
                () -> prompt.confirm(AllowOrDeny.ALLOW), () -> prompt.confirm(AllowOrDeny.DENY),
                fallback == null ? null : fallback.onPopupPrompt(session, prompt));
    }

    @Override public GeckoResult<PromptResponse> onRedirectPrompt(GeckoSession session, RedirectPrompt prompt) {
        return confirmAllowOrDeny("Redirect request", "A third-party frame wants to redirect this page.",
                () -> prompt.confirm(AllowOrDeny.ALLOW), () -> prompt.confirm(AllowOrDeny.DENY),
                fallback == null ? null : fallback.onRedirectPrompt(session, prompt));
    }

    @Override public GeckoResult<PromptResponse> onSharePrompt(GeckoSession session, SharePrompt prompt) {
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

    private GeckoResult<PromptResponse> textValuePrompt(String title, String hint, String defaultValue,
            ValueResponseFactory confirm, ResponseFactory cancel, GeckoResult<PromptResponse> delegated) {
        Activity activity = ForegroundActivityTracker.current();
        if (activity == null) return delegated != null ? delegated : GeckoResult.fromValue(cancel.create());
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(hint);
        if (defaultValue != null) input.setText(defaultValue);
        new AlertDialog.Builder(activity)
                .setTitle(textOr(title, hint))
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String value = input.getText().toString().trim();
                    complete(result, value.isEmpty() ? cancel.create() : confirm.create(value));
                })
                .setNegativeButton("Cancel", (d, w) -> complete(result, cancel.create()))
                .setOnCancelListener(d -> complete(result, cancel.create()))
                .show();
        return result;
    }

    private static void flattenChoices(ChoicePrompt.Choice[] input, List<ChoicePrompt.Choice> out) {
        if (input == null) return;
        for (ChoicePrompt.Choice choice : input) {
            if (choice == null || choice.separator) continue;
            if (choice.items != null && choice.items.length > 0) flattenChoices(choice.items, out);
            else out.add(choice);
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
    private interface ValueResponseFactory { PromptResponse create(String value); }
}
