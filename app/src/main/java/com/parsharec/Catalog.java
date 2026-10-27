package com.parsharec;

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar.Parsha;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every folder the app offers (parshiyot, holidays, general) plus calendar lookups. */
public final class Catalog {
    private Catalog() {}

    public static final String SEC_HOLIDAY = "Holidays & special days";
    public static final String SEC_GENERAL = "General";
    public static final String[] BOOKS = {"Bereshit", "Shemot", "Vayikra", "Bamidbar", "Devarim"};

    public static final class Folder {
        public final String key, en, he, section;
        Folder(String key, String en, String he, String section) {
            this.key = key; this.en = en; this.he = he; this.section = section;
        }
    }

    private static final Map<String, Folder> BY_KEY = new LinkedHashMap<>();
    private static final List<Folder> ALL = new ArrayList<>();

    private static void add(String key, String en, String he, String section) {
        Folder f = new Folder(key, en, he, section);
        BY_KEY.put(key, f);
        ALL.add(f);
    }

    static {
        String[][] b1 = {{"BERESHIS", "Bereshit", "בראשית"}, {"NOACH", "Noach", "נח"}, {"LECH_LECHA", "Lech Lecha", "לך לך"},
                {"VAYERA", "Vayera", "וירא"}, {"CHAYEI_SARA", "Chayei Sarah", "חיי שרה"}, {"TOLDOS", "Toldot", "תולדות"},
                {"VAYETZEI", "Vayetzei", "ויצא"}, {"VAYISHLACH", "Vayishlach", "וישלח"}, {"VAYESHEV", "Vayeshev", "וישב"},
                {"MIKETZ", "Miketz", "מקץ"}, {"VAYIGASH", "Vayigash", "ויגש"}, {"VAYECHI", "Vayechi", "ויחי"}};
        String[][] b2 = {{"SHEMOS", "Shemot", "שמות"}, {"VAERA", "Vaera", "וארא"}, {"BO", "Bo", "בא"},
                {"BESHALACH", "Beshalach", "בשלח"}, {"YISRO", "Yitro", "יתרו"}, {"MISHPATIM", "Mishpatim", "משפטים"},
                {"TERUMAH", "Terumah", "תרומה"}, {"TETZAVEH", "Tetzaveh", "תצוה"}, {"KI_SISA", "Ki Tisa", "כי תשא"},
                {"VAYAKHEL", "Vayakhel", "ויקהל"}, {"PEKUDEI", "Pekudei", "פקודי"}, {"VAYAKHEL_PEKUDEI", "Vayakhel-Pekudei", "ויקהל־פקודי"}};
        String[][] b3 = {{"VAYIKRA", "Vayikra", "ויקרא"}, {"TZAV", "Tzav", "צו"}, {"SHMINI", "Shemini", "שמיני"},
                {"TAZRIA", "Tazria", "תזריע"}, {"METZORA", "Metzora", "מצורע"}, {"TAZRIA_METZORA", "Tazria-Metzora", "תזריע־מצורע"},
                {"ACHREI_MOS", "Acharei Mot", "אחרי מות"}, {"KEDOSHIM", "Kedoshim", "קדושים"},
                {"ACHREI_MOS_KEDOSHIM", "Acharei Mot-Kedoshim", "אחרי מות־קדושים"}, {"EMOR", "Emor", "אמור"},
                {"BEHAR", "Behar", "בהר"}, {"BECHUKOSAI", "Bechukotai", "בחקתי"}, {"BEHAR_BECHUKOSAI", "Behar-Bechukotai", "בהר־בחקתי"}};
        String[][] b4 = {{"BAMIDBAR", "Bamidbar", "במדבר"}, {"NASSO", "Naso", "נשא"}, {"BEHAALOSCHA", "Behaalotecha", "בהעלותך"},
                {"SHLACH", "Shelach", "שלח"}, {"KORACH", "Korach", "קרח"}, {"CHUKAS", "Chukat", "חקת"}, {"BALAK", "Balak", "בלק"},
                {"CHUKAS_BALAK", "Chukat-Balak", "חקת־בלק"}, {"PINCHAS", "Pinchas", "פינחס"}, {"MATOS", "Matot", "מטות"},
                {"MASEI", "Masei", "מסעי"}, {"MATOS_MASEI", "Matot-Masei", "מטות־מסעי"}};
        String[][] b5 = {{"DEVARIM", "Devarim", "דברים"}, {"VAESCHANAN", "Vaetchanan", "ואתחנן"}, {"EIKEV", "Eikev", "עקב"},
                {"REEH", "Reeh", "ראה"}, {"SHOFTIM", "Shoftim", "שפטים"}, {"KI_SEITZEI", "Ki Teitzei", "כי תצא"},
                {"KI_SAVO", "Ki Tavo", "כי תבוא"}, {"NITZAVIM", "Nitzavim", "נצבים"}, {"VAYEILECH", "Vayelech", "וילך"},
                {"NITZAVIM_VAYEILECH", "Nitzavim-Vayelech", "נצבים־וילך"}, {"HAAZINU", "Haazinu", "האזינו"},
                {"VZOS_HABERACHA", "Vezot Haberacha", "וזאת הברכה"}};
        String[][][] books = {b1, b2, b3, b4, b5};
        for (int i = 0; i < books.length; i++)
            for (String[] p : books[i]) add("p_" + p[0], p[1], p[2], BOOKS[i]);

        add("h_elul", "Elul", "אלול", SEC_HOLIDAY);
        add("h_rh", "Rosh Hashanah", "ראש השנה", SEC_HOLIDAY);
        add("h_aseret", "Ten Days of Teshuvah", "עשרת ימי תשובה", SEC_HOLIDAY);
        add("h_yk", "Yom Kippur", "יום כיפור", SEC_HOLIDAY);
        add("h_sukkot", "Sukkot", "סוכות", SEC_HOLIDAY);
        add("h_simchat", "Shemini Atzeret & Simchat Torah", "שמיני עצרת ושמחת תורה", SEC_HOLIDAY);
        add("h_chanukah", "Chanukah", "חנוכה", SEC_HOLIDAY);
        add("h_tevet10", "Asara B'Tevet", "עשרה בטבת", SEC_HOLIDAY);
        add("h_tubishvat", "Tu BiShvat", "ט״ו בשבט", SEC_HOLIDAY);
        add("h_purim", "Purim", "פורים", SEC_HOLIDAY);
        add("h_pesach", "Pesach", "פסח", SEC_HOLIDAY);
        add("h_omer", "Sefirat HaOmer & Lag BaOmer", "ספירת העומר", SEC_HOLIDAY);
        add("h_shavuot", "Shavuot", "שבועות", SEC_HOLIDAY);
        add("h_tammuz17", "17 Tammuz & the Three Weeks", "י״ז בתמוז", SEC_HOLIDAY);
        add("h_tisha", "Tisha B'Av", "תשעה באב", SEC_HOLIDAY);
        add("g_general", "General thoughts", "כללי", SEC_GENERAL);
    }

    public static List<Folder> all() { return Collections.unmodifiableList(ALL); }
    public static Folder get(String key) { return BY_KEY.get(key); }

    public static String title(String key) {
        Folder f = BY_KEY.get(key);
        return f == null ? key : f.en;
    }

    // ---- calendar helpers ----

    /** Folder key for the parsha read on the Shabbat that ends the week containing day {@code c}. */
    public static String parshaKeyForWeek(Calendar day, boolean israel) {
        Calendar sat = (Calendar) day.clone();
        int add = (Calendar.SATURDAY - sat.get(Calendar.DAY_OF_WEEK) + 7) % 7;
        sat.add(Calendar.DAY_OF_MONTH, add);
        return parshaKeyOnShabbat(sat, israel);
    }

    /** Parsha for the exact Shabbat given, or null if that Shabbat has a yom tov reading. */
    public static String parshaKeyOnShabbat(Calendar sat, boolean israel) {
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