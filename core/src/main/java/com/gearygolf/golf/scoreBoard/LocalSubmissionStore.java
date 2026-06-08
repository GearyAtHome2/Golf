package com.gearygolf.golf.scoreBoard;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.gearygolf.golf.session.SessionPersistence;

import java.util.EnumSet;
import java.util.Set;

/**
 * Persists which daily course types the current user has submitted today to a
 * per-user local file. This is the initial source of truth for button state:
 * buttons are locked immediately on app start without waiting for Firebase.
 *
 * File format: {"date":20260605,"submitted":["HOLES_18","HOLES_9"]}
 * The date field uses the same UTC YYYYMMDD integer as SessionPersistence so
 * the file is automatically stale-detected and deleted on a new day.
 *
 * All methods are safe to call from the GL thread.
 */
public final class LocalSubmissionStore {

    private LocalSubmissionStore() {}

    private static String fileName(String uid) {
        if (uid == null || uid.isEmpty()) return "";
        return "daily_submitted_" + uid + ".json";
    }

    /**
     * Loads the set of submitted course types for today.
     * Returns an empty (mutable) set if no valid local data exists.
     */
    public static Set<CourseType> load(String uid) {
        Set<CourseType> result = EnumSet.noneOf(CourseType.class);
        String name = fileName(uid);
        if (name.isEmpty()) return result;

        FileHandle file = Gdx.files.local(name);
        if (!file.exists()) return result;

        try {
            JsonValue root = new JsonReader().parse(file.readString());
            long storedDate = root.getLong("date", 0);
            if (storedDate != SessionPersistence.getTodayTimestamp()) {
                file.delete(); // stale — different day
                return result;
            }
            JsonValue arr = root.get("submitted");
            if (arr != null && arr.isArray()) {
                for (JsonValue v : arr) {
                    try {
                        result.add(CourseType.valueOf(v.asString()));
                    } catch (IllegalArgumentException ignored) {
                        Gdx.app.log("LocalSubmissionStore", "Unknown CourseType: " + v.asString());
                    }
                }
            }
        } catch (Exception e) {
            Gdx.app.error("LocalSubmissionStore", "Load error: " + e.getMessage());
        }
        return result;
    }

    /**
     * Writes the given set of submitted types for today.
     * Overwrites any existing file for this user.
     */
    public static void save(String uid, Set<CourseType> submitted) {
        String name = fileName(uid);
        if (name.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("{\"date\":").append(SessionPersistence.getTodayTimestamp());
        sb.append(",\"submitted\":[");
        boolean first = true;
        for (CourseType t : submitted) {
            if (!first) sb.append(",");
            sb.append("\"").append(t.name()).append("\"");
            first = false;
        }
        sb.append("]}");

        try {
            Gdx.files.local(name).writeString(sb.toString(), false);
        } catch (Exception e) {
            Gdx.app.error("LocalSubmissionStore", "Save error: " + e.getMessage());
        }
    }
}
