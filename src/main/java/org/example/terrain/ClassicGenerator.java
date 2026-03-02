package org.example.terrain;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.LevelData;
import java.util.List;
import java.util.Random;

public class ClassicGenerator implements ITerrainGenerator {

    private final float SCALE = 1.0f;
    private final LevelData data;
    private final Random rng;
    private float off1, off2;

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
        float maxFairwayWidth = data.getFairwayWidth();

        boolean isCliffMap = data.getArchetype() == LevelData.Archetype.CLIFFSIDE_BLUFF;
        boolean isIslandMap = data.getArchetype() == LevelData.Archetype.ISLAND_COAST;
        boolean isBunkerIslands = data.getArchetype() == LevelData.Archetype.BUNKER_ISLANDS;
        boolean isRaisedFairway = data.getTerrainAlgorithm() == LevelData.TerrainAlgorithm.RAISED_FAIRWAY;

        float cliffSteepness = isCliffMap ? 100.0f : 15.0f;
        float teeFlatBufferZ = 0.12f;
        int greenCenterZ = (int)(SIZE_Z * 0.92f);
        float greenOffset = (rng.nextFloat() - 0.5f) * (SIZE_X * 0.5f);
        int greenCenterX = MathUtils.clamp((int)(SIZE_X / 2 + greenOffset), 20, SIZE_X - 20);

        // 1. HEIGHT PASS
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                float zNorm = z / (float) (SIZE_Z - 1);
                map[x][z] = Terrain.TerrainType.ROUGH;

                if (zNorm <= teeFlatBufferZ) {
                    heights[x][z] = data.getTeeHeight();
                } else {
                    float climbNorm = (zNorm - teeFlatBufferZ) / (1.0f - teeFlatBufferZ);
                    float baseElevation;

                    if (isCliffMap) {
                        float sigmoid = 1f / (1f + (float)Math.exp(-cliffSteepness * (climbNorm - 0.15f)));
                        baseElevation = MathUtils.lerp(data.getTeeHeight(), data.getGreenHeight(), sigmoid);
                    } else {
                        float curveStep = 1.0f - (float)Math.pow(1.0f - climbNorm, 2.5f);
                        baseElevation = MathUtils.lerp(data.getTeeHeight(), data.getGreenHeight(), curveStep);
                    }

                    float noise = calculateHeightNoise(x, z, hillFreq, undulation, maxHeight, data.getTerrainAlgorithm());
                    float hillRamp = MathUtils.clamp(climbNorm / 0.15f, 0f, 1f);
                    hillRamp = hillRamp * hillRamp * (3 - 2 * hillRamp);

                    float rawHeight = baseElevation + (noise * hillRamp);
                    if (isIslandMap) rawHeight -= (float) Math.pow(zNorm, 2.5f) * (data.getTeeHeight() - waterLevel + 20.0f);

                    float dxGreen = x - greenCenterX;
                    float dzGreen = z - greenCenterZ;
                    float distGreen = (float) Math.sqrt(dxGreen * dxGreen + dzGreen * dzGreen);
                    float protectionRadius = SIZE_Z * 0.22f;

                    float protectedHeight = getFinalRaw(distGreen, protectionRadius, rawHeight);
                    float greenEffectMask = 1.0f - MathUtils.clamp(distGreen / (protectionRadius * 0.7f), 0f, 1f);
                    protectedHeight += (calculateGreenUndulation(x, z) * greenEffectMask * greenEffectMask * (3 - 2 * greenEffectMask));

                    float teeTransition = MathUtils.clamp(climbNorm / 0.15f, 0f, 1f);
                    heights[x][z] = MathUtils.lerp(data.getTeeHeight(), protectedHeight, teeTransition * teeTransition * (3 - 2 * teeTransition));
                }
            }
        }

        // 2. TEE & BASIC MAPPING
        mapStandardFeatures(map, heights, greenCenterX, greenCenterZ, waterLevel, maxFairwayWidth);

        // 3. RAISED FAIRWAY LOGIC (Updated for long curved slopes)
        if (isRaisedFairway) {
            waterLevel = applyRaisedFairwayLogic(map, heights, waterLevel);
        }

        if (isBunkerIslands) {
            generateBunkerIslands(map, heights, greenCenterX, greenCenterZ);
        }

        finalizePositionsAndTrees(map, heights, teePos, holePos, trees, greenCenterX, greenCenterZ, waterLevel, isCliffMap);
    }

    private float applyRaisedFairwayLogic(Terrain.TerrainType[][] map, float[][] heights, float currentWater) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        float DROP_AMOUNT = 25.0f;
        float SLOPE_DISTANCE = 8.0f; // Increase this for longer slopes (e.g., 12.0f or 15.0f)

        // 1. Identify "High Ground" tiles
        boolean[][] isHighGround = new boolean[SIZE_X][SIZE_Z];
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                Terrain.TerrainType t = map[x][z];
                if (t == Terrain.TerrainType.FAIRWAY || t == Terrain.TerrainType.GREEN || t == Terrain.TerrainType.TEE) {
                    isHighGround[x][z] = true;
                }
            }
        }

        // 2. Calculate distance to nearest high ground and apply curved drop
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (isHighGround[x][z]) continue;

                float minDistSq = SLOPE_DISTANCE * SLOPE_DISTANCE;
                // Optimization: only check a local neighborhood
                int range = (int)SLOPE_DISTANCE + 1;
                for (int ix = x - range; ix <= x + range; ix++) {
                    for (int iz = z - range; iz <= z + range; iz++) {
                        if (ix >= 0 && ix < SIZE_X && iz >= 0 && iz < SIZE_Z && isHighGround[ix][iz]) {
                            float dSq = (x - ix) * (x - ix) + (z - iz) * (z - iz);
                            if (dSq < minDistSq) minDistSq = dSq;
                        }
                    }
                }

                float dist = (float)Math.sqrt(minDistSq);
                float t = MathUtils.clamp(dist / SLOPE_DISTANCE, 0, 1);

                // Use smoothstep or pow for a nice curved "shoulder" on the fairway
                float curvedT = t * t * (3 - 2 * t);
                heights[x][z] -= (DROP_AMOUNT * curvedT);
            }
        }

        float newWater = currentWater - DROP_AMOUNT - 2.0f;
        data.setWaterLevel(newWater);
        return newWater;
    }

    private void generateBunkerIslands(Terrain.TerrainType[][] map, float[][] heights, int gX, int gZ) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        int attempts = data.getnBunkers();
        int placed = 0;
        int maxIslands = data.getnBunkers() / 3;

        for (int i = 0; i < attempts && placed < maxIslands; i++) {
            float zP = 0.2f + rng.nextFloat() * 0.65f;
            int cz = (int)(SIZE_Z * zP);
            float centerlineX = MathUtils.lerp(SIZE_X / 2f, (float)gX, zP);
            int cx = (int)(centerlineX + (rng.nextFloat() - 0.5f) * 60f);
            cx = MathUtils.clamp(cx, 20, SIZE_X - 20);

            float sX = heights[cx+1][cz] - heights[cx-1][cz];
            float sZ = heights[cx][cz+1] - heights[cx][cz-1];
            if (Math.sqrt(sX*sX + sZ*sZ) > 0.8f) continue;

            placed++;
            float baseH = heights[cx][cz];
            float islandRadiusOuter = 8.0f + rng.nextFloat() * 6.0f;
            float islandRadiusInner = islandRadiusOuter * 0.6f;
            float moatWidth = 4.0f + rng.nextFloat() * 2.0f;
            float flattenRadius = islandRadiusOuter + moatWidth + 5.0f;

            for (int x = cx - 25; x <= cx + 25; x++) {
                for (int z = cz - 25; z <= cz + 25; z++) {
                    if (x < 0 || x >= SIZE_X || z < 0 || z >= SIZE_Z) continue;
                    float dx = x - cx;
                    float dz = z - cz;
                    float angle = (float)Math.atan2(dz, dx);
                    float wobble = MathUtils.sin(angle * 3 + i) * 1.5f + MathUtils.cos(angle * 5 - i) * 1.0f;
                    float dist = (float)Math.sqrt(dx*dx + dz*dz);
                    float effectiveDist = dist + wobble;

                    if (dist < flattenRadius) {
                        float fT = 1.0f - MathUtils.clamp(dist / flattenRadius, 0, 1);
                        fT = Interpolation.pow2In.apply(fT);
                        heights[x][z] = MathUtils.lerp(heights[x][z], baseH, fT * 0.8f);
                    }

                    if (effectiveDist < islandRadiusOuter) {
                        map[x][z] = Terrain.TerrainType.FAIRWAY;
                        float lift = 2.2f;
                        if (effectiveDist > islandRadiusInner) {
                            float t = (effectiveDist - islandRadiusInner) / (islandRadiusOuter - islandRadiusInner);
                            lift *= Interpolation.pow2Out.apply(1.0f - t);
                        }
                        heights[x][z] += lift;
                    } else if (effectiveDist < islandRadiusOuter + moatWidth) {
                        map[x][z] = Terrain.TerrainType.BUNKER;
                        float dipT = 1.0f - Math.abs((islandRadiusOuter + moatWidth/2f) - effectiveDist) / (moatWidth/2f);
                        heights[x][z] -= MathUtils.sin(dipT * MathUtils.PI / 2f) * 1.5f;
                    }
                }
            }
        }
    }

    private void mapStandardFeatures(Terrain.TerrainType[][] map, float[][] heights, int gX, int gZ, float water, float fWidth) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        int teeCenterX = SIZE_X / 2;
        int teeCenterZ = (int)(SIZE_Z * 0.05f);
        for (int x = teeCenterX - 7; x < teeCenterX + 7; x++) {
            for (int z = teeCenterZ - 6; z < teeCenterZ + 6; z++) {
                if (x >= 0 && x < SIZE_X && z >= 0 && z < SIZE_Z) {
                    map[x][z] = Terrain.TerrainType.TEE;
                    heights[x][z] = data.getTeeHeight();
                }
            }
        }

        float greenRX = 12f, greenRZ = 18f;
        for (int z = 0; z < SIZE_Z; z++) {
            float zNorm = z / (float) (SIZE_Z - 1);
            float centerlineX = MathUtils.lerp(SIZE_X / 2f, (float)gX, zNorm) + MathUtils.sin(z * 0.02f + off1) * (SIZE_X * 0.1f);
            float currentWidth = fWidth * (0.8f + (MathUtils.sin(z * 0.03f + off2) + 1f) * 0.2f);

            for (int x = 0; x < SIZE_X; x++) {
                float dxG = (x - gX) * SCALE;
                float dzG = (z - gZ) * SCALE;
                if ((dxG*dxG)/(greenRX*greenRX) + (dzG*dzG)/(greenRZ*greenRZ) <= 1.0f && heights[x][z] > water) {
                    map[x][z] = Terrain.TerrainType.GREEN;
                } else if (Math.abs(x - centerlineX) < currentWidth / 2f && map[x][z] == Terrain.TerrainType.ROUGH && heights[x][z] > water) {
                    map[x][z] = Terrain.TerrainType.FAIRWAY;
                }
            }
        }
    }

    private void finalizePositionsAndTrees(Terrain.TerrainType[][] map, float[][] heights, Vector3 teeP, Vector3 holeP, List<Terrain.Tree> trees, int gX, int gZ, float water, boolean isCliff) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        teeP.set((SIZE_X / 2f * SCALE) - (SIZE_X * SCALE / 2f), data.getTeeHeight() + 0.2f, ((SIZE_Z * 0.05f) * SCALE) - (SIZE_Z * SCALE / 2f));
        holeP.set((gX * SCALE) - (SIZE_X * SCALE / 2f), heights[gX][gZ], (gZ * SCALE) - (SIZE_Z * SCALE / 2f));

        float cliffDelta = Math.abs(data.getTeeHeight() - data.getGreenHeight());
        int treeCount = (int) (SIZE_Z * data.getTreeDensity());

        for (int i = 0; i < treeCount; i++) {
            int tx = rng.nextInt(SIZE_X - 1);
            int tz = rng.nextInt(SIZE_Z - 1);
            float worldY = heights[tx][tz];
            if (worldY < water + 0.5f || map[tx][tz] != Terrain.TerrainType.ROUGH) continue;

            float slope = (float) Math.sqrt(Math.pow(heights[tx+1][tz]-worldY, 2) + Math.pow(heights[tx][tz+1]-worldY, 2));
            if (slope > 1.2f) continue;

            float tH = isCliff ? cliffDelta * (0.8f + rng.nextFloat() * 0.2f) : data.getTreeHeight();
            trees.add(new Terrain.Tree((tx * SCALE) - (SIZE_X * SCALE / 2f), worldY, (tz * SCALE) - (SIZE_Z * SCALE / 2f), tH, data.getTrunkRadius(), data.getFoliageRadius()));
        }
    }

    private float getFinalRaw(float dG, float pR, float rH) {
        float t = MathUtils.clamp(dG / pR, 0f, 1f);
        return MathUtils.lerp(rH, data.getGreenHeight(), 1.0f - (float)Math.pow(t, 3.0f));
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
            case MULTI_WAVE -> generateMultiWaveNoise(wX, wZ, freq) * und * maxH * 2.5f;
            case TERRACED -> Math.signum(generateMultiWaveNoise(wX, wZ, freq)) * (float)Math.pow(Math.abs(generateMultiWaveNoise(wX, wZ, freq)), 0.4f) * und * maxH * 2.5f;
            case MOUNDS -> (float)Math.pow(Math.abs(generateMultiWaveNoise(wX, wZ, freq)), 1.5f) * und * maxH * 4.0f;
            case RAISED_FAIRWAY -> generateMultiWaveNoise(wX, wZ, freq * 0.5f) * und * maxH * 1.5f;
            default -> (MathUtils.sin(wX * freq) * 2.0f + MathUtils.cos(wZ * freq * 0.6f) * 1.7f) * und * maxH;
        };
    }

    private float generateMultiWaveNoise(float x, float z, float bF) {
        float total = 0, totalA = 0;
        for (int i = 0; i < 10; i++) {
            float coord = (x * MathUtils.cos(waveAngles[i]) + z * MathUtils.sin(waveAngles[i])) * (bF * waveFreqs[i]);
            total += MathUtils.sin(coord + waveOffsets[i]) * waveAmps[i];
            totalA += waveAmps[i];
        }
        return total / totalA;
    }
}