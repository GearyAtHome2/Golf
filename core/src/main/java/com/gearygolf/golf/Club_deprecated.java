package com.gearygolf.golf;

/**
 * DEPRECATED — original Club power values calibrated for the classic spindicator system.
 * Kept for reference when classic mode is re-enabled.
 *
 * In the spindicator system, MinigameEngine applied tier multipliers on top of these values:
 *   baseDiff >= 1.6 → PERFECTION zone × 1.50
 *   baseDiff >= 1.3 → SUPER zone     × 1.25
 *   baseDiff >= 1.0 → GREAT zone     × 1.10
 *   baseDiff <  1.0 → GOOD only      × 1.00
 *
 * The new swing gesture system bakes these multipliers directly into powerMult (see Club.java).
 */
public enum Club_deprecated {
    // Name, Loft, Power, BaseDiff, TeeBonus, SpinMult
    DRIVER  ("Driver",         10.5f, 35.0f, 1.9f,  0.6f, 0.55f),
    WOOD_3  ("3 Wood",         15.0f, 30.0f, 1.7f,  0.4f, 0.65f),
    IRON_2  ("2 Iron",         14.5f, 26.4f, 1.9f,  0.1f, 1.00f),
    WOOD_5  ("5 Wood",         18.0f, 25.5f, 1.65f, 0.3f, 0.65f),
    IRON_3  ("3 Iron",         20.0f, 22.0f, 1.75f, 0.1f, 1.00f),
    HYBRID_3("3 Hybrid",       21.0f, 21.0f, 1.60f, 0.3f, 0.80f),
    IRON_4  ("4 Iron",         24.0f, 19.5f, 1.6f,  0.0f, 1.00f),
    IRON_5  ("5 Iron",         27.0f, 21.4f, 1.4f,  0.0f, 1.00f),
    IRON_6  ("6 Iron",         31.0f, 19.9f, 1.3f,  0.0f, 1.00f),
    IRON_7  ("7 Iron",         35.0f, 21.0f, 1.2f,  0.0f, 1.00f),
    IRON_8  ("8 Iron",         39.0f, 18.9f, 1.1f,  0.0f, 1.00f),
    IRON_9  ("9 Iron",         43.0f, 17.0f, 1.0f,  0.0f, 1.00f),
    PWEDGE  ("Pitching Wedge", 46.0f, 16.0f, 0.9f,  0.0f, 0.90f),
    GWEDGE  ("Gap Wedge",      50.0f, 14.5f, 0.85f, 0.0f, 0.85f),
    SWEDGE  ("Sand Wedge",     54.0f, 13.0f, 0.8f,  0.0f, 0.80f),
    LWEDGE  ("Lob Wedge",      58.0f, 11.5f, 0.9f,  0.0f, 0.75f),
    PUTTER  ("Putter",          0.0f,  8.0f, 0.4f,  0.0f, 1.00f);

    public final String name;
    public final float loft;
    public final float powerMult;
    public final float baseDifficulty;
    public final float teeBonus;
    public final float spinMult;

    Club_deprecated(String name, float loft, float powerMult, float baseDiff, float teeBonus, float spinMult) {
        this.name = name;
        this.loft = loft;
        this.powerMult = powerMult;
        this.baseDifficulty = baseDiff;
        this.teeBonus = teeBonus;
        this.spinMult = spinMult;
    }
}
