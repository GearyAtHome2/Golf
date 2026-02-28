package org.example;

public enum Club {
    DRIVER("Driver", 15f, 35f),
    WOOD_3("3 Wood", 19f, 30f),
    WOOD_5("5 Wood", 26f, 25f),
    HYBRID_3("3 Hybrid", 24f, 25f),
    IRON_5("5 Iron", 31f, 21.9f),
    IRON_6("6 Iron", 34f, 20.8f),
    IRON_7("7 Iron", 37f, 20f),
    IRON_8("8 Iron", 40f, 19f),
    IRON_9("9 Iron", 43f, 18f),
    PWEDGE("Pitching Wedge", 45f, 13f),
    GWEDGE("Gap Wedge", 50f, 12.6f),
    SWEDGE("Sand Wedge", 55f, 12.2f),
    LWEDGE("Lob Wedge", 60f, 12f),
    PUTTER("Putter", 2f, 6f);

    public final String name;
    public final float loft;
    public final float powerMult;

    Club(String name, float loft, float powerMult) {
        this.name = name;
        this.loft = loft;
        this.powerMult = powerMult;
    }
}