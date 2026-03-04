package org.example;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.Random;


public class LevelDataGenerator {

    public static LevelData createRandomLevelData() {
        return createFixedLevelData(System.nanoTime());
    }

    public static LevelData createFixedLevelData(long seed) {
        LevelData data = new LevelData();
        data.setSeed(seed);
        Random r = new Random(seed);

        LevelData.Archetype[] types = LevelData.Archetype.values();
        LevelData.Archetype selectedType = types[r.nextInt(types.length)];
        selectedType = LevelData.Archetype.RAZORBACK_RIDGE;
        data.setArchetype(selectedType);

        // --- 1. Select Algorithm based on Archetype ---
        LevelData.TerrainAlgorithm algo;
        switch (selectedType) {
            case CLIFFSIDE_BLUFF:
                algo = r.nextBoolean() ? LevelData.TerrainAlgorithm.TERRACED : LevelData.TerrainAlgorithm.MULTI_WAVE;
                break;
            case MOGUL_HIGHLANDS:
                algo = LevelData.TerrainAlgorithm.MOUNDS;
                break;
            case BUSH_WORLD:
            case ISLAND_COAST:
                algo = r.nextBoolean() ? LevelData.TerrainAlgorithm.SMOOTH_SINE : LevelData.TerrainAlgorithm.MULTI_WAVE;
                break;
            case CRATER_FIELDS:
                algo = LevelData.TerrainAlgorithm.SMOOTH_SINE;
                break;
            case SHADOW_CANYON:
                algo = LevelData.TerrainAlgorithm.SUNKEN_FAIRWAY;
                break;
            case RAZORBACK_RIDGE:
                algo = LevelData.TerrainAlgorithm.RAISED_FAIRWAY;
                break;
            default:
                algo = LevelData.TerrainAlgorithm.MULTI_WAVE;
                break;
        }
        data.setTerrainAlgorithm(algo);

        // Local defaults
        float teeH = 10.0f;
        float greenH = 10.0f;
        float maxWindSpeed = 15.0f;
        float treeH = 7.0f;
        float foliageR = 2.5f;
        float trunkR = 0.4f;
        float hillFreq = 0.035f;
        float maxH = 7.0f;
        float treeDensity = 0.15f;
        float maxFairwayWidth = 45.0f;
        float minFairwayWidth = 0f;
        float undulation = r.nextFloat() * 0.4f + 0.2f;
        int bunkerCount = r.nextInt(10) + 5;

        // Fairway variation defaults
        float fairwayWiggle = 0.3f;
        float islands = 0.1f;
        float cohesion = 0.5f;     // Default: moderate continuity
        float breakDensity = 0.3f; // Default: moderate frequency of gaps

        // --- 2. Archetype Specific Tuning ---
        switch (selectedType) {
            case ISLAND_COAST:
                teeH = 15.0f + r.nextFloat() * 10.0f;
                greenH = 15.0f + r.nextFloat() * 5.0f;
                maxWindSpeed = 30.0f;
                hillFreq = 0.02f;
                maxH = 15.0f;
                treeDensity = 0.03f;
                maxFairwayWidth = 35.0f;
                minFairwayWidth = 15.0f;
                undulation = 0.4f;
                fairwayWiggle = 0.28f;
                islands = 0f;
                cohesion = 0.9f;     // Very connected
                breakDensity = 0.1f;
                break;

            case CLIFFSIDE_BLUFF:
                teeH = 60.0f + r.nextFloat() * 30.0f;
                greenH = 5.0f + r.nextFloat() * 10.0f;
                maxWindSpeed = 22.0f;
                hillFreq = 0.012f;
                maxH = 10.0f;
                foliageR = 8.0f;
                trunkR = 1.2f;
                treeDensity = 0.25f;
                maxFairwayWidth = 40.0f;
                undulation = 0.4f;
                fairwayWiggle = 0.25f;
                islands = 0.05f;
                cohesion = 0.7f;
                breakDensity = 0.2f;
                break;

            case REDWOOD_VALLEY:
                teeH = 5.0f + r.nextFloat() * 5.0f;
                greenH = 5.0f + r.nextFloat() * 5.0f;
                maxWindSpeed = 5.0f;
                treeH = 30.0f + r.nextFloat() * 20.0f;
                foliageR = 10.0f;
                trunkR = 1.8f;
                hillFreq = 0.025f;
                maxH = 5.0f;
                treeDensity = 0.35f;
                maxFairwayWidth = 25.0f;
                minFairwayWidth = 10.0f;
                fairwayWiggle = 0.35f;
                islands = 0.0f;
                cohesion = 0.95f;    // Almost no breaks
                breakDensity = 0.05f;
                break;

            case MOGUL_HIGHLANDS:
                teeH = 20.0f;
                greenH = 20.0f;
                maxWindSpeed = 18.0f;
                hillFreq = 0.08f;
                maxH = 18.0f;
                treeDensity = 0.05f;
                maxFairwayWidth = 40.0f;
                minFairwayWidth = 25.0f;
                undulation = 0.2f;
                fairwayWiggle = 0.22f;
                islands = 0.3f;
                cohesion = 0.4f;
                breakDensity = 0.5f;
                break;

            case BUSH_WORLD:
                treeH = 4.0f;
                foliageR = 3.5f;
                trunkR = 0.5f;
                hillFreq = 0.045f;
                treeDensity = 0.95f;
                maxFairwayWidth = 35.0f;
                fairwayWiggle = 0.55f;
                islands = 0.7f;
                cohesion = 0.4f;     // Very fragmented "stepping stones"
                breakDensity = 0.8f;
                break;

            case CRATER_FIELDS:
                teeH = 8.0f;
                greenH = 12.0f;
                maxWindSpeed = 20.0f;
                treeH = 5.0f;
                foliageR = 4.0f;
                trunkR = 0.6f;
                hillFreq = 0.06f;
                maxH = 12.0f;
                treeDensity = 0.10f;
                maxFairwayWidth = 40.0f;
                undulation = 0.25f;
                bunkerCount = 25 + r.nextInt(15);
                fairwayWiggle = 0.1f;
                islands = 0.4f;
                cohesion = 0.9f;
                breakDensity = 0.7f;
                break;

            case SHADOW_CANYON:
                teeH = 0f;
                greenH = 10.0f + r.nextFloat() * 5.0f;
                maxWindSpeed = 6.0f;
                treeH = 4.0f + r.nextFloat() * 2.0f;
                treeDensity = 0.06f;
                foliageR = 3.0f;
                trunkR = 0.5f;
                hillFreq = 0.02f;
                maxH = 4.0f;
                maxFairwayWidth = 32.0f;
                minFairwayWidth = 5f;
                undulation = 0.5f;
                bunkerCount = 0;
                fairwayWiggle = 0.31f;
                islands = 0.0f;
                cohesion = 0.8f;
                breakDensity = 0.15f;
                break;

            case RAZORBACK_RIDGE:
                teeH = 11.0f;
                greenH = 35.0f + r.nextFloat() * 15.0f;
                maxWindSpeed = 35.0f;
                treeH = 12.0f + r.nextFloat() * 8.0f;
                treeDensity = 0.18f;
                foliageR = 5.0f;
                trunkR = 0.8f;
                hillFreq = 0.02f;
                maxH = 5.0f;
                maxFairwayWidth = 35.0f;
                minFairwayWidth = 0f;
                undulation = 0.6f;
                bunkerCount = 12 + r.nextInt(10);
                fairwayWiggle = 0.25f;
                islands = 0.1f;
                cohesion = 0.6f;
                breakDensity = 0.4f;
                break;

            case STANDARD_LINKS:
            default:
                fairwayWiggle = 0.1f;
                islands = 0.25f;
                maxFairwayWidth = 49f;
                cohesion = 1.3f;
                breakDensity = 0.25f;
                break;
        }

        // --- 3. Apply variables to LevelData ---
        data.setTeeHeight(teeH);
        data.setGreenHeight(greenH);
        data.setTreeHeight(treeH);
        data.setFoliageRadius(foliageR);
        data.setTrunkRadius(trunkR);
        data.setHillFrequency(hillFreq);
        data.setMaxHeight(maxH);
        data.setTreeDensity(treeDensity);
        data.setMaxFairwayWidth(maxFairwayWidth);
        data.setMinFairwayWidth(minFairwayWidth);
        data.setUndulation(undulation);
        data.setHoleSize(0.6f);
        data.setnBunkers(bunkerCount);

        // New fairway parameters
        data.setFairwayWiggle(fairwayWiggle);
        data.setFairwayRoughIslands(islands);
        data.setFairwayCohesion(cohesion);
        data.setFairwayBreakDensity(breakDensity);

        // Water logic
        float lowerBound = Math.min(teeH, greenH);
        data.setWaterLevel(lowerBound - (3.0f + r.nextFloat() * 2.0f));

        // Wind logic
        float angle = r.nextFloat() * MathUtils.PI2;
        float strength = r.nextFloat() * maxWindSpeed;
        data.setWind(new Vector3(MathUtils.cos(angle) * strength, 0, MathUtils.sin(angle) * strength));

        System.out.println("Generated " + data.getArchetype() + " | Algo: " + data.getTerrainAlgorithm() + " | Seed: " + data.getSeed() + " | Bunkers: " + bunkerCount);
        return data;
    }
}