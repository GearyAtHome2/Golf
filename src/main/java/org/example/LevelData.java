package org.example;

import com.badlogic.gdx.math.Vector3;

public class LevelData {
    public enum Archetype {
        BUSH_WORLD,
        MOGUL_HIGHLANDS,
        STANDARD_LINKS,
        REDWOOD_VALLEY,
        CLIFFSIDE_BLUFF,
        ISLAND_COAST
    }

    public enum TerrainAlgorithm {
        SMOOTH_SINE,    // The original simple style
        MULTI_WAVE,     // The 10-layer overlapping waves
        TERRACED,       // Flat plateaus with steep drops
        MOUNDS          // Sharp, isolated peaks
    }

    private Archetype archetype;
    private TerrainAlgorithm terrainAlgorithm;
    private long seed;
    private int nBunkers;
    private float waterLevel;
    private float undulation;
    private float treeDensity;
    private float treeHeight;
    private float foliageRadius;
    private float trunkRadius;
    private float holeSize;
    private float hillFrequency;
    private float maxHeight;

    private float teeHeight;
    private float greenHeight;
    private float fairwayWidth;

    // Wind vector (x, 0, z)
    private Vector3 wind = new Vector3();

    public Archetype getArchetype() { return archetype; }
    public void setArchetype(Archetype archetype) { this.archetype = archetype; }

    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }

    public int getnBunkers() { return nBunkers; }
    public void setnBunkers(int nBunkers) { this.nBunkers = nBunkers; }

    public float getWaterLevel() { return waterLevel; }
    public void setWaterLevel(float waterLevel) { this.waterLevel = waterLevel; }

    public float getUndulation() { return undulation; }
    public void setUndulation(float undulation) { this.undulation = undulation; }

    public float getTreeDensity() { return treeDensity; }
    public void setTreeDensity(float treeDensity) { this.treeDensity = treeDensity; }

    public float getTreeHeight() { return treeHeight; }
    public void setTreeHeight(float treeHeight) { this.treeHeight = treeHeight; }

    public float getFoliageRadius() { return foliageRadius; }
    public void setFoliageRadius(float foliageRadius) { this.foliageRadius = foliageRadius; }

    public float getTrunkRadius() { return trunkRadius; }
    public void setTrunkRadius(float trunkRadius) { this.trunkRadius = trunkRadius; }

    public float getHoleSize() { return holeSize; }
    public void setHoleSize(float holeSize) { this.holeSize = holeSize; }

    public float getHillFrequency() { return hillFrequency; }
    public void setHillFrequency(float hillFrequency) { this.hillFrequency = hillFrequency; }

    public float getMaxHeight() { return maxHeight; }
    public void setMaxHeight(float maxHeight) { this.maxHeight = maxHeight; }

    public float getTeeHeight() { return teeHeight; }
    public void setTeeHeight(float teeHeight) { this.teeHeight = teeHeight; }

    public float getGreenHeight() { return greenHeight; }
    public void setGreenHeight(float greenHeight) { this.greenHeight = greenHeight; }

    public float getFairwayWidth() { return fairwayWidth; }
    public void setFairwayWidth(float fairwayWidth) { this.fairwayWidth = fairwayWidth; }

    public Vector3 getWind() { return wind; }
    public void setWind(Vector3 wind) { this.wind.set(wind); }

    public TerrainAlgorithm getTerrainAlgorithm() { return terrainAlgorithm; }
    public void setTerrainAlgorithm(TerrainAlgorithm algorithm) { this.terrainAlgorithm = algorithm; }
}