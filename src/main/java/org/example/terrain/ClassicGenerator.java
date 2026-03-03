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
        int greenCenterZ = (int) (SIZE_Z * 0.92f);
        float greenOffset = (rng.nextFloat() - 0.5f) * (SIZE_X * 0.5f);
        int greenCenterX = MathUtils.clamp((int) (SIZE_X / 2 + greenOffset), 20, SIZE_X - 20);

        // 1. HEIGHT & MAPPING PASS
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                float zNorm = z / (float) (SIZE_Z - 1);
                map[x][z] = Terrain.TerrainType.ROUGH;

                if (zNorm <= teeFlatBufferZ) {
                    heights[x][z] = data.getTeeHeight();
                } else {
                    float climbNorm = (zNorm - teeFlatBufferZ) / (1.0f - teeFlatBufferZ);
                    float pathHeight;

                    if (isCliffMap) {
                        float sigmoid = 1f / (1f + (float) Math.exp(-cliffSteepness * (climbNorm - 0.15f)));
                        pathHeight = MathUtils.lerp(data.getTeeHeight(), data.getGreenHeight(), sigmoid);
                    } else {
                        float curveStep = 1.0f - (float) Math.pow(1.0f - climbNorm, 2.5f);
                        pathHeight = MathUtils.lerp(data.getTeeHeight(), data.getGreenHeight(), curveStep);
                    }

                    float noise = calculateHeightNoise(x, z, hillFreq, undulation, maxHeight, data.getTerrainAlgorithm());
                    float hillRamp = MathUtils.clamp(climbNorm / 0.15f, 0f, 1f);
                    hillRamp = hillRamp * hillRamp * (3 - 2 * hillRamp);

                    heights[x][z] = pathHeight + (noise * hillRamp);

                    if (isIslandMap) {
                        heights[x][z] -= (float) Math.pow(zNorm, 2.5f) * (data.getTeeHeight() - waterLevel + 20.0f);
                    }
                }
            }
        }

        // Apply features (Green/Fairway)
        mapStandardFeatures(map, heights, greenCenterX, greenCenterZ, waterLevel, maxFairwayWidth);

        // 2. OFFSET LOGIC
        float offsetAmount = 25.0f;
        if (isRaisedFairway) {
            applyPathOffset(map, heights, offsetAmount);
        } else if (isSunkenFairway) {
            applyPathOffset(map, heights, -offsetAmount);

            // Calculate a safe water level: find the lowest target height on the path
            // then subtract an additional buffer so the trench isn't flooded.
            float minPathHeight = Math.min(data.getTeeHeight(), data.getGreenHeight()) - offsetAmount;
            float newWater = minPathHeight - 10.0f;

            data.setWaterLevel(newWater);
            waterLevel = newWater;
        }

        // 3. ARCHETYPE SPECIFICS
        if (isBunkerIslands) {
            processor.generateBunkerIslands(map, heights, greenCenterX, greenCenterZ);
        }

        finalizePositionsAndTrees(map, heights, teePos, holePos, trees, greenCenterX, greenCenterZ, waterLevel, isCliffMap);
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

    private void mapStandardFeatures(Terrain.TerrainType[][] map, float[][] heights, int gX, int gZ, float water, float fWidth) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        int teeCenterX = SIZE_X / 2;
        int teeCenterZ = (int) (SIZE_Z * 0.05f);
        for (int x = teeCenterX - 7; x < teeCenterX + 7; x++) {
            for (int z = teeCenterZ - 6; z < teeCenterZ + 6; z++) {
                if (x >= 0 && x < SIZE_X && z >= 0 && z < SIZE_Z) {
                    map[x][z] = Terrain.TerrainType.TEE;
                }
            }
        }

        float greenRX = 12f, greenRZ = 18f;
        for (int z = 0; z < SIZE_Z; z++) {
            float zNorm = z / (float) (SIZE_Z - 1);
            float centerlineX = MathUtils.lerp(SIZE_X / 2f, (float) gX, zNorm) + MathUtils.sin(z * 0.02f + off1) * (SIZE_X * 0.1f);
            float currentWidth = fWidth * (0.8f + (MathUtils.sin(z * 0.03f + off2) + 1f) * 0.2f);

            for (int x = 0; x < SIZE_X; x++) {
                float dxG = (x - gX) * SCALE;
                float dzG = (z - gZ) * SCALE;
                if ((dxG * dxG) / (greenRX * greenRX) + (dzG * dzG) / (greenRZ * greenRZ) <= 1.0f) {
                    map[x][z] = Terrain.TerrainType.GREEN;
                    heights[x][z] += calculateGreenUndulation(x, z);
                } else if (Math.abs(x - centerlineX) < currentWidth / 2f && map[x][z] == Terrain.TerrainType.ROUGH) {
                    map[x][z] = Terrain.TerrainType.FAIRWAY;
                }
            }
        }
    }

    private void finalizePositionsAndTrees(Terrain.TerrainType[][] map, float[][] heights, Vector3 teeP, Vector3 holeP, List<Terrain.Tree> trees, int gX, int gZ, float water, boolean isCliff) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        teeP.set((SIZE_X / 2f * SCALE) - (SIZE_X * SCALE / 2f), heights[SIZE_X / 2][(int)(SIZE_Z * 0.05f)] + 0.2f, ((SIZE_Z * 0.05f) * SCALE) - (SIZE_Z * SCALE / 2f));
        holeP.set((gX * SCALE) - (SIZE_X * SCALE / 2f), heights[gX][gZ], (gZ * SCALE) - (SIZE_Z * SCALE / 2f));

        float cliffDelta = Math.abs(data.getTeeHeight() - data.getGreenHeight());
        int treeCount = (int) (SIZE_Z * data.getTreeDensity());

        for (int i = 0; i < treeCount; i++) {
            int tx = rng.nextInt(SIZE_X - 1);
            int tz = rng.nextInt(SIZE_Z - 1);
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