package com.edgeflow.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.URLUtil;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.mozilla.geckoview.WebResponse;

final class DownloadController {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    static void handle(Activity activity, WebResponse response) {
        if (response == null) return;
        if (response.body == null) {
            Toast.makeText(activity, "Download has no readable body", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, String> headers = response.headers;
        String contentDisposition = headers == null ? null : headers.get("content-disposition");
        String mime = headers == null ? null : headers.get("content-type");
        if (mime != null) {
            int semicolon = mime.indexOf(';');
            if (semicolon > 0) mime = mime.substring(0, semicolon).trim();
        }
        String filename = URLUtil.guessFileName(response.uri, contentDisposition, mime);
        if (filename == null || filename.trim().isEmpty()) filename = "download";

        final String finalFilename = sanitize(filename);
        final String finalMime = mime == null || mime.isEmpty() ? "application/octet-stream" : mime;
        new AlertDialog.Builder(activity)
                .setTitle("Download file")
                .setMessage(finalFilename)
                .setPositiveButton("Download", (dialog, which) -> save(activity, response, finalFilename, finalMime))
                .setNegativeButton("Cancel", (dialog, which) -> closeQuietly(response.body))
                .setOnCancelListener(dialog -> closeQuietly(response.body))
                .show();
    }

    private static void save(Activity activity, WebResponse response, String filename, String mime) {
        IO.execute(() -> {
            boolean ok = false;
            String destination = filename;
            try (InputStream input = response.body) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentResolver resolver = activity.getContentResolver();
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                    values.put(MediaStore.Downloads.MIME_TYPE, mime);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/EdgeFlow");
                    values.put(MediaStore.Downloads.IS_PENDING, 1);
                    Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new IllegalStateException("Could not create download destination");
                    try {
                        try (OutputStream output = resolver.openOutputStream(uri, "w")) {
                            if (output == null) throw new IllegalStateException("Could not open download destination");
                            copy(input, output);
                        }
                        ContentValues done = new ContentValues();
                        done.put(MediaStore.Downloads.IS_PENDING, 0);
                        resolver.update(uri, done, null, null);
                        destination = "Downloads/EdgeFlow/" + filename;
                        ok = true;
                    } catch (Throwable error) {
                        resolver.delete(uri, null, null);
                        throw error;
                    }
                } else {
                    File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) throw new IllegalStateException("No external downloads directory");
                    if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create downloads directory");
                    File out = uniqueFile(dir, filename);
                    try (OutputStream output = new FileOutputStream(out)) {
                        copy(input, output);
                    }
                    destination = out.getAbsolutePath();
                    ok = true;
                }
            } catch (Throwable ignored) {
                closeQuietly(response.body);
            }

            final boolean success = ok;
            final String where = destination;
            activity.runOnUiThread(() -> Toast.makeText(activity,
                    success ? "Downloaded: " + where : "Download failed",
                    Toast.LENGTH_LONG).show());
        });
    }

    private static void copy(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        output.flush();
    }

    private static File uniqueFile(File dir, String filename) {
        File candidate = new File(dir, filename);
        if (!candidate.exists()) return candidate;
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String ext = dot > 0 ? filename.substring(dot) : "";
        for (int i = 1; i < 10000; i++) {
            candidate = new File(dir, base + " (" + i + ")" + ext);
            if (!candidate.exists()) return candidate;
        }
        return new File(dir, System.currentTimeMillis() + "-" + filename);
    }

    private static String sanitize(String filename) {
        String out = filename.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (out.isEmpty()) out = "download";
        return out.length() > 180 ? out.substring(0, 180) : out;
    }

    private static void closeQuietly(InputStream stream) {
        if (stream == null) return;
        try { stream.close(); } catch (Throwable ignored) {}
    }

    private DownloadController() {}
}
