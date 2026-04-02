package org.example.session;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import java.util.Calendar;

public class SessionPersistence {
    private static final Json json = new Json();
    private static final String STANDARD_FILE = "save_standard.json";
    private static final String DAILY_18_FILE = "save_daily_18.json";
    private static final String DAILY_9_FILE = "save_daily_9.json";
    private static final String DAILY_1_FILE = "save_daily_1.json";

    static String getFileName(GameSession.GameMode mode) {
        return switch (mode) {
            case STANDARD_18 -> STANDARD_FILE;
            case DAILY_18 -> DAILY_18_FILE;
            case DAILY_9 -> DAILY_9_FILE;
            case DAILY_1 -> DAILY_1_FILE;
        };
    }

    public static void saveSession(GameSession session) {
        if (session == null) return;
        String fileName = getFileName(session.getMode());
        FileHandle file = Gdx.files.local(fileName);
        file.writeString(json.prettyPrint(session), false);
    }

    public static GameSession loadSession(GameSession.GameMode mode) {
        String fileName = getFileName(mode);
        FileHandle file = Gdx.files.local(fileName);
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

    public static long getTodayTimestamp() {
        Calendar cal = Calendar.getInstance();
        return (long) cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
    }
}
