package org.example;

public enum Club {
    DRIVER("Driver", 12f, 35f),
    WOOD_3("3 Wood", 15f, 30f),
    WOOD_5("5 Wood", 40f, 25f), // Your Baseline
    IRON_5("5 Iron", 25f, 20f),
    IRON_9("9 Iron", 45f, 15f),
    WEDGE("Wedge", 55f, 12f),
    PUTTER("Putter", 0f, 8f);

    public final String name;
    public final float loft;
    public final float powerMult;

    Club(String name, float loft, float powerMult) {
        this.name = name;
        this.loft = loft;
        this.powerMult = powerMult;
    }
}