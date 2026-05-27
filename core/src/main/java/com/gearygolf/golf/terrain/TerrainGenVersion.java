package com.gearygolf.golf.terrain;

import com.gearygolf.golf.terrain.level.LevelData;

/**
 * Versions the terrain generation algorithm so that shot export strings remain
 * replayable even after ClassicGenerator is changed.
 *
 * When making a breaking change to ClassicGenerator:
 *   1. Copy the current ClassicGenerator.java to archive/ClassicGeneratorV1.java
 *   2. Modify ClassicGenerator.java freely (becomes V2)
 *   3. Add V2(ClassicGeneratorV2::new) to this enum — always append, never reorder.
 *   4. Update CURRENT to V2.
 */
public enum TerrainGenVersion {

    V1(ClassicGenerator::new);

    public static final TerrainGenVersion CURRENT = V1;

    private final java.util.function.Function<LevelData, ITerrainGenerator> factory;

    TerrainGenVersion(java.util.function.Function<LevelData, ITerrainGenerator> factory) {
        this.factory = factory;
    }

    public ITerrainGenerator create(LevelData data) {
        return factory.apply(data);
    }

    /** Returns the version for the given ordinal, falling back to V1 for unknown values. */
    public static TerrainGenVersion fromOrdinal(int ord) {
        TerrainGenVersion[] vals = values();
        return (ord >= 0 && ord < vals.length) ? vals[ord] : V1;
    }
}
