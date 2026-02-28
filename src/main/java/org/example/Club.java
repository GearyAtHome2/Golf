package org.example;

public enum Club {
    DRIVER("Driver", 15f, 30f),
    WOOD_3("3 Wood", 19f, 26f),
    WOOD_5("5 Wood", 26f, 21f),
    HYBRID_3("3 Hybrid", 24f, 20f),
    IRON_5("5 Iron", 31f, 19.2f),
    IRON_6("6 Iron", 34f, 18.8f),
    IRON_7("7 Iron", 37f, 18f),
    IRON_8("8 Iron", 40f, 17f),
    IRON_9("9 Iron", 43f, 16f),
    PWEDGE("Pitching Wedge", 45f, 12.5f),
    GWEDGE("Gap Wedge", 50f, 12.4f),
    SWEDGE("Sand Wedge", 55f, 11.6f),
    LWEDGE("Lob Wedge", 60f, 11f),
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