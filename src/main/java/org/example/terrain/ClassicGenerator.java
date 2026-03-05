package org.example.terrain;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.LevelData;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ClassicGenerator implements ITerrainGenerator {

    private final float SCALE = 1.0f;
    private final LevelData data;
    private final Random rng;
    private final FeatureProcessor processor;

    // --- Smoothing Configuration ---
    private final int SMOOTH_RADIUS = 2;
    private final float SMOOTH_FAIRWAY_BUFFER = 6.0f;
    private final float SMOOTH_STRENGTH = 0.9f;

    // --- Monolith Configuration ---
    private final float MONOLITH_UNDERGROUND_OFFSET = 1.0f;
    /** * Base spawn probability scalar.
     * 0.033f represents a ~30x reduction from the original 1.0f probability curve.
     */
    private final float MONOLITH_SPAWN_CHANCE = 0.033f;

    // --- Mogul Configuration ---
    private final float MOGUL_BASE_DAMPING = 0.3f;
    private final float MOGUL_FADE_DISTANCE = 18.0f;

    private final float off1, off2;
    private final float[] waveAngles = new float[10];
    private final float[] waveFreqs = new float[10];
    private final float[] waveAmps = new float[10];
    private final float[] waveOffsets = new float[10];

    private final float[] greenWaveAngles = new float[4];
    private final float[] greenWaveOffsets = new float[4];

    private List<CraterRecord> craters = new ArrayList<>();

    public static record CraterRecord(float x, float z, float radius) {
    }

    public ClassicGenerator(LevelData data) {
        this.data = data;
        this.rng = new Random(data.getSeed());
        this.off1 = rng.nextFloat() * 1000f;
        this.off2 = rng.nextFloat() * 1000f;

        for (int i = 0; i < 10; i++) {
            waveAngles[i] = rng.nextFloat() * MathUtils.PI * 2;
            waveFreqs[i] = 1.0f + (rng.nextFloat() * 1.5f);
            waveAmps[i] = 1.0f / (i + 1.5f);
            waveOffsets[i] = rng.nextFloat() * 100f;
        }

        for (int i = 0; i < 4; i++) {
            greenWaveAngles[i] = rng.nextFloat() * MathUtils.PI * 2;
            greenWaveOffsets[i] = rng.nextFloat() * 100f;
        }

        this.processor = new FeatureProcessor(data, rng, off1, off2, waveAngles, waveFreqs, waveAmps, waveOffsets);
    }

    public LevelData getData() {
        return data;
    }

    @Override
    public void generate(Terrain.TerrainType[][] map, float[][] heights, List<Terrain.Tree> trees, List<Terrain.Monolith> monoliths, Vector3 teePos, Vector3 holePos) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        craters.clear();
        monoliths.clear();

        float hillFreq = data.getHillFrequency();
        float maxHeight = data.getMaxHeight();
        float undulation = data.getUndulation();
        float waterLevel = data.getWaterLevel();
        float maxFairwayWidth = data.getMaxFairwayWidth();
        float teeSafetyElevation = data.getTeeHeight() + 0.2f;

        boolean isCliffMap = data.getArchetype() == LevelData.Archetype.CLIFFSIDE_BLUFF;
        boolean isIslandMap = data.getArchetype() == LevelData.Archetype.ISLAND_COAST;
        boolean isCraterFields = data.getArchetype() == LevelData.Archetype.CRATER_FIELDS;
        boolean isMogulHighlands = data.getArchetype() == LevelData.Archetype.MOGUL_HIGHLANDS;
        boolean isMonolithPlains = data.getArchetype() == LevelData.Archetype.MONOLITH_PLAINS;

        boolean isRaisedFairway = data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.RAISED_FAIRWAY;
        boolean isSunkenFairway = data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.SUNKEN_FAIRWAY;

        int greenCenterZ = (int) (SIZE_Z * 0.92f);
        float greenOffset = (rng.nextFloat() - 0.5f) * (SIZE_X * 0.5f);
        int greenCenterX = MathUtils.clamp((int) (SIZE_X / 2 + greenOffset), 20, SIZE_X - 20);

        mapStandardFeatures(map, heights, greenCenterX, greenCenterZ, waterLevel, maxFairwayWidth, isIslandMap);
        boolean[][] isPathMask = getPathMask(map);

        boolean[][] greenBuffer = new boolean[SIZE_X][SIZE_Z];
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (map[x][z] == Terrain.TerrainType.GREEN) {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            int nx = x + dx, nz = z + dz;
                            if (nx >= 0 && nx < SIZE_X && nz >= 0 && nz < SIZE_Z) {
                                if (map[nx][nz] != Terrain.TerrainType.GREEN) greenBuffer[nx][nz] = true;
                            }
                        }
                    }
                }
            }
        }

        float cliffSteepness = isCliffMap ? 100.0f : 15.0f;
        float teeFlatBufferZ = 0.12f;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                float zNorm = z / (float) (SIZE_Z - 1);
                float baseElevation;
                boolean isPath = isPathMask[x][z];
                boolean isElevated = isPath || greenBuffer[x][z];

                if (zNorm <= teeFlatBufferZ) {
                    baseElevation = teeSafetyElevation;
                } else {
                    float climbNorm = (zNorm - teeFlatBufferZ) / (1.0f - teeFlatBufferZ);
                    float slopedElevation;
                    if (isCliffMap) {
                        float sigmoid = 1f / (1f + (float) Math.exp(-cliffSteepness * (climbNorm - 0.15f)));
                        slopedElevation = MathUtils.lerp(teeSafetyElevation, data.getGreenHeight(), sigmoid);
                    } else {
                        float curveStep = 1.0f - (float) Math.pow(1.0f - climbNorm, 2.5f);
                        slopedElevation = MathUtils.lerp(teeSafetyElevation, data.getGreenHeight(), curveStep);
                    }
                    baseElevation = (isRaisedFairway || isSunkenFairway) ? (isElevated ? slopedElevation : teeSafetyElevation) : slopedElevation;
                }

                float noise;
                if (isMogulHighlands) {
                    float wX = x * SCALE + off1, wZ = z * SCALE + off2;
                    float rawMogul = (float) Math.pow(Math.abs(processor.generateMultiWaveNoise(wX, wZ, hillFreq)), 1.2f) * undulation * maxHeight * 6.0f;

                    float dist = getDistanceToPath(x, z, isPathMask, MOGUL_FADE_DISTANCE);
                    float t = MathUtils.clamp(dist / MOGUL_FADE_DISTANCE, 0f, 1f);
                    float smoothT = t * t * (3 - 2 * t);

                    float mogulIntensity = MathUtils.lerp(MOGUL_BASE_DAMPING, 1.0f, smoothT);
                    noise = rawMogul * mogulIntensity;
                } else {
                    noise = calculateHeightNoise(x, z, hillFreq, undulation, maxHeight, data.getTerrainAlgorithm());
                }

                float hillRamp = MathUtils.clamp((zNorm - teeFlatBufferZ) / 0.1f, 0f, 1f);
                hillRamp = hillRamp * hillRamp * (3 - 2 * hillRamp);
                float currentHeight = baseElevation + (noise * hillRamp);

                if (isIslandMap) currentHeight = applyIslandCoastline(x, z, zNorm, currentHeight, map[x][z], waterLevel);

                float dxGreen = x - greenCenterX;
                float dzGreen = z - greenCenterZ;
                float distGreen = (float) Math.sqrt(dxGreen * dxGreen + dzGreen * dzGreen);
                float protectionRadius = SIZE_Z * 0.22f;

                float protectedHeight = ((isRaisedFairway || isSunkenFairway) && !isElevated) ? currentHeight : getFinalRaw(distGreen, protectionRadius, currentHeight);

                float t = MathUtils.clamp(distGreen / protectionRadius, 0f, 1f);
                float greenEffectMask = 1.0f - (t * t * (3 - 2 * t));
                greenEffectMask *= greenEffectMask;

                protectedHeight += (calculateGreenUndulation(x, z) * greenEffectMask);

                float teeT = MathUtils.clamp(zNorm / 0.15f, 0f, 1f);
                float smoothT = teeT * teeT * (3 - 2 * teeT);
                heights[x][z] = MathUtils.lerp(teeSafetyElevation, protectedHeight, smoothT);
            }
        }

        if (isCraterFields) {
            data.setWaterLevel(-10f);
            waterLevel = -10.0f;
            int craterCount = 10 + rng.nextInt(15);
            this.craters = processor.generateCraterField(map, heights, craterCount);
        }

        smoothGreenBorders(map, heights, isRaisedFairway || isSunkenFairway);

        float offsetAmount = 25.0f;
        if (isRaisedFairway) {
            applyPathOffset(map, heights, offsetAmount, greenBuffer);
            tagChasmWalls(map);
        } else if (isSunkenFairway) {
            applyPathOffset(map, heights, -offsetAmount, greenBuffer);
            tagChasmWalls(map);
            waterLevel = (Math.min(teeSafetyElevation, data.getGreenHeight()) - offsetAmount) - 10.0f;
            data.setWaterLevel(waterLevel);
        }

        if (isMogulHighlands) {
            applyFairwayGaussianSmoothing(map, heights);
        }

        applyFairwayWaterBuffer(map, heights, waterLevel, 3.0f);
        applySlopeBasedStone(map, heights, 0.35f);
        finalizePositionsAndTrees(map, heights, teePos, holePos, trees, monoliths, greenCenterX, greenCenterZ, waterLevel, isCliffMap);

        if (isMonolithPlains) {
            generateMonolithPlains(map, heights, monoliths, greenCenterX, greenCenterZ, (int) (SIZE_X * 0.5f), (int) (SIZE_Z * 0.05f));
        }
    }

    private void generateMonolithPlains(Terrain.TerrainType[][] map, float[][] heights, List<Terrain.Monolith> monoliths, int gX, int gZ, int tX, int tZ) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        int spawnAttempts = (int) (SIZE_Z * 2.5f);
        float maxDist = (float) Math.sqrt(Math.pow(SIZE_X, 2) + Math.pow(SIZE_Z, 2));

        for (int i = 0; i < spawnAttempts; i++) {
            int x = rng.nextInt(SIZE_X);
            int z = rng.nextInt(SIZE_Z);

            Terrain.TerrainType type = map[x][z];
            if (type == Terrain.TerrainType.GREEN || type == Terrain.TerrainType.TEE) continue;

            float dx = x - gX;
            float dz = z - gZ;
            float distToGreen = (float) Math.sqrt(dx * dx + dz * dz);

            // Base probability curve (higher near green)
            float p = MathUtils.clamp(1.0f - (distToGreen / maxDist), 0f, 1f);
            float baseProbability = p * p * (3 - 2 * p);

            // Apply the configurable spawn chance scalar (e.g. 0.033 for ~30x reduction)
            if (rng.nextFloat() < (baseProbability * MONOLITH_SPAWN_CHANCE)) {
                float worldX = (x * SCALE) - (SIZE_X * SCALE / 2f);
                float worldZ = (z * SCALE) - (SIZE_Z * SCALE / 2f);
                float worldY = heights[x][z] - MONOLITH_UNDERGROUND_OFFSET;
                float rotation = rng.nextFloat() * 360f;

                // 1:4:9 Ratio Monolith
                monoliths.add(new Terrain.Monolith(worldX, worldY, worldZ, 2.0f, 18.0f, 8.0f, rotation));
            }
        }
    }

    private void applyFairwayGaussianSmoothing(Terrain.TerrainType[][] map, float[][] heights) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        float[][] smoothed = new float[SIZE_X][SIZE_Z];
        boolean[][] isPathMask = getPathMask(map);

        float[][] kernel = {
                {1 / 256f, 4 / 256f, 6 / 256f, 4 / 256f, 1 / 256f},
                {4 / 256f, 16 / 256f, 24 / 256f, 16 / 256f, 4 / 256f},
                {6 / 256f, 24 / 256f, 36 / 256f, 24 / 256f, 6 / 256f},
                {4 / 256f, 16 / 256f, 24 / 256f, 16 / 256f, 4 / 256f},
                {1 / 256f, 4 / 256f, 6 / 256f, 4 / 256f, 1 / 256f}
        };

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                float dist = getDistanceToPath(x, z, isPathMask, SMOOTH_FAIRWAY_BUFFER);
                if (dist < SMOOTH_FAIRWAY_BUFFER) {
                    float weightSum = 0, heightSum = 0;
                    for (int dx = -SMOOTH_RADIUS; dx <= SMOOTH_RADIUS; dx++) {
                        for (int dz = -SMOOTH_RADIUS; dz <= SMOOTH_RADIUS; dz++) {
                            int nx = x + dx, nz = z + dz;
                            if (nx >= 0 && nx < SIZE_X && nz >= 0 && nz < SIZE_Z) {
                                float kVal = kernel[dx + SMOOTH_RADIUS][dz + SMOOTH_RADIUS];
                                heightSum += heights[nx][nz] * kVal;
                                weightSum += kVal;
                            }
                        }
                    }
                    float blurredHeight = heightSum / weightSum;
                    float falloff = MathUtils.clamp(1.0f - (dist / SMOOTH_FAIRWAY_BUFFER), 0, 1);
                    smoothed[x][z] = MathUtils.lerp(heights[x][z], blurredHeight, SMOOTH_STRENGTH * falloff);
                } else {
                    smoothed[x][z] = heights[x][z];
                }
            }
        }
        for (int x = 0; x < SIZE_X; x++) System.arraycopy(smoothed[x], 0, heights[x], 0, SIZE_Z);
    }

    private void tagChasmWalls(Terrain.TerrainType[][] map) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (map[x][z] == Terrain.TerrainType.ROUGH) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int nx = x + dx, nz = z + dz;
                            if (nx >= 0 && nx < SIZE_X && nz >= 0 && nz < SIZE_Z) {
                                Terrain.TerrainType t = map[nx][nz];
                                if (t == Terrain.TerrainType.FAIRWAY || t == Terrain.TerrainType.GREEN || t == Terrain.TerrainType.TEE) {
                                    map[x][z] = Terrain.TerrainType.STONE;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void applyPathOffset(Terrain.TerrainType[][] map, float[][] heights, float amount, boolean[][] greenBuffer) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        boolean[][] isPathMask = getPathMask(map);
        boolean isSunken = data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.SUNKEN_FAIRWAY;
        boolean isRaised = data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.RAISED_FAIRWAY;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (isPathMask[x][z] || greenBuffer[x][z]) {
                    heights[x][z] += amount;
                } else if (!isSunken && !isRaised) {
                    float dist = getDistanceToPath(x, z, isPathMask, 8.0f);
                    float t = MathUtils.clamp(1.0f - (dist / 8.0f), 0, 1);
                    heights[x][z] += (amount * t * t * (3 - 2 * t));
                }
            }
        }
    }

    private void finalizePositionsAndTrees(Terrain.TerrainType[][] map, float[][] heights, Vector3 teeP, Vector3 holeP, List<Terrain.Tree> trees, List<Terrain.Monolith> monoliths, int gX, int gZ, float water, boolean isCliff) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        int teeZ = (int) (SIZE_Z * 0.05f), teeX = SIZE_X / 2;
        teeP.set((teeX * SCALE) - (SIZE_X * SCALE / 2f), heights[teeX][teeZ] + 0.2f, (teeZ * SCALE) - (SIZE_Z * SCALE / 2f));

        float randomAngle = rng.nextFloat() * MathUtils.PI * 2, randomDist = rng.nextFloat() * 8f;
        int flagX = MathUtils.clamp(gX + (int) (MathUtils.cos(randomAngle) * randomDist), 0, SIZE_X - 1);
        int flagZ = MathUtils.clamp(gZ + (int) (MathUtils.sin(randomAngle) * randomDist), 0, SIZE_Z - 1);
        holeP.set((flagX * SCALE) - (SIZE_X * SCALE / 2f), heights[flagX][flagZ], (flagZ * SCALE) - (SIZE_Z * SCALE / 2f));

        float cliffDelta = Math.abs(data.getTeeHeight() - data.getGreenHeight());
        int treeCount = (int) (SIZE_Z * data.getTreeDensity());

        for (int i = 0; i < treeCount; i++) {
            int tx = rng.nextInt(SIZE_X - 1), tz = rng.nextInt(SIZE_Z - 1);
            float worldY = heights[tx][tz];
            if (tz <= teeZ + 30 && Math.abs(tx - teeX) < 15) continue;
            if (worldY < water + 0.5f || (map[tx][tz] != Terrain.TerrainType.ROUGH)) continue;
            float slope = (float) Math.sqrt(Math.pow(heights[tx + 1][tz] - worldY, 2) + Math.pow(heights[tx][tz + 1] - worldY, 2));
            if (slope > 1.2f) continue;

            float treeChance = 1.0f;
            for (CraterRecord crater : craters) {
                float dist = (float) Math.sqrt(Math.pow(tx - crater.x, 2) + Math.pow(tz - crater.z, 2));
                if (dist < crater.radius) { treeChance = 0; break; }
                else if (dist < crater.radius * 2.0f) treeChance = Math.min(treeChance, (dist - crater.radius) / crater.radius);
            }
            if (rng.nextFloat() > treeChance) continue;
            float tH = isCliff ? cliffDelta * (0.8f + rng.nextFloat() * 0.2f) : data.getTreeHeight();
            trees.add(new Terrain.Tree((tx * SCALE) - (SIZE_X * SCALE / 2f), worldY, (tz * SCALE) - (SIZE_Z * SCALE / 2f), tH, data.getTrunkRadius(), data.getFoliageRadius()));
        }
    }

    private float applyIslandCoastline(int x, int z, float zNorm, float currentHeight, Terrain.TerrainType type, float waterLevel) {
        float coastThreshold = 0.45f + MathUtils.sin(x * 0.05f + off1) * 0.04f + MathUtils.cos(x * 0.4f + off2) * 0.01f;
        boolean isPlayable = (type == Terrain.TerrainType.FAIRWAY || type == Terrain.TerrainType.TEE || type == Terrain.TerrainType.GREEN);
        if (zNorm > coastThreshold && !isPlayable) {
            if (type == Terrain.TerrainType.ROUGH) currentHeight -= 20.0f;
            else if (type == Terrain.TerrainType.SAND) currentHeight = MathUtils.lerp(waterLevel - 1.5f, currentHeight, MathUtils.clamp((currentHeight - waterLevel) / 5.0f, 0f, 1f));
        }
        if (isPlayable && currentHeight < waterLevel + 1.0f) currentHeight = waterLevel + 1.0f;
        return currentHeight;
    }

    private void applyFairwayWaterBuffer(Terrain.TerrainType[][] map, float[][] heights, float waterLevel, float tileDistance) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        float distSqThreshold = tileDistance * tileDistance;
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (map[x][z] == Terrain.TerrainType.FAIRWAY) {
                    if (heights[x][z] <= waterLevel + 0.5f) { map[x][z] = Terrain.TerrainType.ROUGH; continue; }
                    boolean nearWater = false;
                    int range = (int) Math.ceil(tileDistance);
                    for (int dx = -range; dx <= range; dx++) {
                        for (int dz = -range; dz <= range; dz++) {
                            int nx = x + dx, nz = z + dz;
                            if (nx >= 0 && nx < SIZE_X && nz >= 0 && nz < SIZE_Z) {
                                if (heights[nx][nz] < waterLevel && (dx * dx + dz * dz) <= distSqThreshold) { nearWater = true; break; }
                            }
                        }
                        if (nearWater) break;
                    }
                    if (nearWater) map[x][z] = Terrain.TerrainType.ROUGH;
                }
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
                boolean isGreen = map[x][z] == Terrain.TerrainType.GREEN;
                boolean isIslandRough = !skipRough && data.getArchetype() == LevelData.Archetype.ISLAND_COAST && map[x][z] == Terrain.TerrainType.ROUGH;
                if ((isGreen || isIslandRough) && isBorderTile(x, z, map)) {
                    float avg = 0; int kIdx = 0;
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
                if (nx >= 0 && nx < map.length && nz >= 0 && nz < map[0].length && map[nx][nz] != map[x][z]) return true;
            }
        }
        return false;
    }

    private float getFinalRaw(float dG, float pR, float rH) {
        float t = MathUtils.clamp(dG / pR, 0f, 1f);
        float smoothT = t * t * (3 - 2 * t);
        float plateauT = smoothT * smoothT;

        return MathUtils.lerp(data.getGreenHeight(), rH, plateauT);
    }

    private boolean[][] getPathMask(Terrain.TerrainType[][] map) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        boolean[][] mask = new boolean[SIZE_X][SIZE_Z];
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                Terrain.TerrainType t = map[x][z];
                mask[x][z] = (t == Terrain.TerrainType.FAIRWAY || t == Terrain.TerrainType.GREEN || t == Terrain.TerrainType.TEE);
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

    private void mapStandardFeatures(Terrain.TerrainType[][] map, float[][] heights, int gX, int gZ, float water, float fWidth, boolean isIsland) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        int teeCenterX = SIZE_X / 2, teeCenterZ = (int) (SIZE_Z * 0.05f);
        float minFWidth = data.getMinFairwayWidth();
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                map[x][z] = Terrain.TerrainType.ROUGH;
                if (Math.abs(x - teeCenterX) < 7 && Math.abs(z - teeCenterZ) < 6) map[x][z] = Terrain.TerrainType.TEE;
                else {
                    float dist = (float) Math.sqrt(Math.pow(x - gX, 2) + Math.pow(z - gZ, 2));
                    float dynamicRadius = 20f + (MathUtils.sin(MathUtils.atan2(z - gZ, x - gX) * 3 + off1) * 3.1f);
                    if (dist <= dynamicRadius) map[x][z] = Terrain.TerrainType.GREEN;
                }
            }
        }
        if (minFWidth <= 0) processor.generateSegmentedFairway(map, gX, gZ, fWidth);
        else processor.generateContinuousFairway(map, gX, gZ, fWidth, minFWidth, data.getFairwayWiggle(), isIsland, data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.SUNKEN_FAIRWAY);
    }

    private float calculateGreenUndulation(int x, int z) {
        float total = 0;
        for (int i = 0; i < 4; i++) {
            float coord = (x * MathUtils.cos(greenWaveAngles[i]) + z * MathUtils.sin(greenWaveAngles[i])) * 0.32f;
            total += MathUtils.sin(coord + greenWaveOffsets[i]);
        }
        return total / 4.0f;
    }

    private float calculateHeightNoise(int x, int z, float freq, float und, float maxH, LevelData.TerrainAlgorithm algo) {
        float wX = x * SCALE + off1, wZ = z * SCALE + off2;
        return switch (algo) {
            case MULTI_WAVE -> processor.generateMultiWaveNoise(wX, wZ, freq) * und * maxH * 2.5f;
            case TERRACED -> Math.signum(processor.generateMultiWaveNoise(wX, wZ, freq)) * (float) Math.pow(Math.abs(processor.generateMultiWaveNoise(wX, wZ, freq)), 0.4f) * und * maxH * 2.5f;
            case MOUNDS -> (float) Math.pow(Math.abs(processor.generateMultiWaveNoise(wX, wZ, freq)), 1.5f) * und * maxH * 4.0f;
            case RAISED_FAIRWAY, SUNKEN_FAIRWAY -> processor.generateMultiWaveNoise(wX, wZ, freq * 0.5f) * und * maxH * 1.5f;
            default -> (MathUtils.sin(wX * freq) * 2.0f + MathUtils.cos(wZ * freq * 0.6f) * 1.7f) * und * maxH;
        };
    }

    private void applySlopeBasedStone(Terrain.TerrainType[][] map, float[][] heights, float threshold) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        for (int x = 0; x < SIZE_X - 1; x++) {
            for (int z = 0; z < SIZE_Z - 1; z++) {
                float dx = heights[x + 1][z] - heights[x][z], dz = heights[x][z + 1] - heights[x][z];
                if (1.0f - new Vector3(-dx, 1.0f, -dz).nor().y > threshold && map[x][z] == Terrain.TerrainType.ROUGH) map[x][z] = Terrain.TerrainType.STONE;
            }
        }
    }
}