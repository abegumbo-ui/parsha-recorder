package com.parsharec;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Keeps a visible copy of every recording at Music/Parsha Recorder/&lt;folder&gt;/ so it shows up in any file manager.
 * A "ref" is a content:// URI on Android 10+, or a plain file path on older versions.
 */
final class Mirror {
    private Mirror() {}

    static final String ROOT = "Parsha Recorder";

    static String clean(String s) {
        String t = s.replace("&", "and").replaceAll("[\\\\/:*?\"<>|']", "").replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? "Recording" : t;
    }

    private static String relPath(String folder) { return "Music/" + ROOT + "/" + clean(folder) + "/"; }

    static String create(Context c, File src, String folder, String name) throws IOException {
        String file = clean(name) + ".m4a";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = c.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, file);
            v.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, relPath(folder));
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("MediaStore insert failed");
            try (OutputStream os = cr.openOutputStream(uri); FileInputStream in = new FileInputStream(src)) {
                copy(in, os);
            } catch (IOException e) {
                cr.delete(uri, null, null);
                throw e;
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, done, null, null);
            return uri.toString();
        }
        File dir = new File(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), ROOT), clean(folder));
        dir.mkdirs();
        File dst = unique(new File(dir, file));
        try (OutputStream os = new java.io.FileOutputStream(dst); FileInputStream in = new FileInputStream(src)) {
            copy(in, os);
        }
        MediaScannerConnection.scanFile(c, new String[]{dst.getAbsolutePath()}, null, null);
        return dst.getAbsolutePath();
    }

    /** Applies a new name and/or folder to an existing copy. Returns the (possibly new) ref. */
    static String update(Context c, String ref, String folder, String name) {
        String file = clean(name) + ".m4a";
        try {
            if (ref.startsWith("content:")) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, file);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, relPath(folder));
                c.getContentResolver().update(Uri.parse(ref), v, null, null);
                return ref;
            }
            File old = new File(ref);
            File dir = new File(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), ROOT), clean(folder));
            dir.mkdirs();
            File dst = unique(new File(dir, file));
            if (old.renameTo(dst)) {
                MediaScannerConnection.scanFile(c, new String[]{old.getAbsolutePath(), dst.getAbsolutePath()}, null, null);
                return dst.getAbsolutePath();
            }
        } catch (RuntimeException ignored) {
            // The visible copy is a convenience; the app's own copy stays authoritative.
        }
        return ref;
    }

    static void delete(Context c, String ref) {
        try {
            if (ref.startsWith("content:")) c.getContentResolver().delete(Uri.parse(ref), null, null);
            else if (new File(ref).delete()) MediaScannerConnection.scanFile(c, new String[]{ref}, null, null);
        } catch (RuntimeException ignored) {}
    }

    private static File unique(File f) {
        if (!f.exists()) return f;
        String n = f.getName();
        String base = n.substring(0, n.length() - 4);
        for (int i = 2; ; i++) {
            File g = new File(f.getParentFile(), base + " (" + i + ").m4a");
            if (!g.exists()) return g;
        }
    }

    private static void copy(FileInputStream in, OutputStream os) throws IOException {
        byte[] buf = new byte[16384];
        int k;
        while ((k = in.read(buf)) > 0) os.write(buf, 0, k);
    }
}
