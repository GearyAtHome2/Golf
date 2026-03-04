package org.example.terrain;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.LevelData;
import java.util.List;
import java.util.Random;

public class ClassicGenerator implements ITerrainGenerator {

    private final float SCALE = 1.0f;
    private final LevelData data;
    private final Random rng;
    private final FeatureProcessor processor;

    private final float off1, off2;
    private final float[] waveAngles = new float[10];
    private final float[] waveFreqs = new float[10];
    private final float[] waveAmps = new float[10];
    private final float[] waveOffsets = new float[10];

    private final float[] greenWaveAngles = new float[4];
    private final float[] greenWaveOffsets = new float[4];

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
    public void generate(Terrain.TerrainType[][] map, float[][] heights, List<Terrain.Tree> trees, Vector3 teePos, Vector3 holePos) {
        float COAST_Z_THRESHOLD = 0.45f;
        float COAST_CURVINESS = 0.04f;
        float COAST_JAGGEDNESS = 0.01f;
        float COAST_DROP_DEPTH = 20.0f;

        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        float hillFreq = data.getHillFrequency();
        float maxHeight = data.getMaxHeight();
        float undulation = data.getUndulation();
        float waterLevel = data.getWaterLevel();
        float maxFairwayWidth = data.getMaxFairwayWidth();

        boolean isCliffMap = data.getArchetype() == LevelData.Archetype.CLIFFSIDE_BLUFF;
        boolean isIslandMap = data.getArchetype() == LevelData.Archetype.ISLAND_COAST;
        boolean isBunkerIslands = data.getArchetype() == LevelData.Archetype.BUNKER_ISLANDS;
        boolean isRaisedFairway = data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.RAISED_FAIRWAY;
        boolean isSunkenFairway = data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.SUNKEN_FAIRWAY;

        float cliffSteepness = isCliffMap ? 100.0f : 15.0f;
        float teeFlatBufferZ = 0.12f;
        float teeSafetyElevation = data.getTeeHeight() + 0.2f;
        int greenCenterZ = (int) (SIZE_Z * 0.92f);
        float greenOffset = (rng.nextFloat() - 0.5f) * (SIZE_X * 0.5f);
        int greenCenterX = MathUtils.clamp((int) (SIZE_X / 2 + greenOffset), 20, SIZE_X - 20);

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                map[x][z] = Terrain.TerrainType.ROUGH;
                float zNorm = z / (float) (SIZE_Z - 1);

                if (zNorm <= teeFlatBufferZ) {
                    heights[x][z] = teeSafetyElevation;
                } else {
                    float climbNorm = (zNorm - teeFlatBufferZ) / (1.0f - teeFlatBufferZ);
                    float baseElevation;

                    if (isCliffMap) {
                        float sigmoid = 1f / (1f + (float) Math.exp(-cliffSteepness * (climbNorm - 0.15f)));
                        baseElevation = MathUtils.lerp(teeSafetyElevation, data.getGreenHeight(), sigmoid);
                    } else {
                        float curveStep = 1.0f - (float) Math.pow(1.0f - climbNorm, 2.5f);
                        baseElevation = MathUtils.lerp(teeSafetyElevation, data.getGreenHeight(), curveStep);
                    }

                    float noise = calculateHeightNoise(x, z, hillFreq, undulation, maxHeight, data.getTerrainAlgorithm());
                    float hillRamp = MathUtils.clamp((zNorm - teeFlatBufferZ) / 0.1f, 0f, 1f);
                    hillRamp = hillRamp * hillRamp * (3 - 2 * hillRamp);

                    heights[x][z] = baseElevation + (noise * hillRamp);
                }
            }
        }

        mapStandardFeatures(map, heights, greenCenterX, greenCenterZ, waterLevel, maxFairwayWidth, isIslandMap);

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                float zNorm = z / (float) (SIZE_Z - 1);
                float currentHeight = heights[x][z];

                if (isIslandMap) {
                    float noiseCurvy = MathUtils.sin(x * 0.05f + off1) * COAST_CURVINESS;
                    float noiseJagged = MathUtils.cos(x * 0.4f + off2) * COAST_JAGGEDNESS;
                    float coastThreshold = COAST_Z_THRESHOLD + noiseCurvy + noiseJagged;

                    boolean isPlayable = (map[x][z] == Terrain.TerrainType.FAIRWAY || map[x][z] == Terrain.TerrainType.TEE || map[x][z] == Terrain.TerrainType.GREEN);

                    if (zNorm > coastThreshold && !isPlayable) {
                        if (map[x][z] == Terrain.TerrainType.ROUGH) {
                            currentHeight -= COAST_DROP_DEPTH;
                        } else if (map[x][z] == Terrain.TerrainType.BUNKER) {
                            float beachT = MathUtils.clamp((currentHeight - waterLevel) / 5.0f, 0f, 1f);
                            currentHeight = MathUtils.lerp(waterLevel - 1.5f, currentHeight, beachT);
                        }
                    }

                    if (isPlayable && currentHeight < waterLevel + 1.0f) {
                        currentHeight = waterLevel + 1.0f;
                    }
                }

                float dxGreen = x - greenCenterX;
                float dzGreen = z - greenCenterZ;
                float distGreen = (float) Math.sqrt(dxGreen * dxGreen + dzGreen * dzGreen);
                float protectionRadius = SIZE_Z * 0.22f;

                float protectedHeight = getFinalRaw(distGreen, protectionRadius, currentHeight);
                float greenEffectMask = 1.0f - MathUtils.clamp(distGreen / (protectionRadius * 0.7f), 0f, 1f);
                protectedHeight += (calculateGreenUndulation(x, z) * greenEffectMask * greenEffectMask * (3 - 2 * greenEffectMask));

                float teeT = MathUtils.clamp(zNorm / 0.15f, 0f, 1f);
                float smoothT = teeT * teeT * (3 - 2 * teeT);
                heights[x][z] = MathUtils.lerp(teeSafetyElevation, protectedHeight, smoothT);
            }
        }

        smoothGreenBorders(map, heights);

        float offsetAmount = 25.0f;
        if (isRaisedFairway) {
            applyPathOffset(map, heights, offsetAmount);
        } else if (isSunkenFairway) {
            applyPathOffset(map, heights, -offsetAmount);
            float minPathHeight = Math.min(teeSafetyElevation, data.getGreenHeight()) - offsetAmount;
            float newWater = minPathHeight - 10.0f;
            data.setWaterLevel(newWater);
            waterLevel = newWater;
        }

        applyFairwayWaterBuffer(map, heights, waterLevel, 3.0f);

        finalizePositionsAndTrees(map, heights, teePos, holePos, trees, greenCenterX, greenCenterZ, waterLevel, isCliffMap);
    }

    private void applyFairwayWaterBuffer(Terrain.TerrainType[][] map, float[][] heights, float waterLevel, float tileDistance) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        // Use a distance squared check for better performance than Math.sqrt
        float distSqThreshold = tileDistance * tileDistance;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                // We only care about cleaning up the Fairway
                if (map[x][z] == Terrain.TerrainType.FAIRWAY) {

                    // 1. Immediate height check: If it's literally underwater or too close to it
                    if (heights[x][z] <= waterLevel + 0.5f) {
                        map[x][z] = Terrain.TerrainType.ROUGH;
                        continue;
                    }

                    // 2. Proximity check: Look for nearby water in the horizontal plane
                    boolean nearWater = false;
                    int range = (int) Math.ceil(tileDistance);

                    for (int dx = -range; dx <= range; dx++) {
                        for (int dz = -range; dz <= range; dz++) {
                            int nx = x + dx;
                            int nz = z + dz;

                            if (nx >= 0 && nx < SIZE_X && nz >= 0 && nz < SIZE_Z) {
                                // If any neighbor is below the water line
                                if (heights[nx][nz] < waterLevel) {
                                    float currentDistSq = dx * dx + dz * dz;
                                    if (currentDistSq <= distSqThreshold) {
                                        nearWater = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (nearWater) break;
                    }

                    if (nearWater) {
                        map[x][z] = Terrain.TerrainType.ROUGH;
                    }
                }
            }
        }
    }

    private void smoothGreenBorders(Terrain.TerrainType[][] map, float[][] heights) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        float[][] buffer = new float[SIZE_X][SIZE_Z];
        float[] kernel = {1/16f, 2/16f, 1/16f, 2/16f, 4/16f, 2/16f, 1/16f, 2/16f, 1/16f};

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                buffer[x][z] = heights[x][z];
                if ((map[x][z] == Terrain.TerrainType.GREEN || (data.getArchetype() == LevelData.Archetype.ISLAND_COAST && map[x][z] == Terrain.TerrainType.ROUGH)) && isBorderTile(x, z, map)) {
                    float avg = 0;
                    int kIdx = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int nx = MathUtils.clamp(x + dx, 0, SIZE_X - 1);
                            int nz = MathUtils.clamp(z + dz, 0, SIZE_Z - 1);
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
                if (nx >= 0 && nx < map.length && nz >= 0 && nz < map[0].length) {
                    if (map[nx][nz] != map[x][z]) return true;
                }
            }
        }
        return false;
    }

    private float getFinalRaw(float dG, float pR, float rH) {
        float t = MathUtils.clamp(dG / pR, 0f, 1f);
        return MathUtils.lerp(rH, data.getGreenHeight(), 1.0f - (float) Math.pow(t, 3.0f));
    }

    private void applyPathOffset(Terrain.TerrainType[][] map, float[][] heights, float amount) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        float SLOPE_DISTANCE = 8.0f;
        boolean[][] isPath = getPathMask(map);

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (isPath[x][z]) {
                    heights[x][z] += amount;
                } else {
                    float dist = getDistanceToPath(x, z, isPath, SLOPE_DISTANCE);
                    float t = MathUtils.clamp(1.0f - (dist / SLOPE_DISTANCE), 0, 1);
                    float curvedT = t * t * (3 - 2 * t);
                    heights[x][z] += (amount * curvedT);
                }
            }
        }
    }

    private boolean[][] getPathMask(Terrain.TerrainType[][] map) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
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
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        int teeCenterX = SIZE_X / 2;
        int teeCenterZ = (int) (SIZE_Z * 0.05f);
        float minFWidth = data.getMinFairwayWidth();

        // 1. Map Tee and Green (Keep this logic here as it's "Standard")
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                if (Math.abs(x - teeCenterX) < 7 && Math.abs(z - teeCenterZ) < 6) {
                    map[x][z] = Terrain.TerrainType.TEE;
                } else {
                    float dxG = (x - gX);
                    float dzG = (z - gZ);
                    float distToGreenCenter = (float) Math.sqrt(dxG * dxG + dzG * dzG);
                    float angle = MathUtils.atan2(dzG, dxG);
                    float dynamicRadius = 20f + (MathUtils.sin(angle * 3 + off1) * 3.1f + MathUtils.cos(angle * 5 + off2) * 1.5f);
                    if (distToGreenCenter <= dynamicRadius) {
                        map[x][z] = Terrain.TerrainType.GREEN;
                    }
                }
            }
        }

        if (minFWidth <= 0) {
            processor.generateSegmentedFairway(map, gX, gZ, fWidth);
        } else {
            processor.generateContinuousFairway(
                    map,
                    gX,
                    gZ,
                    fWidth,
                    minFWidth,
                    data.getFairwayWiggle(),
                    isIsland
            );
        }
    }

    private void finalizePositionsAndTrees(Terrain.TerrainType[][] map, float[][] heights, Vector3 teeP, Vector3 holeP, List<Terrain.Tree> trees, int gX, int gZ, float water, boolean isCliff) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        teeP.set((SIZE_X / 2f * SCALE) - (SIZE_X * SCALE / 2f), heights[SIZE_X / 2][(int)(SIZE_Z * 0.05f)] + 0.2f, ((SIZE_Z * 0.05f) * SCALE) - (SIZE_Z * SCALE / 2f));
        float randomAngle = rng.nextFloat() * MathUtils.PI * 2;
        float randomDist = rng.nextFloat() * 8f;
        int flagX = MathUtils.clamp(gX + (int)(MathUtils.cos(randomAngle) * randomDist), 0, SIZE_X - 1);
        int flagZ = MathUtils.clamp(gZ + (int)(MathUtils.sin(randomAngle) * randomDist), 0, SIZE_Z - 1);
        holeP.set((flagX * SCALE) - (SIZE_X * SCALE / 2f), heights[flagX][flagZ], (flagZ * SCALE) - (SIZE_Z * SCALE / 2f));
        float cliffDelta = Math.abs(data.getTeeHeight() - data.getGreenHeight());
        int treeCount = (int) (SIZE_Z * data.getTreeDensity());
        for (int i = 0; i < treeCount; i++) {
            int tx = rng.nextInt(SIZE_X - 1), tz = rng.nextInt(SIZE_Z - 1);
            float worldY = heights[tx][tz];
            if (worldY < water + 0.5f || map[tx][tz] != Terrain.TerrainType.ROUGH) continue;
            float slope = (float) Math.sqrt(Math.pow(heights[tx + 1][tz] - worldY, 2) + Math.pow(heights[tx][tz + 1] - worldY, 2));
            if (slope > 1.2f) continue;
            float tH = isCliff ? cliffDelta * (0.8f + rng.nextFloat() * 0.2f) : data.getTreeHeight();
            trees.add(new Terrain.Tree((tx * SCALE) - (SIZE_X * SCALE / 2f), worldY, (tz * SCALE) - (SIZE_Z * SCALE / 2f), tH, data.getTrunkRadius(), data.getFoliageRadius()));
        }
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
        float wX = x * SCALE + off1;
        float wZ = z * SCALE + off2;
        return switch (algo) {
            case MULTI_WAVE -> processor.generateMultiWaveNoise(wX, wZ, freq) * und * maxH * 2.5f;
            case TERRACED -> Math.signum(processor.generateMultiWaveNoise(wX, wZ, freq)) * (float) Math.pow(Math.abs(processor.generateMultiWaveNoise(wX, wZ, freq)), 0.4f) * und * maxH * 2.5f;
            case MOUNDS -> (float) Math.pow(Math.abs(processor.generateMultiWaveNoise(wX, wZ, freq)), 1.5f) * und * maxH * 4.0f;
            case RAISED_FAIRWAY, SUNKEN_FAIRWAY -> processor.generateMultiWaveNoise(wX, wZ, freq * 0.5f) * und * maxH * 1.5f;
            default -> (MathUtils.sin(wX * freq) * 2.0f + MathUtils.cos(wZ * freq * 0.6f) * 1.7f) * und * maxH;
        };
    }
}