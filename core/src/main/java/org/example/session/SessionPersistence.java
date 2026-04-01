package org.example.session;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

import java.util.Calendar;

public class SessionPersistence {
    private static final Json json = new Json();
    private static final String STANDARD_FILE = "save_standard.json";
    private static final String DAILY_FILE = "save_daily.json";

    public static void saveSession(GameSession session) {
        if (session == null) return;
        String fileName = (session.getMode() == GameSession.GameMode.STANDARD_18) ? STANDARD_FILE : DAILY_FILE;
        FileHandle file = Gdx.files.local(fileName);
        file.writeString(json.prettyPrint(session), false);
    }

    public static GameSession loadSession(GameSession.GameMode mode) {
        String fileName = (mode == GameSession.GameMode.STANDARD_18) ? STANDARD_FILE : DAILY_FILE;
        FileHandle file = Gdx.files.local(fileName);
        
        if (!file.exists()) return null;

        try {
            GameSession session = json.fromJson(GameSession.class, file.readString());
            
            if (mode == GameSession.GameMode.STANDARD_18) {
                if (session.isFinished()) {
                    file.delete();
                    return null;
                }
            } else if (mode == GameSession.GameMode.DAILY_CHALLENGE) {
                if (session.getTimestamp() != getTodayTimestamp()) {
                    file.delete();
                    return null;
                }
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