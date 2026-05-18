package com.gearygolf.golf.glamour;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

public final class SoundPrefs {

    private static final String PREFS_NAME  = "golf_sound";
    private static final String KEY_MASTER  = "vol_master";
    private static final String KEY_SFX     = "vol_sfx";
    private static final String KEY_AMBIENT = "vol_ambient";
    private static final String KEY_ARCADE   = "arcade_flight";
    private static final String KEY_CINEMATIC = "cinematic_mode";

    // Advanced per-channel scales
    private static final String KEY_BOUNCE          = "adv_bounce";
    private static final String KEY_ARCADE_AIRBORNE = "adv_arcade_airborne";
    private static final String KEY_AIR_WHOOSH      = "adv_air_whoosh";
    private static final String KEY_AMB_TREES       = "adv_amb_trees";
    private static final String KEY_AMB_WATER       = "adv_amb_water";
    private static final String KEY_BIRDSONG        = "adv_birdsong";

    private SoundPrefs() {}

    public static void save(float master, float sfx, float ambient, boolean arcadeFlight, boolean cinematic) {
        Preferences p = Gdx.app.getPreferences(PREFS_NAME);
        p.putFloat(KEY_MASTER,      master);
        p.putFloat(KEY_SFX,         sfx);
        p.putFloat(KEY_AMBIENT,     ambient);
        p.putBoolean(KEY_ARCADE,    arcadeFlight);
        p.putBoolean(KEY_CINEMATIC, cinematic);
        p.flush();
    }

    public static void saveAdvanced(float bounce, float arcadeAirborne, float airWhoosh,
                                    float ambTrees, float ambWater, float birdsong) {
        Preferences p = Gdx.app.getPreferences(PREFS_NAME);
        p.putFloat(KEY_BOUNCE,          bounce);
        p.putFloat(KEY_ARCADE_AIRBORNE, arcadeAirborne);
        p.putFloat(KEY_AIR_WHOOSH,      airWhoosh);
        p.putFloat(KEY_AMB_TREES,       ambTrees);
        p.putFloat(KEY_AMB_WATER,       ambWater);
        p.putFloat(KEY_BIRDSONG,        birdsong);
        p.flush();
    }

    public static float   loadMaster()       { return prefs().getFloat(KEY_MASTER,   1f); }
    public static float   loadSfx()          { return prefs().getFloat(KEY_SFX,      1f); }
    public static float   loadAmbient()      { return prefs().getFloat(KEY_AMBIENT,  1f); }
    public static boolean loadArcadeFlight()  { return prefs().getBoolean(KEY_ARCADE,    true);  }
    public static boolean loadCinematicMode() { return prefs().getBoolean(KEY_CINEMATIC, false); }

    public static float loadBounce()          { return prefs().getFloat(KEY_BOUNCE,          1f); }
    public static float loadArcadeAirborne()  { return prefs().getFloat(KEY_ARCADE_AIRBORNE, 1f); }
    public static float loadAirWhoosh()       { return prefs().getFloat(KEY_AIR_WHOOSH,      1f); }
    public static float loadAmbTrees()        { return prefs().getFloat(KEY_AMB_TREES,       1f); }
    public static float loadAmbWater()        { return prefs().getFloat(KEY_AMB_WATER,       1f); }
    public static float loadBirdsong()        { return prefs().getFloat(KEY_BIRDSONG,        1f); }

    private static Preferences prefs() { return Gdx.app.getPreferences(PREFS_NAME); }
}
