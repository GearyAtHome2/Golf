package org.example.session;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import java.util.Calendar;

public class SessionPersistence {
    private static final Json json = new Json();
    private static final String STANDARD_FILE = "save_standard.json";

    /**
     * Returns the save filename for the given mode and user.
     * Daily modes are scoped by uid so two accounts on the same device don't share saves.
     * Passing an empty/null uid produces the legacy unscoped filename (used when not logged in).
     */
    static String getFileName(GameSession.GameMode mode, String uid) {
        boolean hasUid = uid != null && !uid.isEmpty();
        return switch (mode) {
            case STANDARD_18  -> STANDARD_FILE;
            case DAILY_18     -> hasUid ? "save_daily_18_" + uid + ".json" : "save_daily_18.json";
            case DAILY_9      -> hasUid ? "save_daily_9_"  + uid + ".json" : "save_daily_9.json";
            case DAILY_1      -> hasUid ? "save_daily_1_"  + uid + ".json" : "save_daily_1.json";
            case MULTIPLAYER_9 -> ""; // ephemeral — never saved to disk
        };
    }

    /** Convenience overload — no uid, used for standard saves and tests. */
    static String getFileName(GameSession.GameMode mode) {
        return getFileName(mode, "");
    }

    public static void saveSession(GameSession session, String uid) {
        if (session == null) return;
        String filename = getFileName(session.getMode(), uid);
        if (filename.isEmpty()) return; // ephemeral mode (e.g. MULTIPLAYER_9) — not persisted
        FileHandle file = Gdx.files.local(filename);
        file.writeString(json.prettyPrint(session), false);
    }

    public static GameSession loadSession(GameSession.GameMode mode, String uid) {
        String filename = getFileName(mode, uid);
        if (filename.isEmpty()) return null;
        FileHandle file = Gdx.files.local(filename);
        if (!file.exists()) return null;
        try {
            GameSession session = json.fromJson(GameSession.class, file.readString());
            if (mode == GameSession.GameMode.STANDARD_18) {
                if (session.isFinished()) { file.delete(); return null; }
            } else {
                if (session.getTimestamp() != getTodayTimestamp()) { file.delete(); return null; }
            }
            return session;
        } catch (Exception e) {
            Gdx.app.error("SessionPersistence", "Failed to load session", e);
            return null;
        }
    }

    /** Convenience overload for standard (non-daily) saves. */
    public static GameSession loadSession(GameSession.GameMode mode) {
        return loadSession(mode, "");
    }

    /**
     * Returns today's date as YYYYMMDD (e.g. 20260428) in UTC.
     * UTC is used so all players share the same daily seed regardless of timezone,
     * matching the server-side leaderboard window which also starts at UTC midnight.
     */
    public static long getTodayTimestamp() {
        Calendar cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        return (long) cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
    }
}
