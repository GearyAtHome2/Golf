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
            case ISLAND_COAST:
                algo = r.nextBoolean() ? LevelData.TerrainAlgorithm.SMOOTH_SINE : LevelData.TerrainAlgorithm.MULTI_WAVE;
                break;
            case BUSH_WORLD:
            case BUNKER_ISLANDS:
                algo = LevelData.TerrainAlgorithm.MULTI_WAVE;
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
        float fairwayWidth = 45.0f;
        float undulation = r.nextFloat() * 0.4f + 0.2f;

        // Default bunker count logic (can be overridden in switch)
        int bunkerCount = r.nextInt(10) + 5;

        // --- 2. Archetype Specific Tuning ---
        switch (selectedType) {
            case ISLAND_COAST:
                teeH = 15.0f + r.nextFloat() * 10.0f;
                greenH = 15.0f + r.nextFloat() * 5.0f;
                maxWindSpeed = 30.0f;
                hillFreq = 0.02f;
                maxH = 15.0f;
                treeDensity = 0.03f;
                fairwayWidth = 38.0f;
                undulation = 0.6f;
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
                fairwayWidth = 30.0f;
                undulation = 0.4f;
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
                fairwayWidth = 24.0f;
                break;

            case MOGUL_HIGHLANDS:
                teeH = 20.0f;
                greenH = 20.0f;
                maxWindSpeed = 18.0f;
                hillFreq = 0.08f;
                maxH = 18.0f;
                treeDensity = 0.05f;
                fairwayWidth = 55.0f;
                undulation = 0.2f;
                break;

            case BUSH_WORLD:
                treeH = 4.0f;
                foliageR = 3.5f;
                trunkR = 0.5f;
                hillFreq = 0.045f;
                treeDensity = 0.65f;
                fairwayWidth = 25.0f;
                break;

            case BUNKER_ISLANDS:
                teeH = 8.0f;
                greenH = 12.0f;
                maxWindSpeed = 20.0f;
                treeH = 5.0f;
                foliageR = 4.0f;
                trunkR = 0.6f;
                hillFreq = 0.06f;
                maxH = 12.0f;
                treeDensity = 0.10f;
                fairwayWidth = 20.0f;
                undulation = 0.8f;
                bunkerCount = 25 + r.nextInt(15);
                break;

            case STANDARD_LINKS:
            default:
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
        data.setFairwayWidth(fairwayWidth);
        data.setUndulation(undulation);
        data.setHoleSize(0.6f);
        data.setnBunkers(bunkerCount); // Using the variable that might have been overridden

        float lowerBound = Math.min(teeH, greenH);
        data.setWaterLevel(lowerBound - (3.0f + r.nextFloat() * 2.0f));

        float angle = r.nextFloat() * MathUtils.PI2;
        float strength = r.nextFloat() * maxWindSpeed;
        data.setWind(new Vector3(MathUtils.cos(angle) * strength, 0, MathUtils.sin(angle) * strength));

        System.out.println("Generated " + data.getArchetype() + " | Algo: " + data.getTerrainAlgorithm() + " | Seed: " + data.getSeed() + " | Bunkers: " + bunkerCount);
        return data;
    }
}