package com.parsharec;

import android.content.SharedPreferences;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PIN for the app lock. Only a salted PBKDF2 hash is stored, never the PIN. There is deliberately no recovery path.
 * Repeated wrong guesses add a growing wait (30 s after 5 misses, doubling, capped at one hour).
 */
final class AppLock {
    private static final int ITERATIONS = 20000;
    static final int MAX_LEN = 64;

    private final SharedPreferences p;

    AppLock(SharedPreferences prefs) { p = prefs; }

    boolean enabled() { return p.contains("pin_hash"); }

    void set(String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        p.edit().putString("pin_salt", hex(salt)).putString("pin_hash", hex(hash(pin, salt)))
                .putInt("pin_fails", 0).putLong("pin_until", 0).commit();
    }

    void clear() {
        p.edit().remove("pin_salt").remove("pin_hash").remove("pin_fails").remove("pin_until").commit();
    }

    /** Milliseconds the user still has to wait before another attempt is allowed (0 when free to try). */
    long waitMs() { return Math.max(0, p.getLong("pin_until", 0) - System.currentTimeMillis()); }

    /** Checks a guess and keeps the miss counter. Callers should honour {@link #waitMs} first. */
    boolean check(String pin) {
        String salt = p.getString("pin_salt", null), want = p.getString("pin_hash", null);
        if (salt == null || want == null) return true;
        boolean ok = MessageDigest.isEqual(hash(pin, unhex(salt)), unhex(want));
        if (ok) {
            p.edit().putInt("pin_fails", 0).putLong("pin_until", 0).apply();
        } else {
            int fails = p.getInt("pin_fails", 0) + 1;
            long until = 0;
            if (fails >= 5) until = System.currentTimeMillis() + Math.min(3600_000L, 30_000L << Math.min(fails - 5, 7));
            p.edit().putInt("pin_fails", fails).putLong("pin_until", until).commit();
        }
        return ok;
    }

    static byte[] hash(String pin, byte[] salt) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static byte[] unhex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }
}
