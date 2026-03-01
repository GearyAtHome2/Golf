package org.example;

public enum Club {
    // Loft (deg), PowerMult (Efficiency/Speed)
    IRON_2("2 Iron", 12.5f, 32.0f), // Real stingers use low lofts, but 2 is a putter. 12.5 is a 2-iron.
    DRIVER("Driver", 10.5f, 31.0f),
    WOOD_3("3 Wood", 15.0f, 28.5f),
    WOOD_5("5 Wood", 18.0f, 26.0f),
    HYBRID_3("3 Hybrid", 21.0f, 24.0f),
    IRON_5("5 Iron", 26.0f, 21.0f),
    IRON_6("6 Iron", 30.0f, 19.5f),
    IRON_7("7 Iron", 34.0f, 18.0f),
    IRON_8("8 Iron", 38.0f, 16.5f),
    IRON_9("9 Iron", 42.0f, 15.0f),
    PWEDGE("Pitching Wedge", 46.0f, 13.5f),
    GWEDGE("Gap Wedge", 50.0f, 12.0f),
    SWEDGE("Sand Wedge", 54.0f, 10.5f),
    LWEDGE("Lob Wedge", 58.0f, 9.0f),
    PUTTER("Putter", 2f, 8.0f); // Putters need ~3-4 deg to lift ball out of its 'indent' on grass

    public final String name;
    public final float loft;
    public final float powerMult;

    Club(String name, float loft, float powerMult) {
        this.name = name;
        this.loft = loft;
        this.powerMult = powerMult;
    }
}