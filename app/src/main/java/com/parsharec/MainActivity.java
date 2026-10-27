package com.parsharec;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int C_BG = 0xFFF7F3EA, C_CARD = 0xFFFFFFFF, C_INK = 0xFF1E2A3A, C_SUB = 0xFF6B7685,
            C_ACCENT = 0xFF23406B, C_RED = 0xFFD93025, C_LINE = 0xFFE4DDCC, C_SOFT = 0xFFEAF0F8;
    private static final int REQ_MIC = 1, REQ_BACKUP = 2, REQ_RESTORE = 3, REQ_COPY = 4, REQ_STORAGE = 5;

    private enum Screen { HOME, CALENDAR, FOLDER, RECORDING, REVIEW }

    private Library lib;
    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private float density;

    private Screen screen = Screen.HOME;
    private boolean recordMode = true;      // home grid: tap = record (true) or open folder (false)
    private boolean israel;
    private String openFolder;              // folder being viewed / recorded into
    private Screen backTo = Screen.HOME;    // where Back from a folder returns
    private int calYear, calMonth;

    // recording
    private MediaRecorder recorder;
    private Library.Rec curRec;
    private long accumMs, segStart;
    private boolean paused;
    private Glyph pulse;
    private TextView timerView;
    private String pendingFolder;

    // playback
    private MediaPlayer player;
    private String playingKey;
    private boolean playerPaused;
    private SeekBar curSeek;
    private TextView curTime;

    // pending SAF actions
    private Library.Rec copyRec;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (screen == Screen.RECORDING && recorder != null) {
                if (timerView != null) timerView.setText(fmt(recordedMs()));
                if (pulse != null && !paused) {
                    float a = 0;
                    try { a = recorder.getMaxAmplitude() / 32768f; } catch (RuntimeException ignored) {}
                    float s = 1f + Math.min(a * 1.6f, 0.45f);
                    pulse.setScaleX(s);
                    pulse.setScaleY(s);
                }
            }
            if (player != null && playingKey != null && !playerPaused && curSeek != null) {
                try {
                    curSeek.setProgress(player.getCurrentPosition());
                    if (curTime != null) curTime.setText(fmt(player.getCurrentPosition()) + " / " + fmt(player.getDuration()));
                } catch (IllegalStateException ignored) {}
            }
            ui.postDelayed(this, 100);
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;
        lib = new Library(this);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        israel = prefs.getBoolean("israel", false);
        Calendar now = Calendar.getInstance();
        calYear = now.get(Calendar.YEAR);
        calMonth = now.get(Calendar.MONTH);

        root = new FrameLayout(this);
        root.setBackgroundColor(C_BG);
        root.setOnApplyWindowInsetsListener((v, in) -> {
            v.setPadding(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(), in.getSystemWindowInsetRight(),
                    in.getSystemWindowInsetBottom());
            return in;
        });
        setContentView(root);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        render();
        ui.post(tick);

        IntentFilter filt = new IntentFilter(Updater.ACTION_RESULT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(installResult, filt, RECEIVER_NOT_EXPORTED);
        else registerReceiver(installResult, filt);

        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        else syncMirrorsAsync();
        checkForUpdate(false);
        recordNowIfAsked(getIntent(), b == null);
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        density = getResources().getDisplayMetrics().density;
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A new day may have started (new week, new Parsha) while the app sat in the background.
        if (screen == Screen.HOME) render();
        RecordWidget.refreshAll(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Never lose a thought: if the app leaves the screen mid-recording or mid-review, keep what exists.
        if (screen == Screen.RECORDING && recorder != null) {
            if (finishRecording()) saveReview(null);
            else showHome();
        } else if (screen == Screen.REVIEW && curRec != null) {
            saveReview(null);
        }
        stopPlayback();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        stopPlayback();
        try { unregisterReceiver(installResult); } catch (RuntimeException ignored) {}
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        switch (screen) {
            case HOME: super.onBackPressed(); break;
            case CALENDAR: showHome(); break;
            case FOLDER: if (backTo == Screen.CALENDAR) showCalendar(); else showHome(); break;
            case RECORDING: if (finishRecording()) showReview(); else showHome(); break;
            case REVIEW: saveReview(null); break;
        }
    }

    private void render() {
        switch (screen) {
            case HOME: showHome(); break;
            case CALENDAR: showCalendar(); break;
            case FOLDER: showFolder(openFolder, backTo); break;
            case RECORDING: showRecording(); break;
            case REVIEW: showReview(); break;
        }
    }

    // ------------------------------------------------------------------ small ui helpers

    private int dp(float v) { return Math.round(v * density); }
    private float widthDp() { return getResources().getDisplayMetrics().widthPixels / density; }

    private void setScreen(View v) {
        root.removeAllViews();
        root.addView(v, new FrameLayout.LayoutParams(-1, -1));
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable shape(int fill, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private void press(View v, int fill, float radiusDp, int stroke) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), shape(fill, radiusDp, stroke), shape(0xFFFFFFFF, radiusDp, 0)));
    }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams lp(int w, int h, float weight, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private View iconButton(Glyph g, int sizeDp, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(this);
        f.addView(g, new FrameLayout.LayoutParams(dp(sizeDp * 0.62f), dp(sizeDp * 0.62f), Gravity.CENTER));
        f.setOnClickListener(l);
        press(f, 0x00000000, sizeDp / 2f, 0);
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return f;
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private String hebrewDate(Calendar c) {
        JewishCalendar jc = new JewishCalendar(c);
        return new HebrewDateFormatter().format(jc);
    }

    // ------------------------------------------------------------------ HOME

    private View header(boolean showTabs) {
        LinearLayout h = vbox();
        h.setPadding(dp(16), dp(10), dp(8), dp(6));

        LinearLayout row = hbox();
        TextView title = text("Parsha Recorder", 24, C_INK, true);
        row.addView(title, lp(0, -2, 1, 0, 0, 0, 0));
        row.addView(iconButton(new Glyph(this, Glyph.MORE, C_INK), 48, v -> showMenu(v)));
        h.addView(row);

        Calendar now = Calendar.getInstance();
        String d = DateFormat.getDateInstance(DateFormat.FULL).format(now.getTime()) + "  ·  " + hebrewDate(now);
        h.addView(text(d, 14, C_SUB, false), lp(-2, -2, 0, 0, 0, 8, 0));

        if (showTabs) {
            LinearLayout tabs = hbox();
            tabs.setPadding(0, dp(10), dp(8), dp(2));
            tabs.addView(tab("Record", screen == Screen.HOME && recordMode, v -> { recordMode = true; showHome(); }), lp(0, dp(46), 1, 0, 0, 6, 0));
            tabs.addView(tab("Files", screen == Screen.HOME && !recordMode, v -> { recordMode = false; showHome(); }), lp(0, dp(46), 1, 0, 0, 6, 0));
            tabs.addView(tab("Calendar", screen == Screen.CALENDAR, v -> showCalendar()), lp(0, dp(46), 1, 0, 0, 0, 0));
            h.addView(tabs);
        }
        return h;
    }

    private TextView tab(String label, boolean on, View.OnClickListener l) {
        TextView t = text(label, 16, on ? 0xFFFFFFFF : C_ACCENT, true);
        t.setGravity(Gravity.CENTER);
        press(t, on ? C_ACCENT : C_SOFT, 12, 0);
        t.setOnClickListener(l);
        return t;
    }

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Back up everything…");
        m.getMenu().add(0, 2, 1, "Restore from a backup…");
        m.getMenu().add(0, 3, 2, israel ? "Calendar: Israel (tap for Diaspora)" : "Calendar: Diaspora (tap for Israel)");
        m.getMenu().add(0, 6, 3, prefs.getBoolean("autoRecord", false)
                ? "Record as soon as the app opens: ON" : "Record as soon as the app opens: off");
        m.getMenu().add(0, 5, 4, "Check for updates");
        m.getMenu().add(0, 4, 5, lib.total() + " recordings  ·  v" + versionName()).setEnabled(false);
        m.setOnMenuItemClickListener(i -> {
            switch (i.getItemId()) {
                case 1: startBackup(); break;
                case 2: startRestore(); break;
                case 5: checkForUpdate(true); break;
                case 6:
                    prefs.edit().putBoolean("autoRecord", !prefs.getBoolean("autoRecord", false)).apply();
                    toast(prefs.getBoolean("autoRecord", false) ? "The app will start recording this week's Parsha as soon as it opens." : "Auto-record off.");
                    break;
                case 3:
                    israel = !israel;
                    prefs.edit().putBoolean("israel", israel).apply();
                    render();
                    break;
            }
            return true;
        });
        m.show();
    }

    private Set<String> pins() { return new HashSet<>(prefs.getStringSet("pins", new HashSet<String>())); }

    private void togglePin(String key) {
        Set<String> p = pins();
        if (!p.remove(key)) p.add(key);
        prefs.edit().putStringSet("pins", p).apply();
        showHome();
    }

    private void recordNowIfAsked(Intent i, boolean freshLaunch) {
        if (recorder != null || screen == Screen.REVIEW) return;
        boolean shortcut = i != null && "com.parsharec.RECORD_NOW".equals(i.getAction());
        if (shortcut || (freshLaunch && prefs.getBoolean("autoRecord", false))) startRecording(Catalog.currentKey(israel));
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        recordNowIfAsked(i, false);
    }

    private void showHome() {
        screen = Screen.HOME;
        stopPlayback();
        LinearLayout page = vbox();
        page.addView(header(true));

        ScrollView sv = new ScrollView(this);
        LinearLayout body = vbox();
        body.setPadding(dp(16), dp(8), dp(16), dp(32));
        sv.addView(body);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        Map<String, Integer> counts = lib.counts();
        Calendar today = Calendar.getInstance();

        // Pinned: this week's parsha, the nearest holiday, and anything the user pinned.
        String weekKey = null;
        Calendar probe = (Calendar) today.clone();
        for (int i = 0; i < 3 && weekKey == null; i++) {
            weekKey = Catalog.parshaKeyForWeek(probe, israel);
            probe.add(Calendar.DAY_OF_MONTH, 7);
        }
        Object[] hol = Catalog.upcomingHoliday(today, israel, 21);
        List<View> feature = new ArrayList<>();
        if (weekKey != null) feature.add(featureCard("THIS WEEK'S PARSHA", Catalog.get(weekKey), counts, null));
        if (hol != null) {
            int days = (Integer) hol[1];
            String when = days == 0 ? "HAPPENING NOW" : (days == 1 ? "STARTS TOMORROW" : "COMING UP IN " + days + " DAYS");
            feature.add(featureCard(when, Catalog.get((String) hol[0]), counts, null));
        }
        boolean wide = widthDp() >= 560 && feature.size() > 1;
        if (wide) {
            LinearLayout r = hbox();
            for (int i = 0; i < feature.size(); i++)
                r.addView(feature.get(i), lp(0, -2, 1, i == 0 ? 0 : 6, 0, i == feature.size() - 1 ? 0 : 6, 0));
            body.addView(r, lp(-1, -2, 0, 0, 4, 0, 6));
        } else {
            for (View f : feature) body.addView(f, lp(-1, -2, 0, 0, 4, 0, 8));
        }

        Set<String> pinSet = pins();
        List<Catalog.Folder> pinned = new ArrayList<>();
        for (Catalog.Folder f : Catalog.all()) if (pinSet.contains(f.key)) pinned.add(f);
        if (!pinned.isEmpty()) {
            body.addView(sectionTitle("Pinned"));
            body.addView(grid(pinned, counts));
        }

        List<Catalog.Folder> cur = new ArrayList<>();
        String curSection = null;
        for (Catalog.Folder f : Catalog.all()) {
            if (!f.section.equals(curSection)) {
                flushSection(body, curSection, cur, counts);
                cur = new ArrayList<>();
                curSection = f.section;
            }
            cur.add(f);
        }
        flushSection(body, curSection, cur, counts);
        setScreen(page);
    }

    private void flushSection(LinearLayout body, String section, List<Catalog.Folder> fs, Map<String, Integer> counts) {
        if (section == null || fs.isEmpty()) return;
        boolean book = java.util.Arrays.asList(Catalog.BOOKS).contains(section);
        body.addView(sectionTitle(book ? "Sefer " + section : section));
        body.addView(grid(fs, counts));
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 18, C_ACCENT, true);
        t.setPadding(dp(2), dp(18), 0, dp(8));
        return t;
    }

    private View grid(List<Catalog.Folder> fs, Map<String, Integer> counts) {
        int cols = Math.max(2, (int) ((widthDp() - 32) / 190));
        LinearLayout g = vbox();
        for (int i = 0; i < fs.size(); i += cols) {
            LinearLayout r = hbox();
            for (int j = 0; j < cols; j++) {
                int k = i + j;
                if (k < fs.size()) r.addView(tile(fs.get(k), counts), lp(0, -2, 1, j == 0 ? 0 : 4, 4, j == cols - 1 ? 0 : 4, 4));
                else r.addView(new View(this), lp(0, 1, 1, 4, 0, 4, 0));
            }
            g.addView(r);
        }
        return g;
    }

    private View tile(Catalog.Folder f, Map<String, Integer> counts) {
        LinearLayout t = hbox();
        t.setPadding(dp(14), dp(12), dp(10), dp(12));
        t.setMinimumHeight(dp(78));
        press(t, C_CARD, 16, C_LINE);

        LinearLayout names = vbox();
        TextView en = text(f.en, 16, C_INK, true);
        en.setMaxLines(2);
        names.addView(en);
        names.addView(text(f.he, 15, C_SUB, false));
        int n = counts.containsKey(f.key) ? counts.get(f.key) : 0;
        if (n > 0) names.addView(text(n + (n == 1 ? " recording" : " recordings"), 12, C_ACCENT, false));
        t.addView(names, lp(0, -2, 1, 0, 0, 6, 0));

        if (recordMode) t.addView(new Glyph(this, Glyph.REC, C_RED), lp(dp(40), dp(40), 0, 0, 0, 0, 0));
        else t.addView(new Glyph(this, Glyph.CHEVRON, C_SUB), lp(dp(22), dp(22), 0, 0, 0, 0, 0));

        t.setOnClickListener(v -> { if (recordMode) startRecording(f.key); else showFolder(f.key, Screen.HOME); });
        t.setOnLongClickListener(v -> {
            PopupMenu m = new PopupMenu(this, v);
            m.getMenu().add(0, 1, 0, "Open folder");
            m.getMenu().add(0, 2, 1, "Record here");
            m.getMenu().add(0, 3, 2, pins().contains(f.key) ? "Unpin from top" : "Pin to top");
            m.setOnMenuItemClickListener(i -> {
                if (i.getItemId() == 1) showFolder(f.key, Screen.HOME);
                else if (i.getItemId() == 2) startRecording(f.key);
                else togglePin(f.key);
                return true;
            });
            m.show();
            return true;
        });
        return t;
    }

    private View featureCard(String label, Catalog.Folder f, Map<String, Integer> counts, Object unused) {
        LinearLayout c = hbox();
        c.setPadding(dp(18), dp(16), dp(16), dp(16));
        press(c, C_ACCENT, 20, 0);

        LinearLayout names = vbox();
        names.addView(text(label, 12, 0xFFB9C8E0, true));
        TextView en = text(f.en, 28, 0xFFFFFFFF, true);
        en.setPadding(0, dp(2), 0, 0);
        names.addView(en);
        names.addView(text(f.he, 20, 0xFFE3EAF6, false));
        int n = counts.containsKey(f.key) ? counts.get(f.key) : 0;
        names.addView(text(n == 0 ? "No thoughts yet" : n + (n == 1 ? " recording" : " recordings"), 13, 0xFFB9C8E0, false));
        c.addView(names, lp(0, -2, 1, 0, 0, 8, 0));

        View action;
        if (recordMode) {
            LinearLayout b = vbox();
            b.setGravity(Gravity.CENTER);
            b.addView(new Glyph(this, Glyph.REC, C_RED), lp(dp(72), dp(72), 0, 0, 0, 0, 0));
            TextView lab = text("Record", 13, 0xFFFFFFFF, true);
            lab.setGravity(Gravity.CENTER);
            b.addView(lab, lp(-2, -2, 0, 0, 4, 0, 0));
            action = b;
        } else {
            action = new Glyph(this, Glyph.CHEVRON, 0xFFFFFFFF);
        }
        c.addView(action, recordMode ? lp(-2, -2, 0, 0, 0, 0, 0) : lp(dp(26), dp(26), 0, 0, 0, 0, 0));
        c.setOnClickListener(v -> { if (recordMode) startRecording(f.key); else showFolder(f.key, Screen.HOME); });
        c.setOnLongClickListener(v -> { showFolder(f.key, Screen.HOME); return true; });
        return c;
    }

    // ------------------------------------------------------------------ CALENDAR

    private void showCalendar() {
        screen = Screen.CALENDAR;
        stopPlayback();
        LinearLayout page = vbox();
        page.addView(header(true));

        Calendar first = Calendar.getInstance();
        first.clear();
        first.set(calYear, calMonth, 1, 12, 0, 0);
        Calendar mid = (Calendar) first.clone();
        mid.set(Calendar.DAY_OF_MONTH, 15);
        HebrewDateFormatter hf = new HebrewDateFormatter();
        String hebMonths = hf.formatMonth(new JewishCalendar(first)) + " – " + hf.formatMonth(new JewishCalendar(monthEnd(first)));
        if (hf.formatMonth(new JewishCalendar(first)).equals(hf.formatMonth(new JewishCalendar(monthEnd(first)))))
            hebMonths = hf.formatMonth(new JewishCalendar(first));

        LinearLayout nav = hbox();
        nav.setPadding(dp(8), dp(4), dp(8), dp(0));
        nav.addView(iconButton(new Glyph(this, Glyph.BACK, C_INK), 48, v -> shiftMonth(-1)));
        LinearLayout mid2 = vbox();
        mid2.setGravity(Gravity.CENTER_HORIZONTAL);
        mid2.addView(text(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(first.getTime()), 20, C_INK, true));
        mid2.addView(text(hebMonths, 13, C_SUB, false));
        nav.addView(mid2, lp(0, -2, 1, 0, 0, 0, 0));
        nav.addView(iconButton(new Glyph(this, Glyph.CHEVRON, C_INK), 48, v -> shiftMonth(1)));
        page.addView(nav);

        ScrollView sv = new ScrollView(this);
        LinearLayout body = vbox();
        body.setPadding(dp(10), dp(6), dp(10), dp(24));
        sv.addView(body);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        String[] dow = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Shabbat"};
        LinearLayout hd = hbox();
        for (String s : dow) {
            TextView t = text(s, 12, C_SUB, true);
            t.setGravity(Gravity.CENTER);
            hd.addView(t, lp(0, -2, 1, 0, 0, 0, 0));
        }
        body.addView(hd, lp(-1, -2, 0, 0, 4, 0, 4));

        Calendar day = (Calendar) first.clone();
        day.add(Calendar.DAY_OF_MONTH, -(first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY));
        Calendar today = Calendar.getInstance();
        HebrewDateFormatter hn = new HebrewDateFormatter();
        hn.setHebrewFormat(true);
        do {
            LinearLayout wk = hbox();
            for (int i = 0; i < 7; i++) {
                wk.addView(calCell(day, calMonth, today, hn), lp(0, dp(84), 1, 2, 2, 2, 2));
                day.add(Calendar.DAY_OF_MONTH, 1);
            }
            body.addView(wk);
        } while (day.get(Calendar.MONTH) == calMonth);
        setScreen(page);
    }

    private Calendar monthEnd(Calendar first) {
        Calendar e = (Calendar) first.clone();
        e.set(Calendar.DAY_OF_MONTH, e.getActualMaximum(Calendar.DAY_OF_MONTH));
        return e;
    }

    private void shiftMonth(int d) {
        calMonth += d;
        if (calMonth < 0) { calMonth = 11; calYear--; }
        if (calMonth > 11) { calMonth = 0; calYear++; }
        showCalendar();
    }

    private View calCell(Calendar day, int month, Calendar today, HebrewDateFormatter hn) {
        boolean inMonth = day.get(Calendar.MONTH) == month;
        boolean isToday = day.get(Calendar.YEAR) == today.get(Calendar.YEAR) && day.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
        String key = null;
        if (day.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) key = Catalog.parshaKeyOnShabbat(day, israel);
        String hk = Catalog.holidayKeyForDay(day, israel);
        if (key == null && hk != null) {
            Calendar prev = (Calendar) day.clone();
            prev.add(Calendar.DAY_OF_MONTH, -1);
            if (day.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || !hk.equals(Catalog.holidayKeyForDay(prev, israel))) key = hk;
        }
        LinearLayout c = vbox();
        c.setPadding(dp(5), dp(4), dp(3), dp(3));
        boolean shabbat = day.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY;
        press(c, isToday ? C_SOFT : (shabbat ? 0xFFFFFBF0 : C_CARD), 10, isToday ? C_ACCENT : C_LINE);
        c.setAlpha(inMonth ? 1f : 0.35f);
        c.addView(text(String.valueOf(day.get(Calendar.DAY_OF_MONTH)), 15, C_INK, true));
        c.addView(text(hn.formatHebrewNumber(new JewishCalendar(day).getJewishDayOfMonth()), 11, C_SUB, false));
        if (key != null) {
            TextView l = text(Catalog.title(key), 10.5f, C_ACCENT, true);
            l.setMaxLines(2);
            c.addView(l);
            final String fk = key;
            c.setOnClickListener(v -> showFolder(fk, Screen.CALENDAR));
        }
        return c;
    }

    // ------------------------------------------------------------------ FOLDER

    private void showFolder(String key, Screen from) {
        if (screen != Screen.FOLDER || !key.equals(openFolder)) stopPlayback();
        screen = Screen.FOLDER;
        openFolder = key;
        backTo = from;
        Catalog.Folder f = Catalog.get(key);

        LinearLayout page = vbox();
        LinearLayout bar = hbox();
        bar.setPadding(dp(8), dp(8), dp(12), dp(4));
        bar.addView(iconButton(new Glyph(this, Glyph.BACK, C_INK), 52, v -> onBackPressed()));
        LinearLayout names = vbox();
        names.addView(text(f.en, 22, C_INK, true));
        names.addView(text(f.he, 16, C_SUB, false));
        bar.addView(names, lp(0, -2, 1, 4, 0, 0, 0));
        page.addView(bar);

        TextView rec = text("●  Record a thought here", 17, 0xFFFFFFFF, true);
        rec.setGravity(Gravity.CENTER);
        press(rec, C_RED, 16, 0);
        rec.setOnClickListener(v -> startRecording(key));
        page.addView(rec, lp(-1, dp(56), 0, 16, 6, 16, 8));

        ScrollView sv = new ScrollView(this);
        LinearLayout list = vbox();
        list.setPadding(dp(16), dp(4), dp(16), dp(32));
        sv.addView(list);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        List<Library.Rec> recs = lib.inFolder(key);
        if (recs.isEmpty()) {
            TextView e = text("Nothing recorded here yet.\nTap the red button when a thought comes.", 16, C_SUB, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(40), 0, 0);
            list.addView(e);
        }
        for (Library.Rec r : recs) list.addView(recRow(r), lp(-1, -2, 0, 0, 5, 0, 5));
        setScreen(page);
    }

    private View recRow(Library.Rec r) {
        boolean mine = r.id.equals(playingKey);
        LinearLayout card = vbox();
        card.setPadding(dp(8), dp(8), dp(4), dp(8));
        press(card, C_CARD, 16, mine ? C_ACCENT : C_LINE);

        LinearLayout top = hbox();
        boolean playing = mine && !playerPaused;
        FrameLayout play = new FrameLayout(this);
        play.addView(new Glyph(this, playing ? Glyph.PAUSE : Glyph.PLAY, 0xFFFFFFFF), new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        press(play, C_ACCENT, 26, 0);
        play.setOnClickListener(v -> togglePlay(r.id, lib.audioFile(r.id), () -> showFolder(openFolder, backTo)));
        top.addView(play, lp(dp(52), dp(52), 0, 4, 0, 10, 0));

        LinearLayout info = vbox();
        TextView t = text(titleOf(r), 16, C_INK, true);
        t.setMaxLines(2);
        info.addView(t);
        info.addView(text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(r.createdMs)) + "  ·  " + fmt(r.durationMs), 13, C_SUB, false));
        top.addView(info, lp(0, -2, 1, 0, 0, 4, 0));
        top.addView(iconButton(new Glyph(this, Glyph.MORE, C_SUB), 44, v -> rowMenu(v, r)));
        card.addView(top);

        if (mine) {
            LinearLayout pr = hbox();
            pr.setPadding(dp(8), dp(4), dp(8), 0);
            SeekBar sb = new SeekBar(this);
            sb.setMax(Math.max(1, player.getDuration()));
            sb.setProgress(player.getCurrentPosition());
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean user) { if (user && player != null) player.seekTo(p); }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
            TextView tm = text(fmt(player.getCurrentPosition()) + " / " + fmt(player.getDuration()), 12, C_SUB, false);
            pr.addView(sb, lp(0, -2, 1, 0, 0, 8, 0));
            pr.addView(tm);
            card.addView(pr);
            curSeek = sb;
            curTime = tm;
        }
        return card;
    }

    private String titleOf(Library.Rec r) {
        if (r.title != null && !r.title.trim().isEmpty()) return r.title;
        return new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(new Date(r.createdMs));
    }

    private void rowMenu(View anchor, Library.Rec r) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Rename");
        m.getMenu().add(0, 2, 1, "Move to another folder");
        m.getMenu().add(0, 3, 2, "Save a copy…");
        m.getMenu().add(0, 4, 3, "Delete");
        m.setOnMenuItemClickListener(i -> {
            switch (i.getItemId()) {
                case 1: askText("Rename", titleOf(r), s -> { lib.rename(r, s); showFolder(openFolder, backTo); }); break;
                case 2: pickFolder("Move to…", k -> { lib.move(r, k); stopPlayback(); showFolder(openFolder, backTo); toast("Moved to " + Catalog.title(k)); }); break;
                case 3: startCopy(r); break;
                case 4:
                    new AlertDialog.Builder(this).setTitle("Delete this recording?").setMessage(titleOf(r))
                            .setPositiveButton("Delete", (d, w) -> { stopPlayback(); lib.delete(r); showFolder(openFolder, backTo); })
                            .setNegativeButton("Keep", null).show();
                    break;
            }
            return true;
        });
        m.show();
    }

    private interface Str { void got(String s); }

    private void askText(String title, String initial, Str done) {
        EditText e = new EditText(this);
        e.setText(initial);
        e.setSelectAllOnFocus(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        FrameLayout f = new FrameLayout(this);
        f.setPadding(dp(20), dp(8), dp(20), 0);
        f.addView(e);
        new AlertDialog.Builder(this).setTitle(title).setView(f)
                .setPositiveButton("OK", (d, w) -> done.got(e.getText().toString().trim()))
                .setNegativeButton("Cancel", null).show();
    }

    private void pickFolder(String title, Str done) {
        List<Catalog.Folder> all = Catalog.all();
        String[] names = new String[all.size()];
        for (int i = 0; i < names.length; i++) names[i] = all.get(i).en + "   " + all.get(i).he;
        new AlertDialog.Builder(this).setTitle(title)
                .setItems(names, (d, w) -> done.got(all.get(w).key)).setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------------ RECORDING

    private void startRecording(String folderKey) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingFolder = folderKey;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        stopPlayback();
        openFolder = folderKey;
        curRec = lib.newRec(folderKey);
        try {
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setOutputFile(lib.audioFile(curRec.id).getAbsolutePath());
            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            toast("Could not start recording: " + e.getMessage());
            releaseRecorder();
            lib.audioFile(curRec.id).delete();
            curRec = null;
            return;
        }
        accumMs = 0;
        segStart = SystemClock.elapsedRealtime();
        paused = false;
        screen = Screen.RECORDING;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        showRecording();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == REQ_STORAGE) syncMirrorsAsync();
        if (code == REQ_MIC) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED && pendingFolder != null) startRecording(pendingFolder);
            else toast("Microphone permission is needed to record.");
        }
    }

    private long recordedMs() { return accumMs + (paused ? 0 : SystemClock.elapsedRealtime() - segStart); }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.release(); } catch (RuntimeException ignored) {}
            recorder = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /** Stops the recorder. Returns false (and deletes the file) if nothing usable was captured. */
    private boolean finishRecording() {
        long ms = recordedMs();
        boolean ok = true;
        try { recorder.stop(); } catch (RuntimeException e) { ok = false; }
        releaseRecorder();
        if (!ok || ms < 500) {
            if (curRec != null) lib.audioFile(curRec.id).delete();
            curRec = null;
            if (!ok || ms < 500) toast("That was too short to keep.");
            return false;
        }
        curRec.durationMs = ms;
        curRec.createdMs = System.currentTimeMillis();
        return true;
    }

    private void showRecording() {
        screen = Screen.RECORDING;
        Catalog.Folder f = Catalog.get(curRec.folder);
        LinearLayout page = vbox();
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(24), dp(24), dp(24), dp(24));

        page.addView(text("RECORDING INTO", 12, C_SUB, true), lp(-2, -2, 0, 0, 8, 0, 0));
        page.addView(text(f.en, 30, C_INK, true));
        page.addView(text(f.he, 20, C_SUB, false));

        View spacer = new View(this);
        page.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        FrameLayout dotBox = new FrameLayout(this);
        pulse = new Glyph(this, Glyph.REC, paused ? C_SUB : C_RED);
        dotBox.addView(pulse, new FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER));
        page.addView(dotBox, lp(dp(190), dp(190), 0, 0, 0, 0, 0));

        timerView = text(fmt(recordedMs()), 56, C_INK, true);
        timerView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        page.addView(timerView, lp(-2, -2, 0, 0, 8, 0, 0));
        page.addView(text(paused ? "Paused" : "Listening…", 16, C_SUB, false));

        View spacer2 = new View(this);
        page.addView(spacer2, new LinearLayout.LayoutParams(1, 0, 1));

        LinearLayout btns = hbox();
        btns.setGravity(Gravity.CENTER);
        TextView pause = bigButton(paused ? Glyph.PLAY : Glyph.PAUSE, paused ? "Resume" : "Pause", C_SOFT, C_ACCENT);
        pause.setOnClickListener(v -> togglePauseRecording());
        TextView stop = bigButton(Glyph.STOP, "Finish", C_RED, 0xFFFFFFFF);
        stop.setOnClickListener(v -> { if (finishRecording()) showReview(); else showHome(); });
        btns.addView(pause, lp(dp(150), dp(64), 0, 8, 0, 8, 0));
        btns.addView(stop, lp(dp(150), dp(64), 0, 8, 0, 8, 0));
        page.addView(btns);

        TextView cancel = text("Throw away", 15, C_SUB, false);
        cancel.setPadding(dp(16), dp(14), dp(16), dp(8));
        cancel.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Throw this recording away?")
                .setPositiveButton("Throw away", (d, w) -> {
                    try { recorder.stop(); } catch (RuntimeException ignored) {}
                    releaseRecorder();
                    lib.audioFile(curRec.id).delete();
                    curRec = null;
                    showHome();
                }).setNegativeButton("Keep recording", null).show());
        page.addView(cancel);
        setScreen(page);
    }

    /** A pill button with a drawn icon and a label (the icon is a View, so wrap in a TextView host). */
    private TextView bigButton(int glyph, String label, int fill, int fg) {
        TextView t = text((glyph == Glyph.PAUSE ? "❚❚  " : glyph == Glyph.PLAY ? "▶  " : "■  ") + label, 18, fg, true);
        t.setGravity(Gravity.CENTER);
        press(t, fill, 32, 0);
        return t;
    }

    private void togglePauseRecording() {
        try {
            if (paused) {
                recorder.resume();
                segStart = SystemClock.elapsedRealtime();
                paused = false;
            } else {
                recorder.pause();
                accumMs += SystemClock.elapsedRealtime() - segStart;
                paused = true;
            }
        } catch (RuntimeException e) {
            toast("Could not pause: " + e.getMessage());
        }
        showRecording();
    }

    // ------------------------------------------------------------------ REVIEW

    private EditText titleEdit;

    private void showReview() {
        screen = Screen.REVIEW;
        Catalog.Folder f = Catalog.get(curRec.folder);
        String keepTitle = titleEdit != null && titleEdit.getParent() != null ? titleEdit.getText().toString() : null;
        LinearLayout page = vbox();
        page.setPadding(dp(24), dp(24), dp(24), dp(24));

        page.addView(text("SAVE TO", 12, C_SUB, true));
        TextView dest = text(f.en + "   " + f.he, 22, C_INK, true);
        page.addView(dest);
        TextView change = text("Change folder", 15, C_ACCENT, true);
        change.setPadding(0, dp(6), 0, dp(14));
        change.setOnClickListener(v -> {
            String t = titleEdit.getText().toString();
            pickFolder("Save to…", k -> { curRec.folder = k; titleEdit.setText(t); showReview(); titleEdit.setText(t); });
        });
        page.addView(change);

        titleEdit = new EditText(this);
        titleEdit.setHint("Title (optional)");
        titleEdit.setText(keepTitle != null ? keepTitle : new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(new Date(curRec.createdMs)));
        titleEdit.setSingleLine(true);
        titleEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        page.addView(titleEdit, lp(-1, -2, 0, 0, 0, 0, 14));

        LinearLayout listen = vbox();
        listen.setPadding(dp(14), dp(12), dp(14), dp(12));
        press(listen, C_CARD, 16, C_LINE);
        LinearLayout lr = hbox();
        boolean playing = curRec.id.equals(playingKey) && !playerPaused;
        FrameLayout play = new FrameLayout(this);
        play.addView(new Glyph(this, playing ? Glyph.PAUSE : Glyph.PLAY, 0xFFFFFFFF), new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
        press(play, C_ACCENT, 30, 0);
        play.setOnClickListener(v -> togglePlay(curRec.id, lib.audioFile(curRec.id), this::showReview));
        lr.addView(play, lp(dp(60), dp(60), 0, 0, 0, 12, 0));
        LinearLayout li = vbox();
        li.addView(text("Listen first", 17, C_INK, true));
        TextView tm = text(fmt(curRec.durationMs) + " recorded", 13, C_SUB, false);
        li.addView(tm);
        lr.addView(li, lp(0, -2, 1, 0, 0, 0, 0));
        listen.addView(lr);
        if (curRec.id.equals(playingKey) && player != null) {
            SeekBar sb = new SeekBar(this);
            sb.setMax(Math.max(1, player.getDuration()));
            sb.setProgress(player.getCurrentPosition());
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean user) { if (user && player != null) player.seekTo(p); }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
            listen.addView(sb, lp(-1, -2, 0, 0, 8, 0, 0));
            curSeek = sb;
            curTime = tm;
        }
        page.addView(listen);

        View spacer = new View(this);
        page.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        TextView save = text("Save", 20, 0xFFFFFFFF, true);
        save.setGravity(Gravity.CENTER);
        press(save, C_ACCENT, 18, 0);
        save.setOnClickListener(v -> saveReview(titleEdit.getText().toString().trim()));
        page.addView(save, lp(-1, dp(60), 0, 0, 0, 0, 10));

        TextView discard = text("Discard", 17, C_RED, true);
        discard.setGravity(Gravity.CENTER);
        press(discard, 0x00000000, 18, C_RED);
        discard.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Discard this recording?")
                .setPositiveButton("Discard", (d, w) -> {
                    stopPlayback();
                    lib.audioFile(curRec.id).delete();
                    curRec = null;
                    titleEdit = null;
                    showHome();
                }).setNegativeButton("Keep", null).show());
        page.addView(discard, lp(-1, dp(52), 0, 0, 0, 0, 0));
        setScreen(page);
    }

    /** Saves the pending recording; a null title means "default". Then returns to the home screen. */
    private void saveReview(String title) {
        if (curRec == null) return;
        stopPlayback();
        if (title == null && titleEdit != null && titleEdit.getParent() != null) title = titleEdit.getText().toString().trim();
        curRec.title = title == null ? "" : title;
        lib.commit(curRec);
        syncMirrorsAsync();
        toast("Saved to " + Catalog.title(curRec.folder));
        curRec = null;
        titleEdit = null;
        showHome();
    }

    // ------------------------------------------------------------------ playback

    private void togglePlay(String key, File file, Runnable refresh) {
        try {
            if (key.equals(playingKey) && player != null) {
                if (playerPaused) { player.start(); playerPaused = false; } else { player.pause(); playerPaused = true; }
            } else {
                stopPlayback();
                player = new MediaPlayer();
                player.setDataSource(file.getAbsolutePath());
                player.prepare();
                player.setOnCompletionListener(mp -> { stopPlayback(); refresh.run(); });
                player.start();
                playingKey = key;
                playerPaused = false;
            }
        } catch (Exception e) {
            toast("Could not play this recording.");
            stopPlayback();
        }
        refresh.run();
    }

    private void stopPlayback() {
        if (player != null) {
            try { player.release(); } catch (RuntimeException ignored) {}
            player = null;
        }
        playingKey = null;
        playerPaused = false;
        curSeek = null;
        curTime = null;
    }

    // ------------------------------------------------------------------ backup / restore / copy

    private void startBackup() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE, "ParshaRecorder-backup-" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".zip");
        startActivityForResult(i, REQ_BACKUP);
    }

    private void startRestore() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        startActivityForResult(i, REQ_RESTORE);
    }

    private void startCopy(Library.Rec r) {
        copyRec = r;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/mp4");
        i.putExtra(Intent.EXTRA_TITLE, titleOf(r).replaceAll("[\\\\/:*?\"<>|]", "-") + ".m4a");
        startActivityForResult(i, REQ_COPY);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_BACKUP) {
            toast("Backing up…");
            new Thread(() -> {
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    int n = lib.backup(os);
                    ui.post(() -> toast("Backed up " + n + " recordings."));
                } catch (Exception e) {
                    ui.post(() -> toast("Backup failed: " + e.getMessage()));
                }
            }).start();
        } else if (req == REQ_RESTORE) {
            toast("Restoring…");
            new Thread(() -> {
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    int n = lib.restore(is);
                    lib.syncMirrors();
                    ui.post(() -> { toast(n == 0 ? "Nothing new to restore." : "Restored " + n + " recordings."); render(); });
                } catch (Exception e) {
                    ui.post(() -> toast("Restore failed: " + e.getMessage()));
                }
            }).start();
        } else if (req == REQ_COPY && copyRec != null) {
            final Library.Rec r = copyRec;
            new Thread(() -> {
                try (OutputStream os = getContentResolver().openOutputStream(uri); InputStream in = new FileInputStream(lib.audioFile(r.id))) {
                    byte[] buf = new byte[16384];
                    int k;
                    while ((k = in.read(buf)) > 0) os.write(buf, 0, k);
                    ui.post(() -> toast("Saved a copy."));
                } catch (Exception e) {
                    ui.post(() -> toast("Could not save: " + e.getMessage()));
                }
            }).start();
        }
    }

    // ------------------------------------------------------------------ file-manager copy + updates

    private void syncMirrorsAsync() { new Thread(lib::syncMirrors).start(); }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    private final BroadcastReceiver installResult = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) startActivity(confirm);
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                toast("Update did not install: " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            }
        }
    };

    /** Looks for a newer GitHub release. {@code manual} also reports "up to date" and errors. */
    private void checkForUpdate(boolean manual) {
        if (manual) toast("Checking for updates…");
        new Thread(() -> {
            try {
                Updater.Info info = Updater.check(versionName());
                ui.post(() -> {
                    if (info == null) { if (manual) toast("You have the latest version (" + versionName() + ")."); }
                    else offerUpdate(info);
                });
            } catch (Exception e) {
                if (manual) ui.post(() -> toast("Could not check for updates: " + e.getMessage()));
            }
        }).start();
    }

    private void offerUpdate(Updater.Info info) {
        if (isFinishing()) return;
        String notes = info.notes.isEmpty() ? "" : "\n\n" + info.notes;
        new AlertDialog.Builder(this).setTitle("Update available: " + info.version)
                .setMessage("You have " + versionName() + "." + notes)
                .setPositiveButton("Update now", (d, w) -> {
                    toast("Downloading…");
                    new Thread(() -> {
                        try {
                            File apk = Updater.download(this, info);
                            ui.post(() -> {
                                try { Updater.install(this, apk); } catch (Exception e) { toast("Could not start the install: " + e.getMessage()); }
                            });
                        } catch (Exception e) {
                            ui.post(() -> toast("Download failed: " + e.getMessage()));
                        }
                    }).start();
                })
                .setNegativeButton("Later", null).show();
    }
}