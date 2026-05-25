package com.gearygolf.golf;

public enum Club {
    // Name, Loft, Power, BaseDiff, TeeBonus, SpinMult
    //
    // Lofts: real-world modern cavity-back lofts.
    // Power: anchored at 5-iron = 21.0 (~245 units at PERFECTION).
    //   Long irons (2i-4i) have lower loft → more horizontal carry per unit of power, so
    //   their powerMult can be lower than you'd expect and they still out-distance the 5i.
    //   7i powerMult (20.0) exceeds 6i (19.5) to compensate for the steeper loft (35° vs 31°)
    //   consuming more energy as height/spin; the 7i still lands ~20 units shorter than the 6i.
    // SpinMult: woods/hybrids generate less spin per unit energy (shallow face geometry).
    //   Irons all stay at 1.0 — higher loft naturally raises sForce, no manual boost needed.
    DRIVER  ("Driver",         10.5f, 35.0f, 1.9f,  0.6f, 0.55f),  // ~360
    WOOD_3  ("3 Wood",         15.0f, 30.0f, 1.7f,  0.4f, 0.65f),  // ~335
    IRON_2  ("2 Iron",         14.5f, 26.4f, 1.9f,  0.1f, 1.00f),  // ~300 — driving iron, high skill floor
    WOOD_5  ("5 Wood",         18.0f, 25.5f, 1.65f, 0.3f, 0.65f),  // ~300
    IRON_3  ("3 Iron",         20.0f, 22.0f, 1.75f, 0.1f, 1.00f),  // ~275
    HYBRID_3("3 Hybrid",       21.0f, 21.0f, 1.60f, 0.3f, 0.80f),  // ~265
    IRON_4  ("4 Iron",         24.0f, 19.5f, 1.6f,  0.0f, 1.00f),  // ~260
    IRON_5  ("5 Iron",         27.0f, 21.4f, 1.4f,  0.0f, 1.00f),  // ~245 (anchor)
    IRON_6  ("6 Iron",         31.0f, 19.9f, 1.3f,  0.0f, 1.00f),  // ~228
    IRON_7  ("7 Iron",         35.0f, 21.0f, 1.2f,  0.0f, 1.00f),  // ~208 (powerMult > 6i to offset steeper loft)
    IRON_8  ("8 Iron",         39.0f, 18.9f, 1.1f,  0.0f, 1.00f),  // ~188
    IRON_9  ("9 Iron",         43.0f, 17.0f, 1.0f,  0.0f, 1.00f),  // ~165
    PWEDGE  ("Pitching Wedge", 46.0f, 16.0f, 0.9f,  0.0f, 0.90f),  // ~145
    GWEDGE  ("Gap Wedge",      50.0f, 14.5f, 0.85f, 0.0f, 0.85f),  // ~125
    SWEDGE  ("Sand Wedge",     54.0f, 13.0f, 0.8f,  0.0f, 0.80f),  // ~105
    LWEDGE  ("Lob Wedge",      58.0f, 11.5f, 0.9f,  0.0f, 0.75f),  // ~88
    PUTTER  ("Putter",          0.0f,  8.0f, 0.4f,  0.0f, 1.00f);

    public final String name;
    public final float loft;
    public final float powerMult;
    public final float baseDifficulty;
    public final float teeBonus;
    /** Fraction of spin imparted relative to a standard iron (1.0). Woods and hybrids
     *  generate less spin per unit of impact energy due to shallower face geometry. */
    public final float spinMult;

    Club(String name, float loft, float powerMult, float baseDiff, float teeBonus, float spinMult) {
        this.name = name;
        this.loft = loft;
        this.powerMult = powerMult;
        this.baseDifficulty = baseDiff;
        this.teeBonus = teeBonus;
        this.spinMult = spinMult;
    }
}
