package org.example.terrain.level;

import com.badlogic.gdx.math.Vector3;
import org.example.terrain.objects.Tree.TreeScheme;

import java.util.*;

public class LevelDataGenerator {

    public static LevelData createFixedLevelData(long seed) {
        if (seed == -1) {
            seed = System.nanoTime();
        }

        Random r = new Random(seed);
        LevelData.Archetype[] types = LevelData.Archetype.values();
        LevelData.Archetype selectedType = types[r.nextInt(types.length)];
        return createFixedLevelData(seed, selectedType);
    }

    public static LevelData createFixedLevelData(long seed, LevelData.Archetype forcedType) {
        LevelData data = new LevelData();
        data.setSeed(seed);
        Random r = new Random(seed);
        data.setArchetype(forcedType);

        applyArchetypeVisuals(data, forcedType, r);

        ArchetypeSettings settings = new ArchetypeSettings(r);
        applyArchetypeTuning(settings, forcedType, r);
        finalizeLevelData(data, settings, forcedType, r);

        return data;
    }

    private static void applyArchetypeVisuals(LevelData data, LevelData.Archetype type, Random r) {
        LevelData.TerrainAlgorithm algo;
        TreeScheme scheme;

        switch (type) {
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
            case PLUNGE_CENOTES:
                algo = r.nextBoolean() && type == LevelData.Archetype.ISLAND_COAST ? LevelData.TerrainAlgorithm.MULTI_WAVE : LevelData.TerrainAlgorithm.SMOOTH_SINE;
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
            case MONOLITH_PLAINS:
                algo = LevelData.TerrainAlgorithm.MULTI_WAVE;
                scheme = (type == LevelData.Archetype.REDWOOD_FOREST) ? TreeScheme.REDWOOD : TreeScheme.CHERRY_BLOSSOM;
                break;
            case WETLANDS:
            case BIG_GRAPE_VINEYARDS:
            case CLIPPERTON_ROCK:
                algo = LevelData.TerrainAlgorithm.DUNES;
                scheme = (type == LevelData.Archetype.WETLANDS) ? TreeScheme.OAK : (type == LevelData.Archetype.BIG_GRAPE_VINEYARDS ? TreeScheme.GRAPEVINE : TreeScheme.PALM);
                break;
            case WHISTLING_ISLES:
                algo = LevelData.TerrainAlgorithm.CRAGGY_RIDGES;
                scheme = TreeScheme.OAK;
                break;
            default:
                algo = LevelData.TerrainAlgorithm.MULTI_WAVE;
                scheme = r.nextBoolean() ? TreeScheme.OAK : TreeScheme.AUTUMN_MAPLE;
                break;
        }
        data.setTerrainAlgorithm(algo);
        data.setTreeScheme(scheme);
    }

    private static void applyArchetypeTuning(ArchetypeSettings s, LevelData.Archetype type, Random r) {
        switch (type) {
            case RAZORBACK_RIDGE:
                s.baseDifficultyIndex = 1f; s.teeH = 0f; s.greenH = 18f + r.nextFloat() * 8f;
                s.windMin = 5f; s.windMax = 13f; s.treeH = 12f; s.treeDensity = 0.18f;
                s.foliageR = 5f; s.trunkR = 0.8f; s.hillFreq = 0.02f; s.maxH = 5f;
                s.maxFairwayWidth = 51f; s.undulation = 1.2f; s.fairwayWiggle = 0.22f + r.nextFloat() * 0.08f;
                s.cohesion = 0.75f;
                s.distance = Math.round(470 + r.nextFloat() * 110); s.par = 5;
                break;
            case CRATER_FIELDS:
                s.baseDifficultyIndex = 2f; s.teeH = 8f; s.greenH = 12f; s.windMin = 5f; s.windMax = 18f;
                s.treeH = 8f; s.foliageR = 3f; s.trunkR = 0.6f; s.hillFreq = 0.06f; s.maxH = 12f;
                s.treeDensity = 0.10f; s.maxFairwayWidth = 50f; s.undulation = 0.28f;
                s.fairwayWiggle = 0.1f + r.nextFloat() * 0.08f; s.islands = 0.4f; s.cohesion = 0.9f;
                s.distance = Math.round(500 + r.nextFloat() * 150); s.par = s.distance < 600 ? 4 : 5;
                break;
            case REDWOOD_FOREST:
                s.baseDifficultyIndex = 3f; s.teeH = 5f + r.nextFloat() * 5f; s.greenH = 5f + r.nextFloat() * 5f;
                s.windMin = 0f; s.windMax = 7f; s.treeH = 45f; s.foliageR = 10f; s.trunkR = 1.8f;
                s.hillFreq = 0.025f; s.maxH = 5f; s.treeDensity = 0.26f; s.maxFairwayWidth = 45f;
                s.minFairwayWidth = 25f; s.fairwayWiggle = 0.06f + r.nextFloat() * 0.02f; s.islands = 0.0f;
                s.cohesion = 0.95f; s.distance = Math.round(550 + r.nextFloat() * 200); s.par = 5;
                break;
            case CLIFFSIDE_BLUFF:
                s.baseDifficultyIndex = 2f; s.teeH = 60f + r.nextFloat() * 30f; s.greenH = 5f + r.nextFloat() * 10f;
                s.windMin = 7f; s.windMax = 18f; s.hillFreq = 0.012f; s.maxH = 10f; s.foliageR = 8f;
                s.trunkR = 1.2f; s.treeDensity = 0.25f; s.maxFairwayWidth = 40f;
                s.fairwayWiggle = 0.20f + r.nextFloat() * 0.1f; s.islands = 0.05f; s.cohesion = 0.9f;
                s.distance = Math.round(450 + r.nextFloat() * 80); s.par = 4;
                s.bunkerCount = 1 + r.nextInt(2); s.bunkerDepth = 0.8f;
                break;
            case BUSH_WORLD:
                s.baseDifficultyIndex = 3f; s.treeH = 6f; s.foliageR = 3f; s.trunkR = 0.5f;
                s.windMin = 2f; s.windMax = 15f; s.hillFreq = 0.045f; s.treeDensity = 0.82f;
                s.maxFairwayWidth = 35f; s.fairwayWiggle = 0.4f + r.nextFloat() * 0.3f; s.islands = 0.7f;
                s.cohesion = 0.42f; s.distance = Math.round(430 + r.nextFloat() * 160); s.par = s.distance < 515 ? 4 : 5;
                s.bunkerCount = 1; s.bunkerDepth = 2.2f;
                break;
            case ISLAND_COAST:
                s.baseDifficultyIndex = 3f; s.teeH = 15f + r.nextFloat() * 10f; s.greenH = 15f + r.nextFloat() * 5f;
                s.windMin = 6f; s.windMax = 23f; s.hillFreq = 0.02f; s.maxH = 15f; s.treeDensity = 0.03f;
                s.maxFairwayWidth = 55f; s.minFairwayWidth = 35f; s.fairwayWiggle = 0.25f + r.nextFloat() * 0.06f;
                s.islands = 0f; s.cohesion = 0.9f; s.distance = Math.round(500 + r.nextFloat() * 200); s.par = s.distance < 600 ? 4 : 5;
                break;
            case MONOLITH_PLAINS:
                s.baseDifficultyIndex = 5f; s.teeH = 20f; s.greenH = 15f + r.nextFloat() * 10f;
                s.windMin = 0f; s.windMax = 12f; s.fairwayWiggle = 0.05f + r.nextFloat() * 0.1f; s.islands = 0.25f;
                s.maxFairwayWidth = 60f; s.cohesion = 1.3f; s.undulation = 0.9f; s.bunkerCount = 1 + r.nextInt(4);
                s.bunkerDepth = 2.5f + r.nextFloat(); s.hillFreq = 0.008f; s.maxH = 15f;
                s.distance = Math.round(600 + r.nextFloat() * 200); s.par = s.distance < 690 ? 4 : 5;
                break;
            case MOGUL_HIGHLANDS:
                s.baseDifficultyIndex = 6f; s.teeH = 20f; s.greenH = 20f; s.windMin = 1f; s.windMax = 16f;
                s.hillFreq = 0.08f; s.maxH = 18f; s.treeDensity = 0.05f; s.maxFairwayWidth = 40f;
                s.minFairwayWidth = 27f; s.undulation = 0.2f; s.fairwayWiggle = 0.16f + r.nextFloat() * 0.08f;
                s.islands = 0.3f; s.cohesion = 0.6f; s.distance = Math.round(650 + r.nextFloat() * 100);
                s.par = s.distance < 700 ? 4 : 5; s.bunkerCount = 1; s.bunkerDepth = 3.5f;
                break;
            case SHADOW_CANYON:
            case PLUNGE_CENOTES:
                s.baseDifficultyIndex = 7f; s.teeH = (type == LevelData.Archetype.SHADOW_CANYON) ? 0f : 5f + r.nextFloat() * 5f;
                s.greenH = (type == LevelData.Archetype.SHADOW_CANYON) ? 15f + r.nextFloat() * 8f : 0f;
                s.windMin = (type == LevelData.Archetype.SHADOW_CANYON) ? 0f : 6f;
                s.windMax = (type == LevelData.Archetype.SHADOW_CANYON) ? 7f : 12f;
                s.treeH = 4f + r.nextFloat() * 2f; s.treeDensity = (type == LevelData.Archetype.SHADOW_CANYON) ? 0.06f : 0.13f;
                s.foliageR = 3f; s.trunkR = 0.5f; s.hillFreq = 0.02f; s.maxH = 4f;
                s.maxFairwayWidth = (type == LevelData.Archetype.SHADOW_CANYON) ? 40f : 35f;
                s.minFairwayWidth = (type == LevelData.Archetype.SHADOW_CANYON) ? 10f : 0f;
                s.fairwayWiggle = 0.1f + r.nextFloat() * 0.1f; s.distance = Math.round((type == LevelData.Archetype.SHADOW_CANYON ? 420 : 480) + r.nextFloat() * (type == LevelData.Archetype.SHADOW_CANYON ? 120 : 100));
                s.par = 4;
                if (type == LevelData.Archetype.PLUNGE_CENOTES) s.baseDifficultyIndex = 2f;
                break;
            case WETLANDS:
            case ROUGH_HOUGH_BLUFFS:
                s.baseDifficultyIndex = (type == LevelData.Archetype.WETLANDS) ? 6f : 2f;
                s.teeH = 17f + r.nextFloat() * 2f; s.greenH = 13f + r.nextFloat() * 3f;
                s.windMin = (type == LevelData.Archetype.WETLANDS) ? 6f : 8f;
                s.windMax = (type == LevelData.Archetype.WETLANDS) ? 12f : 14f;
                s.treeH = 6f + r.nextFloat() * 2f; s.treeDensity = 0.13f; s.foliageR = 3f; s.trunkR = 0.5f;
                s.hillFreq = 0.06f; s.maxH = 4f; s.maxFairwayWidth = 50f; s.minFairwayWidth = 28f;
                s.undulation = (type == LevelData.Archetype.WETLANDS) ? 1.4f : 0.4f;
                s.fairwayWiggle = 0.1f + r.nextFloat() * 0.1f;
                s.distance = Math.round((type == LevelData.Archetype.WETLANDS ? 250 : 400) + r.nextFloat() * (type == LevelData.Archetype.WETLANDS ? 80 : 100));
                s.par = (type == LevelData.Archetype.WETLANDS) ? 3 : 4;
                if (type == LevelData.Archetype.WETLANDS) { s.bunkerCount = 1; s.bunkerDepth = 3.4f; }
                break;
            case WHISTLING_ISLES:
                s.baseDifficultyIndex = 1f; s.teeH = 0.2f; s.greenH = 1.0f; s.windMin = 11f; s.windMax = 18f;
                s.treeH = 8f + r.nextFloat() * 2f; s.treeDensity = 0.98f; s.foliageR = 2.7f; s.trunkR = 0.4f;
                s.hillFreq = 0.06f; s.maxH = 4f; s.maxFairwayWidth = 50f; s.minFairwayWidth = 28f;
                s.undulation = 1.4f; s.fairwayWiggle = 0.1f + r.nextFloat() * 0.1f;
                s.distance = Math.round(600 + r.nextFloat() * 100); s.par = 5; s.cohesion = 1f;
                break;
            case BIG_GRAPE_VINEYARDS:
                s.baseDifficultyIndex = 7f; s.teeH = 22f; s.greenH = 1.0f; s.windMin = 2f; s.windMax = 8f;
                s.treeH = 1.5f + r.nextFloat() * 0.2f; s.treeDensity = 0.2f; s.foliageR = 1f; s.trunkR = 0.2f;
                s.hillFreq = 0.06f; s.maxH = 4f; s.maxFairwayWidth = 50f; s.minFairwayWidth = 0f;
                s.undulation = 0.7f; s.fairwayWiggle = 0.5f + r.nextFloat() * 0.1f; s.cohesion = 1f;
                s.distance = Math.round(550 + r.nextFloat() * 60); s.par = 4; s.bunkerCount = 2 + r.nextInt(2);
                s.bunkerDepth = 1.1f;
                break;
            case CLIPPERTON_ROCK:
                s.baseDifficultyIndex = 4f; s.teeH = 18f + r.nextFloat() * 5f; s.greenH = 4f + r.nextFloat() * 2f;
                s.windMin = 12f; s.windMax = 20f; s.treeH = 11.5f + r.nextFloat() * 2f; s.treeDensity = 0.4f;
                s.foliageR = 4.2f; s.trunkR = 0.5f; s.hillFreq = 0.06f; s.maxH = 4f; s.maxFairwayWidth = 50f;
                s.minFairwayWidth = 0f; s.undulation = 0.2f; s.fairwayWiggle = 0.5f + r.nextFloat() * 0.1f;
                s.cohesion = 1.0f; s.distance = Math.round(450 + r.nextFloat() * 50); s.par = 4; s.mapWidth = s.distance;
                break;
            case STANDARD_LINKS:
            default:
                s.baseDifficultyIndex = 8f; s.windMin = 0f; s.windMax = 19f; s.fairwayWiggle = 0.05f + r.nextFloat() * 0.15f;
                s.islands = 0.25f; s.maxFairwayWidth = 49f; s.cohesion = 1.3f; s.bunkerCount = 2 + r.nextInt(4);
                s.bunkerDepth = 2.3f; s.distance = Math.round(300 + r.nextFloat() * 500);
                s.par = s.distance < 435 ? 3 : s.distance < 710 ? 4 : 5;
                break;
        }
    }

    private static void finalizeLevelData(LevelData data, ArchetypeSettings s, LevelData.Archetype type, Random r) {
        float actualWindSpeed = s.windMin + r.nextFloat() * (s.windMax - s.windMin);
        float randomizedTreeH = s.treeH * (0.8f + r.nextFloat() * 0.4f);

        float finalShotIndex = s.baseDifficultyIndex;
        finalShotIndex -= (actualWindSpeed / 8.0f);
        finalShotIndex -= (45.0f - s.maxFairwayWidth) * 0.1f;

        if (type == LevelData.Archetype.REDWOOD_FOREST || type == LevelData.Archetype.CLIFFSIDE_BLUFF) {
            finalShotIndex -= (s.distance / 200.0f);
        }

        data.setShotIndex(finalShotIndex);
        data.setTeeHeight(s.teeH);
        data.setGreenHeight(s.greenH);
        data.setTreeHeight(randomizedTreeH);
        data.setFoliageRadius(s.foliageR);
        data.setTrunkRadius(s.trunkR);
        data.setHillFrequency(s.hillFreq);
        data.setMaxHeight(s.maxH);
        data.setTreeDensity(s.treeDensity);
        data.setMaxFairwayWidth(s.maxFairwayWidth);
        data.setMinFairwayWidth(s.minFairwayWidth);
        data.setUndulation(s.undulation);
        data.setHoleSize(0.6f);
        data.setnBunkers(s.bunkerCount);
        data.setBunkerDepth(s.bunkerDepth);
        data.setDistance(s.distance);
        data.setMapWidth(s.mapWidth);
        data.setPar(s.par);
        data.setFairwayWiggle(s.fairwayWiggle);
        data.setFairwayRoughIslands(s.islands);
        data.setFairwayCohesion(s.cohesion);

        data.setWaterLevel(Math.min(s.teeH, s.greenH) - (3.0f + r.nextFloat() * 2.0f));
        data.setWind(new Vector3(r.nextFloat() - 0.5f, 0, r.nextFloat() - 0.5f).nor().scl(actualWindSpeed));
    }

    private static class ArchetypeSettings {
        float teeH = 10f, greenH = 10f, treeH = 7f, foliageR = 2.5f, trunkR = 0.4f;
        float hillFreq = 0.035f, maxH = 7f, treeDensity = 0.15f, undulation;
        float fairwayWiggle = 0.3f, islands = 0.1f, cohesion = 0.5f, maxFairwayWidth = 45f, minFairwayWidth = 0f;
        int bunkerCount = 0, distance = 500, par = 1, mapWidth = 160;
        float bunkerDepth = 2f, windMin = 0f, windMax = 15f, baseDifficultyIndex = 8f;

        ArchetypeSettings(Random r) {
            this.undulation = r.nextFloat() * 0.4f + 0.2f;
        }
    }

    public static List<LevelData> generate18Holes(long seed) {
        if (seed < 0) seed = System.nanoTime();
        Random masterR = new Random(seed);
        List<LevelData> pool = new ArrayList<>();
        LevelData.Archetype[] archetypes = LevelData.Archetype.values();

        for (LevelData.Archetype arch : archetypes) pool.add(createFixedLevelData(masterR.nextLong(), arch));
        while (pool.size() < 18) pool.add(createFixedLevelData(masterR.nextLong()));

        pool.sort(Comparator.comparingDouble(LevelData::getShotIndex).reversed());

        List<LevelData> finalCourse = new ArrayList<>();
        while (!pool.isEmpty()) {
            LevelData.Archetype lastArch = finalCourse.isEmpty() ? null : finalCourse.get(finalCourse.size() - 1).getArchetype();
            boolean foundFit = false;
            for (int i = 0; i < pool.size(); i++) {
                if (pool.get(i).getArchetype() != lastArch) {
                    finalCourse.add(pool.remove(i));
                    foundFit = true;
                    break;
                }
            }
            if (!foundFit) {
                finalCourse.add(pool.remove(0));
                resolveEndConflict(finalCourse);
            }
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
}