package com.edgeflow.installer;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

final class CrxStore {
    static final String FILE_NAME = "EdgeFlow-Mobile-Performance-v3.1.0.crx";
    static final String EXPECTED_SHA256 = "d1a20e525e51f5bfaf632956618a588f312d6c420ddce7279ed4b057ae370444";
    static final int PART_COUNT = 10;

    static final class Result {
        final Uri uri;
        final String sha256;
        Result(Uri uri, String sha256) { this.uri = uri; this.sha256 = sha256; }
    }

    static Result export(Context context) throws Exception {
        byte[] crx = readBundledCrx(context);
        String sha = sha256(crx);
        verify(sha);
        Uri uri = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? exportMediaStore(context, crx)
                : exportLegacy(context, crx);
        return new Result(uri, sha);
    }

    private static byte[] readBundledCrx(Context context) throws Exception {
        StringBuilder b64 = new StringBuilder(23000);
        byte[] buf = new byte[4096];
        for (int i = 1; i <= PART_COUNT; i++) {
            String name = String.format("crx-parts/part-%02d.b64", i);
            try (InputStream in = context.getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                b64.append(out.toString("US-ASCII").trim());
            }
        }
        return Base64.decode(b64.toString(), Base64.DEFAULT);
    }

    private static Uri exportMediaStore(Context context, byte[] crx) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/x-chrome-extension");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/EdgeFlow");
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("Could not create Downloads entry");
        try (OutputStream out = resolver.openOutputStream(uri, "w")) {
            if (out == null) throw new IllegalStateException("Could not open Downloads output stream");
            out.write(crx);
            out.flush();
        } catch (Exception e) {
            resolver.delete(uri, null, null);
            throw e;
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(uri, done, null, null);
        return uri;
    }

    @SuppressWarnings("deprecation")
    private static Uri exportLegacy(Context context, byte[] crx) throws Exception {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "EdgeFlow");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create Downloads/EdgeFlow");
        File file = new File(dir, FILE_NAME);
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(crx);
        }
        return Uri.fromFile(file);
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void verify(String sha) {
        if (!EXPECTED_SHA256.equalsIgnoreCase(sha)) {
            throw new IllegalStateException("Bundled CRX failed SHA-256 verification");
        }
    }

    private CrxStore() {}
}
