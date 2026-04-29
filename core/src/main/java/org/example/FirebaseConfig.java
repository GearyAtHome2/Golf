package org.example;

/**
 * Single source of truth for Firebase project constants.
 * API_KEY is set at startup by the platform launcher (AndroidLauncher) reading
 * from a gitignored key.properties file via BuildConfig — it is never hardcoded here.
 */
public final class FirebaseConfig {
    public static String API_KEY  = ""; // injected by AndroidLauncher before game start
    public static final String RTDB_URL = "https://geary-golf-default-rtdb.europe-west1.firebasedatabase.app";

    /** Escapes backslashes and double-quotes for embedding in JSON string values. */
    public static String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private FirebaseConfig() {}
}
