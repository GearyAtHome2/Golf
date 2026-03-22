package org.example.terrain.level;

import com.badlogic.gdx.math.Vector3;
import org.example.terrain.objects.Tree.TreeScheme;

import java.util.*;

import static org.example.terrain.level.LevelData.Archetype.*;

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
//        selectedType = WETLANDS;
        data.setArchetype(selectedType);

        LevelData.TerrainAlgorithm algo;
        TreeScheme scheme;

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
            case PLUNGE_CENOTES:
                algo = LevelData.TerrainAlgorithm.SMOOTH_SINE;
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
            case REDWOOD_FOREST:
                algo = LevelData.TerrainAlgorithm.MULTI_WAVE;
                scheme = TreeScheme.REDWOOD;
                break;
            case MONOLITH_PLAINS:
                algo = LevelData.TerrainAlgorithm.MULTI_WAVE;
                scheme = TreeScheme.CHERRY_BLOSSOM;
                break;
            case WETLANDS:
                algo = LevelData.TerrainAlgorithm.DUNES;
                scheme = TreeScheme.OAK;
                break;
            case WHISTLING_ISLES:
                algo = LevelData.TerrainAlgorithm.CRAGGY_RIDGES;
                scheme = TreeScheme.OAK;
                break;
            case BIG_GRAPE_VINEYARDS:
                algo = LevelData.TerrainAlgorithm.DUNES;
                scheme = TreeScheme.GRAPEVINE;
                break;
            default:
                algo = LevelData.TerrainAlgorithm.MULTI_WAVE;
                scheme = r.nextBoolean() ? TreeScheme.OAK : TreeScheme.AUTUMN_MAPLE;
                break;
        }
        data.setTerrainAlgorithm(algo);
        data.setTreeScheme(scheme);

        // Defaults
        float teeH = 10.0f, greenH = 10.0f, treeH = 7.0f, foliageR = 2.5f, trunkR = 0.4f;
        float hillFreq = 0.035f, maxH = 7.0f, treeDensity = 0.15f, undulation = r.nextFloat() * 0.4f + 0.2f;
        float fairwayWiggle = 0.3f, islands = 0.1f, cohesion = 0.5f, maxFairwayWidth = 45.0f, minFairwayWidth = 0f;
        int bunkerCount = 0, distance = 500, par = 1;
        float bunkerDepth = 2.0f; // Default depth

        // Wind Bounds
        float windMin = 0f, windMax = 15f;
        float baseDifficultyIndex = 8.0f;

        // --- 2. Archetype Specific Tuning ---
        switch (selectedType) {
            case RAZORBACK_RIDGE:
                baseDifficultyIndex = 1f;
                teeH = 0f;
                greenH = 15.0f + r.nextFloat() * 15.0f;
                windMin = 8f;
                windMax = 18f;
                treeH = 12.0f;
                treeDensity = 0.18f;
                foliageR = 5.0f;
                trunkR = 0.8f;
                hillFreq = 0.02f;
                maxH = 5.0f;
                maxFairwayWidth = 48.0f;
                undulation = 1.2f;
                fairwayWiggle = 0.15f + r.nextFloat() * 0.1f;
                islands = 0.1f;
                cohesion = 0.63f;
                distance = Math.round(450 + r.nextFloat() * 100);
                par = 5;
                break;
            case CRATER_FIELDS:
                baseDifficultyIndex = 2f;
                teeH = 8.0f;
                greenH = 12.0f;
                windMin = 5f;
                windMax = 18f;
                treeH = 5.0f;
                foliageR = 4.0f;
                trunkR = 0.6f;
                hillFreq = 0.06f;
                maxH = 12.0f;
                treeDensity = 0.10f;
                maxFairwayWidth = 50.0f;
                undulation = 0.28f;
                fairwayWiggle = 0.1f + r.nextFloat() * 0.08f;
                islands = 0.4f;
                cohesion = 0.9f;
                distance = Math.round(500 + r.nextFloat() * 150);
                par = distance < 600 ? 4 : 5;
                break;
            case REDWOOD_FOREST:
                baseDifficultyIndex = 3f;
                teeH = 5.0f + r.nextFloat() * 5.0f;
                greenH = 5.0f + r.nextFloat() * 5.0f;
                windMin = 0f;
                windMax = 7f;
                treeH = 45.0f;
                foliageR = 10.0f;
                trunkR = 1.8f;
                hillFreq = 0.025f;
                maxH = 5.0f;
                treeDensity = 0.26f;
                maxFairwayWidth = 45.0f;
                minFairwayWidth = 25.0f;
                fairwayWiggle = 0.06f + r.nextFloat() * 0.02f;
                islands = 0.0f;
                cohesion = 0.95f;
                distance = Math.round(550 + r.nextFloat() * 200);
                par = 5;
                break;
            case CLIFFSIDE_BLUFF:
                baseDifficultyIndex = 2f;
                teeH = 60.0f + r.nextFloat() * 30.0f;
                greenH = 5.0f + r.nextFloat() * 10.0f;
                windMin = 7f;
                windMax = 18f;
                hillFreq = 0.012f;
                maxH = 10.0f;
                foliageR = 8.0f;
                trunkR = 1.2f;
                treeDensity = 0.25f;
                maxFairwayWidth = 40.0f;
                undulation = 0.4f;
                fairwayWiggle = 0.20f + r.nextFloat() * 0.1f;
                islands = 0.05f;
                cohesion = 0.9f;
                distance = Math.round(450 + r.nextFloat() * 80);
                par = 4;
                bunkerCount = 1 + r.nextInt(2);
                bunkerDepth = 0.8f;
                break;
            case BUSH_WORLD:
                baseDifficultyIndex = 3f;
                treeH = 6.0f;
                foliageR = 3f;
                trunkR = 0.5f;
                windMin = 2f;
                windMax = 15f;
                hillFreq = 0.045f;
                treeDensity = 0.82f;
                maxFairwayWidth = 35.0f;
                fairwayWiggle = 0.4f + r.nextFloat() * 0.3f;
                islands = 0.7f;
                cohesion = 0.42f;
                distance = Math.round(430 + r.nextFloat() * 160);
                par = distance < 515 ? 4 : 5;
                bunkerCount = 1;
                bunkerDepth = 2.2f;
                break;
            case ISLAND_COAST:
                baseDifficultyIndex = 3f;
                teeH = 15.0f + r.nextFloat() * 10.0f;
                greenH = 15.0f + r.nextFloat() * 5.0f;
                windMin = 6f;
                windMax = 23f;
                hillFreq = 0.02f;
                maxH = 15.0f;
                treeDensity = 0.03f;
                maxFairwayWidth = 55.0f;
                minFairwayWidth = 35.0f;
                undulation = 0.4f;
                fairwayWiggle = 0.25f + r.nextFloat() * 0.06f;
                islands = 0f;
                cohesion = 0.9f;
                distance = Math.round(500 + r.nextFloat() * 200);
                par = distance < 600 ? 4 : 5;
                break;
            case MONOLITH_PLAINS:
                baseDifficultyIndex = 5f;
                teeH = 20f;
                greenH = 15.0f + r.nextFloat() * 10.0f;
                windMin = 0f;
                windMax = 12f;
                fairwayWiggle = 0.05f + r.nextFloat() * 0.1f;
                islands = 0.25f;
                maxFairwayWidth = 60f;
                cohesion = 1.3f;
                undulation = 0.9f;
                bunkerCount = 1 + r.nextInt(4);
                bunkerDepth = 2.5f + r.nextFloat();
                hillFreq = 0.008f;
                maxH = 15f;
                distance = Math.round(600 + r.nextFloat() * 200);
                par = distance < 690 ? 4 : 5;
                break;
            case MOGUL_HIGHLANDS:
                baseDifficultyIndex = 6f;
                teeH = 20.0f;
                greenH = 20.0f;
                windMin = 1f;
                windMax = 16f;
                hillFreq = 0.08f;
                maxH = 18.0f;
                treeDensity = 0.05f;
                maxFairwayWidth = 40.0f;
                minFairwayWidth = 27.0f;
                undulation = 0.2f;
                fairwayWiggle = 0.16f + r.nextFloat() * 0.08f;
                islands = 0.3f;
                cohesion = 0.6f;
                distance = Math.round(650 + r.nextFloat() * 100);
                par = distance < 700 ? 4 : 5;
                bunkerCount = 1;
                bunkerDepth = 3.5f;
                break;
            case SHADOW_CANYON:
                baseDifficultyIndex = 7f;
                teeH = 0f;
                greenH = 15.0f + r.nextFloat() * 8.0f;
                windMin = 0f;
                windMax = 7f;
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
            case PLUNGE_CENOTES:
                baseDifficultyIndex = 2f;
                teeH = 5.0f + r.nextFloat() * 5.0f;
                greenH = 0f;
                windMin = 6f;
                windMax = 12f;
                treeH = 6.0f + r.nextFloat() * 2.0f;
                treeDensity = 0.13f;
                foliageR = 3.0f;
                trunkR = 0.5f;
                hillFreq = 0.02f;
                maxH = 4.0f;
                maxFairwayWidth = 35f;
                minFairwayWidth = 0;
                undulation = 0.5f;
                fairwayWiggle = 0.1f + r.nextFloat() * 0.1f;
                islands = 0.0f;
                cohesion = 0.6f;
                distance = Math.round(480 + r.nextFloat() * 100);
                par = 4;
                break;
            case WETLANDS:
                baseDifficultyIndex = 6f;
                teeH = 17.0f + r.nextFloat() * 2.0f;
                greenH = 13.0f + r.nextFloat() * 3.0f;
                windMin = 6f;
                windMax = 12f;
                treeH = 6.0f + r.nextFloat() * 2.0f;
                treeDensity = 0.13f;
                foliageR = 3.0f;
                trunkR = 0.5f;
                hillFreq = 0.06f;
                maxH = 4.0f;
                maxFairwayWidth = 50f;
                minFairwayWidth = 28;
                undulation = 1.4f;
                fairwayWiggle = 0.1f + r.nextFloat() * 0.1f;
                islands = 0.0f;
                cohesion = 1f;
                distance = Math.round(250 + r.nextFloat() * 80);
                bunkerCount = 1;
                bunkerDepth = 3.4f;
                par = 3;
                break;
            case WHISTLING_ISLES:
                baseDifficultyIndex = 1f;
                teeH = 0.2f;
                greenH = 1.0f;
                windMin = 11f;
                windMax = 18f;
                treeH = 8.0f + r.nextFloat() * 2.0f;
                treeDensity = 0.98f;
                foliageR = 2.7f;
                trunkR = 0.4f;
                hillFreq = 0.06f;
                maxH = 4.0f;
                maxFairwayWidth = 50f;
                minFairwayWidth = 28;
                undulation = 1.4f;
                fairwayWiggle = 0.1f + r.nextFloat() * 0.1f;
                islands = 0.0f;
                cohesion = 1f;
                distance = Math.round(600 + r.nextFloat() * 100);
                par = 5;
                break;
            case BIG_GRAPE_VINEYARDS:
                baseDifficultyIndex = 7f;
                teeH = 22f;
                greenH = 1.0f;
                windMin = 2f;
                windMax = 8f;
                treeH = 1.5f + r.nextFloat() * 0.2f;
                treeDensity = 0.2f;
                foliageR = 1f;
                trunkR = 0.2f;
                hillFreq = 0.06f;
                maxH = 4.0f;
                maxFairwayWidth = 50f;
                minFairwayWidth = 0;
                undulation = 0.7f;
                fairwayWiggle = 0.5f + r.nextFloat() * 0.1f;
                islands = 0.0f;
                cohesion = 1.0f;
                distance = Math.round(550 + r.nextFloat() * 60);
                par = 4;
                bunkerCount = 2 + r.nextInt(2);
                bunkerDepth = 1.1f;
                break;
            case ROUGH_HOUGH_BLUFFS:
                baseDifficultyIndex = 2f;
                teeH = 17.0f + r.nextFloat() * 2.0f;
                greenH = 13.0f + r.nextFloat() * 3.0f;
                windMin = 8f;
                windMax = 14f;
                treeH = 6.0f + r.nextFloat() * 2.0f;
                treeDensity = 0.13f;
                foliageR = 3.0f;
                trunkR = 0.5f;
                hillFreq = 0.06f;
                maxH = 4.0f;
                maxFairwayWidth = 50f;
                minFairwayWidth = 28;
                undulation = 0.4f;
                fairwayWiggle = 0.1f + r.nextFloat() * 0.1f;
                islands = 0.0f;
                cohesion = 1f;
                distance = Math.round(400 + r.nextFloat() * 100);
                par = 4;
                break;
            case STANDARD_LINKS:
            default:
                baseDifficultyIndex = 8f;
                windMin = 0f;
                windMax = 19f;
                fairwayWiggle = 0.05f + r.nextFloat() * 0.15f;
                islands = 0.25f;
                maxFairwayWidth = 49f;
                cohesion = 1.3f;
                bunkerCount = 2 + r.nextInt(4);
                bunkerDepth = 2.3f;
                distance = Math.round(300 + r.nextFloat() * 500);
                par = distance < 410 ? 3 : distance < 675 ? 4 : 5;
                break;
        }

        // --- 3. Final Calculations & Shot Index Mapping ---
        float actualWindSpeed = windMin + r.nextFloat() * (windMax - windMin);
        float randomizedTreeH = treeH * (0.8f + r.nextFloat() * 0.4f);

        float finalShotIndex = baseDifficultyIndex;
        finalShotIndex -= (actualWindSpeed / 8.0f);
        finalShotIndex -= (45.0f - maxFairwayWidth) * 0.1f;

        if (selectedType == LevelData.Archetype.REDWOOD_FOREST || selectedType == LevelData.Archetype.CLIFFSIDE_BLUFF) {
            finalShotIndex -= (distance / 200.0f);
        }

        data.setShotIndex(finalShotIndex);
        data.setTeeHeight(teeH);
        data.setGreenHeight(greenH);
        data.setTreeHeight(randomizedTreeH);
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
        data.setBunkerDepth(bunkerDepth);
        data.setDistance(distance);
        data.setPar(par);
        data.setFairwayWiggle(fairwayWiggle);
        data.setFairwayRoughIslands(islands);
        data.setFairwayCohesion(cohesion);

        float lowerBound = Math.min(teeH, greenH);
        data.setWaterLevel(lowerBound - (3.0f + r.nextFloat() * 2.0f));

        Vector3 windDir = new Vector3(r.nextFloat() - 0.5f, 0, r.nextFloat() - 0.5f).nor();
        data.setWind(windDir.scl(actualWindSpeed));

        System.out.printf("[GEN] %s | Index: %.2f | Wind: %.1f MPH | Dist: %d | Bunkers: %d (Depth: %.1f) | Seed: %d%n",
                selectedType, finalShotIndex, actualWindSpeed, distance, bunkerCount, bunkerDepth, seed);

        return data;
    }

    public static List<LevelData> generate18Holes() {
        List<LevelData> pool = new ArrayList<>();
        LevelData.Archetype[] archetypes = LevelData.Archetype.values();

        // 1. Ensure 100% representation: Add one of every archetype first
        for (LevelData.Archetype arch : archetypes) {
            pool.add(createLevelDataForArchetype(arch));
        }

        // 2. Fill remaining slots with random levels until we have 18
        while (pool.size() < 18) {
            pool.add(createRandomLevelData());
        }

        // 3. Sort the pool by ShotIndex (Descending: Easiest to Hardest)
        pool.sort(Comparator.comparingDouble(LevelData::getShotIndex).reversed());

        // 4. Build the final course with "Next Best Fit" logic
        List<LevelData> finalCourse = new ArrayList<>();

        while (!pool.isEmpty()) {
            boolean foundFit = false;
            LevelData.Archetype lastArch = finalCourse.isEmpty() ? null : finalCourse.get(finalCourse.size() - 1).getArchetype();

            // Find the easiest level in the pool that isn't the same type as the last one
            for (int i = 0; i < pool.size(); i++) {
                if (pool.get(i).getArchetype() != lastArch) {
                    finalCourse.add(pool.remove(i));
                    foundFit = true;
                    break;
                }
            }

            // Emergency Fallback: If no fit is found (only duplicates left),
            // we force the last one in and perform a "Ripple Swap"
            if (!foundFit) {
                LevelData misfit = pool.remove(0);
                finalCourse.add(misfit);
                resolveEndConflict(finalCourse);
            }
        }

        // 5. Final Logging
        System.out.println("\n--- [DIVERSE & ORDERED COURSE GENERATED] ---");
        for (int i = 0; i < finalCourse.size(); i++) {
            LevelData ld = finalCourse.get(i);
            System.out.printf("Hole %02d | Index: %5.2f | Type: %-15s | Seed: %d%n",
                    (i + 1), ld.getShotIndex(), ld.getArchetype(), ld.getSeed());
        }

        return finalCourse;
    }

    private static void resolveEndConflict(List<LevelData> course) {
        if (course.size() < 2) return;
        int last = course.size() - 1;

        if (course.get(last).getArchetype() == course.get(last - 1).getArchetype()) {
            for (int i = 1; i < last - 1; i++) {
                LevelData.Archetype lastType = course.get(last).getArchetype();
                if (course.get(i - 1).getArchetype() != lastType && course.get(i + 1).getArchetype() != lastType) {
                    if (course.get(last - 1).getArchetype() != course.get(i).getArchetype()) {
                        Collections.swap(course, i, last);
                        break;
                    }
                }
            }
        }
    }

    public static LevelData createLevelDataForArchetype(LevelData.Archetype arch) {
        LevelData ld = createRandomLevelData();
        int safety = 0;
        while (ld.getArchetype() != arch && safety < 100) {
            ld = createRandomLevelData();
            safety++;
        }
        return ld;
    }
}