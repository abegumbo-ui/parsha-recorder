package com.parsharec;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
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
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class MainActivity extends Activity {
    private static final int C_BG = 0xFFF7F3EA, C_CARD = 0xFFFFFFFF, C_INK = 0xFF1E2A3A, C_SUB = 0xFF6B7685,
            C_ACCENT = 0xFF23406B, C_RED = 0xFFD93025, C_LINE = 0xFFE4DDCC, C_SOFT = 0xFFEAF0F8;
    private static final int REQ_MIC = 1, REQ_BACKUP = 2, REQ_RESTORE = 3, REQ_COPY = 4, REQ_STORAGE = 5;

    private enum Screen { HOME, CALENDAR, CATEGORY, MASECHTA, FOLDER, RECORDING, REVIEW }

    /** A place in the app: a screen plus what it shows (category id, masechta index or folder key). */
    private static final class Dest {
        final Screen s;
        final String arg;
        Dest(Screen s, String arg) { this.s = s; this.arg = arg; }
    }

    /** One tile in a grid. */
    private static final class Tile {
        String key, en, he;
        int count;
        boolean compact, highlight;
        View.OnClickListener click;
        View.OnLongClickListener longClick;
    }

    private static final class TileRow {
        final Tile[] tiles;
        final int cols;
        TileRow(Tile[] t, int c) { tiles = t; cols = c; }
    }

    private Library lib;
    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private float density;

    private Dest cur = new Dest(Screen.HOME, null);
    private Dest origin = new Dest(Screen.HOME, null);   // where to return after a recording
    private final ArrayDeque<Dest> backStack = new ArrayDeque<>();
    private boolean israel;
    private boolean filesOpen;                            // folder screen: recordings list shown
    private int calYear, calMonth;

    private Rows folderRows;

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

    private Library.Rec copyRec;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try {
                if (cur.s == Screen.RECORDING && recorder != null) {
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
                    int pos = player.getCurrentPosition();
                    curSeek.setProgress(pos);
                    if (curTime != null) curTime.setText(fmt(pos) + " / " + fmt(player.getDuration()));
                }
            } catch (RuntimeException ignored) {
                // A ticker hiccup (player released mid-tick, view gone) must never take the app down.
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
        if (cur.s == Screen.HOME) render();
        RecordWidget.refreshAll(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Never lose a thought: if the app leaves the screen mid-recording or mid-review, keep what exists.
        try {
            if (cur.s == Screen.RECORDING && recorder != null) {
                if (finishRecording()) saveReview(null);
                else leaveRecording();
            } else if (cur.s == Screen.REVIEW && curRec != null) {
                saveReview(null);
            }
        } catch (RuntimeException e) {
            toast("Something went wrong saving; your earlier recordings are safe.");
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
        switch (cur.s) {
            case HOME: super.onBackPressed(); break;
            case RECORDING: if (finishRecording()) showReview(); else leaveRecording(); break;
            case REVIEW: saveReview(null); break;
            default:
                if (!backStack.isEmpty()) { cur = backStack.pop(); render(); }
                else goRoot(Screen.HOME, null);
        }
    }

    // ------------------------------------------------------------------ navigation

    private void go(Screen s, String arg) {
        backStack.push(cur);
        while (backStack.size() > 40) backStack.removeLast();
        cur = new Dest(s, arg);
        if (s == Screen.FOLDER) filesOpen = false;
        stopPlayback();
        render();
    }

    private void goRoot(Screen s, String arg) {
        backStack.clear();
        cur = new Dest(s, arg);
        stopPlayback();
        render();
    }

    /** Redraws the current screen. Any failure falls back to the home screen instead of crashing the app. */
    private void render() {
        try {
            renderUnsafe();
        } catch (RuntimeException e) {
            try {
                backStack.clear();
                cur = new Dest(Screen.HOME, null);
                showHome();
                toast("Something went wrong, so you were taken home.");
            } catch (RuntimeException e2) {
                TextView t = text("Parsha Recorder hit a problem.\nClose the app and open it again.\nYour recordings are safe.", 18, C_INK, false);
                t.setGravity(Gravity.CENTER);
                setScreen(t);
            }
        }
    }

    private void renderUnsafe() {
        switch (cur.s) {
            case HOME: showHome(); break;
            case CALENDAR: showCalendar(); break;
            case CATEGORY: showCategory(cur.arg); break;
            case MASECHTA: showMasechta(Integer.parseInt(cur.arg)); break;
            case FOLDER: showFolder(cur.arg); break;
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

    private GradientDrawable shape(int fill, float radiusDp, int stroke, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(strokeDp)), stroke);
        return g;
    }

    private void press(View v, int fill, float radiusDp, int stroke) { press(v, fill, radiusDp, stroke, 1); }

    private void press(View v, int fill, float radiusDp, int stroke, float strokeDp) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), shape(fill, radiusDp, stroke, strokeDp),
                shape(0xFFFFFFFF, radiusDp, 0, 0)));
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
        return new HebrewDateFormatter().format(new JewishCalendar(c));
    }

    /** A virtualized list: only the rows on screen are ever built, however long the data is. */
    private final class Rows extends BaseAdapter {
        List<Object> items;
        final Function<Object, View> factory;
        Rows(List<Object> items, Function<Object, View> factory) { this.items = items; this.factory = factory; }
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public boolean isEnabled(int i) { return false; }
        @Override public View getView(int i, View cv, ViewGroup parent) {
            try {
                return factory.apply(items.get(i));
            } catch (RuntimeException e) {
                return new View(MainActivity.this);   // one bad row must not break the whole list
            }
        }
    }

    private ListView listView(Rows rows) {
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        lv.setClipToPadding(false);
        lv.setPadding(dp(16), dp(4), dp(16), dp(32));
        lv.setVerticalScrollBarEnabled(false);
        lv.setAdapter(rows);
        return lv;
    }

    // ------------------------------------------------------------------ tiles & rows

    private Tile tile(String key, String en, String he, int count, boolean compact, View.OnClickListener click) {
        Tile t = new Tile();
        t.key = key; t.en = en; t.he = he; t.count = count; t.compact = compact; t.click = click;
        return t;
    }

    private void pinnable(Tile t) {
        t.longClick = v -> {
            PopupMenu m = new PopupMenu(this, v);
            m.getMenu().add(0, 1, 0, pins().contains(t.key) ? "Unpin from home" : "Pin to home");
            m.setOnMenuItemClickListener(i -> { togglePin(t.key); return true; });
            m.show();
            return true;
        };
    }

    private int colsFor(boolean compact) {
        return compact ? Math.max(4, (int) ((widthDp() - 32) / 84)) : Math.max(2, (int) ((widthDp() - 32) / 190));
    }

    private void chunk(List<Object> out, List<Tile> tiles, boolean compact) {
        int cols = colsFor(compact);
        for (int i = 0; i < tiles.size(); i += cols)
            out.add(new TileRow(tiles.subList(i, Math.min(tiles.size(), i + cols)).toArray(new Tile[0]), cols));
    }

    private View rowView(TileRow r) {
        LinearLayout row = hbox();
        for (int j = 0; j < r.cols; j++) {
            if (j < r.tiles.length) row.addView(tileView(r.tiles[j]), lp(0, -2, 1, j == 0 ? 0 : 4, 4, j == r.cols - 1 ? 0 : 4, 4));
            else row.addView(new View(this), lp(0, 1, 1, 4, 0, 4, 0));
        }
        return row;
    }

    private View headerRow(String s) {
        TextView t = text(s, 18, C_ACCENT, true);
        t.setPadding(dp(2), dp(18), 0, dp(6));
        return t;
    }

    private View tileView(Tile t) {
        if (t.compact) {
            LinearLayout v = vbox();
            v.setGravity(Gravity.CENTER);
            v.setPadding(dp(4), dp(10), dp(4), dp(10));
            v.setMinimumHeight(dp(68));
            press(v, t.count > 0 ? C_SOFT : C_CARD, 14, t.count > 0 ? C_ACCENT : C_LINE);
            TextView n = text(t.en, 20, C_INK, true);
            n.setGravity(Gravity.CENTER);
            v.addView(n);
            TextView h = text(t.he, 12, C_SUB, false);
            h.setGravity(Gravity.CENTER);
            v.addView(h);
            if (t.count > 0) {
                TextView c = text("● " + t.count, 11, C_RED, true);
                c.setGravity(Gravity.CENTER);
                v.addView(c);
            }
            v.setOnClickListener(t.click);
            v.setOnLongClickListener(t.longClick);
            return v;
        }
        LinearLayout v = hbox();
        v.setPadding(dp(14), dp(12), dp(10), dp(12));
        v.setMinimumHeight(dp(78));
        press(v, C_CARD, 16, t.highlight ? C_ACCENT : C_LINE, t.highlight ? 2.5f : 1);
        LinearLayout names = vbox();
        TextView en = text(t.en, 16, C_INK, true);
        en.setMaxLines(2);
        names.addView(en);
        if (t.he != null && !t.he.isEmpty()) names.addView(text(t.he, 15, C_SUB, false));
        if (t.count > 0) names.addView(text(t.count + (t.count == 1 ? " recording" : " recordings"), 12, C_ACCENT, false));
        v.addView(names, lp(0, -2, 1, 0, 0, 6, 0));
        v.addView(new Glyph(this, Glyph.CHEVRON, C_SUB), lp(dp(22), dp(22), 0, 0, 0, 0, 0));
        v.setOnClickListener(t.click);
        v.setOnLongClickListener(t.longClick);
        return v;
    }

    private Function<Object, View> rowFactory() {
        return o -> o instanceof String ? headerRow((String) o) : rowView((TileRow) o);
    }

    // ------------------------------------------------------------------ HOME

    private View header(boolean showTabs) {
        LinearLayout h = vbox();
        h.setPadding(dp(16), dp(10), dp(8), dp(6));

        LinearLayout row = hbox();
        row.addView(text("Parsha Recorder", 24, C_INK, true), lp(0, -2, 1, 0, 0, 0, 0));
        row.addView(iconButton(new Glyph(this, Glyph.MORE, C_INK), 48, v -> showMenu(v)));
        h.addView(row);

        Calendar now = Calendar.getInstance();
        String d = DateFormat.getDateInstance(DateFormat.FULL).format(now.getTime()) + "  ·  " + hebrewDate(now);
        h.addView(text(d, 14, C_SUB, false), lp(-2, -2, 0, 0, 0, 8, 0));

        if (showTabs) {
            LinearLayout tabs = hbox();
            tabs.setPadding(0, dp(10), dp(8), dp(2));
            tabs.addView(tab("Browse", cur.s != Screen.CALENDAR, v -> goRoot(Screen.HOME, null)), lp(0, dp(46), 1, 0, 0, 6, 0));
            tabs.addView(tab("Calendar", cur.s == Screen.CALENDAR, v -> goRoot(Screen.CALENDAR, null)), lp(0, dp(46), 1, 0, 0, 0, 0));
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

    private View backBar(String title, String sub, String crumb) {
        LinearLayout bar = hbox();
        bar.setPadding(dp(8), dp(8), dp(12), dp(4));
        bar.addView(iconButton(new Glyph(this, Glyph.BACK, C_INK), 52, v -> onBackPressed()));
        LinearLayout names = vbox();
        if (crumb != null) names.addView(text(crumb, 12, C_SUB, true));
        names.addView(text(title, 22, C_INK, true));
        if (sub != null && !sub.isEmpty()) names.addView(text(sub, 16, C_SUB, false));
        bar.addView(names, lp(0, -2, 1, 4, 0, 0, 0));
        return bar;
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
                case 3:
                    israel = !israel;
                    prefs.edit().putBoolean("israel", israel).apply();
                    render();
                    break;
                case 5: checkForUpdate(true); break;
                case 6:
                    prefs.edit().putBoolean("autoRecord", !prefs.getBoolean("autoRecord", false)).apply();
                    toast(prefs.getBoolean("autoRecord", false) ? "The app will start recording this week's Parsha as soon as it opens." : "Auto-record off.");
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
        render();
    }

    /** Total recordings whose folder key starts with {@code prefix}. */
    private int total(Map<String, Integer> counts, String prefix) {
        int n = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) if (e.getKey().startsWith(prefix)) n += e.getValue();
        return n;
    }

    private void showHome() {
        cur = new Dest(Screen.HOME, null);
        LinearLayout page = vbox();
        page.addView(header(true));

        ScrollView sv = new ScrollView(this);
        LinearLayout body = vbox();
        body.setPadding(dp(16), dp(8), dp(16), dp(32));
        sv.addView(body);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        Map<String, Integer> counts = lib.counts();
        Calendar today = Calendar.getInstance();

        // Quick record cards: this week's Parsha, the nearest Yom Tov, today's Daf Yomi.
        List<View> feature = new ArrayList<>();
        String weekKey = null;
        Calendar probe = (Calendar) today.clone();
        for (int i = 0; i < 3 && weekKey == null; i++) {
            weekKey = Catalog.parshaKeyForWeek(probe, israel);
            probe.add(Calendar.DAY_OF_MONTH, 7);
        }
        if (weekKey != null) feature.add(featureCard("THIS WEEK'S PARSHA", weekKey, counts));
        Object[] hol = Catalog.upcomingHoliday(today, israel, 21);
        if (hol != null) {
            int days = (Integer) hol[1];
            String when = days == 0 ? "HAPPENING NOW" : (days == 1 ? "STARTS TOMORROW" : "COMING UP IN " + days + " DAYS");
            feature.add(featureCard(when, (String) hol[0], counts));
        }
        String daf = Catalog.dafYomiKey(today);
        if (daf != null) feature.add(featureCard("TODAY'S DAF YOMI", daf, counts));

        boolean wide = widthDp() >= 700 && feature.size() > 1;
        if (wide) {
            LinearLayout r = hbox();
            r.setBaselineAligned(false);
            for (int i = 0; i < feature.size(); i++)
                r.addView(feature.get(i), lp(0, -1, 1, i == 0 ? 0 : 6, 0, i == feature.size() - 1 ? 0 : 6, 0));
            body.addView(r, lp(-1, -2, 0, 0, 4, 0, 6));
        } else {
            for (View f : feature) body.addView(f, lp(-1, -2, 0, 0, 4, 0, 8));
        }

        body.addView(sectionTitle("Browse"));
        int nP = total(counts, "p_"), nY = total(counts, "h_"), nG = total(counts, "d_");
        List<View> cats = new ArrayList<>();
        cats.add(bigCard("Parsha", "פרשה", "All the parshiyos, Bereishis to Devarim", nP, v -> go(Screen.CATEGORY, Catalog.CAT_PARSHA)));
        cats.add(bigCard("Yom Tov", "יום טוב", "Yomim tovim, fasts and special days", nY, v -> go(Screen.CATEGORY, Catalog.CAT_YOMTOV)));
        cats.add(bigCard("Gemara", "גמרא", "Every masechta, every daf", nG, v -> go(Screen.CATEGORY, Catalog.CAT_GEMARA)));
        if (widthDp() >= 700) {
            LinearLayout r = hbox();
            r.setBaselineAligned(false);
            for (int i = 0; i < cats.size(); i++) r.addView(cats.get(i), lp(0, -1, 1, i == 0 ? 0 : 5, 0, i == cats.size() - 1 ? 0 : 5, 0));
            body.addView(r);
        } else {
            for (View c : cats) body.addView(c, lp(-1, -2, 0, 0, 4, 0, 4));
        }

        List<Tile> pinned = new ArrayList<>();
        List<String> pinKeys = new ArrayList<>(pins());
        java.util.Collections.sort(pinKeys);
        for (String k : pinKeys) {
            Catalog.Folder f = Catalog.get(k);
            if (f == null) continue;
            Tile t = tile(k, f.en, f.he, counts.containsKey(k) ? counts.get(k) : 0, false, v -> go(Screen.FOLDER, k));
            pinnable(t);
            pinned.add(t);
        }
        if (!pinned.isEmpty()) {
            body.addView(sectionTitle("Pinned"));
            List<Object> rows = new ArrayList<>();
            chunk(rows, pinned, false);
            for (Object o : rows) body.addView(rowView((TileRow) o));
        }

        body.addView(sectionTitle("Anything else"));
        Tile gen = tile("g_general", "General thoughts", "כללי", counts.containsKey("g_general") ? counts.get("g_general") : 0, false,
                v -> go(Screen.FOLDER, "g_general"));
        List<Object> grow = new ArrayList<>();
        chunk(grow, java.util.Collections.singletonList(gen), false);
        body.addView(rowView((TileRow) grow.get(0)));
        setScreen(page);
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 18, C_ACCENT, true);
        t.setPadding(dp(2), dp(18), 0, dp(8));
        return t;
    }

    private View bigCard(String en, String he, String sub, int count, View.OnClickListener l) {
        LinearLayout c = hbox();
        c.setPadding(dp(18), dp(16), dp(14), dp(16));
        press(c, C_CARD, 20, C_LINE);
        LinearLayout names = vbox();
        names.addView(text(en, 24, C_INK, true));
        names.addView(text(he, 18, C_SUB, false));
        names.addView(text(sub, 13, C_SUB, false), lp(-2, -2, 0, 0, 4, 0, 0));
        if (count > 0) names.addView(text(count + (count == 1 ? " recording" : " recordings"), 13, C_ACCENT, true));
        c.addView(names, lp(0, -2, 1, 0, 0, 8, 0));
        c.addView(new Glyph(this, Glyph.CHEVRON, C_SUB), lp(dp(26), dp(26), 0, 0, 0, 0, 0));
        c.setOnClickListener(l);
        return c;
    }

    /** Big blue card: tap the card to open the folder, tap the red button to record straight away. */
    private View featureCard(String label, String key, Map<String, Integer> counts) {
        Catalog.Folder f = Catalog.getOrGeneral(key);
        LinearLayout c = hbox();
        c.setPadding(dp(18), dp(16), dp(16), dp(16));
        press(c, C_ACCENT, 20, 0);

        LinearLayout names = vbox();
        names.addView(text(label, 12, 0xFFB9C8E0, true));
        TextView en = text(f.en, 26, 0xFFFFFFFF, true);
        en.setPadding(0, dp(2), 0, 0);
        names.addView(en);
        names.addView(text(f.he, 19, 0xFFE3EAF6, false));
        int n = counts.containsKey(f.key) ? counts.get(f.key) : 0;
        names.addView(text(n == 0 ? "No thoughts yet  ·  tap to open" : n + (n == 1 ? " recording" : " recordings") + "  ·  tap to open", 13, 0xFFB9C8E0, false));
        c.addView(names, lp(0, -2, 1, 0, 0, 8, 0));

        LinearLayout b = vbox();
        b.setGravity(Gravity.CENTER);
        b.addView(new Glyph(this, Glyph.REC, C_RED), lp(dp(72), dp(72), 0, 0, 0, 0, 0));
        TextView lab = text("Record", 13, 0xFFFFFFFF, true);
        lab.setGravity(Gravity.CENTER);
        b.addView(lab, lp(-2, -2, 0, 0, 4, 0, 0));
        b.setPadding(dp(6), dp(4), dp(6), dp(4));
        press(b, 0x00000000, 16, 0);
        b.setOnClickListener(v -> startRecording(f.key));
        c.addView(b, lp(-2, -2, 0, 0, 0, 0, 0));
        c.setOnClickListener(v -> go(Screen.FOLDER, f.key));
        return c;
    }

    // ------------------------------------------------------------------ CATEGORY / MASECHTA

    private void showCategory(String cat) {
        cur = new Dest(Screen.CATEGORY, cat);
        Map<String, Integer> counts = lib.counts();
        LinearLayout page = vbox();
        List<Object> items = new ArrayList<>();
        int scrollTo = -1;

        if (Catalog.CAT_PARSHA.equals(cat)) {
            page.addView(backBar("Parsha", "פרשה", null));
            String now = Catalog.currentKey(israel);
            for (String book : Catalog.BOOKS) {
                items.add("Sefer " + book);
                List<Tile> tiles = new ArrayList<>();
                for (Catalog.Folder f : Catalog.inSection(book)) {
                    Tile t = tile(f.key, f.en, f.he, counts.containsKey(f.key) ? counts.get(f.key) : 0, false, v -> go(Screen.FOLDER, f.key));
                    t.highlight = f.key.equals(now);
                    pinnable(t);
                    if (t.highlight) scrollTo = items.size() + tiles.size() / colsFor(false);
                    tiles.add(t);
                }
                chunk(items, tiles, false);
            }
        } else if (Catalog.CAT_YOMTOV.equals(cat)) {
            page.addView(backBar("Yom Tov", "יום טוב", null));
            List<Tile> tiles = new ArrayList<>();
            for (Catalog.Folder f : Catalog.inSection(Catalog.SEC_HOLIDAY)) {
                Tile t = tile(f.key, f.en, f.he, counts.containsKey(f.key) ? counts.get(f.key) : 0, false, v -> go(Screen.FOLDER, f.key));
                pinnable(t);
                tiles.add(t);
            }
            chunk(items, tiles, false);
        } else {
            page.addView(backBar("Gemara", "גמרא", null));
            String lastSeder = null;
            List<Tile> tiles = new ArrayList<>();
            int[] per = new int[Catalog.shas().length];
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                int m = Catalog.masechtaOf(e.getKey());
                if (m >= 0 && m < per.length) per[m] += e.getValue();
            }
            for (Catalog.Masechta m : Catalog.shas()) {
                if (!m.seder.equals(lastSeder)) {
                    if (!tiles.isEmpty()) { chunk(items, tiles, false); tiles = new ArrayList<>(); }
                    items.add("Seder " + m.seder);
                    lastSeder = m.seder;
                }
                tiles.add(tile(null, m.en, m.he, per[m.idx], false, v -> go(Screen.MASECHTA, String.valueOf(m.idx))));
            }
            chunk(items, tiles, false);
        }
        ListView lv = listView(new Rows(items, rowFactory()));
        page.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1));
        final int target = scrollTo;
        if (target > 0) lv.post(() -> lv.setSelection(Math.max(0, target - 1)));
        setScreen(page);
    }

    private void showMasechta(int idx) {
        cur = new Dest(Screen.MASECHTA, String.valueOf(idx));
        Catalog.Masechta m = Catalog.masechta(idx);
        if (m == null) { goRoot(Screen.HOME, null); return; }
        Map<String, Integer> counts = lib.counts();
        LinearLayout page = vbox();
        int tot = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) if (Catalog.masechtaOf(e.getKey()) == idx) tot += e.getValue();
        page.addView(backBar(m.en, m.he + "  ·  Daf " + m.firstDaf + "–" + m.lastDaf + (tot > 0 ? "  ·  " + tot + " recorded" : ""), "GEMARA"));

        List<Tile> tiles = new ArrayList<>();
        int todayDaf = -1;
        String dy = Catalog.dafYomiKey(Calendar.getInstance());
        if (dy != null && Catalog.masechtaOf(dy) == idx) todayDaf = Integer.parseInt(dy.split("_")[2]);
        for (int d = m.firstDaf; d <= m.lastDaf; d++) {
            String key = Catalog.dafKey(idx, d);
            Tile t = tile(key, String.valueOf(d), Catalog.hebrewNumber(d), counts.containsKey(key) ? counts.get(key) : 0, true,
                    v -> go(Screen.FOLDER, key));
            pinnable(t);
            tiles.add(t);
        }
        List<Object> items = new ArrayList<>();
        chunk(items, tiles, true);
        ListView lv = listView(new Rows(items, rowFactory()));
        page.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1));
        if (todayDaf > 0) {
            final int row = (todayDaf - m.firstDaf) / colsFor(true);
            lv.post(() -> lv.setSelection(Math.max(0, row - 1)));
        }
        setScreen(page);
    }

    // ------------------------------------------------------------------ CALENDAR

    private void showCalendar() {
        cur = new Dest(Screen.CALENDAR, null);
        LinearLayout page = vbox();
        page.addView(header(true));

        Calendar first = Calendar.getInstance();
        first.clear();
        first.set(calYear, calMonth, 1, 12, 0, 0);
        HebrewDateFormatter hf = new HebrewDateFormatter();
        String a = hf.formatMonth(new JewishCalendar(first)), z = hf.formatMonth(new JewishCalendar(monthEnd(first)));
        String hebMonths = a.equals(z) ? a : a + " – " + z;

        LinearLayout nav = hbox();
        nav.setPadding(dp(8), dp(4), dp(8), dp(0));
        nav.addView(iconButton(new Glyph(this, Glyph.BACK, C_INK), 48, v -> shiftMonth(-1)));
        LinearLayout mid = vbox();
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        mid.addView(text(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(first.getTime()), 20, C_INK, true));
        mid.addView(text(hebMonths, 13, C_SUB, false));
        nav.addView(mid, lp(0, -2, 1, 0, 0, 0, 0));
        nav.addView(iconButton(new Glyph(this, Glyph.CHEVRON, C_INK), 48, v -> shiftMonth(1)));
        page.addView(nav);

        ScrollView sv = new ScrollView(this);
        LinearLayout body = vbox();
        body.setPadding(dp(10), dp(6), dp(10), dp(24));
        sv.addView(body);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        String[] dow = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Shabbos"};
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
        boolean shabbos = day.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY;
        if (shabbos) key = Catalog.parshaKeyOnShabbos(day, israel);
        String hk = Catalog.holidayKeyForDay(day, israel);
        if (key == null && hk != null) {
            Calendar prev = (Calendar) day.clone();
            prev.add(Calendar.DAY_OF_MONTH, -1);
            if (shabbos || !hk.equals(Catalog.holidayKeyForDay(prev, israel))) key = hk;
        }
        LinearLayout c = vbox();
        c.setPadding(dp(5), dp(4), dp(3), dp(3));
        press(c, isToday ? C_SOFT : (shabbos ? 0xFFFFFBF0 : C_CARD), 10, isToday ? C_ACCENT : C_LINE);
        c.setAlpha(inMonth ? 1f : 0.35f);
        c.addView(text(String.valueOf(day.get(Calendar.DAY_OF_MONTH)), 15, C_INK, true));
        c.addView(text(hn.formatHebrewNumber(new JewishCalendar(day).getJewishDayOfMonth()), 11, C_SUB, false));
        if (key != null) {
            TextView l = text(Catalog.title(key), 10.5f, C_ACCENT, true);
            l.setMaxLines(2);
            c.addView(l);
            final String fk = key;
            c.setOnClickListener(v -> go(Screen.FOLDER, fk));
        }
        return c;
    }

    // ------------------------------------------------------------------ FOLDER (record button + recordings)

    private String crumbFor(String key) {
        String cat = Catalog.category(key);
        if (Catalog.CAT_PARSHA.equals(cat)) return "PARSHA";
        if (Catalog.CAT_YOMTOV.equals(cat)) return "YOM TOV";
        if (Catalog.CAT_GEMARA.equals(cat)) {
            Catalog.Masechta m = Catalog.masechta(Catalog.masechtaOf(key));
            return "GEMARA  ›  " + (m == null ? "" : m.en.toUpperCase(Locale.US));
        }
        return null;
    }

    private void showFolder(String key) {
        Catalog.Folder f = Catalog.getOrGeneral(key);
        cur = new Dest(Screen.FOLDER, f.key);
        List<Library.Rec> recs = lib.inFolder(f.key);

        LinearLayout page = vbox();
        page.addView(backBar(f.en, f.he, crumbFor(f.key)));

        TextView rec = text("●  Record a thought", 22, 0xFFFFFFFF, true);
        rec.setGravity(Gravity.CENTER);
        press(rec, C_RED, 22, 0);
        rec.setOnClickListener(v -> startRecording(f.key));
        page.addView(rec, lp(-1, dp(88), 0, 16, 8, 16, 10));

        TextView files = text((filesOpen ? "Hide recordings" : "Recordings") + "  (" + recs.size() + ")", 17, C_ACCENT, true);
        files.setGravity(Gravity.CENTER);
        press(files, filesOpen ? C_SOFT : C_CARD, 16, C_ACCENT);
        files.setOnClickListener(v -> { filesOpen = !filesOpen; stopPlayback(); render(); });
        page.addView(files, lp(-1, dp(54), 0, 16, 0, 16, 6));

        folderRows = null;
        if (filesOpen) {
            if (recs.isEmpty()) {
                TextView e = text("Nothing recorded here yet.\nTap the red button when a thought comes.", 16, C_SUB, false);
                e.setGravity(Gravity.CENTER);
                e.setPadding(0, dp(30), 0, 0);
                page.addView(e);
            } else {
                folderRows = new Rows(new ArrayList<Object>(recs), o -> recRow((Library.Rec) o));
                page.addView(listView(folderRows), new LinearLayout.LayoutParams(-1, 0, 1));
            }
        }
        setScreen(page);
    }

    /** Re-draws the recording rows in place (keeps scroll position), e.g. after play/pause. */
    private void refreshFolder() {
        if (cur.s == Screen.FOLDER && folderRows != null) folderRows.notifyDataSetChanged();
    }

    private View recRow(Library.Rec r) {
        boolean mine = r.id.equals(playingKey) && player != null;
        LinearLayout wrap = vbox();
        wrap.setPadding(0, dp(5), 0, dp(5));
        LinearLayout card = vbox();
        card.setPadding(dp(8), dp(8), dp(4), dp(8));
        press(card, C_CARD, 16, mine ? C_ACCENT : C_LINE);

        LinearLayout top = hbox();
        boolean playing = mine && !playerPaused;
        FrameLayout play = new FrameLayout(this);
        play.addView(new Glyph(this, playing ? Glyph.PAUSE : Glyph.PLAY, 0xFFFFFFFF), new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        press(play, C_ACCENT, 26, 0);
        play.setOnClickListener(v -> togglePlay(r.id, lib.audioFile(r.id), this::refreshFolder));
        top.addView(play, lp(dp(52), dp(52), 0, 4, 0, 10, 0));

        LinearLayout info = vbox();
        TextView t = text(titleOf(r) + "  ·  " + Catalog.location(r.folder), 16, C_INK, true);
        t.setMaxLines(3);
        info.addView(t);
        info.addView(text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(r.createdMs)) + "  ·  " + fmt(r.durationMs), 13, C_SUB, false));
        top.addView(info, lp(0, -2, 1, 0, 0, 4, 0));
        top.addView(iconButton(new Glyph(this, Glyph.MORE, C_SUB), 44, v -> rowMenu(v, r)));
        card.addView(top);

        if (mine) {
            try {
                LinearLayout pr = hbox();
                pr.setPadding(dp(8), dp(4), dp(8), 0);
                SeekBar sb = new SeekBar(this);
                sb.setMax(Math.max(1, player.getDuration()));
                sb.setProgress(player.getCurrentPosition());
                sb.setOnSeekBarChangeListener(seekListener());
                TextView tm = text(fmt(player.getCurrentPosition()) + " / " + fmt(player.getDuration()), 12, C_SUB, false);
                pr.addView(sb, lp(0, -2, 1, 0, 0, 8, 0));
                pr.addView(tm);
                card.addView(pr);
                curSeek = sb;
                curTime = tm;
            } catch (IllegalStateException ignored) {}
        }
        wrap.addView(card);
        return wrap;
    }

    private SeekBar.OnSeekBarChangeListener seekListener() {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                try { if (user && player != null) player.seekTo(p); } catch (IllegalStateException ignored) {}
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };
    }

    private String titleOf(Library.Rec r) {
        if (r.named && r.title != null && !r.title.trim().isEmpty()) return r.title;
        return String.valueOf(lib.numberOf(r));
    }

    private void rowMenu(View anchor, Library.Rec r) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Rename");
        m.getMenu().add(0, 2, 1, "Move to another folder");
        m.getMenu().add(0, 3, 2, "Save a copy…");
        m.getMenu().add(0, 4, 3, "Delete");
        m.setOnMenuItemClickListener(i -> {
            switch (i.getItemId()) {
                case 1: askText("Name (leave empty for its number)", r.named ? r.title : "", s -> { lib.rename(r, s); render(); syncMirrorsAsync(); }); break;
                case 2: pickFolder("Move to…", k -> { stopPlayback(); lib.move(r, k); render(); syncMirrorsAsync(); toast("Moved to " + Catalog.title(k)); }); break;
                case 3: startCopy(r); break;
                case 4:
                    new AlertDialog.Builder(this).setTitle("Delete this recording?").setMessage(titleOf(r))
                            .setPositiveButton("Delete", (d, w) -> { stopPlayback(); lib.delete(r); render(); syncMirrorsAsync(); })
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

    /** Folder chooser that mirrors the app: Parsha / Yom Tov / Gemara (masechta, then daf) / General. */
    private void pickFolder(String title, Str done) {
        String[] top = {"Parsha", "Yom Tov", "Gemara", "General thoughts"};
        new AlertDialog.Builder(this).setTitle(title).setItems(top, (d, w) -> {
            if (w == 0) {
                List<Catalog.Folder> list = new ArrayList<>();
                for (String b : Catalog.BOOKS) list.addAll(Catalog.inSection(b));
                chooseFrom(title, list, done);
            } else if (w == 1) {
                chooseFrom(title, Catalog.inSection(Catalog.SEC_HOLIDAY), done);
            } else if (w == 2) {
                Catalog.Masechta[] ms = Catalog.shas();
                String[] names = new String[ms.length];
                for (int i = 0; i < ms.length; i++) names[i] = ms[i].en + "   " + ms[i].he;
                new AlertDialog.Builder(this).setTitle("Which masechta?").setItems(names, (d2, i) -> {
                    Catalog.Masechta m = ms[i];
                    String[] dapim = new String[m.dapim()];
                    for (int k = 0; k < dapim.length; k++) dapim[k] = m.en + " " + (m.firstDaf + k);
                    new AlertDialog.Builder(this).setTitle(m.en + ": which daf?")
                            .setItems(dapim, (d3, k) -> done.got(Catalog.dafKey(m.idx, m.firstDaf + k)))
                            .setNegativeButton("Cancel", null).show();
                }).setNegativeButton("Cancel", null).show();
            } else done.got("g_general");
        }).setNegativeButton("Cancel", null).show();
    }

    private void chooseFrom(String title, List<Catalog.Folder> list, Str done) {
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.get(i).en + "   " + list.get(i).he;
        new AlertDialog.Builder(this).setTitle(title).setItems(names, (d, w) -> done.got(list.get(w).key))
                .setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------------ RECORDING

    private void recordNowIfAsked(Intent i, boolean freshLaunch) {
        if (recorder != null || cur.s == Screen.REVIEW) return;
        boolean shortcut = i != null && "com.parsharec.RECORD_NOW".equals(i.getAction());
        if (shortcut || (freshLaunch && prefs.getBoolean("autoRecord", false))) startRecording(Catalog.currentKey(israel));
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        recordNowIfAsked(i, false);
    }

    private void startRecording(String folderKey) {
        if (recorder != null) return;
        if (Catalog.get(folderKey) == null) folderKey = "g_general";
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingFolder = folderKey;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        stopPlayback();
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
        if (cur.s != Screen.RECORDING && cur.s != Screen.REVIEW) origin = cur;
        cur = new Dest(Screen.RECORDING, null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        render();
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
            toast("That was too short to keep.");
            return false;
        }
        curRec.durationMs = ms;
        curRec.createdMs = System.currentTimeMillis();
        return true;
    }

    /** Back to wherever the recording was started from. */
    private void leaveRecording() {
        cur = origin != null && origin.s != Screen.RECORDING && origin.s != Screen.REVIEW ? origin : new Dest(Screen.HOME, null);
        render();
    }

    private void showRecording() {
        cur = new Dest(Screen.RECORDING, null);
        if (curRec == null || recorder == null) { leaveRecording(); return; }
        Catalog.Folder f = Catalog.getOrGeneral(curRec.folder);
        LinearLayout page = vbox();
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(24), dp(24), dp(24), dp(24));

        page.addView(text("RECORDING INTO", 12, C_SUB, true), lp(-2, -2, 0, 0, 8, 0, 0));
        TextView name = text(f.en, 30, C_INK, true);
        name.setGravity(Gravity.CENTER);
        page.addView(name);
        page.addView(text(f.he, 20, C_SUB, false));

        page.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));

        FrameLayout dotBox = new FrameLayout(this);
        pulse = new Glyph(this, Glyph.REC, paused ? C_SUB : C_RED);
        dotBox.addView(pulse, new FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER));
        page.addView(dotBox, lp(dp(190), dp(190), 0, 0, 0, 0, 0));

        timerView = text(fmt(recordedMs()), 56, C_INK, true);
        timerView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        page.addView(timerView, lp(-2, -2, 0, 0, 8, 0, 0));
        page.addView(text(paused ? "Paused" : "Listening…", 16, C_SUB, false));

        page.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));

        LinearLayout btns = hbox();
        btns.setGravity(Gravity.CENTER);
        TextView pause = bigButton(paused ? Glyph.PLAY : Glyph.PAUSE, paused ? "Resume" : "Pause", C_SOFT, C_ACCENT);
        pause.setOnClickListener(v -> togglePauseRecording());
        TextView stop = bigButton(Glyph.STOP, "Finish", C_RED, 0xFFFFFFFF);
        stop.setOnClickListener(v -> { if (finishRecording()) showReview(); else leaveRecording(); });
        btns.addView(pause, lp(dp(150), dp(64), 0, 8, 0, 8, 0));
        btns.addView(stop, lp(dp(150), dp(64), 0, 8, 0, 8, 0));
        page.addView(btns);

        TextView cancel = text("Throw away", 15, C_SUB, false);
        cancel.setPadding(dp(16), dp(14), dp(16), dp(8));
        cancel.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Throw this recording away?")
                .setPositiveButton("Throw away", (d, w) -> {
                    try { recorder.stop(); } catch (RuntimeException ignored) {}
                    releaseRecorder();
                    if (curRec != null) lib.audioFile(curRec.id).delete();
                    curRec = null;
                    leaveRecording();
                }).setNegativeButton("Keep recording", null).show());
        page.addView(cancel);
        setScreen(page);
    }

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
        cur = new Dest(Screen.REVIEW, null);
        if (curRec == null) { leaveRecording(); return; }
        Catalog.Folder f = Catalog.getOrGeneral(curRec.folder);
        String keepTitle = titleEdit != null && titleEdit.getParent() != null ? titleEdit.getText().toString() : null;
        LinearLayout page = vbox();
        page.setPadding(dp(24), dp(24), dp(24), dp(24));

        page.addView(text("SAVE TO", 12, C_SUB, true));
        page.addView(text(f.en + "   " + f.he, 22, C_INK, true));
        TextView change = text("Change folder", 15, C_ACCENT, true);
        change.setPadding(0, dp(6), 0, dp(14));
        change.setOnClickListener(v -> {
            String t = titleEdit.getText().toString();
            pickFolder("Save to…", k -> { curRec.folder = k; showReview(); titleEdit.setText(t); });
        });
        page.addView(change);

        titleEdit = new EditText(this);
        titleEdit.setHint("Name (optional) — otherwise it is saved as the next number");
        titleEdit.setText(keepTitle != null ? keepTitle : "");
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
            try {
                SeekBar sb = new SeekBar(this);
                sb.setMax(Math.max(1, player.getDuration()));
                sb.setProgress(player.getCurrentPosition());
                sb.setOnSeekBarChangeListener(seekListener());
                listen.addView(sb, lp(-1, -2, 0, 0, 8, 0, 0));
                curSeek = sb;
                curTime = tm;
            } catch (IllegalStateException ignored) {}
        }
        page.addView(listen);

        page.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));

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
                    if (curRec != null) lib.audioFile(curRec.id).delete();
                    curRec = null;
                    titleEdit = null;
                    leaveRecording();
                }).setNegativeButton("Keep", null).show());
        page.addView(discard, lp(-1, dp(52), 0, 0, 0, 0, 0));
        setScreen(page);
    }

    /** Saves the pending recording; a null title means "default". Then returns to where recording began. */
    private void saveReview(String title) {
        if (curRec == null) return;
        stopPlayback();
        if (title == null && titleEdit != null && titleEdit.getParent() != null) title = titleEdit.getText().toString().trim();
        curRec.title = title == null ? "" : title;
        curRec.named = !curRec.title.isEmpty();
        lib.commit(curRec);
        syncMirrorsAsync();
        toast("Saved to " + Catalog.title(curRec.folder) + " as " + (curRec.named ? curRec.title : "number " + lib.numberOf(curRec)));
        curRec = null;
        titleEdit = null;
        leaveRecording();
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
        i.putExtra(Intent.EXTRA_TITLE, (Catalog.title(r.folder) + " " + titleOf(r)).replaceAll("[\\\\/:*?\"<>|]", "-") + ".m4a");
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
                } catch (Throwable e) {
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
                } catch (Throwable e) {
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
                } catch (Throwable e) {
                    ui.post(() -> toast("Could not save: " + e.getMessage()));
                }
            }).start();
        }
    }

    // ------------------------------------------------------------------ file-manager copy + updates

    private void syncMirrorsAsync() {
        new Thread(() -> {
            try { lib.syncMirrors(); } catch (Throwable ignored) {}
        }).start();
    }

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
            } catch (Throwable e) {
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
                        } catch (Throwable e) {
                            ui.post(() -> toast("Download failed: " + e.getMessage()));
                        }
                    }).start();
                })
                .setNegativeButton("Later", null).show();
    }
}
