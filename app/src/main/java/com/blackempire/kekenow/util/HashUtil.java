package com.blackempire.kekenow.util;

import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** One-way SHA-256 hashing for device IDs. */
public final class HashUtil {

    private static final String TAG = "KekeNow";

    private HashUtil() { /* static utility */ }

    /**
     * Returns the lowercase hex SHA-256 digest of the input string,
     * or an empty string if SHA-256 is unavailable on this platform.
     */
    public static String sha256(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-256 not available", e);
            return "";
        }
    }
}
