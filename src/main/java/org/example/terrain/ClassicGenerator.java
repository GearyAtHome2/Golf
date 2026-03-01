package org.example.terrain;

import com.badlogic.gdx.Gdx;
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

    // Multi-wave parameters stored per seed
    private final float[] waveAngles = new float[10];
    private final float[] waveFreqs = new float[10];
    private final float[] waveAmps = new float[10];
    private final float[] waveOffsets = new float[10];

    public ClassicGenerator(LevelData data) {
        this.data = data;
        this.rng = new Random(data.getSeed());
        this.off1 = rng.nextFloat() * 1000f;
        this.off2 = rng.nextFloat() * 1000f;

        // Pre-randomize the 10 waves for the Multi-Wave algorithm
        for (int i = 0; i < 10; i++) {
            waveAngles[i] = rng.nextFloat() * MathUtils.PI * 2;
            waveFreqs[i] = 1.0f + (rng.nextFloat() * 1.5f);
            waveAmps[i] = 1.0f / (i + 1.5f);
            waveOffsets[i] = rng.nextFloat() * 100f;
        }
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

        Gdx.app.log("GEN_DEBUG", "Starting Gen. Archetype: " + data.getArchetype() + " | Algo: " + data.getTerrainAlgorithm());

        float slopeEaseExp = 2.5f;

        int greenCenterZ = (int)(SIZE_Z * 0.92f);
        float greenOffset = (rng.nextFloat() - 0.5f) * (SIZE_X * 0.5f);
        int greenCenterX = MathUtils.clamp((int)(SIZE_X / 2 + greenOffset), 20, SIZE_X - 20);

        float teeFlatBufferZ = 0.12f;

        boolean isCliffMap = data.getArchetype() == LevelData.Archetype.CLIFFSIDE_BLUFF;
        boolean isIslandMap = data.getArchetype() == LevelData.Archetype.ISLAND_COAST;
        float cliffSteepness = isCliffMap ? 100.0f : 15.0f;

        float greenRadiusX = 10.0f + rng.nextFloat() * 5.0f;
        float greenRadiusZ = 14.0f + rng.nextFloat() * 6.0f;

        // This radius defines the "cliff edge" of the protection.
        float protectionRadius = SIZE_Z * 0.22f;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                float zNorm = z / (float) (SIZE_Z - 1);
                float finalHeight;
                map[x][z] = Terrain.TerrainType.ROUGH;

                if (zNorm <= teeFlatBufferZ) {
                    finalHeight = data.getTeeHeight();
                } else {
                    float climbNorm = (zNorm - teeFlatBufferZ) / (1.0f - teeFlatBufferZ);
                    float baseElevation;

                    if (isCliffMap) {
                        float sigmoid = 1f / (1f + (float)Math.exp(-cliffSteepness * (climbNorm - 0.15f)));
                        baseElevation = MathUtils.lerp(data.getTeeHeight(), data.getGreenHeight(), sigmoid);
                    } else {
                        float curveStep = 1.0f - (float)Math.pow(1.0f - climbNorm, slopeEaseExp);
                        baseElevation = MathUtils.lerp(data.getTeeHeight(), data.getGreenHeight(), curveStep);
                    }

                    float noise = calculateHeightNoise(x, z, hillFreq, undulation, maxHeight, data.getTerrainAlgorithm());
                    float hillRamp = MathUtils.clamp(climbNorm / 0.15f, 0f, 1f);
                    hillRamp = hillRamp * hillRamp * (3 - 2 * hillRamp);

                    float rawHeight = baseElevation + (noise * hillRamp);

                    if (isIslandMap) {
                        // Exponential dip that gets very deep
                        float dipStrength = (float) Math.pow(zNorm, 2.5f) * (data.getTeeHeight() - waterLevel + 20.0f);
                        rawHeight -= dipStrength;
                    }

                    // --- ULTRA-STRONG GREEN PROTECTION ---
                    float dxGreen = x - greenCenterX;
                    float dzGreen = z - greenCenterZ;
                    float distGreen = (float) Math.sqrt(dxGreen * dxGreen + dzGreen * dzGreen);

                    // Normalize distance 0.0 to 1.0
                    float finalRaw = getFinalRaw(distGreen, protectionRadius, rawHeight);

                    float teeTransition = MathUtils.clamp(climbNorm / 0.15f, 0f, 1f);
                    finalHeight = MathUtils.lerp(data.getTeeHeight(), finalRaw, teeTransition * teeTransition * (3 - 2 * teeTransition));
                }
                heights[x][z] = finalHeight;
            }
        }

        // 2. TEE BOX MAPPING
        int teeWidth = 14, teeLength = 12;
        int teeCenterX = SIZE_X / 2;
        int teeCenterZ = (int)(SIZE_Z * 0.05f);
        int teeStartX = teeCenterX - teeWidth / 2;
        int teeStartZ = teeCenterZ - teeLength / 2;

        for (int x = teeStartX; x < teeStartX + teeWidth; x++) {
            for (int z = teeStartZ; z < teeStartZ + teeLength; z++) {
                if (x >= 0 && x < SIZE_X && z >= 0 && z < SIZE_Z) {
                    map[x][z] = Terrain.TerrainType.TEE;
                    heights[x][z] = data.getTeeHeight();
                }
            }
        }

        // 3. FAIRWAY & GREEN PASS
        int adjacencyCheckRadius = 2;
        for (int z = 0; z < SIZE_Z; z++) {
            float zNorm = z / (float) (SIZE_Z - 1);
            float centerlineX = MathUtils.lerp(SIZE_X / 2f, (float)greenCenterX, zNorm);
            centerlineX += MathUtils.sin(z * 0.02f + off1) * (SIZE_X * 0.1f);
            float widthNoise = (MathUtils.sin(z * 0.03f + off2) + 1f) * 0.5f;
            float currentWidth = maxFairwayWidth * (0.8f + widthNoise * 0.4f);

            for (int x = 0; x < SIZE_X; x++) {
                float dxGreen = (x - greenCenterX) * SCALE;
                float dzGreen = (z - greenCenterZ) * SCALE;
                boolean isGreenShape = (dxGreen*dxGreen)/(greenRadiusX*greenRadiusX) + (dzGreen*dzGreen)/(greenRadiusZ*greenRadiusZ) <= 1.0f;
                float distToCenter = Math.abs(x - centerlineX) * SCALE;
                boolean isFairwayShape = distToCenter < currentWidth / 2f;
                float height = heights[x][z];

                if (isGreenShape && height > waterLevel) {
                    map[x][z] = Terrain.TerrainType.GREEN;
                }
                else if (isFairwayShape && map[x][z] == Terrain.TerrainType.ROUGH) {
                    if (height <= waterLevel) continue;
                    boolean nearWater = false;
                    for (int nx = -adjacencyCheckRadius; nx <= adjacencyCheckRadius; nx++) {
                        for (int nz = -adjacencyCheckRadius; nz <= adjacencyCheckRadius; nz++) {
                            int cx = x + nx; int cz = z + nz;
                            if (cx >= 0 && cx < SIZE_X && cz >= 0 && cz < SIZE_Z) {
                                if (heights[cx][cz] < waterLevel) { nearWater = true; break; }
                            }
                        }
                        if (nearWater) break;
                    }
                    if (!nearWater) map[x][z] = Terrain.TerrainType.FAIRWAY;
                }
            }
        }

        // --- POSITIONING ---
        float ballWorldX = (teeCenterX * SCALE) - (SIZE_X * SCALE / 2f);
        float ballWorldZ = (teeCenterZ * SCALE) - (SIZE_Z * SCALE / 2f);
        teePos.set(ballWorldX, data.getTeeHeight() + 0.2f, ballWorldZ);

        float holeWorldX = (greenCenterX * SCALE) - (SIZE_X * SCALE / 2f);
        float holeWorldZ = (greenCenterZ * SCALE) - (SIZE_Z * SCALE / 2f);
        holePos.set(holeWorldX, heights[greenCenterX][greenCenterZ], holeWorldZ);

        // 4. TREE GENERATION
        float treeDensity = data.getTreeDensity();
        int treeCount = (int) (SIZE_Z * treeDensity);
        for (int i = 0; i < treeCount; i++) {
            int tx = rng.nextInt(SIZE_X - 1);
            int tz = rng.nextInt(SIZE_Z - 1);
            float worldY = heights[tx][tz];
            if (worldY < waterLevel + 0.5f) continue;
            float dx = heights[tx + 1][tz] - worldY;
            float dz = heights[tx][tz + 1] - worldY;
            float slopeMagnitude = (float) Math.sqrt(dx * dx + dz * dz);
            if (slopeMagnitude > 1.2f || map[tx][tz] != Terrain.TerrainType.ROUGH) continue;

            trees.add(new Terrain.Tree((tx * SCALE) - (SIZE_X * SCALE / 2f), worldY, (tz * SCALE) - (SIZE_Z * SCALE / 2f),
                    data.getTreeHeight(), data.getTrunkRadius(), data.getFoliageRadius()));
        }
    }

    private float getFinalRaw(float distGreen, float protectionRadius, float rawHeight) {
        float t = MathUtils.clamp(distGreen / protectionRadius, 0f, 1f);

        // High-exponent falloff: stays near 1.0 for a long time, then crashes to 0.0.
        // This ensures 100% at hole and ~99% at the edge of the green.
        float protectionMask = 1.0f - (float)Math.pow(t, 3.0f);

        // Blending the "ocean floor" height with our target Green height
        float finalRaw = MathUtils.lerp(rawHeight, data.getGreenHeight(), protectionMask);
        return finalRaw;
    }

    private float calculateHeightNoise(int x, int z, float freq, float undulation, float maxHeight, LevelData.TerrainAlgorithm algorithm) {
        float worldX = x * SCALE + off1;
        float worldZ = z * SCALE + off2;

        return switch (algorithm) {
            case MULTI_WAVE -> generateMultiWaveNoise(worldX, worldZ, freq) * undulation * maxHeight * 2.5f;
            case TERRACED -> {
                float base = generateMultiWaveNoise(worldX, worldZ, freq);
                yield Math.signum(base) * (float)Math.pow(Math.abs(base), 0.4f) * undulation * maxHeight * 2.5f;
            }
            case MOUNDS -> (float)Math.pow(Math.abs(generateMultiWaveNoise(worldX, worldZ, freq)), 1.5f) * undulation * maxHeight * 4.0f;
            default -> (MathUtils.sin(worldX * freq) * 2.0f + MathUtils.cos(worldZ * freq * 0.6f) * 1.7f) * undulation * maxHeight;
        };
    }

    private float generateMultiWaveNoise(float x, float z, float baseFreq) {
        float total = 0;
        float totalAmp = 0;
        for (int i = 0; i < 10; i++) {
            float angle = waveAngles[i];
            float cosA = MathUtils.cos(angle);
            float sinA = MathUtils.sin(angle);
            float rotatedCoord = (x * cosA + z * sinA) * (baseFreq * waveFreqs[i]);
            total += MathUtils.sin(rotatedCoord + waveOffsets[i]) * waveAmps[i];
            totalAmp += waveAmps[i];
        }
        return total / totalAmp;
    }
}