package com.stocksim.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 password hashing. Passwords are never stored or compared in plain text.
 * Demonstrates: java.security.MessageDigest, byte to hex conversion.
 */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    public static String hash(String plainPassword) {
        if (plainPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(plainPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to exist on every Java platform
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    /** Hashes the supplied password and compares it with the stored hash. */
    public static boolean matches(String plainPassword, String storedHash) {
        return storedHash != null && storedHash.equalsIgnoreCase(hash(plainPassword));
    }
}
