package com.parsharec;

import com.kosherjava.zmanim.hebrewcalendar.Daf;
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar.Parsha;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;
import com.kosherjava.zmanim.hebrewcalendar.YomiCalculator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything that can hold a recording: parshiyos, Yom Tov folders, a General folder, and every daf of Shas.
 * Parsha and Yom Tov folders are a small fixed list; dapim (about 2,700) are created on demand from a key and never listed up front.
 */
public final class Catalog {
    private Catalog() {}

    public static final String CAT_PARSHA = "parsha", CAT_YOMTOV = "yomtov", CAT_GEMARA = "gemara";
    public static final String SEC_HOLIDAY = "Yom Tov";
    public static final String SEC_GENERAL = "General";
    public static final String[] BOOKS = {"Bereishis", "Shemos", "Vayikra", "Bamidbar", "Devarim"};

    public static final class Folder {
        public final String key, en, he, section;
        Folder(String key, String en, String he, String section) {
            this.key = key; this.en = en; this.he = he; this.section = section;
        }
    }

    public static final class Masechta {
        public final int idx, firstDaf, lastDaf;
        public final String en, he, seder;
        Masechta(int idx, String en, String he, int firstDaf, int lastDaf, String seder) {
            this.idx = idx; this.en = en; this.he = he; this.firstDaf = firstDaf; this.lastDaf = lastDaf; this.seder = seder;
        }
        public int dapim() { return lastDaf - firstDaf + 1; }
    }

    private static final Map<String, Folder> BY_KEY = new LinkedHashMap<>();
    private static final List<Folder> FIXED = new ArrayList<>();

    private static void add(String key, String en, String he, String section) {
        Folder f = new Folder(key, en, he, section);
        BY_KEY.put(key, f);
        FIXED.add(f);
    }

    /** Bavli in Daf Yomi order (same order KosherJava numbers them). Dapim run firstDaf..lastDaf as in the Daf Yomi cycle. */
    private static final Masechta[] SHAS;

    static {
        String[][] b1 = {{"BERESHIS", "Bereishis", "בראשית"}, {"NOACH", "Noach", "נח"}, {"LECH_LECHA", "Lech Lecha", "לך לך"},
                {"VAYERA", "Vayeira", "וירא"}, {"CHAYEI_SARA", "Chayei Sarah", "חיי שרה"}, {"TOLDOS", "Toldos", "תולדות"},
                {"VAYETZEI", "Vayeitzei", "ויצא"}, {"VAYISHLACH", "Vayishlach", "וישלח"}, {"VAYESHEV", "Vayeishev", "וישב"},
                {"MIKETZ", "Mikeitz", "מקץ"}, {"VAYIGASH", "Vayigash", "ויגש"}, {"VAYECHI", "Vayechi", "ויחי"}};
        String[][] b2 = {{"SHEMOS", "Shemos", "שמות"}, {"VAERA", "Vaeira", "וארא"}, {"BO", "Bo", "בא"},
                {"BESHALACH", "Beshalach", "בשלח"}, {"YISRO", "Yisro", "יתרו"}, {"MISHPATIM", "Mishpatim", "משפטים"},
                {"TERUMAH", "Terumah", "תרומה"}, {"TETZAVEH", "Tetzaveh", "תצוה"}, {"KI_SISA", "Ki Sisa", "כי תשא"},
                {"VAYAKHEL", "Vayakhel", "ויקהל"}, {"PEKUDEI", "Pekudei", "פקודי"}, {"VAYAKHEL_PEKUDEI", "Vayakhel-Pekudei", "ויקהל־פקודי"}};
        String[][] b3 = {{"VAYIKRA", "Vayikra", "ויקרא"}, {"TZAV", "Tzav", "צו"}, {"SHMINI", "Shemini", "שמיני"},
                {"TAZRIA", "Tazria", "תזריע"}, {"METZORA", "Metzora", "מצורע"}, {"TAZRIA_METZORA", "Tazria-Metzora", "תזריע־מצורע"},
                {"ACHREI_MOS", "Acharei Mos", "אחרי מות"}, {"KEDOSHIM", "Kedoshim", "קדושים"},
                {"ACHREI_MOS_KEDOSHIM", "Acharei Mos-Kedoshim", "אחרי מות־קדושים"}, {"EMOR", "Emor", "אמור"},
                {"BEHAR", "Behar", "בהר"}, {"BECHUKOSAI", "Bechukosai", "בחקתי"}, {"BEHAR_BECHUKOSAI", "Behar-Bechukosai", "בהר־בחקתי"}};
        String[][] b4 = {{"BAMIDBAR", "Bamidbar", "במדבר"}, {"NASSO", "Naso", "נשא"}, {"BEHAALOSCHA", "Behaaloscha", "בהעלותך"},
                {"SHLACH", "Shelach", "שלח"}, {"KORACH", "Korach", "קרח"}, {"CHUKAS", "Chukas", "חקת"}, {"BALAK", "Balak", "בלק"},
                {"CHUKAS_BALAK", "Chukas-Balak", "חקת־בלק"}, {"PINCHAS", "Pinchas", "פינחס"}, {"MATOS", "Matos", "מטות"},
                {"MASEI", "Masei", "מסעי"}, {"MATOS_MASEI", "Matos-Masei", "מטות־מסעי"}};
        String[][] b5 = {{"DEVARIM", "Devarim", "דברים"}, {"VAESCHANAN", "Vaeschanan", "ואתחנן"}, {"EIKEV", "Eikev", "עקב"},
                {"REEH", "Re'eh", "ראה"}, {"SHOFTIM", "Shoftim", "שפטים"}, {"KI_SEITZEI", "Ki Seitzei", "כי תצא"},
                {"KI_SAVO", "Ki Savo", "כי תבוא"}, {"NITZAVIM", "Nitzavim", "נצבים"}, {"VAYEILECH", "Vayeilech", "וילך"},
                {"NITZAVIM_VAYEILECH", "Nitzavim-Vayeilech", "נצבים־וילך"}, {"HAAZINU", "Haazinu", "האזינו"},
                {"VZOS_HABERACHA", "V'zos Haberacha", "וזאת הברכה"}};
        String[][][] books = {b1, b2, b3, b4, b5};
        for (int i = 0; i < books.length; i++)
            for (String[] p : books[i]) add("p_" + p[0], p[1], p[2], BOOKS[i]);

        add("h_elul", "Elul", "אלול", SEC_HOLIDAY);
        add("h_rh", "Rosh Hashanah", "ראש השנה", SEC_HOLIDAY);
        add("h_aseret", "Aseres Yemei Teshuvah", "עשרת ימי תשובה", SEC_HOLIDAY);
        add("h_yk", "Yom Kippur", "יום כיפור", SEC_HOLIDAY);
        add("h_sukkot", "Sukkos", "סוכות", SEC_HOLIDAY);
        add("h_simchat", "Shemini Atzeres & Simchas Torah", "שמיני עצרת ושמחת תורה", SEC_HOLIDAY);
        add("h_chanukah", "Chanukah", "חנוכה", SEC_HOLIDAY);
        add("h_tevet10", "Asarah B'Teves", "עשרה בטבת", SEC_HOLIDAY);
        add("h_tubishvat", "Tu BiShvat", "ט״ו בשבט", SEC_HOLIDAY);
        add("h_purim", "Purim", "פורים", SEC_HOLIDAY);
        add("h_pesach", "Pesach", "פסח", SEC_HOLIDAY);
        add("h_omer", "Sefiras HaOmer & Lag BaOmer", "ספירת העומר", SEC_HOLIDAY);
        add("h_shavuot", "Shavuos", "שבועות", SEC_HOLIDAY);
        add("h_tammuz17", "17 Tammuz & the Three Weeks", "י״ז בתמוז", SEC_HOLIDAY);
        add("h_tisha", "Tisha B'Av", "תשעה באב", SEC_HOLIDAY);
        add("g_general", "General thoughts", "כללי", SEC_GENERAL);

        Object[][] s = {
                {"Berachos", "ברכות", 2, 64, "Zeraim"}, {"Shabbos", "שבת", 2, 157, "Moed"}, {"Eruvin", "עירובין", 2, 105, "Moed"},
                {"Pesachim", "פסחים", 2, 121, "Moed"}, {"Shekalim", "שקלים", 2, 22, "Moed"}, {"Yoma", "יומא", 2, 88, "Moed"},
                {"Sukkah", "סוכה", 2, 56, "Moed"}, {"Beitzah", "ביצה", 2, 40, "Moed"}, {"Rosh Hashanah", "ראש השנה", 2, 35, "Moed"},
                {"Taanis", "תענית", 2, 31, "Moed"}, {"Megillah", "מגילה", 2, 32, "Moed"}, {"Moed Katan", "מועד קטן", 2, 29, "Moed"},
                {"Chagigah", "חגיגה", 2, 27, "Moed"},
                {"Yevamos", "יבמות", 2, 122, "Nashim"}, {"Kesubos", "כתובות", 2, 112, "Nashim"}, {"Nedarim", "נדרים", 2, 91, "Nashim"},
                {"Nazir", "נזיר", 2, 66, "Nashim"}, {"Sotah", "סוטה", 2, 49, "Nashim"}, {"Gittin", "גיטין", 2, 90, "Nashim"},
                {"Kiddushin", "קידושין", 2, 82, "Nashim"},
                {"Bava Kamma", "בבא קמא", 2, 119, "Nezikin"}, {"Bava Metzia", "בבא מציעא", 2, 119, "Nezikin"},
                {"Bava Basra", "בבא בתרא", 2, 176, "Nezikin"}, {"Sanhedrin", "סנהדרין", 2, 113, "Nezikin"},
                {"Makkos", "מכות", 2, 24, "Nezikin"}, {"Shevuos", "שבועות", 2, 49, "Nezikin"},
                {"Avodah Zarah", "עבודה זרה", 2, 76, "Nezikin"}, {"Horayos", "הוריות", 2, 14, "Nezikin"},
                {"Zevachim", "זבחים", 2, 120, "Kodashim"}, {"Menachos", "מנחות", 2, 110, "Kodashim"}, {"Chullin", "חולין", 2, 142, "Kodashim"},
                {"Bechoros", "בכורות", 2, 61, "Kodashim"}, {"Arachin", "ערכין", 2, 34, "Kodashim"}, {"Temurah", "תמורה", 2, 34, "Kodashim"},
                {"Kerisos", "כריתות", 2, 28, "Kodashim"}, {"Meilah", "מעילה", 2, 22, "Kodashim"}, {"Kinnim", "קנים", 23, 25, "Kodashim"},
                {"Tamid", "תמיד", 26, 33, "Kodashim"}, {"Midos", "מידות", 34, 37, "Kodashim"},
                {"Niddah", "נדה", 2, 73, "Taharos"}};
        SHAS = new Masechta[s.length];
        for (int i = 0; i < s.length; i++)
            SHAS[i] = new Masechta(i, (String) s[i][0], (String) s[i][1], (Integer) s[i][2], (Integer) s[i][3], (String) s[i][4]);
    }

    // ---- lookups ----

    /** The fixed folders: parshiyos, Yom Tov, General. Dapim are deliberately not in here. */
    public static List<Folder> fixed() { return Collections.unmodifiableList(FIXED); }

    public static List<Folder> inSection(String section) {
        List<Folder> out = new ArrayList<>();
        for (Folder f : FIXED) if (f.section.equals(section)) out.add(f);
        return out;
    }

    public static Masechta[] shas() { return SHAS.clone(); }

    public static Masechta masechta(int idx) { return idx >= 0 && idx < SHAS.length ? SHAS[idx] : null; }

    public static String dafKey(int m, int daf) { return "d_" + m + "_" + daf; }

    /** Folder for a key, or null if the key means nothing (e.g. a recording from a newer or damaged index). */
    public static Folder get(String key) {
        if (key == null) return null;
        Folder f = BY_KEY.get(key);
        if (f != null) return f;
        if (key.startsWith("d_")) {
            String[] p = key.split("_");
            if (p.length == 3) {
                try {
                    int m = Integer.parseInt(p[1]), d = Integer.parseInt(p[2]);
                    Masechta ms = masechta(m);
                    if (ms != null && d >= ms.firstDaf && d <= ms.lastDaf)
                        return new Folder(key, ms.en + " " + d, ms.he + " " + hebNum(d), "Gemara");
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    /** Never null: unknown keys fall back to General so a stray recording is still reachable. */
    public static Folder getOrGeneral(String key) {
        Folder f = get(key);
        return f != null ? f : BY_KEY.get("g_general");
    }

    public static String title(String key) {
        Folder f = get(key);
        return f == null ? "General thoughts" : f.en;
    }

    public static String category(String key) {
        if (key == null) return null;
        if (key.startsWith("p_")) return CAT_PARSHA;
        if (key.startsWith("h_")) return CAT_YOMTOV;
        if (key.startsWith("d_")) return CAT_GEMARA;
        return null;
    }

    /** Masechta index for a daf key, or -1. */
    public static int masechtaOf(String key) {
        if (key == null || !key.startsWith("d_")) return -1;
        String[] p = key.split("_");
        try { return p.length == 3 ? Integer.parseInt(p[1]) : -1; } catch (NumberFormatException e) { return -1; }
    }

    /** Folder path used for the copies in the file manager, e.g. "Gemara/Berachos/Daf 005". */
    public static String path(String key) {
        Folder f = getOrGeneral(key);
        String cat = category(f.key);
        if (CAT_PARSHA.equals(cat)) return "Parsha/" + f.en;
        if (CAT_YOMTOV.equals(cat)) return "Yom Tov/" + f.en;
        if (CAT_GEMARA.equals(cat)) {
            Masechta m = masechta(masechtaOf(f.key));
            String[] p = f.key.split("_");
            return "Gemara/" + m.en + "/Daf " + String.format(java.util.Locale.US, "%03d", Integer.parseInt(p[2]));
        }
        return "General";
    }

    private static String hebNum(int n) {
        try { return new HebrewDateFormatter().formatHebrewNumber(n); } catch (RuntimeException e) { return String.valueOf(n); }
    }

    public static String hebrewNumber(int n) { return hebNum(n); }

    /** Today's Daf Yomi as a folder key, or null if it cannot be worked out. */
    public static String dafYomiKey(Calendar day) {
        try {
            Daf d = YomiCalculator.getDafYomiBavli(new JewishCalendar(day));
            if (d == null) return null;
            String key = dafKey(d.getMasechtaNumber(), d.getDaf());
            return get(key) != null ? key : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- calendar helpers ----

    /** Folder key for the parsha read on the Shabbos that ends the week containing day {@code c}. */
    public static String parshaKeyForWeek(Calendar day, boolean israel) {
        Calendar sat = (Calendar) day.clone();
        int add = (Calendar.SATURDAY - sat.get(Calendar.DAY_OF_WEEK) + 7) % 7;
        sat.add(Calendar.DAY_OF_MONTH, add);
        return parshaKeyOnShabbos(sat, israel);
    }

    /** Parsha for the exact Shabbos given, or null if that Shabbos has a yom tov reading. */
    public static String parshaKeyOnShabbos(Calendar sat, boolean israel) {
        JewishCalendar jc = new JewishCalendar(sat);
        jc.setInIsrael(israel);
        Parsha p = jc.getParshah();
        if (p == null || p == Parsha.NONE) return null;
        String key = "p_" + p.name();
        return BY_KEY.containsKey(key) ? key : null;
    }

    /** Which holiday folder (if any) covers this civil day. */
    public static String holidayKeyForDay(Calendar day, boolean israel) {
        JewishCalendar jc = new JewishCalendar(day);
        jc.setInIsrael(israel);
        int m = jc.getJewishMonth(), d = jc.getJewishDayOfMonth();
        boolean leap = jc.isJewishLeapYear();
        switch (m) {
            case JewishDate.ELUL: return "h_elul";
            case JewishDate.TISHREI:
                if (d <= 2) return "h_rh";
                if (d <= 10) return d == 10 ? "h_yk" : "h_aseret";
                if (d >= 15 && d <= 21) return "h_sukkot";
                if (d >= 22 && d <= 23) return "h_simchat";
                return null;
            case JewishDate.KISLEV:
                return d >= 25 ? "h_chanukah" : null;
            case JewishDate.TEVES:
                if (d <= 2 || (d == 3 && jc.isChanukah())) return "h_chanukah";
                return d == 10 ? "h_tevet10" : null;
            case JewishDate.SHEVAT:
                return d == 15 ? "h_tubishvat" : null;
            case JewishDate.ADAR:
                if (leap) return null;
                return (d >= 13 && d <= 15) ? "h_purim" : null;
            case JewishDate.ADAR_II:
                return (d >= 13 && d <= 15) ? "h_purim" : null;
            case JewishDate.NISSAN:
                if (d >= 14 && d <= 22) return "h_pesach";
                return d >= 16 ? "h_omer" : null;
            case JewishDate.IYAR:
                return "h_omer";
            case JewishDate.SIVAN:
                if (d <= 5) return "h_omer";
                return (d >= 5 && d <= 7) ? "h_shavuot" : null;
            case JewishDate.TAMMUZ:
                return d >= 17 ? "h_tammuz17" : null;
            case JewishDate.AV:
                if (d <= 8) return "h_tammuz17";
                return d <= 10 ? "h_tisha" : null;
            default:
                return null;
        }
    }

    /** Holiday folder that is happening today or starts within {@code horizonDays}, plus days until it starts. */
    public static Object[] upcomingHoliday(Calendar today, boolean israel, int horizonDays) {
        Calendar c = (Calendar) today.clone();
        String now = holidayKeyForDay(c, israel);
        // Elul, the Omer and the Three Weeks run for weeks; they are only "upcoming" when they begin.
        for (int i = 0; i <= horizonDays; i++) {
            String k = holidayKeyForDay(c, israel);
            if (k != null && (i > 0 || k.equals(now))) {
                if (i == 0) return new Object[]{k, 0};
                Calendar prev = (Calendar) c.clone();
                prev.add(Calendar.DAY_OF_MONTH, -1);
                if (!k.equals(holidayKeyForDay(prev, israel))) return new Object[]{k, i};
            }
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return null;
    }

    /** Folder for "right now": this week's Parsha, else the holiday in progress or imminent, else General. */
    public static String currentKey(boolean israel) {
        Calendar probe = Calendar.getInstance();
        for (int i = 0; i < 3; i++) {
            String k = parshaKeyForWeek(probe, israel);
            if (k != null) return k;
            probe.add(Calendar.DAY_OF_MONTH, 7);
        }
        Object[] hol = upcomingHoliday(Calendar.getInstance(), israel, 7);
        return hol != null ? (String) hol[0] : "g_general";
    }
}
