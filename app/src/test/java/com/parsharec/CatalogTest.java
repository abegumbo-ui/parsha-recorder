package com.parsharec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Calendar;

public class CatalogTest {
    private static Calendar d(int y, int m, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, day, 12, 0, 0);
        return c;
    }

    @Test public void knownShabbatot() {
        assertEquals("p_VAYERA", Catalog.parshaKeyForWeek(d(2026, 10, 27), false));
        assertEquals("p_BERESHIS", Catalog.parshaKeyForWeek(d(2026, 10, 5), false));
        assertEquals("p_VAYAKHEL_PEKUDEI", Catalog.parshaKeyForWeek(d(2023, 3, 15), false));
        assertEquals("p_PEKUDEI", Catalog.parshaKeyForWeek(d(2025, 3, 26), false));
        assertEquals("p_NITZAVIM_VAYEILECH", Catalog.parshaKeyForWeek(d(2024, 9, 25), false));
        assertEquals("p_NITZAVIM", Catalog.parshaKeyForWeek(d(2025, 9, 15), false));
    }

    @Test public void yomTovShabbatHasNoParsha() {
        // Shabbat of Pesach 5785 (April 19, 2025)
        assertNull(Catalog.parshaKeyOnShabbat(d(2025, 4, 19), false));
    }

    @Test public void everyParshaEnumHasAFolder() {
        for (com.kosherjava.zmanim.hebrewcalendar.JewishCalendar.Parsha p :
                com.kosherjava.zmanim.hebrewcalendar.JewishCalendar.Parsha.values()) {
            String k = "p_" + p.name();
            if (p.ordinal() <= com.kosherjava.zmanim.hebrewcalendar.JewishCalendar.Parsha.NITZAVIM_VAYEILECH.ordinal() && p.ordinal() > 0)
                assertNotNull(k, Catalog.get(k));
        }
    }

    @Test public void holidays() {
        assertEquals("h_rh", Catalog.holidayKeyForDay(d(2026, 9, 12), false));
        assertEquals("h_yk", Catalog.holidayKeyForDay(d(2026, 9, 21), false));
        assertEquals("h_tisha", Catalog.holidayKeyForDay(d(2026, 7, 23), false));
        Object[] up = Catalog.upcomingHoliday(d(2026, 10, 27), false, 60);
        assertNotNull(up);
        assertEquals("h_chanukah", up[0]);
    }
}
