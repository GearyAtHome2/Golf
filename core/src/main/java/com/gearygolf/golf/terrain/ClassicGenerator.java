package com.gearygolf.golf.terrain;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.gearygolf.golf.terrain.features.*;
import com.gearygolf.golf.terrain.level.ArchetypeSpec;
import com.gearygolf.golf.terrain.level.LevelData;
import com.gearygolf.golf.terrain.objects.Monolith;
import com.gearygolf.golf.terrain.objects.Tree;
import com.gearygolf.golf.util.PerfLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ClassicGenerator implements ITerrainGenerator {

    private final float SCALE = 1.0f;
    private final LevelData data;
    private final ArchetypeSpec spec;
    private final Random rng;
    private final FeatureProcessor processor;
    private final CenoteGenerator cenoteGenerator;
    private final CoastlineGenerator coastlineGenerator;
    private final BeachBluffsGenerator beachBluffsGenerator;
    private final WhistlingIslesGenerator whistlingIslesGenerator;
    private final CraterGenerator craterGenerator;
    private final VineyardsGenerator vineyardsGenerator;
    private final BunkerGenerator bunkerGenerator;
    private final ClippertonRockGenerator clippertonRockGenerator;
    private final OasisDunesGenerator oasisDunesGenerator;
    private final ForestEdgeGenerator woodlandEdgeGenerator;
    private final StoneRunGenerator stoneRunGenerator;
    private final CypressBayouGenerator cypressBayouGenerator;
    private final BadlandsGenerator badlandsGenerator;
    private final TableMountainGenerator tableMountainGenerator;
    private final GoldsmithBowlGenerator goldsmithBowlGenerator;
    private final DoglegRiverGenerator doglegRiverGenerator;
    private final TreePlacer treePlacer;
    private final TerrainPassApplier terrainPassApplier;

    private final float MONOLITH_UNDERGROUND_OFFSET = 1.0f;
    private final float MONOLITH_SPAWN_CHANCE = 0.033f;

    private final float off1, off2;
    private final float[] waveAngles = new float[10];
    private final float[] waveFreqs = new float[10];
    private final float[] waveAmps = new float[10];
    private final float[] waveOffsets = new float[10];

    private final float[] greenWaveAngles = new float[4];
    private final float[] greenWaveOffsets = new float[4];
    private final float[] greenWaveFreqs = new float[4];
    private float greenTiltDx, greenTiltDz;

    private List<CraterRecord> craters = new ArrayList<>();
    private List<BunkerGenerator.BunkerRecord> bunkerRecords = new ArrayList<>();

    public static record CraterRecord(float x, float z, float radius) {}

    public ClassicGenerator(LevelData data) {
        this.data = data;
        this.spec = data.getArchetype().spec();
        this.rng = new Random(data.getSeed());
        this.off1 = rng.nextFloat() * 1000f;
        this.off2 = rng.nextFloat() * 1000f;

        for (int i = 0; i < 10; i++) {
            if (spec.cardinalWaveAngles) {
                float baseAngle = rng.nextBoolean() ? MathUtils.PI * 0.5f : MathUtils.PI * 1.5f;
                float variation = (rng.nextFloat() - 0.5f) * (MathUtils.PI / 6f);
                waveAngles[i] = baseAngle + variation;
            } else {
                waveAngles[i] = rng.nextFloat() * MathUtils.PI * 2;
            }
            waveFreqs[i]   = 1.0f + (rng.nextFloat() * 1.5f);
            waveAmps[i]    = 1.0f / (i + 1.5f);
            waveOffsets[i] = rng.nextFloat() * 100f;
        }

        for (int i = 0; i < 4; i++) {
            greenWaveAngles[i]  = rng.nextFloat() * MathUtils.PI * 2;
            greenWaveOffsets[i] = rng.nextFloat() * 100f;
            greenWaveFreqs[i]   = 0.32f * (1f + (rng.nextFloat() - 0.5f) * 0.1f);
        }
        float tiltAngle = rng.nextFloat() * MathUtils.PI * 2;
        float tiltMag   = 0.006f + rng.nextFloat() * 0.009f;
        greenTiltDx = MathUtils.cos(tiltAngle) * tiltMag;
        greenTiltDz = MathUtils.sin(tiltAngle) * tiltMag;

        this.processor          = new FeatureProcessor(data, rng, off1, off2, waveAngles, waveFreqs, waveAmps, waveOffsets);
        this.cenoteGenerator    = new CenoteGenerator(rng);
        this.coastlineGenerator = new CoastlineGenerator();
        this.beachBluffsGenerator    = new BeachBluffsGenerator(data, rng, waveAngles, waveFreqs, waveAmps, waveOffsets);
        this.craterGenerator         = new CraterGenerator(rng);
        this.whistlingIslesGenerator = new WhistlingIslesGenerator(data, rng, waveAngles, waveFreqs, waveAmps, waveOffsets);
        this.vineyardsGenerator      = new VineyardsGenerator();
        this.bunkerGenerator         = new BunkerGenerator(rng);
        this.clippertonRockGenerator = new ClippertonRockGenerator(data, rng, waveAngles, waveFreqs, waveAmps, waveOffsets);
        this.oasisDunesGenerator     = new OasisDunesGenerator(data);
        this.woodlandEdgeGenerator   = new ForestEdgeGenerator(data);
        this.stoneRunGenerator       = new StoneRunGenerator(data);
        this.cypressBayouGenerator   = new CypressBayouGenerator(data, rng, waveAngles, waveFreqs, waveAmps, waveOffsets);
        this.badlandsGenerator       = (spec.terrainFeature == ArchetypeSpec.TerrainFeature.BADLANDS)
                ? new BadlandsGenerator(data) : null;
        this.tableMountainGenerator  = (spec.terrainFeature == ArchetypeSpec.TerrainFeature.TABLE_MOUNTAIN)
                ? new TableMountainGenerator(data) : null;
        this.goldsmithBowlGenerator  = (spec.terrainFeature == ArchetypeSpec.TerrainFeature.GOLDSMITH_BOWL)
                ? new GoldsmithBowlGenerator(data) : null;
        this.doglegRiverGenerator    = (spec.terrainFeature == ArchetypeSpec.TerrainFeature.DOGLEG_RIVER)
                ? new DoglegRiverGenerator(data, spec) : null;

        this.treePlacer         = new TreePlacer(rng, data, spec, craters, bunkerRecords, cypressBayouGenerator);
        this.terrainPassApplier = new TerrainPassApplier(data, spec, processor, rng, off1, off2);
    }

    public LevelData getData() {
        return data;
    }

    @Override
    public void generate(Terrain.TerrainType[][] map, float[][] heights, List<Tree> trees, List<Monolith> monoliths, Vector3 teePos, Vector3 holePos) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        craters.clear();
        bunkerRecords.clear();
        monoliths.clear();

        boolean isPathDependent = data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.RAISED_FAIRWAY
                || data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.SUNKEN_FAIRWAY;

        int greenCenterZ = (int) (SIZE_Z * 0.85f);
        float greenOffset = spec.greenXFraction >= 0
                ? (spec.greenXFraction * SIZE_X - SIZE_X / 2f)
                : (rng.nextFloat() - 0.5f) * (SIZE_X * 0.5f);
        int greenCenterX = MathUtils.clamp((int) (SIZE_X / 2 + greenOffset), 20, SIZE_X - 20);

        long tTotal = PerfLog.now();
        PerfLog.snapshot("ClassicGenerator.generate() start  arch=" + data.getArchetype().name() + " dist=" + data.getDistance());

        long t1 = PerfLog.now();
        mapStandardFeatures(map, heights, greenCenterX, greenCenterZ, spec.islandCoastline);
        PerfLog.log("mapStandardFeatures", t1);

        if (spec.useMogulNoise) {
            processor.buildDistanceCache(map);
        }

        long t2 = PerfLog.now();
        boolean[][] isPathMask = getPathMask(map);
        boolean[][] greenBuffer = createGreenBuffer(map);
        PerfLog.log("getPathMask + createGreenBuffer", t2);

        long t3 = PerfLog.now();
        generateHeightMap(map, heights, greenCenterX, greenCenterZ, isPathMask, greenBuffer, isPathDependent);
        PerfLog.log("generateHeightMap", t3);

        long t4 = PerfLog.now();
        applyPostProcessing(map, heights, monoliths, trees, greenCenterX, greenCenterZ, teePos, holePos, greenBuffer, isPathDependent);
        PerfLog.log("applyPostProcessing", t4);

        PerfLog.total("ClassicGenerator.generate() TOTAL", tTotal);

        Gdx.app.log("GENERATOR", String.format("Map: %s length: %s par: %s", data.getArchetype().name(), data.getDistance(), data.getPar()));
    }

    private void generateHeightMap(Terrain.TerrainType[][] map, float[][] heights, int gCX, int gCZ, boolean[][] pathMask, boolean[][] greenBuf, boolean isPathDependent) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        float teeSafety = data.getTeeHeight() + 0.2f;
        float teeFlatBufferZ = 0.12f;

        long tCellLoop = PerfLog.now();
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                float zNorm = z / (float) (SIZE_Z - 1);
                boolean isElevated = pathMask[x][z] || greenBuf[x][z];

                float baseElevation = calculateBaseElevation(zNorm, teeFlatBufferZ, teeSafety, isElevated, spec.useCliffElevation);
                float noise = calculateNoise(x, z, pathMask, spec.useMogulNoise);

                float hillRamp = MathUtils.clamp((zNorm - teeFlatBufferZ) / 0.1f, 0f, 1f);
                hillRamp = TerrainUtils.smoothstep(hillRamp);

                float currentHeight = baseElevation + (noise * hillRamp);

                if (spec.islandCoastline) {
                    currentHeight = coastlineGenerator.applyIslandCoastline(x, z, zNorm, currentHeight, map[x][z], data.getWaterLevel(), off1, off2);
                }

                float distGreen = Vector3.dst(x, z, 0, gCX, gCZ, 0);
                float greenRadius = SIZE_Z * spec.greenHmapRadiusFraction;
                float protectedHeight = (isPathDependent && !isElevated) ? currentHeight : getFinalRaw(distGreen, greenRadius, currentHeight);

                float greenEffectMask = (float) Math.pow(1.0f - MathUtils.clamp(distGreen / 94.0f, 0f, 1f), 4.0f);
                float tilt = (x - gCX) * greenTiltDx + (z - gCZ) * greenTiltDz;
                protectedHeight += (GreenHelper.calculateUndulation(x, z, greenWaveAngles, greenWaveOffsets, greenWaveFreqs) + tilt) * greenEffectMask;

                float teeT = MathUtils.clamp(zNorm / 0.15f, 0f, 1f);
                heights[x][z] = MathUtils.lerp(teeSafety, protectedHeight, TerrainUtils.smoothstep(teeT));
            }
        }
        if (spec.useMogulNoise) {
            PerfLog.log("generateHeightMap cell loop [" + SIZE_X + "x" + SIZE_Z + "=" + (SIZE_X * SIZE_Z) + " cells, mogul getDistToPath r=18 per cell]", tCellLoop);
        }
    }

    private float calculateBaseElevation(float zNorm, float buffer, float safety, boolean isElevated, boolean useCliffElevation) {
        if (zNorm <= buffer) return safety;

        float climbNorm = (zNorm - buffer) / (1.0f - buffer);
        float slopedElevation;

        if (useCliffElevation) {
            float sigmoid = 1f / (1f + (float) Math.exp(-100.0f * (climbNorm - 0.005f)));
            slopedElevation = MathUtils.lerp(safety, data.getGreenHeight(), sigmoid);
        } else {
            float curveStep = 1.0f - (float) Math.pow(1.0f - climbNorm, 2.5f);
            slopedElevation = MathUtils.lerp(safety, data.getGreenHeight(), curveStep);
        }

        boolean isPathDependent = data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.RAISED_FAIRWAY
                || data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.SUNKEN_FAIRWAY;
        return isPathDependent ? (isElevated ? slopedElevation : safety) : slopedElevation;
    }

    private float calculateNoise(int x, int z, boolean[][] pathMask, boolean useMogulNoise) {
        if (useMogulNoise) {
            return processor.calculateMogulNoise(x, z, SCALE, off1, off2, data.getHillFrequency(), data.getUndulation(), data.getMaxHeight());
        }
        return calculateHeightNoise(x, z, data.getHillFrequency(), data.getUndulation(), data.getMaxHeight(), data.getTerrainAlgorithm());
    }

    private void applyPostProcessing(Terrain.TerrainType[][] map, float[][] h, List<Monolith> m, List<Tree> t,
                                      int gX, int gZ, Vector3 teeP, Vector3 holeP,
                                      boolean[][] gBuf, boolean isPathDependent) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        if (!Float.isNaN(spec.waterLevelOverride)) {
            data.setWaterLevel(spec.waterLevelOverride);
        }

        long tArch = PerfLog.now();
        switch (spec.terrainFeature) {
            case TABLE_MOUNTAIN:
                tableMountainGenerator.apply(map, h, gX, gZ);
                break;
            case BADLANDS:
                badlandsGenerator.apply(map, h);
                break;
            case CRATERS:
                this.craters = craterGenerator.generateCraterField(map, h,
                        (int) (SIZE_Z * spec.craterDensityPerLength) + rng.nextInt(spec.craterCountVariance));
                break;
            case BEACH_BLUFFS:
                beachBluffsGenerator.generateBeachBLuffs(map, h, gX, gZ, -2f);
                break;
            case WHISTLING_ISLES:
                whistlingIslesGenerator.generateWhistlingIsles(map, h, gX, gZ, data.getWaterLevel());
                break;
            case CENOTES:
                cenoteGenerator.generatePlungeCenotes(map, h, data.getWaterLevel(), 30.0f);
                cenoteGenerator.generateRoughCenotes(map, h, data.getWaterLevel());
                break;
            case VINEYARDS:
                vineyardsGenerator.generateVineyards(h, map, rng, data.getTeeHeight() + 2.0f, 4.0f, 0.12f);
                break;
            case CLIPPERTON_ROCK:
                clippertonRockGenerator.generateClippertonRock(map, h, gX, gZ, data.getWaterLevel());
                break;
            case OASIS_DUNES:
                oasisDunesGenerator.generateOasisDunes(map, h, gX, gZ);
                break;
            case FOREST_EDGE:
                woodlandEdgeGenerator.generate(map, h, teeP, holeP);
                break;
            case STONE_RUN:
                stoneRunGenerator.generateStoneRun(map, h);
                break;
            case CYPRESS_BAYOU:
                cypressBayouGenerator.generateCypressBayou(map, h, gX, gZ, data.getWaterLevel());
                break;
            case GOLDSMITH_BOWL:
                goldsmithBowlGenerator.generateBowl(map, h, gX, gZ);
                break;
            case DOGLEG_RIVER:
                doglegRiverGenerator.generate(map, h, gX, gZ);
                doglegRiverGenerator.placeCornerTrees(map, h, t);
                break;
            case NONE:
            default:
                break;
        }
        PerfLog.log("archetype-specific generator", tArch);

        float water = data.getWaterLevel();

        long tBunker = PerfLog.now();
        if (spec.terrainFeature == ArchetypeSpec.TerrainFeature.TABLE_MOUNTAIN
                || spec.terrainFeature == ArchetypeSpec.TerrainFeature.BADLANDS) {
            bunkerGenerator.setGreensideBunkerRatio(1.0f);
        }
        bunkerGenerator.generateBunkers(map, h, data.getnBunkers(), data.getBunkerDepth(), gX, gZ, teeP, bunkerRecords, water);
        PerfLog.log("generateBunkers", tBunker);

        if (data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.RAISED_FAIRWAY) {
            long tOffset = PerfLog.now();
            applyPathOffset(map, h, 25.0f, gBuf);
            terrainPassApplier.tagChasmWalls(map);
            PerfLog.log("applyPathOffset+tagChasmWalls (RAISED)", tOffset);
        } else if (data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.SUNKEN_FAIRWAY) {
            long tOffset = PerfLog.now();
            applyPathOffset(map, h, -40.0f, gBuf);
            terrainPassApplier.tagChasmWalls(map);
            water = (Math.min(data.getTeeHeight() + 0.2f, data.getGreenHeight()) - 35.0f) - 10.0f;
            data.setWaterLevel(water);
            PerfLog.log("applyPathOffset+tagChasmWalls (SUNKEN)", tOffset);
        }

        long tSmooth = PerfLog.now();
        terrainPassApplier.smoothGreenBorders(map, h, isPathDependent, spec.smoothRoughAtGreenBorder);
        PerfLog.log("smoothGreenBorders", tSmooth);

        long tCoast = PerfLog.now();
        coastlineGenerator.applyFairwayWaterBuffer(map, h, water, 3.0f);
        PerfLog.log("applyFairwayWaterBuffer", tCoast);

        long tStone = PerfLog.now();
        coastlineGenerator.applySlopeBasedStone(map, h, 0.35f);
        PerfLog.log("applySlopeBasedStone", tStone);

        long tDeepRough = PerfLog.now();
        terrainPassApplier.applyDeepRoughPass(map);
        PerfLog.log("applyDeepRoughPass", tDeepRough);

        if (spec.sandPassCoverage > 0f) {
            long tSand = PerfLog.now();
            terrainPassApplier.applySandPass(map, spec.sandPassCoverage);
            PerfLog.log("applySandPass", tSand);
        }

        if (spec.roughToStone) {
            long tRock = PerfLog.now();
            terrainPassApplier.applyRoughToStonePass(map);
            PerfLog.log("roughToStone", tRock);
        }

        if (data.getMudHeight() > -99f) {
            long tMud = PerfLog.now();
            terrainPassApplier.applyMudPass(map, h, water);
            PerfLog.log("applyMudPass", tMud);
        }

        long tTrees = PerfLog.now();
        finalizePositionsAndTrees(map, h, teeP, holeP, t, m, gX, gZ, water);
        PerfLog.log("finalizePositionsAndTrees", tTrees);

        if (spec.spawnMonoliths) {
            long tMono = PerfLog.now();
            generateMonolithPlains(map, h, m, gX, gZ);
            PerfLog.log("generateMonolithPlains", tMono);
        }
    }

    private void finalizePositionsAndTrees(Terrain.TerrainType[][] map, float[][] heights,
                                            Vector3 teeP, Vector3 holeP,
                                            List<Tree> trees, List<Monolith> monoliths,
                                            int gX, int gZ, float water) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        int teeZ = (int) (SIZE_Z * 0.05f), teeX = (int)(SIZE_X * spec.teeXFraction);
        teeP.set((teeX * SCALE) - (SIZE_X * SCALE / 2f), heights[teeX][teeZ] + 0.2f, (teeZ * SCALE) - (SIZE_Z * SCALE / 2f));

        float randomAngle = rng.nextFloat() * MathUtils.PI * 2;
        float randomDist  = rng.nextFloat() * (data.getGreenRadius() * 0.7f);
        int flagX = MathUtils.clamp(gX + (int) (MathUtils.cos(randomAngle) * randomDist), 0, SIZE_X - 1);
        int flagZ = MathUtils.clamp(gZ + (int) (MathUtils.sin(randomAngle) * randomDist), 0, SIZE_Z - 1);
        holeP.set((flagX * SCALE) - (SIZE_X * SCALE / 2f), heights[flagX][flagZ], (flagZ * SCALE) - (SIZE_Z * SCALE / 2f));

        treePlacer.place(map, heights, trees, teeZ, teeX, water, gX, gZ);
    }

    private void generateMonolithPlains(Terrain.TerrainType[][] map, float[][] heights, List<Monolith> monoliths, int gX, int gZ) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        int spawnAttempts = (int) (SIZE_Z * 2.5f);
        float maxDist = (float) Math.sqrt(Math.pow(SIZE_X, 2) + Math.pow(SIZE_Z, 2));

        for (int i = 0; i < spawnAttempts; i++) {
            int x = rng.nextInt(SIZE_X), z = rng.nextInt(SIZE_Z);
            if (map[x][z] == Terrain.TerrainType.GREEN || map[x][z] == Terrain.TerrainType.FRINGE || map[x][z] == Terrain.TerrainType.TEE) continue;

            float distToGreen = Vector3.dst(x, z, 0, gX, gZ, 0);
            float p = MathUtils.clamp(1.0f - (distToGreen / maxDist), 0f, 1f);
            float prob = TerrainUtils.smoothstep(p);

            if (rng.nextFloat() < (prob * MONOLITH_SPAWN_CHANCE)) {
                monoliths.add(new Monolith((x * SCALE) - (SIZE_X * SCALE / 2f), heights[x][z] - MONOLITH_UNDERGROUND_OFFSET, (z * SCALE) - (SIZE_Z * SCALE / 2f), 2.0f, 18.0f, 8.0f, rng.nextFloat() * 360f));
            }
        }
    }

    private void applyPathOffset(Terrain.TerrainType[][] map, float[][] heights, float amount, boolean[][] greenBuffer) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        boolean[][] isPathMask = getPathMask(map);
        final float RAMP_WIDTH = 12.0f;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (isPathMask[x][z] || greenBuffer[x][z]) heights[x][z] += amount;
            }
        }

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (!isPathMask[x][z] && !greenBuffer[x][z]) {
                    float dist = getDistanceToPath(x, z, isPathMask, RAMP_WIDTH);
                    if (dist < RAMP_WIDTH) {
                        float targetDroppedHeight = heights[x][z];
                        float closestDistSq = Float.MAX_VALUE;
                        int range = (int) RAMP_WIDTH + 1;

                        for (int ix = x - range; ix <= x + range; ix++) {
                            for (int iz = z - range; iz <= z + range; iz++) {
                                if (ix >= 0 && ix < SIZE_X && iz >= 0 && iz < SIZE_Z && isPathMask[ix][iz]) {
                                    float dSq = (x - ix) * (x - ix) + (z - iz) * (z - iz);
                                    if (dSq < closestDistSq) {
                                        closestDistSq = dSq;
                                        targetDroppedHeight = heights[ix][iz];
                                    }
                                }
                            }
                        }
                        float t = MathUtils.clamp(dist / RAMP_WIDTH, 0f, 1f);
                        float smoothT = TerrainUtils.smoothstep(t);
                        heights[x][z] = MathUtils.lerp(targetDroppedHeight, heights[x][z], smoothT);
                    }
                }
            }
        }
    }

    private boolean[][] createGreenBuffer(Terrain.TerrainType[][] map) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        boolean[][] buffer = new boolean[SIZE_X][SIZE_Z];
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (map[x][z] == Terrain.TerrainType.GREEN || map[x][z] == Terrain.TerrainType.FRINGE) {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            int nx = x + dx, nz = z + dz;
                            if (nx >= 0 && nx < SIZE_X && nz >= 0 && nz < SIZE_Z && map[nx][nz] != Terrain.TerrainType.GREEN && map[nx][nz] != Terrain.TerrainType.FRINGE) {
                                buffer[nx][nz] = true;
                            }
                        }
                    }
                }
            }
        }
        return buffer;
    }

    private float getFinalRaw(float dG, float pR, float rH) {
        float t = MathUtils.clamp(dG / pR, 0f, 1f);
        return MathUtils.lerp(data.getGreenHeight(), rH, (float) Math.pow(TerrainUtils.smoothstep(t), 2));
    }

    private boolean[][] getPathMask(Terrain.TerrainType[][] map) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        boolean[][] mask = new boolean[SIZE_X][SIZE_Z];
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                Terrain.TerrainType t = map[x][z];
                mask[x][z] = (t == Terrain.TerrainType.FAIRWAY || t == Terrain.TerrainType.GREEN || t == Terrain.TerrainType.FRINGE || t == Terrain.TerrainType.TEE);
            }
        }
        return mask;
    }

    private float getDistanceToPath(int x, int z, boolean[][] mask, float maxDist) {
        float minDistSq = maxDist * maxDist;
        int range = (int) maxDist + 1;
        for (int ix = x - range; ix <= x + range; ix++) {
            for (int iz = z - range; iz <= z + range; iz++) {
                if (ix >= 0 && ix < mask.length && iz >= 0 && iz < mask[0].length && mask[ix][iz]) {
                    float dSq = (x - ix) * (x - ix) + (z - iz) * (z - iz);
                    if (dSq < minDistSq) minDistSq = dSq;
                }
            }
        }
        return (float) Math.sqrt(minDistSq);
    }

    private void mapStandardFeatures(Terrain.TerrainType[][] map, float[][] heights, int gX, int gZ, boolean isIsland) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        int teeCenterX = (int)(SIZE_X * spec.teeXFraction), teeCenterZ = (int) (SIZE_Z * 0.05f);
        float fWidth = data.getMaxFairwayWidth();
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                map[x][z] = Terrain.TerrainType.ROUGH;
                if (Math.abs(x - teeCenterX) < 7 && Math.abs(z - teeCenterZ) < 6) map[x][z] = Terrain.TerrainType.TEE;
                else {
                    GreenHelper.applySingleTileGreen(map, x, z, gX, gZ, data.getGreenRadius(), off1);
                }
            }
        }
        if (!spec.skipFairway) {
            if (data.getMinFairwayWidth() <= 0) processor.generateSegmentedFairway(map, gX, gZ, fWidth);
            else processor.generateContinuousFairway(map, gX, gZ, fWidth, data.getMinFairwayWidth(), data.getFairwayWiggle(), isIsland, data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.SUNKEN_FAIRWAY);
        }
    }

    private float calculateHeightNoise(int x, int z, float freq, float und, float maxH, LevelData.TerrainAlgorithm algo) {
        float wX = x * SCALE + off1, wZ = z * SCALE + off2;
        return switch (algo) {
            case MULTI_WAVE -> processor.generateMultiWaveNoise(wX, wZ, freq) * und * maxH * 2.5f;
            case TERRACED ->
                    Math.signum(processor.generateMultiWaveNoise(wX, wZ, freq)) * (float) Math.pow(Math.abs(processor.generateMultiWaveNoise(wX, wZ, freq)), 0.4f) * und * maxH * 2.5f;
            case MOUNDS ->
                    (float) Math.pow(Math.abs(processor.generateMultiWaveNoise(wX, wZ, freq)), 1.5f) * und * maxH * 4.0f;
            case RAISED_FAIRWAY, SUNKEN_FAIRWAY ->
                    processor.generateMultiWaveNoise(wX, wZ, freq * 0.5f) * und * maxH * 1.5f;
            case CRAGGY_RIDGES -> {
                float noise = processor.generateMultiWaveNoise(wX, wZ, freq);
                yield (1.0f - Math.abs(noise)) * und * maxH * 3.0f;
            }
            case DUNES -> {
                float noise = processor.generateMultiWaveNoise(wX, wZ, freq);
                yield Math.signum(noise) * (noise * noise) * und * maxH * 3.5f;
            }
            case ROLLING_DUNES -> {
                float slowFreq = freq * 0.3f;
                float noise = processor.generateMultiWaveNoise(wX, wZ, slowFreq);
                yield Math.signum(noise) * (float) Math.pow(Math.abs(noise), 1.5f) * und * maxH * 3.5f;
            }
            case PLATEAU -> {
                float noise = processor.generateMultiWaveNoise(wX, wZ, freq);
                float shelf = MathUtils.clamp(noise * 2.0f, -0.5f, 0.5f);
                yield shelf * und * maxH * 4.0f;
            }
            default -> (MathUtils.sin(wX * freq) * 2.0f + MathUtils.cos(wZ * freq * 0.6f) * 1.7f) * und * maxH;
        };
    }
}
