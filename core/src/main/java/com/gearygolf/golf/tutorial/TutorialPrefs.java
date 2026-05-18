package com.gearygolf.golf.tutorial;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

public final class TutorialPrefs {

    private static final String PREFS_NAME  = "geary_golf_prefs";
    private static final String KEY_COMPLETE     = "tutorial_complete";
    private static final String KEY_FIRST_LOGIN  = "first_login_done";

    private TutorialPrefs() {}

    public static boolean isComplete() {
        return Gdx.app.getPreferences(PREFS_NAME).getBoolean(KEY_COMPLETE, false);
    }

    public static void markComplete() {
        Preferences p = Gdx.app.getPreferences(PREFS_NAME);
        p.putBoolean(KEY_COMPLETE, true);
        p.flush();
    }

    public static boolean isFirstLoginDone() {
        return Gdx.app.getPreferences(PREFS_NAME).getBoolean(KEY_FIRST_LOGIN, false);
    }

    public static void markFirstLoginDone() {
        Preferences p = Gdx.app.getPreferences(PREFS_NAME);
        p.putBoolean(KEY_FIRST_LOGIN, true);
        p.flush();
    }
}
