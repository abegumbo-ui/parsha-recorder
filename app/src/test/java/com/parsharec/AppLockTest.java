package com.parsharec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class AppLockTest {
    @Test public void sameInputSameHashDifferentInputOrSaltDiffers() {
        byte[] salt = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        byte[] other = salt.clone();
        other[0] = 9;
        assertArrayEquals(AppLock.hash("1234", salt), AppLock.hash("1234", salt));
        assertFalse(java.util.Arrays.equals(AppLock.hash("1234", salt), AppLock.hash("12345", salt)));
        assertFalse(java.util.Arrays.equals(AppLock.hash("1234", salt), AppLock.hash("1234", other)));
    }

    @Test public void hexRoundTrips() {
        byte[] b = {0, 1, (byte) 0xab, (byte) 0xff, 127, (byte) 0x80};
        assertEquals("0001abff7f80", AppLock.hex(b));
        assertArrayEquals(b, AppLock.unhex(AppLock.hex(b)));
    }

    @Test public void veryLongPinWorks() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < AppLock.MAX_LEN; i++) sb.append(i % 10);
        assertEquals(32, AppLock.hash(sb.toString(), new byte[16]).length);
    }
}
