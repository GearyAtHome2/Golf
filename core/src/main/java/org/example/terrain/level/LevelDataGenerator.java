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

    public static LevelData createFixedLevelData(long seed, LevelData.Archetype archetype) {
        LevelData data = new LevelData();
        data.setSeed(seed);
        data.setArchetype(archetype);
        Random r = new Random(seed);

        ArchetypeSpec spec = archetype.spec();

        // ── Visuals ───────────────────────────────────────────────────────────
        LevelData.TerrainAlgorithm algo =
            (spec.algoAlt != null && r.nextBoolean()) ? spec.algoAlt : spec.algo;
        TreeScheme scheme =
            (spec.treeSchemeAlt != null && r.nextBoolean()) ? spec.treeSchemeAlt : spec.treeScheme;
        data.setTerrainAlgorithm(algo);
        data.setTreeScheme(scheme);

        // ── Generate values from spec ranges ──────────────────────────────────
        float teeH      = spec.teeHVar        > 0 ? spec.teeH        + r.nextFloat() * spec.teeHVar        : spec.teeH;
        float greenH    = spec.greenHVar      > 0 ? spec.greenH      + r.nextFloat() * spec.greenHVar      : spec.greenH;
        float treeHBase = spec.treeHVar       > 0 ? spec.treeH       + r.nextFloat() * spec.treeHVar       : spec.treeH;
        float wiggle    = spec.fairwayWiggleVar > 0 ? spec.fairwayWiggle + r.nextFloat() * spec.fairwayWiggleVar : spec.fairwayWiggle;
        int   bunkers   = spec.bunkerMax > spec.bunkerMin
                          ? spec.bunkerMin + r.nextInt(spec.bunkerMax - spec.bunkerMin + 1)
                          : spec.bunkerMin;
        float bDepth    = spec.bunkerDepthVar > 0 ? spec.bunkerDepth + r.nextFloat() * spec.bunkerDepthVar : spec.bunkerDepth;
        int   distance  = spec.distanceMax > spec.distanceMin
                          ? spec.distanceMin + Math.round(r.nextFloat() * (spec.distanceMax - spec.distanceMin))
                          : spec.distanceMin;
        float greenRadius = spec.greenRadiusMin >= 0
                          ? (spec.greenRadiusMax > spec.greenRadiusMin
                              ? spec.greenRadiusMin + r.nextFloat() * (spec.greenRadiusMax - spec.greenRadiusMin)
                              : spec.greenRadiusMin)
                          : 26f;
        float windSpeed = spec.windMin + r.nextFloat() * (spec.windMax - spec.windMin);
        float undulation = spec.undulation >= 0 ? spec.undulation : r.nextFloat() * 0.4f + 0.2f;

        // ── Par ───────────────────────────────────────────────────────────────
        int par;
        if (spec.parFixed > 0) {
            par = spec.parFixed;
        } else if (spec.parThreshold34 > 0 && distance < spec.parThreshold34) {
            par = 3;
        } else if (spec.parThreshold45 > 0 && distance < spec.parThreshold45) {
            par = 4;
        } else {
            par = 5;
        }

        // ── Map width ─────────────────────────────────────────────────────────
        int mapWidth = spec.mapWidthOverride == 0  ? 160
                     : spec.mapWidthOverride == -1  ? distance
                     : spec.mapWidthOverride;

        // ── Difficulty / shotIndex ────────────────────────────────────────────
        float randomizedTreeH = treeHBase * (0.8f + r.nextFloat() * 0.4f);
        float shotIndex = spec.baseDifficultyIndex;
        shotIndex -= (windSpeed / 8.0f);
        shotIndex -= (45.0f - spec.maxFairwayWidth) * 0.1f;
        if (spec.distancePenalty) shotIndex -= (distance / 200.0f);

        // ── Finalise ──────────────────────────────────────────────────────────
        data.setShotIndex(shotIndex);
        data.setTeeHeight(teeH);
        data.setGreenHeight(greenH);
        data.setTreeHeight(randomizedTreeH);
        data.setFoliageRadius(spec.foliageR);
        data.setTrunkRadius(spec.trunkR);
        data.setHillFrequency(spec.hillFreq);
        data.setMaxHeight(spec.maxH);
        data.setTreeDensity(spec.treeDensity);
        data.setMaxFairwayWidth(spec.maxFairwayWidth);
        data.setMinFairwayWidth(spec.minFairwayWidth);
        data.setUndulation(undulation);
        data.setHoleSize(0.6f);
        data.setGreenRadius(greenRadius);
        data.setnBunkers(bunkers);
        data.setBunkerDepth(bDepth);
        data.setDistance(distance);
        data.setMapWidth(mapWidth);
        data.setPar(par);
        data.setFairwayWiggle(wiggle);
        data.setFairwayRoughIslands(spec.islands);
        data.setFairwayCohesion(spec.cohesion);
        data.setWaterLevel(Math.min(teeH, greenH) - (3.0f + r.nextFloat() * 2.0f));
        data.setWind(new Vector3(r.nextFloat() - 0.5f, 0, r.nextFloat() - 0.5f).nor().scl(windSpeed));

        return data;
    }

    /**
     * Creates a single guaranteed par-3 hole for DAILY_1.
     * Candidates are all archetypes that can ever produce a par-3 (parFixed==3, or
     * parThreshold34 > 0 meaning distance-based par can reach 3).  One is chosen
     * deterministically from the seed.  If the chosen archetype has variable par,
     * generation is retried with successive seeds until par==3 (up to 100 attempts).
     * Falls back to the first fixed-par-3 archetype if all retries fail.
     */
    public static LevelData createPar3Hole(long seed) {
        LevelData.Archetype[] candidates = Arrays.stream(LevelData.Archetype.values())
                .filter(a -> a.spec().parFixed == 3 || a.spec().parThreshold34 > 0)
                .toArray(LevelData.Archetype[]::new);
        LevelData.Archetype chosen = candidates[new Random(seed).nextInt(candidates.length)];

        if (chosen.spec().parFixed == 3) {
            return createFixedLevelData(seed, chosen);
        }

        // Variable-par archetype: retry until distance lands in par-3 range
        for (int attempt = 0; attempt < 100; attempt++) {
            LevelData hole = createFixedLevelData(seed + attempt, chosen);
            if (hole.getPar() == 3) return hole;
        }

        // Fallback: guaranteed par-3 archetype
        LevelData.Archetype fallback = Arrays.stream(LevelData.Archetype.values())
                .filter(a -> a.spec().parFixed == 3)
                .findFirst().orElseThrow();
        return createFixedLevelData(seed, fallback);
    }

    public static List<LevelData> generate18Holes(long seed) {
        if (seed < 0) seed = System.nanoTime();
        Random masterR = new Random(seed);
        List<LevelData> pool = new ArrayList<>();
        LevelData.Archetype[] archetypes = LevelData.Archetype.values();

        for (LevelData.Archetype arch : archetypes) pool.add(createFixedLevelData(masterR.nextLong(), arch));
        while (pool.size() < 18) pool.add(createFixedLevelData(masterR.nextLong()));
        while (pool.size() > 18) pool.remove(masterR.nextInt(pool.size()));

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
