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

        // --- NEW: Select Algorithm based on Archetype ---
        LevelData.TerrainAlgorithm algo;
        switch (selectedType) {
            case CLIFFSIDE_BLUFF -> algo = r.nextBoolean() ? LevelData.TerrainAlgorithm.TERRACED : LevelData.TerrainAlgorithm.MULTI_WAVE;
            case ISLAND_COAST -> algo = r.nextBoolean() ? LevelData.TerrainAlgorithm.SMOOTH_SINE : LevelData.TerrainAlgorithm.MULTI_WAVE;
            default -> {
                LevelData.TerrainAlgorithm[] algos = LevelData.TerrainAlgorithm.values();
                algo = algos[r.nextInt(algos.length)];
            }
        }
        data.setTerrainAlgorithm(algo); // Assuming you add this setter to LevelData

        float teeH = 10.0f;
        float greenH = 10.0f;
        float maxWindSpeed = 15.0f;

        switch (selectedType) {
            case ISLAND_COAST:
                teeH = 8.0f + r.nextFloat() * 4.0f;
                greenH = 8.0f + r.nextFloat() * 4.0f;
                maxWindSpeed = 25.0f;
                data.setHillFrequency(0.03f);
                data.setMaxHeight(10.0f);
                data.setTreeDensity(0.05f);
                data.setFairwayWidth(45.0f);
                break;
            case CLIFFSIDE_BLUFF:
                teeH = 35.0f + r.nextFloat() * 15.0f;
                greenH = 5.0f + r.nextFloat() * 10.0f;
                maxWindSpeed = 22.0f;
                data.setHillFrequency(0.015f);
                data.setMaxHeight(8.0f);
                data.setTreeDensity(0.12f);
                data.setFairwayWidth(55.0f);
                break;
            default:
                data.setHillFrequency(0.035f);
                data.setMaxHeight(7.0f);
                data.setTreeDensity(0.2f);
                data.setFairwayWidth(40.0f);
                break;
        }

        data.setTeeHeight(teeH);
        data.setGreenHeight(greenH);
        data.setTreeHeight(7.0f);
        data.setFoliageRadius(2.5f);
        data.setTrunkRadius(0.4f);

        float lowerBound = Math.min(teeH, greenH);
        float waterLvl = lowerBound - (1.0f + r.nextFloat() * 2.0f);

        if (selectedType == LevelData.Archetype.ISLAND_COAST) {
            waterLvl = lowerBound - 0.5f;
        }

        data.setUndulation(r.nextFloat() * 0.4f + 0.2f);
        data.setWaterLevel(waterLvl);
        data.setnBunkers(r.nextInt(12) + 6);
        data.setHoleSize(0.6f);

        float angle = r.nextFloat() * MathUtils.PI2;
        float strength = r.nextFloat() * maxWindSpeed;
        data.setWind(new Vector3(MathUtils.cos(angle) * strength, 0, MathUtils.sin(angle) * strength));

        System.out.println("Generated terrain: " + data.getArchetype() + " using " + data.getTerrainAlgorithm() + ", seed: " + data.getSeed());
        return data;
    }
}