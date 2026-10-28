package com.parsharec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

public class CatalogTest {
    private static Calendar d(int y, int m, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, day, 12, 0, 0);
        return c;
    }

    @Test public void knownShabbosos() {
        assertEquals("p_VAYERA", Catalog.parshaKeyForWeek(d(2026, 10, 27), false));
        assertEquals("p_BERESHIS", Catalog.parshaKeyForWeek(d(2026, 10, 5), false));
        assertEquals("p_VAYAKHEL_PEKUDEI", Catalog.parshaKeyForWeek(d(2023, 3, 15), false));
        assertEquals("p_PEKUDEI", Catalog.parshaKeyForWeek(d(2025, 3, 26), false));
        assertEquals("p_NITZAVIM_VAYEILECH", Catalog.parshaKeyForWeek(d(2024, 9, 25), false));
        assertEquals("p_NITZAVIM", Catalog.parshaKeyForWeek(d(2025, 9, 15), false));
    }

    @Test public void yomTovShabbosHasNoParsha() {
        assertNull(Catalog.parshaKeyOnShabbos(d(2025, 4, 19), false));
    }

    @Test public void everyParshaFolderResolvesAndUsesAshkenazSpelling() {
        assertEquals("Vayeira", Catalog.get("p_VAYERA").en);
        assertEquals("Bereishis", Catalog.get("p_BERESHIS").en);
        assertEquals("Sukkos", Catalog.get("h_sukkot").en);
        assertEquals("Shavuos", Catalog.get("h_shavuot").en);
        // Every week of several years maps to a real folder (no nulls except yom tov Shabbosos).
        for (int y = 2024; y <= 2032; y++) {
            Calendar c = d(y, 1, 1);
            for (int i = 0; i < 53; i++) {
                String k = Catalog.parshaKeyForWeek(c, false);
                if (k != null) assertNotNull(k, Catalog.get(k));
                String k2 = Catalog.parshaKeyForWeek(c, true);
                if (k2 != null) assertNotNull(k2, Catalog.get(k2));
                c.add(Calendar.DAY_OF_MONTH, 7);
            }
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

    @Test public void shasHasFortyMasechtosAndTheDafYomiCycleLength() {
        Catalog.Masechta[] s = Catalog.shas();
        assertEquals(40, s.length);
        int total = 0;
        for (Catalog.Masechta m : s) {
            assertTrue(m.en, m.lastDaf >= m.firstDaf);
            total += m.dapim();
        }
        // The Daf Yomi cycle is 2,711 days; Meilah/Kinnim/Tamid/Midos share pages so allow the standard counting.
        assertTrue("total dapim " + total, total >= 2700 && total <= 2730);
        assertEquals("Berachos", s[0].en);
        assertEquals("Niddah", s[39].en);
    }

    @Test public void everyDafKeyResolvesAndHasAPath() {
        int n = 0;
        for (Catalog.Masechta m : Catalog.shas()) {
            for (int daf = m.firstDaf; daf <= m.lastDaf; daf++) {
                String key = Catalog.dafKey(m.idx, daf);
                Catalog.Folder f = Catalog.get(key);
                assertNotNull(key, f);
                assertEquals(m.idx, Catalog.masechtaOf(key));
                assertTrue(Catalog.path(key).startsWith("Gemara/" + m.en + "/Daf "));
                n++;
            }
            assertNull(Catalog.get(Catalog.dafKey(m.idx, m.lastDaf + 1)));
        }
        assertTrue(n > 2700);
    }

    @Test public void badKeysNeverCrash() {
        for (String k : new String[]{null, "", "d_", "d_x_y", "d_99_5", "d_0_9999", "p_NOPE", "zzz", "d_1_2_3"}) {
            assertNull(k, Catalog.get(k));
            assertEquals("g_general", Catalog.getOrGeneral(k).key);
            assertNotNull(Catalog.path(k));
        }
    }

    @Test public void dafYomiTodayIsAValidDaf() {
        String k = Catalog.dafYomiKey(d(2026, 10, 27));
        assertNotNull(k);
        assertNotNull(Catalog.get(k));
    }

    @Test public void pathsAreNested() {
        assertEquals("Parsha/Vayeira", Catalog.path("p_VAYERA"));
        assertEquals("Yom Tov/Pesach", Catalog.path("h_pesach"));
        assertEquals("Gemara/Berachos/Daf 005", Catalog.path(Catalog.dafKey(0, 5)));
        assertEquals("General", Catalog.path("g_general"));
    }
}
