package com.gearygolf.golf.scoreBoard;

import com.badlogic.gdx.Gdx;

/**
 * Persists a player's computed skill rating label (e.g. "NOVICE", "ADVANCED -4", "NOVICE +")
 * to device storage using LibGDX Preferences (one entry per uid).
 *
 * Written by ProfileRenderer when all difficulty histories have loaded.
 * Read by GolfGame on login and after closing the profile screen.
 */
public final class SkillRatingStore {

    private static final String PREFS_NAME    = "geary_golf_skill_ratings";
    private static final String DEFAULT_LABEL = "NOVICE";

    private SkillRatingStore() {}

    public static void save(String uid, String label) {
        if (uid == null || uid.isEmpty()) return;
        Gdx.app.getPreferences(PREFS_NAME).putString(uid, label).flush();
    }

    /** Returns the stored label, or "NOVICE" if none exists yet. */
    public static String load(String uid) {
        if (uid == null || uid.isEmpty()) return DEFAULT_LABEL;
        return Gdx.app.getPreferences(PREFS_NAME).getString(uid, DEFAULT_LABEL);
    }
}
