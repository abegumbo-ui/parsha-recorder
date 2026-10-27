package com.parsharec;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Recording index (one small JSON file) plus the audio files; handles backup and restore as a single zip. */
public final class Library {
    public static final class Rec {
        public String id, folder, title;
        public long createdMs, durationMs;
    }

    private static final Pattern AUDIO_ENTRY = Pattern.compile("audio/[0-9a-fA-F-]{36}\\.m4a");

    private final File root, audioDir, index;
    private final List<Rec> recs = new ArrayList<>();

    public Library(Context ctx) {
        File base = ctx.getExternalFilesDir(null);
        if (base == null) base = ctx.getFilesDir();
        root = new File(base, "recordings");
        audioDir = new File(root, "audio");
        index = new File(root, "library.json");
        audioDir.mkdirs();
        load();
    }

    public File audioFile(String id) { return new File(audioDir, id + ".m4a"); }

    /** A fresh file the recorder can write into; call {@link #commit} once it is finished. */
    public Rec newRec(String folder) {
        Rec r = new Rec();
        r.id = UUID.randomUUID().toString();
        r.folder = folder;
        r.createdMs = System.currentTimeMillis();
        return r;
    }

    public synchronized void commit(Rec r) {
        recs.add(r);
        save();
    }

    public synchronized List<Rec> inFolder(String folder) {
        List<Rec> out = new ArrayList<>();
        for (Rec r : recs) if (r.folder.equals(folder)) out.add(r);
        Collections.sort(out, (a, b) -> Long.compare(b.createdMs, a.createdMs));
        return out;
    }

    public synchronized Map<String, Integer> counts() {
        Map<String, Integer> m = new HashMap<>();
        for (Rec r : recs) m.put(r.folder, m.containsKey(r.folder) ? m.get(r.folder) + 1 : 1);
        return m;
    }

    public synchronized int total() { return recs.size(); }

    public synchronized void rename(Rec r, String title) { r.title = title; save(); }

    public synchronized void move(Rec r, String folder) { r.folder = folder; save(); }

    public synchronized void delete(Rec r) {
        recs.remove(r);
        audioFile(r.id).delete();
        save();
    }

    // ---- persistence ----

    private void load() {
        recs.clear();
        if (!index.exists()) return;
        try {
            recs.addAll(parse(readAll(new FileInputStream(index))));
        } catch (IOException | JSONException e) {
            // A corrupt index must never lose audio: rebuild entries from the audio files that exist.
            File[] files = audioDir.listFiles();
            if (files != null) for (File f : files) {
                String n = f.getName();
                if (!n.endsWith(".m4a")) continue;
                Rec r = new Rec();
                r.id = n.substring(0, n.length() - 4);
                r.folder = "g_general";
                r.createdMs = f.lastModified();
                r.title = "Recovered recording";
                recs.add(r);
            }
        }
    }

    private static List<Rec> parse(byte[] json) throws JSONException {
        JSONArray a = new JSONObject(new String(json, java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("recordings");
        List<Rec> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            Rec r = new Rec();
            r.id = o.getString("id");
            r.folder = o.getString("folder");
            r.title = o.optString("title", "");
            r.createdMs = o.optLong("created");
            r.durationMs = o.optLong("duration");
            out.add(r);
        }
        return out;
    }

    private byte[] serialize() {
        try {
            JSONArray a = new JSONArray();
            for (Rec r : recs) {
                a.put(new JSONObject().put("id", r.id).put("folder", r.folder).put("title", r.title == null ? "" : r.title)
                        .put("created", r.createdMs).put("duration", r.durationMs));
            }
            return new JSONObject().put("version", 1).put("recordings", a).toString(1).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private void save() {
        File tmp = new File(root, "library.json.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(serialize());
            out.getFD().sync();
        } catch (IOException e) {
            return;
        }
        if (!tmp.renameTo(index)) { index.delete(); tmp.renameTo(index); }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = i.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        }
    }

    // ---- backup / restore ----

    public synchronized int backup(OutputStream os) throws IOException {
        int n = 0;
        try (ZipOutputStream z = new ZipOutputStream(os)) {
            z.putNextEntry(new ZipEntry("library.json"));
            z.write(serialize());
            z.closeEntry();
            for (Rec r : recs) {
                File f = audioFile(r.id);
                if (!f.exists()) continue;
                z.putNextEntry(new ZipEntry("audio/" + r.id + ".m4a"));
                try (FileInputStream in = new FileInputStream(f)) {
                    byte[] buf = new byte[16384];
                    int k;
                    while ((k = in.read(buf)) > 0) z.write(buf, 0, k);
                }
                z.closeEntry();
                n++;
            }
        }
        return n;
    }

    /** Merges a backup into this library. Recordings already present are left alone. Returns how many were added. */
    public synchronized int restore(InputStream is) throws IOException {
        List<Rec> incoming = null;
        Set<String> got = new HashSet<>();
        File stage = new File(root, "restore-stage");
        stage.mkdirs();
        try (ZipInputStream z = new ZipInputStream(is)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String name = e.getName();
                if (name.equals("library.json")) {
                    try { incoming = parse(readAll(new NoClose(z))); } catch (JSONException ex) { throw new IOException("Not a valid backup"); }
                } else if (AUDIO_ENTRY.matcher(name).matches()) {
                    String id = name.substring(6, name.length() - 4);
                    try (FileOutputStream out = new FileOutputStream(new File(stage, id + ".m4a"))) {
                        byte[] buf = new byte[16384];
                        int k;
                        while ((k = z.read(buf)) > 0) out.write(buf, 0, k);
                    }
                    got.add(id);
                }
            }
        }
        if (incoming == null) { clear(stage); throw new IOException("Not a Parsha Recorder backup"); }
        Set<String> have = new HashSet<>();
        for (Rec r : recs) have.add(r.id);
        int added = 0;
        for (Rec r : incoming) {
            if (have.contains(r.id) || !got.contains(r.id)) continue;
            if (!new File(stage, r.id + ".m4a").renameTo(audioFile(r.id))) continue;
            recs.add(r);
            added++;
        }
        save();
        clear(stage);
        return added;
    }

    private static void clear(File dir) {
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
        dir.delete();
    }

    /** Stops ZipInputStream from being closed by readAll's try-with-resources. */
    private static final class NoClose extends java.io.FilterInputStream {
        NoClose(InputStream in) { super(in); }
        @Override public void close() {}
    }
}
