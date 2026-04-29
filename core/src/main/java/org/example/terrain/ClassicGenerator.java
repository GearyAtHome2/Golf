package org.example.terrain;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.features.*;
import org.example.terrain.level.LevelData;
import org.example.terrain.TerrainUtils;
import org.example.terrain.objects.Monolith;
import org.example.terrain.objects.Tree;
import org.example.util.PerfLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ClassicGenerator implements ITerrainGenerator {

    private final float SCALE = 1.0f;
    private final LevelData data;
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
    private float greenTiltDx, greenTiltDz; // seeded whole-green lean, one random direction per hole

    private List<CraterRecord> craters = new ArrayList<>();
    private List<BunkerGenerator.BunkerRecord> bunkerRecords = new ArrayList<>();

    public static record CraterRecord(float x, float z, float radius) {
    }

    public ClassicGenerator(LevelData data) {
        this.data = data;
        this.rng = new Random(data.getSeed());
        this.off1 = rng.nextFloat() * 1000f;
        this.off2 = rng.nextFloat() * 1000f;

        boolean isVineyard = data.getArchetype() == LevelData.Archetype.BIG_GRAPE_VINEYARDS;

        for (int i = 0; i < 10; i++) {
            if (isVineyard) {
                float baseAngle = rng.nextBoolean() ? MathUtils.PI * 0.5f : MathUtils.PI * 1.5f;
                float variation = (rng.nextFloat() - 0.5f) * (MathUtils.PI / 6f);
                waveAngles[i] = baseAngle + variation;
            } else {
                waveAngles[i] = rng.nextFloat() * MathUtils.PI * 2;
            }

            waveFreqs[i] = 1.0f + (rng.nextFloat() * 1.5f);
            waveAmps[i] = 1.0f / (i + 1.5f);
            waveOffsets[i] = rng.nextFloat() * 100f;
        }

        for (int i = 0; i < 4; i++) {
            greenWaveAngles[i] = rng.nextFloat() * MathUtils.PI * 2;
            greenWaveOffsets[i] = rng.nextFloat() * 100f;
            greenWaveFreqs[i] = 0.32f * (1f + (rng.nextFloat() - 0.5f) * 0.1f); // ±5%
        }
        float tiltAngle = rng.nextFloat() * MathUtils.PI * 2;
        float tiltMag   = 0.006f + rng.nextFloat() * 0.009f; // 0.006–0.015 units/cell
        greenTiltDx = MathUtils.cos(tiltAngle) * tiltMag;
        greenTiltDz = MathUtils.sin(tiltAngle) * tiltMag;

        // Determinism fingerprint — compare between devices to isolate desync source
        Gdx.app.log("GreenDet", "ClassicGenerator seed=" + data.getSeed());
        for (int i = 0; i < 4; i++) {
            float a = greenWaveAngles[i];
            Gdx.app.log("GreenDet", "  greenWave[" + i + "]"
                    + " angle=0x" + Integer.toHexString(Float.floatToRawIntBits(a))
                    + " cos=0x" + Integer.toHexString(Float.floatToRawIntBits(MathUtils.cos(a)))
                    + " sin=0x" + Integer.toHexString(Float.floatToRawIntBits(MathUtils.sin(a)))
                    + " offset=0x" + Integer.toHexString(Float.floatToRawIntBits(greenWaveOffsets[i])));
        }

        this.processor = new FeatureProcessor(data, rng, off1, off2, waveAngles, waveFreqs, waveAmps, waveOffsets);
        this.cenoteGenerator = new CenoteGenerator(rng);
        this.coastlineGenerator = new CoastlineGenerator();
        this.beachBluffsGenerator = new BeachBluffsGenerator(data, rng, waveAngles, waveFreqs, waveAmps, waveOffsets);
        this.craterGenerator = new CraterGenerator(rng);
        this.whistlingIslesGenerator = new WhistlingIslesGenerator(data, rng, waveAngles, waveFreqs, waveAmps, waveOffsets);
        this.vineyardsGenerator = new VineyardsGenerator();
        this.bunkerGenerator = new BunkerGenerator(rng);
        this.clippertonRockGenerator = new ClippertonRockGenerator(data, rng, waveAngles, waveFreqs, waveAmps, waveOffsets);
        this.oasisDunesGenerator = new OasisDunesGenerator(data);
        this.woodlandEdgeGenerator = new ForestEdgeGenerator(data);
        this.stoneRunGenerator = new StoneRunGenerator(data);
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

        ArchetypeFlags flags = new ArchetypeFlags(data);

        int greenCenterZ = (int) (SIZE_Z * 0.85f);
        float greenOffset = (rng.nextFloat() - 0.5f) * (SIZE_X * 0.5f);
        int greenCenterX = MathUtils.clamp((int) (SIZE_X / 2 + greenOffset), 20, SIZE_X - 20);

        long tTotal = PerfLog.now();
        PerfLog.snapshot("ClassicGenerator.generate() start  arch=" + data.getArchetype().name() + " dist=" + data.getDistance());

        long t1 = PerfLog.now();
        mapStandardFeatures(map, heights, greenCenterX, greenCenterZ, flags.isIslandMap);
        PerfLog.log("mapStandardFeatures", t1);

        // Distance cache must be built after mapStandardFeatures has placed the fairway
        if (flags.isMogulHighlands) {
            processor.buildDistanceCache(map);
        }

        long t2 = PerfLog.now();
        boolean[][] isPathMask = getPathMask(map);
        boolean[][] greenBuffer = createGreenBuffer(map);
        PerfLog.log("getPathMask + createGreenBuffer", t2);

        long t3 = PerfLog.now();
        generateHeightMap(map, heights, greenCenterX, greenCenterZ, isPathMask, greenBuffer, flags);
        PerfLog.log("generateHeightMap", t3);

        long t4 = PerfLog.now();
        applyPostProcessing(map, heights, monoliths, trees, greenCenterX, greenCenterZ, teePos, holePos, greenBuffer, flags);
        PerfLog.log("applyPostProcessing", t4);

        PerfLog.total("ClassicGenerator.generate() TOTAL", tTotal);

        Gdx.app.log("GENERATOR", String.format("Map: %s length: %s par: %s", data.getArchetype().name(), data.getDistance(), data.getPar()));
    }

    private void generateHeightMap(Terrain.TerrainType[][] map, float[][] heights, int gCX, int gCZ, boolean[][] pathMask, boolean[][] greenBuf, ArchetypeFlags flags) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        float teeSafety = data.getTeeHeight() + 0.2f;
        float teeFlatBufferZ = 0.12f;

        long tCellLoop = PerfLog.now();
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                float zNorm = z / (float) (SIZE_Z - 1);
                boolean isElevated = pathMask[x][z] || greenBuf[x][z];

                float baseElevation = calculateBaseElevation(zNorm, teeFlatBufferZ, teeSafety, isElevated, flags);
                float noise = calculateNoise(x, z, pathMask, flags);

                float hillRamp = MathUtils.clamp((zNorm - teeFlatBufferZ) / 0.1f, 0f, 1f);
                hillRamp = TerrainUtils.smoothstep(hillRamp);

                float currentHeight = baseElevation + (noise * hillRamp);

                if (flags.isIslandMap) {
                    currentHeight = coastlineGenerator.applyIslandCoastline(x, z, zNorm, currentHeight, map[x][z], data.getWaterLevel(), off1, off2);
                }

                float distGreen = Vector3.dst(x, z, 0, gCX, gCZ, 0);
                float greenRadius = flags.isOasisDunes ? SIZE_Z * 0.44f : SIZE_Z * 0.22f;
                float protectedHeight = (flags.isPathDependent && !isElevated) ? currentHeight : getFinalRaw(distGreen, greenRadius, currentHeight);

                float greenEffectMask = (float) Math.pow(1.0f - MathUtils.clamp(distGreen / 94.0f, 0f, 1f), 4.0f);
                float tilt = (x - gCX) * greenTiltDx + (z - gCZ) * greenTiltDz;
                protectedHeight += (GreenHelper.calculateUndulation(x, z, greenWaveAngles, greenWaveOffsets, greenWaveFreqs) + tilt) * greenEffectMask;

                float teeT = MathUtils.clamp(zNorm / 0.15f, 0f, 1f);
                heights[x][z] = MathUtils.lerp(teeSafety, protectedHeight, TerrainUtils.smoothstep(teeT));
            }
        }
        if (flags.isMogulHighlands) {
            PerfLog.log("generateHeightMap cell loop [" + SIZE_X + "x" + SIZE_Z + "=" + (SIZE_X * SIZE_Z) + " cells, mogul getDistToPath r=18 per cell]", tCellLoop);
        }

        // Heightmap fingerprint — XOR of all raw float bits plus 9 sample points around green centre
        int xorFp = 0;
        for (int x = 0; x < SIZE_X; x++)
            for (int z = 0; z < SIZE_Z; z++)
                xorFp ^= Float.floatToRawIntBits(heights[x][z]);
        Gdx.app.log("GreenDet", "ClassicGenerator heightmap XOR=0x" + Integer.toHexString(xorFp));

        StringBuilder sb = new StringBuilder("ClassicGenerator green samples (hex):");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int sx = gCX + dx * 8;
                int sz = gCZ + dz * 8;
                if (sx >= 0 && sx < SIZE_X && sz >= 0 && sz < SIZE_Z) {
                    sb.append(" [").append(sx).append(',').append(sz).append("]=0x")
                      .append(Integer.toHexString(Float.floatToRawIntBits(heights[sx][sz])));
                }
            }
        }
        Gdx.app.log("GreenDet", sb.toString());
    }

    private float calculateBaseElevation(float zNorm, float buffer, float safety, boolean isElevated, ArchetypeFlags flags) {
        if (zNorm <= buffer) return safety;

        float climbNorm = (zNorm - buffer) / (1.0f - buffer);
        float slopedElevation;

        if (flags.isCliffMap) {
            float sigmoid = 1f / (1f + (float) Math.exp(-100.0f * (climbNorm - 0.005f)));
            slopedElevation = MathUtils.lerp(safety, data.getGreenHeight(), sigmoid);
        } else {
            float curveStep = 1.0f - (float) Math.pow(1.0f - climbNorm, 2.5f);
            slopedElevation = MathUtils.lerp(safety, data.getGreenHeight(), curveStep);
        }

        return flags.isPathDependent ? (isElevated ? slopedElevation : safety) : slopedElevation;
    }

    private float calculateNoise(int x, int z, boolean[][] pathMask, ArchetypeFlags flags) {
        if (flags.isMogulHighlands) {
            return processor.calculateMogulNoise(x, z, SCALE, off1, off2, data.getHillFrequency(), data.getUndulation(), data.getMaxHeight());
        }
        return calculateHeightNoise(x, z, data.getHillFrequency(), data.getUndulation(), data.getMaxHeight(), data.getTerrainAlgorithm());
    }

    private void applyPostProcessing(Terrain.TerrainType[][] map, float[][] h, List<Monolith> m, List<Tree> t, int gX, int gZ, Vector3 teeP, Vector3 holeP, boolean[][] gBuf, ArchetypeFlags flags) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        long tArch = PerfLog.now();
        if (flags.isMonolithPlains) {
            data.setWaterLevel(-2.0f);
        } else if (flags.isCraterFields) {
            data.setWaterLevel(-10.0f);
            this.craters = craterGenerator.generateCraterField(map, h, (int) (SIZE_Z / 50.0f * 2.5f) + rng.nextInt(15));
        } else if (flags.isRoughBluffs) {
            data.setWaterLevel(-1f);
            beachBluffsGenerator.generateBeachBLuffs(map, h, gX, gZ, -2f);
        } else if (flags.isWhistlingIsles) {
            data.setWaterLevel(0f);
            whistlingIslesGenerator.generateWhistlingIsles(map, h, gX, gZ, 0f);
        } else if (flags.isPlungeCenotes) {
            data.setWaterLevel(-8.0f);
            cenoteGenerator.generatePlungeCenotes(map, h, -8f, 30.0f);
            cenoteGenerator.generateRoughCenotes(map, h, -8f);
        } else if (flags.isVineyards) {
            vineyardsGenerator.generateVineyards(h, map, rng, data.getTeeHeight() + 2.0f, 4.0f, 0.12f);
        } else if (flags.isClippertonRock) {
            data.setWaterLevel(0.0f);
            clippertonRockGenerator.generateClippertonRock(map, h, gX, gZ, 0.0f);
        } else if (flags.isOasisDunes) {
            oasisDunesGenerator.generateOasisDunes(map, h, gX, gZ);
        } else if (flags.isWoodlandEdge) {
            woodlandEdgeGenerator.generate(map, h, teeP, holeP);
        } else if (flags.isStoneRun) {
            stoneRunGenerator.generateStoneRun(map, h);
        }
        PerfLog.log("archetype-specific generator", tArch);

        float water = data.getWaterLevel();

        long tBunker = PerfLog.now();
        bunkerGenerator.generateBunkers(map, h, data.getnBunkers(), data.getBunkerDepth(), gX, gZ, teeP, bunkerRecords, water);
        PerfLog.log("generateBunkers", tBunker);

        if (data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.RAISED_FAIRWAY) {
            long tOffset = PerfLog.now();
            applyPathOffset(map, h, 25.0f, gBuf);
            tagChasmWalls(map);
            PerfLog.log("applyPathOffset+tagChasmWalls (RAISED)", tOffset);
        } else if (data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.SUNKEN_FAIRWAY) {
            long tOffset = PerfLog.now();
            applyPathOffset(map, h, -40.0f, gBuf);
            tagChasmWalls(map);
            water = (Math.min(data.getTeeHeight() + 0.2f, data.getGreenHeight()) - 35.0f) - 10.0f;
            data.setWaterLevel(water);
            PerfLog.log("applyPathOffset+tagChasmWalls (SUNKEN)", tOffset);
        }

        long tSmooth = PerfLog.now();
        smoothGreenBorders(map, h, flags.isPathDependent);
        PerfLog.log("smoothGreenBorders", tSmooth);

        long tCoast = PerfLog.now();
        coastlineGenerator.applyFairwayWaterBuffer(map, h, water, 3.0f);
        PerfLog.log("applyFairwayWaterBuffer", tCoast);

        long tStone = PerfLog.now();
        coastlineGenerator.applySlopeBasedStone(map, h, 0.35f);
        PerfLog.log("applySlopeBasedStone", tStone);

        long tDeepRough = PerfLog.now();
        applyDeepRoughPass(map);
        PerfLog.log("applyDeepRoughPass", tDeepRough);

        if (data.getMudHeight() > -99f) {
            long tMud = PerfLog.now();
            applyMudPass(map, h, water);
            PerfLog.log("applyMudPass", tMud);
        }

        long tTrees = PerfLog.now();
        finalizePositionsAndTrees(map, h, teeP, holeP, t, m, gX, gZ, water, flags);
        PerfLog.log("finalizePositionsAndTrees", tTrees);

        if (flags.isMonolithPlains) {
            long tMono = PerfLog.now();
            generateMonolithPlains(map, h, m, gX, gZ);
            PerfLog.log("generateMonolithPlains", tMono);
        }
    }

    private void finalizePositionsAndTrees(Terrain.TerrainType[][] map, float[][] heights, Vector3 teeP, Vector3 holeP, List<Tree> trees, List<Monolith> monoliths, int gX, int gZ, float water, ArchetypeFlags flags) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        int teeZ = (int) (SIZE_Z * 0.05f), teeX = SIZE_X / 2;
        teeP.set((teeX * SCALE) - (SIZE_X * SCALE / 2f), heights[teeX][teeZ] + 0.2f, (teeZ * SCALE) - (SIZE_Z * SCALE / 2f));

        float randomAngle = rng.nextFloat() * MathUtils.PI * 2, randomDist = rng.nextFloat() * 8f;
        int flagX = MathUtils.clamp(gX + (int) (MathUtils.cos(randomAngle) * randomDist), 0, SIZE_X - 1);
        int flagZ = MathUtils.clamp(gZ + (int) (MathUtils.sin(randomAngle) * randomDist), 0, SIZE_Z - 1);
        holeP.set((flagX * SCALE) - (SIZE_X * SCALE / 2f), heights[flagX][flagZ], (flagZ * SCALE) - (SIZE_Z * SCALE / 2f));

        if (flags.isVineyards) {
            generateVineyardTrees(map, heights, trees, teeZ, teeX, water, flags.isCliffMap);
        } else if (flags.isOasisDunes) {
            generateOasisTrees(map, heights, trees, teeZ, teeX, water);
        } else {
            generateRandomTrees(map, heights, trees, teeZ, teeX, water, flags.isCliffMap);
        }
    }

    private void generateVineyardTrees(Terrain.TerrainType[][] map, float[][] heights, List<Tree> trees, int teeZ, int teeX, float water, boolean isCliff) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        float cliffDelta = Math.abs(data.getTeeHeight() - data.getGreenHeight());
        float maxSlopeForFlatness = 0.05f;
        float mapBaseRotation = (rng.nextFloat() - 0.5f) * 10f;

        for (int tx = 4; tx < SIZE_X - 4; tx += 4) {
            for (int tz = 4; tz < SIZE_Z - 4; tz += 10) {
                float worldY = heights[tx][tz];

                if (worldY < water + 0.1f || (map[tx][tz] != Terrain.TerrainType.ROUGH && map[tx][tz] != Terrain.TerrainType.DEEP_ROUGH)) continue;
                if (tz <= teeZ + 40 && Math.abs(tx - teeX) < 30) continue;

                float zNorm = (float) tz / (SIZE_Z - 1);
                float probability = 1.0f;
                if (zNorm < 0.15f) {
                    probability = MathUtils.clamp((zNorm - 0.05f) / 0.10f, 0f, 1f);
                } else if (zNorm > 0.75f) {
                    probability = MathUtils.clamp(1.0f - (zNorm - 0.75f) / 0.10f, 0f, 1f);
                }

                if (rng.nextFloat() > (probability * probability)) continue;

                float dx = heights[tx + 1][tz] - worldY;
                float dz = heights[tx][tz + 1] - worldY;
                float slopeMag = (float) Math.sqrt(dx * dx + dz * dz);

                if (slopeMag < maxSlopeForFlatness) {
                    float offsetX = (rng.nextFloat() - 0.5f) * 0.5f;
                    float offsetZ = (rng.nextFloat() - 0.5f) * 1.0f;

                    float finalX = (tx + offsetX) * SCALE - (SIZE_X * SCALE / 2f);
                    float finalZ = (tz + offsetZ) * SCALE - (SIZE_Z * SCALE / 2f);

                    float tH = (isCliff ? cliffDelta : data.getTreeHeight()) * (0.9f + rng.nextFloat() * 0.2f);

                    trees.add(new Tree(
                            finalX,
                            worldY,
                            finalZ,
                            tH,
                            data.getTrunkRadius(),
                            data.getFoliageRadius(),
                            data.getTreeScheme(),
                            rng,
                            mapBaseRotation
                    ));
                }
            }
        }
    }

    private void generateRandomTrees(Terrain.TerrainType[][] map, float[][] heights, List<Tree> trees, int teeZ, int teeX, float water, boolean isCliff) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        float cliffDelta = Math.abs(data.getTeeHeight() - data.getGreenHeight());
        int treeCount = (int) (SIZE_Z * data.getTreeDensity() * 2.5f);

        float mapBaseRotation = rng.nextFloat() * 360f;

        boolean isWoodlandEdge = data.getArchetype() == LevelData.Archetype.WOODLAND_EDGE;

        for (int i = 0; i < treeCount; i++) {
            int tx = rng.nextInt(SIZE_X - 1), tz = rng.nextInt(SIZE_Z - 1);
            float worldY = heights[tx][tz];

            if (isWoodlandEdge) {
                if (tz <= teeZ + 20 && Math.abs(tx - teeX) < 5) continue;
            } else {
                if (tz <= teeZ + 40 && Math.abs(tx - teeX) < 30) continue;
            }

            if (worldY < water + 0.1f || (map[tx][tz] != Terrain.TerrainType.ROUGH && map[tx][tz] != Terrain.TerrainType.DEEP_ROUGH)) continue;

            float slope = (float) Math.sqrt(Math.pow(heights[tx + 1][tz] - worldY, 2) + Math.pow(heights[tx][tz + 1] - worldY, 2));
            if (slope > 1.2f) continue;

            float treeChance = 1.0f;
            for (CraterRecord crater : craters) {
                float dist = (float) Math.sqrt(Math.pow(tx - crater.x, 2) + Math.pow(tz - crater.z, 2));
                if (dist < crater.radius) {
                    treeChance = 0;
                    break;
                } else if (dist < crater.radius * 2.0f)
                    treeChance = Math.min(treeChance, (dist - crater.radius) / crater.radius);
            }

            for (BunkerGenerator.BunkerRecord br : bunkerRecords) {
                if (Vector2.dst(tx, tz, br.x(), br.z()) < br.radius()) {
                    treeChance = 0;
                    break;
                }
            }

            if (rng.nextFloat() > treeChance) continue;

            float tH = (isCliff ? cliffDelta : data.getTreeHeight()) * (0.8f + rng.nextFloat() * 0.3f);
            boolean placeCypress = isCliff && rng.nextFloat() < 0.2f;
            Tree.TreeScheme scheme = placeCypress ? Tree.TreeScheme.CYPRESS : data.getTreeScheme();
            float finalTH = placeCypress ? tH * 0.6f : tH;

            trees.add(new Tree(
                    (tx * SCALE) - (SIZE_X * SCALE / 2f),
                    worldY,
                    (tz * SCALE) - (SIZE_Z * SCALE / 2f),
                    finalTH,
                    data.getTrunkRadius(),
                    data.getFoliageRadius(),
                    scheme,
                    rng,
                    mapBaseRotation
            ));
        }
    }

    private void generateOasisTrees(Terrain.TerrainType[][] map, float[][] heights, List<Tree> trees, int teeZ, int teeX, float water) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        float mapBaseRotation = rng.nextFloat() * 360f;

        // Probabilities per tile type — tune treeDensity in the spec to scale both together
        float roughProb = data.getTreeDensity() * 0.05f;  // grove around oasis shore
        float sandProb  = data.getTreeDensity() * 0.002f; // occasional scrub on dunes

        for (int tx = 1; tx < SIZE_X - 1; tx++) {
            for (int tz = 1; tz < SIZE_Z - 1; tz++) {
                if (tz <= teeZ + 40 && Math.abs(tx - teeX) < 30) continue;
                if (heights[tx][tz] < water + 0.1f) continue;

                float prob;
                if (map[tx][tz] == Terrain.TerrainType.ROUGH || map[tx][tz] == Terrain.TerrainType.DEEP_ROUGH) {
                    prob = roughProb;
                } else if (map[tx][tz] == Terrain.TerrainType.SAND) {
                    prob = sandProb;
                } else {
                    continue;
                }

                if (rng.nextFloat() > prob) continue;

                float slope = (float) Math.sqrt(
                    Math.pow(heights[tx + 1][tz] - heights[tx][tz], 2) +
                    Math.pow(heights[tx][tz + 1] - heights[tx][tz], 2));
                if (slope > 1.2f) continue;

                float tH = data.getTreeHeight() * (0.8f + rng.nextFloat() * 0.3f);
                trees.add(new Tree(
                    (tx * SCALE) - (SIZE_X * SCALE / 2f),
                    heights[tx][tz],
                    (tz * SCALE) - (SIZE_Z * SCALE / 2f),
                    tH,
                    data.getTrunkRadius(),
                    data.getFoliageRadius(),
                    data.getTreeScheme(),
                    rng,
                    mapBaseRotation
                ));
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

    private void tagChasmWalls(Terrain.TerrainType[][] map) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        Terrain.TerrainType[][] nextMap = new Terrain.TerrainType[SIZE_X][SIZE_Z];
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                nextMap[x][z] = map[x][z];
                boolean isPath = (map[x][z] == Terrain.TerrainType.FAIRWAY || map[x][z] == Terrain.TerrainType.GREEN || map[x][z] == Terrain.TerrainType.FRINGE || map[x][z] == Terrain.TerrainType.TEE);
                boolean neighborIsOpposite = false;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = x + dx, nz = z + dz;
                        if (nx >= 0 && nx < SIZE_X && nz >= 0 && nz < SIZE_Z) {
                            Terrain.TerrainType t = map[nx][nz];
                            boolean neighborIsPath = (t == Terrain.TerrainType.FAIRWAY || t == Terrain.TerrainType.GREEN || t == Terrain.TerrainType.FRINGE || t == Terrain.TerrainType.TEE);
                            if (isPath != neighborIsPath) {
                                neighborIsOpposite = true;
                                break;
                            }
                        }
                    }
                    if (neighborIsOpposite) break;
                }
                if (neighborIsOpposite) nextMap[x][z] = Terrain.TerrainType.STONE;
            }
        }
        for (int x = 0; x < SIZE_X; x++) System.arraycopy(nextMap[x], 0, map[x], 0, SIZE_Z);
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

    /** Converts ROUGH/DEEP_ROUGH tiles at or below water + mudHeight into MUD. */
    private void applyMudPass(Terrain.TerrainType[][] map, float[][] heights, float water) {
        float ceiling = water + data.getMudHeight();
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                Terrain.TerrainType t = map[x][z];
                if (t != Terrain.TerrainType.ROUGH && t != Terrain.TerrainType.DEEP_ROUGH) continue;
                if (heights[x][z] <= ceiling) map[x][z] = Terrain.TerrainType.MUD;
            }
        }
    }

    /**
     * Places DEEP_ROUGH as organic patches within the ROUGH, keeping a hard
     * Probability ramps from 0 at the fairway edge to full patch probability at
     * FALLOFF_TILES distance, then patch noise determines which eligible tiles convert.
     */
    private void applyDeepRoughPass(Terrain.TerrainType[][] map) {
        if (data.getDeepRoughThreshold() < 0) return;

        int SIZE_X = map.length, SIZE_Z = map[0].length;

        final float PATCH_FREQ     = 0.07f;  // ~25-tile patch wavelength
        final float PATCH_COVERAGE = 0.15f / data.getRoughDeepCover();  // higher cover → lower threshold → more deep rough
        final float PATCH_FEATHER  = 0.15f;  // soft edge width
        final float FALLOFF_TILES  = 5f;     // distance at which full patch probability is reached
        final int   FALLOFF_RADIUS = (int) Math.ceil(FALLOFF_TILES);

        // Precompute which tiles count as "near fairway"
        boolean[][] isPath = new boolean[SIZE_X][SIZE_Z];
        for (int x = 0; x < SIZE_X; x++)
            for (int z = 0; z < SIZE_Z; z++) {
                Terrain.TerrainType t = map[x][z];
                isPath[x][z] = t == Terrain.TerrainType.FAIRWAY || t == Terrain.TerrainType.GREEN || t == Terrain.TerrainType.FRINGE;
            }

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (map[x][z] != Terrain.TerrainType.ROUGH) continue;

                // Find distance to nearest path tile within the falloff radius
                float minDistSq = (FALLOFF_RADIUS + 1f) * (FALLOFF_RADIUS + 1f);
                for (int dx = -FALLOFF_RADIUS; dx <= FALLOFF_RADIUS; dx++) {
                    for (int dz = -FALLOFF_RADIUS; dz <= FALLOFF_RADIUS; dz++) {
                        int nx = x + dx, nz = z + dz;
                        if (nx >= 0 && nx < SIZE_X && nz >= 0 && nz < SIZE_Z && isPath[nx][nz]) {
                            float dSq = dx * dx + dz * dz;
                            if (dSq < minDistSq) minDistSq = dSq;
                        }
                    }
                }

                // Ramp from 0 at the fairway edge to 1.0 at FALLOFF_TILES distance
                float distFactor = MathUtils.clamp((float) Math.sqrt(minDistSq) / FALLOFF_TILES, 0f, 1f);

                // Patch noise with feathered edges, scaled by distance factor
                float n = processor.generateMultiWaveNoise(x + off1, z + off2, PATCH_FREQ);
                float chance = MathUtils.clamp((n - (PATCH_COVERAGE - PATCH_FEATHER)) / (PATCH_FEATHER * 2f), 0f, 1f);
                if (rng.nextFloat() < chance * distFactor) map[x][z] = Terrain.TerrainType.DEEP_ROUGH;
            }
        }
    }

    private void smoothGreenBorders(Terrain.TerrainType[][] map, float[][] heights, boolean skipRough) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        float[][] buffer = new float[SIZE_X][SIZE_Z];
        float[] kernel = {1 / 16f, 2 / 16f, 1 / 16f, 2 / 16f, 4 / 16f, 2 / 16f, 1 / 16f, 2 / 16f, 1 / 16f};
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                buffer[x][z] = heights[x][z];
                boolean isGreen = map[x][z] == Terrain.TerrainType.GREEN || map[x][z] == Terrain.TerrainType.FRINGE;
                boolean isIslandRough = !skipRough && data.getArchetype() == LevelData.Archetype.ISLAND_COAST && map[x][z] == Terrain.TerrainType.ROUGH;
                if ((isGreen || isIslandRough) && isBorderTile(x, z, map)) {
                    float avg = 0;
                    int kIdx = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int nx = MathUtils.clamp(x + dx, 0, SIZE_X - 1), nz = MathUtils.clamp(z + dz, 0, SIZE_Z - 1);
                            avg += heights[nx][nz] * kernel[kIdx++];
                        }
                    }
                    buffer[x][z] = avg;
                }
            }
        }
        for (int x = 0; x < SIZE_X; x++) System.arraycopy(buffer[x], 0, heights[x], 0, SIZE_Z);
    }

    private boolean isBorderTile(int x, int z, Terrain.TerrainType[][] map) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int nx = x + dx, nz = z + dz;
                if (nx >= 0 && nx < map.length && nz >= 0 && nz < map[0].length && map[nx][nz] != map[x][z])
                    return true;
            }
        }
        return false;
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
        int teeCenterX = SIZE_X / 2, teeCenterZ = (int) (SIZE_Z * 0.05f);
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
        if (data.getMinFairwayWidth() <= 0) processor.generateSegmentedFairway(map, gX, gZ, fWidth);
        else
            processor.generateContinuousFairway(map, gX, gZ, fWidth, data.getMinFairwayWidth(), data.getFairwayWiggle(), isIsland, data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.SUNKEN_FAIRWAY);
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

    private static class ArchetypeFlags {
        final boolean isCliffMap, isIslandMap, isCraterFields, isRoughBluffs, isWhistlingIsles, isMogulHighlands, isMonolithPlains, isPlungeCenotes, isVineyards, isClippertonRock, isOasisDunes, isStoneRun, isWoodlandEdge, isPathDependent;
        ArchetypeFlags(LevelData data) {
            isCliffMap = data.getArchetype() == LevelData.Archetype.CLIFFSIDE_BLUFF;
            isIslandMap = data.getArchetype() == LevelData.Archetype.ISLAND_COAST;
            isCraterFields = data.getArchetype() == LevelData.Archetype.CRATER_FIELDS;
            isRoughBluffs = data.getArchetype() == LevelData.Archetype.ROUGH_HOUGH_BLUFFS;
            isWhistlingIsles = data.getArchetype() == LevelData.Archetype.WHISTLING_ISLES;
            isMogulHighlands = data.getArchetype() == LevelData.Archetype.MOGUL_HIGHLANDS;
            isMonolithPlains = data.getArchetype() == LevelData.Archetype.MONOLITH_PLAINS;
            isPlungeCenotes = data.getArchetype() == LevelData.Archetype.PLUNGE_CENOTES;
            isVineyards = data.getArchetype() == LevelData.Archetype.BIG_GRAPE_VINEYARDS;
            isClippertonRock = data.getArchetype() == LevelData.Archetype.CLIPPERTON_ROCK;
            isOasisDunes = data.getArchetype() == LevelData.Archetype.OASIS_DUNES;
            isStoneRun = data.getArchetype() == LevelData.Archetype.STONE_RUN;
            isWoodlandEdge = data.getArchetype() == LevelData.Archetype.WOODLAND_EDGE;
            isPathDependent = data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.RAISED_FAIRWAY || data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.SUNKEN_FAIRWAY;
        }
    }
}