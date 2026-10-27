package com.parsharec;

import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.pm.PackageInstaller;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Over-the-air updates: reads the newest GitHub release, downloads its APK, installs it with PackageInstaller. */
final class Updater {
    private Updater() {}

    static final String REPO = "abegumbo-ui/parsha-recorder";
    static final String ACTION_RESULT = "com.parsharec.INSTALL_RESULT";

    static final class Info {
        String version, notes, url;
        long size;
    }

    /** Returns the newest release if it is newer than {@code current}, otherwise null. */
    static Info check(String current) throws IOException {
        HttpURLConnection h = open("https://api.github.com/repos/" + REPO + "/releases/latest");
        if (h.getResponseCode() == 404) throw new IOException("No releases found (is the repository public?)");
        if (h.getResponseCode() != 200) throw new IOException("GitHub answered " + h.getResponseCode());
        try (InputStream in = h.getInputStream()) {
            JSONObject o = new JSONObject(new String(readAll(in), java.nio.charset.StandardCharsets.UTF_8));
            Info i = new Info();
            i.version = o.getString("tag_name").replaceFirst("^[vV]", "");
            i.notes = o.optString("body", "").trim();
            JSONArray assets = o.getJSONArray("assets");
            for (int k = 0; k < assets.length(); k++) {
                JSONObject a = assets.getJSONObject(k);
                if (a.getString("name").endsWith(".apk")) {
                    i.url = a.getString("browser_download_url");
                    i.size = a.optLong("size");
                    break;
                }
            }
            if (i.url == null || compare(i.version, current) <= 0) return null;
            return i;
        } catch (org.json.JSONException e) {
            throw new IOException("Unexpected reply from GitHub");
        }
    }

    static File download(Context c, Info i) throws IOException {
        File dir = c.getExternalCacheDir() != null ? c.getExternalCacheDir() : c.getCacheDir();
        File f = new File(dir, "update.apk");
        HttpURLConnection h = open(i.url);
        if (h.getResponseCode() != 200) throw new IOException("Download failed (" + h.getResponseCode() + ")");
        try (InputStream in = h.getInputStream(); OutputStream out = new FileOutputStream(f)) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return f;
    }

    /** Hands the APK to the system installer; the result arrives as a broadcast to {@link #ACTION_RESULT}. */
    static void install(Context c, File apk) throws IOException {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        int id = pi.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (OutputStream out = s.openWrite("update", 0, apk.length()); FileInputStream in = new FileInputStream(apk)) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            }
            Intent cb = new Intent(ACTION_RESULT).setPackage(c.getPackageName());
            PendingIntent p = PendingIntent.getBroadcast(c, id, cb, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            s.commit(p.getIntentSender());
        }
    }

    static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D.*", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(30000);
        h.setRequestProperty("User-Agent", "ParshaRecorder");
        h.setRequestProperty("Accept", "application/vnd.github+json");
        return h;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }
}
