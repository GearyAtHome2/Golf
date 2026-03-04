package org.example;

import com.badlogic.gdx.math.Vector3;

public class LevelData {
    public enum Archetype {
        STANDARD_LINKS,
        ISLAND_COAST,
        CLIFFSIDE_BLUFF,
        REDWOOD_VALLEY,
        MOGUL_HIGHLANDS,
        BUSH_WORLD,
        CRATER_FIELDS,
        SHADOW_CANYON,    // The Sunken Valley
        RAZORBACK_RIDGE   // The Raised Peak
    }

    public enum TerrainAlgorithm {
        SMOOTH_SINE,
        MULTI_WAVE,
        TERRACED,
        MOUNDS,
        RAISED_FAIRWAY,
        SUNKEN_FAIRWAY
    }

    private long seed;
    private Archetype archetype;
    private TerrainAlgorithm terrainAlgorithm;
    private float teeHeight;
    private float greenHeight;
    private float treeHeight;
    private float foliageRadius;
    private float trunkRadius;
    private float hillFrequency;
    private float maxHeight;
    private float treeDensity;
    private float maxFairwayWidth;
    private float minFairwayWidth;
    private float undulation;
    private float waterLevel;
    private float holeSize;
    private int nBunkers;
    private Vector3 wind;

    // Parameters for fairway control
    private float fairwayWiggle;
    private float fairwayRoughIslands;
    private float fairwayCohesion;
    private float fairwayBreakDensity;

    // Getters and Setters
    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }
    public Archetype getArchetype() { return archetype; }
    public void setArchetype(Archetype archetype) { this.archetype = archetype; }
    public TerrainAlgorithm getTerrainAlgorithm() { return terrainAlgorithm; }
    public void setTerrainAlgorithm(TerrainAlgorithm terrainAlgorithm) { this.terrainAlgorithm = terrainAlgorithm; }
    public float getTeeHeight() { return teeHeight; }
    public void setTeeHeight(float teeHeight) { this.teeHeight = teeHeight; }
    public float getGreenHeight() { return greenHeight; }
    public void setGreenHeight(float greenHeight) { this.greenHeight = greenHeight; }
    public float getTreeHeight() { return treeHeight; }
    public void setTreeHeight(float treeHeight) { this.treeHeight = treeHeight; }
    public float getFoliageRadius() { return foliageRadius; }
    public void setFoliageRadius(float foliageRadius) { this.foliageRadius = foliageRadius; }
    public float getTrunkRadius() { return trunkRadius; }
    public void setTrunkRadius(float trunkRadius) { this.trunkRadius = trunkRadius; }
    public float getHillFrequency() { return hillFrequency; }
    public void setHillFrequency(float hillFrequency) { this.hillFrequency = hillFrequency; }
    public float getMaxHeight() { return maxHeight; }
    public void setMaxHeight(float maxHeight) { this.maxHeight = maxHeight; }
    public float getTreeDensity() { return treeDensity; }
    public void setTreeDensity(float treeDensity) { this.treeDensity = treeDensity; }
    public float getMaxFairwayWidth() { return maxFairwayWidth; }
    public void setMaxFairwayWidth(float fairwayWidth) { this.maxFairwayWidth = fairwayWidth; }

    public float getMinFairwayWidth() { return minFairwayWidth; }
    public void setMinFairwayWidth(float fairwayWidth) { this.minFairwayWidth = fairwayWidth; }
    public float getUndulation() { return undulation; }
    public void setUndulation(float undulation) { this.undulation = undulation; }
    public float getWaterLevel() { return waterLevel; }
    public void setWaterLevel(float waterLevel) { this.waterLevel = waterLevel; }
    public float getHoleSize() { return holeSize; }
    public void setHoleSize(float holeSize) { this.holeSize = holeSize; }
    public int getnBunkers() { return nBunkers; }
    public void setnBunkers(int nBunkers) { this.nBunkers = nBunkers; }
    public Vector3 getWind() { return wind; }
    public void setWind(Vector3 wind) { this.wind = wind; }

    public float getFairwayWiggle() { return fairwayWiggle; }
    public void setFairwayWiggle(float fairwayWiggle) { this.fairwayWiggle = fairwayWiggle; }
    public float getFairwayRoughIslands() { return fairwayRoughIslands; }
    public void setFairwayRoughIslands(float fairwayRoughIslands) { this.fairwayRoughIslands = fairwayRoughIslands; }

    public float getFairwayCohesion() { return fairwayCohesion; }
    public void setFairwayCohesion(float fairwayCohesion) { this.fairwayCohesion = fairwayCohesion; }
    public float getFairwayBreakDensity() { return fairwayBreakDensity; }
    public void setFairwayBreakDensity(float fairwayBreakDensity) { this.fairwayBreakDensity = fairwayBreakDensity; }
}