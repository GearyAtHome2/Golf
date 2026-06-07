package com.gearygolf.golf;

public enum Club {
    // Name, Loft, Power, BaseDiff, TeeBonus, SpinMult, NaturalAttackAngle
    //
    // Lofts: real-world modern cavity-back lofts.
    // Power: old spindicator values × tier multiplier, baked in for the swing gesture system.
    //   In the old system MinigameEngine applied powerMod on top of these (PERFECTION×1.50,
    //   SUPER×1.25, GREAT×1.10). In the new system powerMod=1.0 for PERFECT tempo, so the
    //   same max distances are reached by multiplying once here.
    //   Tier boundaries: baseDiff>=1.6 → ×1.50, >=1.3 → ×1.25, >=1.0 → ×1.10, <1.0 → ×1.00
    // SpinMult: woods/hybrids generate less spin per unit energy (shallow face geometry).
    //   Irons all stay at 1.0 — higher loft naturally raises sForce, no manual boost needed.
    // NaturalAttackAngle: arc-bottom position for this club (degrees). Positive = ascending
    //   blow (driver, putter), negative = descending (irons). Sets the default camera offset
    //   in the swing view; player can adjust ±5° via ball-placement drag.
    DRIVER  ("Driver",         10.5f, 52.5f,  1.9f,  0.6f, 0.75f,  2.0f),  // ~360  (35.0 × 1.50)
    WOOD_3  ("3 Wood",         15.0f, 45.0f,  1.7f,  0.4f, 0.75f,  0.5f),  // ~335  (30.0 × 1.50)
    IRON_2  ("2 Iron",         14.5f, 39.6f,  1.9f,  0.1f, 1.00f, -1.5f),  // ~300  (26.4 × 1.50)
    WOOD_5  ("5 Wood",         18.0f, 38.25f, 1.65f, 0.3f, 0.75f, -1.5f),  // ~300  (25.5 × 1.50)
    IRON_3  ("3 Iron",         20.0f, 33.0f,  1.75f, 0.1f, 1.00f, -2.0f),  // ~275  (22.0 × 1.50)
    HYBRID_3("3 Hybrid",       21.0f, 31.5f,  1.60f, 0.3f, 0.85f, -2.0f),  // ~265  (21.0 × 1.50)
    IRON_4  ("4 Iron",         24.0f, 29.25f, 1.6f,  0.0f, 1.00f, -2.5f),  // ~260  (19.5 × 1.50)
    IRON_5  ("5 Iron",         27.0f, 26.75f, 1.4f,  0.0f, 1.00f, -3.0f),  // ~245  (21.4 × 1.25)
    IRON_6  ("6 Iron",         31.0f, 24.875f,1.3f,  0.0f, 1.00f, -3.5f),  // ~228  (19.9 × 1.25)
    IRON_7  ("7 Iron",         35.0f, 23.1f,  1.2f,  0.0f, 1.00f, -4.0f),  // ~208  (21.0 × 1.10)
    IRON_8  ("8 Iron",         39.0f, 20.79f, 1.1f,  0.0f, 1.00f, -4.5f),  // ~188  (18.9 × 1.10)
    IRON_9  ("9 Iron",         43.0f, 18.7f,  1.0f,  0.0f, 1.00f, -5.0f),  // ~165  (17.0 × 1.10)
    PWEDGE  ("Pitching Wedge", 46.0f, 16.0f,  0.9f,  0.0f, 0.90f, -5.0f),  // ~145  (unchanged)
    GWEDGE  ("Gap Wedge",      50.0f, 14.5f,  0.85f, 0.0f, 0.85f, -5.5f),  // ~125  (unchanged)
    SWEDGE  ("Sand Wedge",     54.0f, 13.0f,  0.8f,  0.0f, 0.80f, -6.0f),  // ~105  (unchanged)
    LWEDGE  ("Lob Wedge",      58.0f, 11.5f,  0.9f,  0.0f, 0.77f, -6.5f),  // ~88   (unchanged)
    PUTTER  ("Putter",          0.0f,  6.0f,  0.4f,  0.0f, 1.00f,  1.0f);

    public final String name;
    public final float loft;
    public final float powerMult;
    public final float baseDifficulty;
    public final float teeBonus;
    /** Fraction of spin imparted relative to a standard iron (1.0). Woods and hybrids
     *  generate less spin per unit of impact energy due to shallower face geometry. */
    public final float spinMult;
    /** Natural arc-bottom attack angle for this club (degrees). Positive = ascending blow,
     *  negative = descending. Used to pre-offset the swing camera and seed the attack-angle
     *  calculation in ShotController. */
    public final float naturalAttackAngleDeg;

    Club(String name, float loft, float powerMult, float baseDiff, float teeBonus, float spinMult,
         float naturalAttackAngleDeg) {
        this.name = name;
        this.loft = loft;
        this.powerMult = powerMult;
        this.baseDifficulty = baseDiff;
        this.teeBonus = teeBonus;
        this.spinMult = spinMult;
        this.naturalAttackAngleDeg = naturalAttackAngleDeg;
    }
}
