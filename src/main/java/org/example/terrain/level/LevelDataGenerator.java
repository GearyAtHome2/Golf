package org.example.terrain.level;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain.TreeScheme;
import java.util.Random;

import static org.example.terrain.level.LevelData.Archetype.ISLAND_COAST;

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
//        selectedType = ISLAND_COAST;
        data.setArchetype(selectedType);

        // --- 1. Select Algorithm and TreeScheme based on Archetype ---
        LevelData.TerrainAlgorithm algo;
        TreeScheme scheme = TreeScheme.OAK; // Default

        switch (selectedType) {
            case CLIFFSIDE_BLUFF:
                algo = r.nextBoolean() ? LevelData.TerrainAlgorithm.TERRACED : LevelData.TerrainAlgorithm.MULTI_WAVE;
                scheme = TreeScheme.FIR;
                break;
            case MOGUL_HIGHLANDS:
                algo = LevelData.TerrainAlgorithm.MOUNDS;
                scheme = TreeScheme.BIRCH;
                break;
            case BUSH_WORLD:
                algo = r.nextBoolean() ? LevelData.TerrainAlgorithm.SMOOTH_SINE : LevelData.TerrainAlgorithm.MULTI_WAVE;
                scheme = r.nextBoolean() ? TreeScheme.OAK : TreeScheme.AUTUMN_MAPLE;
                break;
            case ISLAND_COAST:
                algo = r.nextBoolean() ? LevelData.TerrainAlgorithm.SMOOTH_SINE : LevelData.TerrainAlgorithm.MULTI_WAVE;
                scheme = TreeScheme.BIRCH;
                break;
            case CRATER_FIELDS:
                algo = LevelData.TerrainAlgorithm.SMOOTH_SINE;
                scheme = TreeScheme.DEAD_GRAY;
                break;
            case SHADOW_CANYON:
                algo = LevelData.TerrainAlgorithm.SUNKEN_FAIRWAY;
                scheme = TreeScheme.OAK;
                break;
            case RAZORBACK_RIDGE:
                algo = LevelData.TerrainAlgorithm.RAISED_FAIRWAY;
                scheme = TreeScheme.AUTUMN_MAPLE;
                break;
            case REDWOOD_VALLEY:
                algo = LevelData.TerrainAlgorithm.MULTI_WAVE;
                scheme = TreeScheme.REDWOOD;
                break;
            case MONOLITH_PLAINS:
                algo = LevelData.TerrainAlgorithm.MULTI_WAVE;
                scheme = TreeScheme.CHERRY_BLOSSOM;
                break;
            default:
                algo = LevelData.TerrainAlgorithm.MULTI_WAVE;
                scheme = r.nextBoolean() ? TreeScheme.OAK : TreeScheme.AUTUMN_MAPLE;
                break;
        }
        data.setTerrainAlgorithm(algo);
        data.setTreeScheme(scheme);

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

        float undulation = r.nextFloat() * 0.4f + 0.2f;
        int bunkerCount = 0;
        int distance = 500;
        int par = 1;


        // Fairway variation defaults
        float fairwayWiggle = 0.3f;
        float islands = 0.1f;
        float cohesion = 0.5f;
        float maxFairwayWidth = 45.0f;
        float minFairwayWidth = 0f;

        // --- 2. Archetype Specific Tuning ---
        switch (selectedType) {
            case ISLAND_COAST:
                teeH = 15.0f + r.nextFloat() * 10.0f;
                greenH = 15.0f + r.nextFloat() * 5.0f;
                maxWindSpeed = 22.0f;
                hillFreq = 0.02f;
                maxH = 15.0f;
                treeDensity = 0.03f;
                maxFairwayWidth = 45.0f;
                minFairwayWidth = 28.0f;
                undulation = 0.4f;
                fairwayWiggle = 0.25f + r.nextFloat() * 0.06f;
                islands = 0f;
                cohesion = 0.9f;
                distance = Math.round(400 + r.nextFloat() * 200);
                par = distance < 490? 4 : distance < 560 ? 5 : 6;
                break;


                //can't change distance here really - causes fairway to cut out before the cliff ends
            //par 4 as it stands
            case CLIFFSIDE_BLUFF:
                teeH = 60.0f + r.nextFloat() * 30.0f;
                greenH = 5.0f + r.nextFloat() * 10.0f;
                maxWindSpeed = 21.0f;
                hillFreq = 0.012f;
                maxH = 10.0f;
                foliageR = 8.0f;
                trunkR = 1.2f;
                treeDensity = 0.25f;
                maxFairwayWidth = 40.0f;
                undulation = 0.4f;
                fairwayWiggle = 0.20f + r.nextFloat() * 0.1f;
                islands = 0.05f;
                cohesion = 0.7f;
                distance = Math.round(410 + r.nextFloat() * 120);
                par = distance < 480? 4 : 5 ;
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
                maxFairwayWidth = 35.0f;
                minFairwayWidth = 25.0f;
                fairwayWiggle = 0.25f + r.nextFloat() * 0.2f;
                islands = 0.0f;
                cohesion = 0.95f;
                distance = Math.round(550 + r.nextFloat() * 200);
                par = 5;
                break;

            case MOGUL_HIGHLANDS:
                teeH = 20.0f;
                greenH = 20.0f;
                maxWindSpeed = 15.0f;
                hillFreq = 0.08f;
                maxH = 18.0f;
                treeDensity = 0.05f;
                maxFairwayWidth = 40.0f;
                minFairwayWidth = 27.0f;
                undulation = 0.2f;
                fairwayWiggle = 0.16f + r.nextFloat() * 0.08f;
                islands = 0.3f;
                cohesion = 0.6f;
                distance = Math.round(400 + r.nextFloat() * 150);
                par = distance < 490? 4 : 5 ;
                break;

            case BUSH_WORLD:
                treeH = 4.0f;
                foliageR = 3.5f;
                trunkR = 0.5f;
                maxWindSpeed = 14.0f;
                hillFreq = 0.045f;
                treeDensity = 0.9f;
                maxFairwayWidth = 35.0f;
                fairwayWiggle = 0.4f + r.nextFloat() * 0.3f;
                islands = 0.7f;
                cohesion = 0.42f;
                distance = Math.round(410 + r.nextFloat() * 80);
                par = 5 ;
                break;

            case CRATER_FIELDS:
                teeH = 8.0f;
                greenH = 12.0f;
                maxWindSpeed = 17.0f;
                treeH = 5.0f;
                foliageR = 4.0f;
                trunkR = 0.6f;
                hillFreq = 0.06f;
                maxH = 12.0f;
                treeDensity = 0.10f;
                maxFairwayWidth = 50.0f;
                undulation = 0.15f;
                fairwayWiggle = 0.1f + r.nextFloat() * 0.08f;
                islands = 0.4f;
                cohesion = 0.9f;
                distance = Math.round(500 + r.nextFloat() * 150);
                par = distance < 550? 4 : 5;
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
                maxFairwayWidth = 40.0f;
                minFairwayWidth = 10f;
                undulation = 0.5f;
                fairwayWiggle = 0.1f + r.nextFloat() * 0.1f;
                islands = 0.0f;
                cohesion = 0.8f;
                distance = Math.round(420 + r.nextFloat() * 120);
                par = 4;
                break;

            case RAZORBACK_RIDGE:
                teeH = 0f;
                greenH = 15.0f + r.nextFloat() * 15.0f;
                maxWindSpeed = 22.0f;
                treeH = 12.0f + r.nextFloat() * 8.0f;
                treeDensity = 0.18f;
                foliageR = 5.0f;
                trunkR = 0.8f;
                hillFreq = 0.02f;
                maxH = 5.0f;
                maxFairwayWidth = 48.0f;
                minFairwayWidth = 0f;
                undulation = 0.6f;
                bunkerCount = 12 + r.nextInt(10);
                fairwayWiggle = 0.15f + r.nextFloat() * 0.1f;
                islands = 0.1f;
                cohesion = 0.63f;
                distance = Math.round(450 + r.nextFloat() * 100);
                par = 6;
                break;

            case MONOLITH_PLAINS:
                teeH = 20f;
                greenH = 15.0f + r.nextFloat() * 10.0f;
                fairwayWiggle = 0.05f + r.nextFloat() * 0.1f;
                maxWindSpeed = 10.0f;
                islands = 0.25f;
                maxFairwayWidth = 60f;
                cohesion = 1.3f;
                undulation = 0.9f;
                bunkerCount = 1 + r.nextInt(4);
                hillFreq = 0.008f;
                maxH = 15f;
                distance = Math.round(460 + r.nextFloat() * 120);
                par = distance < 500? 4 : 5;
                break;

            case STANDARD_LINKS:
            default:
                fairwayWiggle = 0.05f + r.nextFloat() * 0.15f;
                islands = 0.25f;
                maxWindSpeed = 18.0f;
                maxFairwayWidth = 49f;
                cohesion = 1.3f;
                bunkerCount = 1 + r.nextInt(4);
                distance = Math.round(400 + r.nextFloat() * 350);
                par = distance < 500? 3 : distance < 620 ? 4 : 5;
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
        data.setDistance(distance);
        data.setPar(par);

        // New fairway parameters
        data.setFairwayWiggle(fairwayWiggle);
        data.setFairwayRoughIslands(islands);
        data.setFairwayCohesion(cohesion);

        // Water logic
        float lowerBound = Math.min(teeH, greenH);
        data.setWaterLevel(lowerBound - (3.0f + r.nextFloat() * 2.0f));

        // Wind logic
        float angle = r.nextFloat() * MathUtils.PI2;
        float strength = r.nextFloat() * maxWindSpeed;
        data.setWind(new Vector3(MathUtils.cos(angle) * strength, 0, MathUtils.sin(angle) * strength));

        System.out.println("Generated " + data.getArchetype() + " | Algo: " + data.getTerrainAlgorithm() + " | Scheme: " + data.getTreeScheme() + " | Seed: " + data.getSeed());
        return data;
    }
}