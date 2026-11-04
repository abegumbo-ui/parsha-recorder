package com.parsharec;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

/**
 * Ongoing notification with a red "record" button for each folder the user added (long-press a folder > Add to notification).
 * It also shows on the lock screen; with no folders added there is no notification.
 */
final class NotifBar {
    private NotifBar() {}

    static final int MAX = 4;
    private static final String CHANNEL = "record_bar";
    private static final int ID = 1;
    private static final int[] BUTTONS = {R.id.nb1, R.id.nb2, R.id.nb3, R.id.nb4};

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("prefs", Context.MODE_PRIVATE); }

    /** Folder keys in the order they were added; unknown keys are dropped. */
    static List<String> keys(Context c) {
        List<String> out = new ArrayList<>();
        String s = prefs(c).getString("notifKeys", "");
        for (String k : s.split(",")) if (!k.isEmpty() && Catalog.get(k) != null && !out.contains(k)) out.add(k);
        return out;
    }

    static boolean has(Context c, String key) { return keys(c).contains(key); }

    private static void save(Context c, List<String> keys) {
        prefs(c).edit().putString("notifKeys", String.join(",", keys)).apply();
    }

    /** Returns false when the bar is already full. */
    static boolean add(Context c, String key) {
        List<String> k = keys(c);
        if (k.contains(key)) return true;
        if (k.size() >= MAX) return false;
        k.add(key);
        save(c, k);
        return true;
    }

    static void remove(Context c, String key) {
        List<String> k = keys(c);
        k.remove(key);
        save(c, k);
    }

    static boolean allowed(Context c) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return false;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm == null || nm.areNotificationsEnabled();
    }

    /** Posts, updates or removes the notification so it matches the saved folders. Safe to call any time. */
    static void refresh(Context c) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm == null) return;
            List<String> keys = keys(c);
            if (keys.isEmpty() || !allowed(c)) { nm.cancel(ID); return; }

            NotificationChannel ch = new NotificationChannel(CHANNEL, "Quick record", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Red record buttons for the folders you added");
            ch.setShowBadge(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);

            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.notif_bar);
            for (int i = 0; i < BUTTONS.length; i++) {
                if (i >= keys.size()) { v.setViewVisibility(BUTTONS[i], android.view.View.GONE); continue; }
                String key = keys.get(i);
                v.setViewVisibility(BUTTONS[i], android.view.View.VISIBLE);
                v.setTextViewText(BUTTONS[i], "●  " + Catalog.get(key).en);
                Intent rec = new Intent("com.parsharec.RECORD_NOW").setClass(c, MainActivity.class)
                        .putExtra("folder", key)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                v.setOnClickPendingIntent(BUTTONS[i], PendingIntent.getActivity(c, key.hashCode(), rec,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            }
            Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            Notification n = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_rec)
                    .setColor(0xFFD93025)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setStyle(new Notification.DecoratedCustomViewStyle())
                    .setCustomContentView(v)
                    .setContentIntent(PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                    .build();
            nm.notify(ID, n);
        } catch (RuntimeException ignored) {
            // The shade button is a convenience; never let it take the app down.
        }
    }
}
