package org.example;

public enum Club {
    STINGER("Stinger", 2f, 30f),
    DRIVER("Driver", 12f, 30f),
    WOOD_3("3 Wood", 19f, 26f),
    WOOD_5("5 Wood", 23f, 21f),
    HYBRID_3("3 Hybrid", 24f, 20f),
    IRON_5("5 Iron", 28, 19.2f),
    IRON_6("6 Iron",31f , 18.8f),
    IRON_7("7 Iron", 34f, 18f),
    IRON_8("8 Iron", 37f, 17f),
    IRON_9("9 Iron", 40f, 16f),
    PWEDGE("Pitching Wedge", 40f, 12.1f),
    GWEDGE("Gap Wedge", 45f, 11.4f),
    SWEDGE("Sand Wedge", 50f, 10.6f),
    LWEDGE("Lob Wedge", 55, 9f),
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