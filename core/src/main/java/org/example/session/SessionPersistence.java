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
            case STANDARD_18 -> STANDARD_FILE;
            case DAILY_18    -> hasUid ? "save_daily_18_" + uid + ".json" : "save_daily_18.json";
            case DAILY_9     -> hasUid ? "save_daily_9_"  + uid + ".json" : "save_daily_9.json";
            case DAILY_1     -> hasUid ? "save_daily_1_"  + uid + ".json" : "save_daily_1.json";
        };
    }

    /** Convenience overload — no uid, used for standard saves and tests. */
    static String getFileName(GameSession.GameMode mode) {
        return getFileName(mode, "");
    }

    public static void saveSession(GameSession session, String uid) {
        if (session == null) return;
        FileHandle file = Gdx.files.local(getFileName(session.getMode(), uid));
        file.writeString(json.prettyPrint(session), false);
    }

    public static GameSession loadSession(GameSession.GameMode mode, String uid) {
        FileHandle file = Gdx.files.local(getFileName(mode, uid));
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

    public static long getTodayTimestamp() {
        Calendar cal = Calendar.getInstance();
        return (long) cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
    }
}
