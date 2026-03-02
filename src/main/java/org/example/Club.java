package org.example;

public enum Club {
    // Name, Loft, Power, BaseDiff, TeeBonus
    DRIVER("Driver", 10.5f, 35.0f, 1.8f, 0.6f), // Brutal off fairway, okay on tee
    WOOD_3("3 Wood", 15.0f, 31.0f, 1.6f, 0.4f),
    WOOD_5("5 Wood", 18.0f, 28.5f, 1.5f, 0.3f),
    IRON_2("2 Iron", 12.5f, 32.0f, 1.9f, 0.1f), // High skill floor
    HYBRID_3("3 Hybrid", 21.0f, 26.0f, 1.3f, 0.3f),
    IRON_5("5 Iron", 26.0f, 23.0f, 1.4f, 0.0f),
    IRON_6("6 Iron", 30.0f, 21.5f, 1.3f, 0.0f),
    IRON_7("7 Iron", 34.0f, 20.0f, 1.2f, 0.0f),
    IRON_8("8 Iron", 38.0f, 18.5f, 1.1f, 0.0f),
    IRON_9("9 Iron", 42.0f, 17.0f, 1.0f, 0.0f),
    PWEDGE("Pitching Wedge", 46.0f, 15.5f, 0.9f, 0.0f),
    GWEDGE("Gap Wedge", 50.0f, 14.0f, 0.85f, 0.0f),
    SWEDGE("Sand Wedge", 54.0f, 12.5f, 0.8f, 0.0f),
    LWEDGE("Lob Wedge", 58.0f, 11.0f, 0.9f, 0.0f), // Slightly harder than SW due to precision needed
    PUTTER("Putter", 0.9f, 8.0f, 0.4f, 0.0f);

    public final String name;
    public final float loft;
    public final float powerMult;
    public final float baseDifficulty;
    public final float teeBonus;

    Club(String name, float loft, float powerMult, float baseDiff, float teeBonus) {
        this.name = name;
        this.loft = loft;
        this.powerMult = powerMult;
        this.baseDifficulty = baseDiff;
        this.teeBonus = teeBonus;
    }
}