package org.example.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import org.example.terrain.Terrain;
import org.example.terrain.level.LevelData;

import java.util.Random;

public class StoneRunGenerator {
    private final LevelData data;
    private final Random rng;

    public StoneRunGenerator(LevelData data) {
        this.data = data;
        this.rng = new Random(data.getSeed() ^ 0x0A515000L);
    }

    public void generateStoneRun(Terrain.TerrainType[][] map, float[][] heights) {
        applyRollingSineGradients(heights);
        generateBaseMoat(heights);
        applyInitialStoneBase(map);
        applyRoughCurvaturePass(map, heights);
        placeIntegratedPads(map, heights);
        applyStrataSand(map, heights);
        applyStoneNoise(map, heights);
    }

    private void applyRollingSineGradients(float[][] heights) {
        int SIZE_X = heights.length;
        int SIZE_Z = heights[0].length;

        float mainFreq = 0.012f;
        float mainAmp = 6.0f;
        float detailFreq = 0.04f;
        float detailAmp = 1.5f;
        float warpFreq = 0.015f;
        float warpAmp = 20.0f;

        float seed1 = rng.nextFloat() * 10000f;
        float seed2 = rng.nextFloat() * 10000f;
        float seed3 = rng.nextFloat() * 10000f;

        float moatBufferPercent = 0.15f;
        float fadeZonePercent = 0.10f;
        int moatEndIndex = (int) (SIZE_Z * moatBufferPercent);
        int fadeEndIndex = (int) (SIZE_Z * (moatBufferPercent + fadeZonePercent));

        for (int x = 0; x < SIZE_X; x++) {
            float xWarp = MathUtils.sin((x + seed1) * warpFreq) * warpAmp;

            for (int z = 0; z < SIZE_Z; z++) {
                float ramp;
                if (z < moatEndIndex) {
                    ramp = 0f;
                } else if (z < fadeEndIndex) {
                    ramp = (float) (z - moatEndIndex) / (fadeEndIndex - moatEndIndex);
                    ramp = ramp * ramp * (3 - 2 * ramp);
                } else {
                    ramp = 1.0f;
                }

                float sampleZ = z + xWarp;
                float wave1 = MathUtils.sin((sampleZ + seed2) * mainFreq) * mainAmp;
                float wave2 = MathUtils.cos((sampleZ + x * 0.01f + seed3) * detailFreq) * detailAmp;

                heights[x][z] += (wave1 + wave2) * ramp;
            }
        }
    }

    private void applyStrataSand(Terrain.TerrainType[][] map, float[][] heights) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        float teeHeight = -1000f;
        float maxH = -1000f;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (map[x][z] == Terrain.TerrainType.TEE) {
                    if (heights[x][z] > teeHeight) teeHeight = heights[x][z];
                }
                if (heights[x][z] > maxH) maxH = heights[x][z];
            }
        }

        float effectiveMin = (teeHeight == -1000f) ? 0 : teeHeight + 1.0f;

        int strataCount = 2 + rng.nextInt(3);
        for (int i = 0; i < strataCount; i++) {
            float baseHeight = effectiveMin + rng.nextFloat() * (maxH - effectiveMin);
            float thickness = 0.4f + rng.nextFloat() * 0.8f;

            float driftFreq = 0.03f;
            float driftAmp = 1.5f;
            float driftSeed = rng.nextFloat() * 1000f;

            for (int x = 0; x < SIZE_X; x++) {
                float localBase = baseHeight + MathUtils.sin((x + driftSeed) * driftFreq) * driftAmp;
                for (int z = 0; z < SIZE_Z; z++) {
                    float h = heights[x][z];
                    if (h >= localBase && h <= localBase + thickness) {
                        if (map[x][z] != Terrain.TerrainType.GREEN && map[x][z] != Terrain.TerrainType.TEE) {
                            map[x][z] = Terrain.TerrainType.SAND;
                        }
                    }
                }
            }
        }
    }

    private void generateBaseMoat(float[][] heights) {
        int SIZE_X = heights.length;
        int SIZE_Z = heights[0].length;

        float baseCentreDip = SIZE_Z * 0.08f;
        float wave1Freq = 0.05f, wave1Amp = 4.0f;
        float wave2Freq = 0.12f, wave2Amp = 1.5f;
        float seedOffset = rng.nextFloat() * 1000f;

        for (int x = 0; x < SIZE_X; x++) {
            float waveShift = MathUtils.sin((x + seedOffset) * wave1Freq) * wave1Amp
                    + MathUtils.cos((x + seedOffset) * wave2Freq) * wave2Amp;
            float currentCentreDip = baseCentreDip + waveShift;

            for (int z = 0; z < SIZE_Z; z++) {
                float distFromCentre = Math.abs(z - currentCentreDip);
                float moatRadius = 20f, flatFloorRadius = 3f, targetWaterDepth = -4f;

                if (distFromCentre <= moatRadius) {
                    float strength;
                    if (distFromCentre <= flatFloorRadius) {
                        strength = 1.0f;
                    } else {
                        float range = moatRadius - flatFloorRadius;
                        float localDist = distFromCentre - flatFloorRadius;
                        strength = 1.0f - (localDist / range);
                        strength = strength * strength * (3 - 2 * strength);
                    }
                    heights[x][z] = MathUtils.lerp(heights[x][z], targetWaterDepth, strength);
                }
            }
        }
    }

    private void applyInitialStoneBase(Terrain.TerrainType[][] map) {
        for (int x = 0; x < map.length; x++) {
            for (int z = 0; z < map[0].length; z++) {
                if (map[x][z] != Terrain.TerrainType.GREEN && map[x][z] != Terrain.TerrainType.TEE) {
                    map[x][z] = Terrain.TerrainType.STONE;
                }
            }
        }
    }

    private void applyRoughCurvaturePass(Terrain.TerrainType[][] map, float[][] heights) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        float sharpThreshold = 0.045f;
        float spreadChance = 0.75f;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (map[x][z] == Terrain.TerrainType.GREEN || map[x][z] == Terrain.TerrainType.TEE) continue;

                float centerHeight = heights[x][z];
                float neighborAvg = getNeighborAvg(heights, x, z);
                float curvature = Math.abs(centerHeight - neighborAvg);

                if (curvature > sharpThreshold) {
                    map[x][z] = Terrain.TerrainType.ROUGH;
                    spreadRough(map, x, z, spreadChance);
                }
            }
        }
    }

    private float getNeighborAvg(float[][] heights, int x, int z) {
        int w = Math.max(0, x - 1), e = Math.min(heights.length - 1, x + 1);
        int s = Math.max(0, z - 1), n = Math.min(heights[0].length - 1, z + 1);
        return (heights[w][z] + heights[e][z] + heights[x][s] + heights[x][n]) / 4.0f;
    }

    private void spreadRough(Terrain.TerrainType[][] map, int x, int z, float chance) {
        int[][] neighbors = {{x - 1, z}, {x + 1, z}, {x, z - 1}, {x, z + 1}};
        for (int[] pos : neighbors) {
            if (pos[0] >= 0 && pos[0] < map.length && pos[1] >= 0 && pos[1] < map[0].length) {
                if (map[pos[0]][pos[1]] == Terrain.TerrainType.STONE && rng.nextFloat() < chance) {
                    map[pos[0]][pos[1]] = Terrain.TerrainType.ROUGH;
                }
            }
        }
    }

    private void placeIntegratedPads(Terrain.TerrainType[][] map, float[][] heights) {
        int SIZE_Z = map[0].length;
        int SIZE_X = map.length;
        int padCount = MathUtils.clamp(MathUtils.floor((SIZE_Z - 150) * 0.03f), 1, 10);
        Array<Vector2> placedCenters = new Array<>();

        for (int i = 0; i < padCount * 10 && placedCenters.size < padCount; i++) {
            int padX = (int) (SIZE_X * (0.35f + rng.nextFloat() * 0.3f));
            int padZ = (int) (SIZE_Z * (0.25f + rng.nextFloat() * 0.5f));

            if (isPositionValidForPad(heights, placedCenters, padX, padZ)) {
                placedCenters.add(new Vector2(padX, padZ));

                Terrain.TerrainType centerType = Terrain.TerrainType.FAIRWAY;
                Terrain.TerrainType edgeType = Terrain.TerrainType.ROUGH;

                if (rng.nextFloat() < 0.30f) {
                    centerType = getRandomPadType();
                    edgeType = getRandomPadType();
                }

                applyIntegratedPad(map, heights, padX, padZ, centerType, edgeType);
            }
        }
    }

    private boolean isPositionValidForPad(float[][] heights, Array<Vector2> placed, int x, int z) {
        for (Vector2 center : placed) {
            if (Vector2.dst(x, z, center.x, center.y) < 60f) return false;
        }
        int zOffset = 5;
        if (z + zOffset < heights[0].length && z - zOffset >= 0) {
            float diff = Math.abs(heights[x][z + zOffset] - heights[x][z - zOffset]);
            return diff >= 1.8f;
        }
        return false;
    }

    private void applyIntegratedPad(Terrain.TerrainType[][] map, float[][] heights, int cx, int cz, Terrain.TerrainType centerType, Terrain.TerrainType edgeType) {
        float targetHeight = heights[cx][cz];
        float baseRadius = 10f + (rng.nextFloat() * 10f);
        float stretchX = 0.85f + rng.nextFloat() * 0.3f;
        float stretchZ = 0.85f + rng.nextFloat() * 0.3f;

        float noiseSeverity = rng.nextFloat();
        float wFreq1 = 1.5f + rng.nextFloat() * 2.0f, wAmp1 = (1.5f + rng.nextFloat() * 3.0f) * noiseSeverity;
        float wFreq2 = 4.0f + rng.nextFloat() * 4.0f, wAmp2 = (0.5f + rng.nextFloat() * 1.5f) * noiseSeverity;

        float bFreq1 = 0.25f + rng.nextFloat() * 0.15f, bAmp1 = 0.5f + rng.nextFloat() * 1.0f;
        float bFreq2 = 0.6f + rng.nextFloat() * 0.4f, bAmp2 = 0.2f + rng.nextFloat() * 0.5f;
        float backEdgeTilt = (rng.nextFloat() - 0.5f) * 0.15f;

        float maxR = (baseRadius + wAmp1 + wAmp2) * Math.max(stretchX, stretchZ);
        float influenceRadius = maxR * 2.5f;
        float shelfFalloff = 5f, edgeSmoothZone = 3.0f;

        for (int x = (int) (cx - influenceRadius); x <= cx + influenceRadius; x++) {
            for (int z = (int) (cz - influenceRadius); z <= cz + influenceRadius; z++) {
                if (x < 0 || x >= map.length || z < 0 || z >= map[0].length) continue;

                float dx = (x - cx) / stretchX, dz = (z - cz) / stretchZ;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                if (dist > influenceRadius) continue;

                float angle = MathUtils.atan2(dz, dx);
                float dynamicRadius = baseRadius + (MathUtils.sin(angle * wFreq1) * wAmp1) + (MathUtils.sin(angle * wFreq2) * wAmp2);

                float xL = x - cx;
                float zEdgePos = cz + (MathUtils.sin(xL * bFreq1) * bAmp1) + (MathUtils.sin(xL * bFreq2) * bAmp2) + (xL * backEdgeTilt);
                float blendFactor = MathUtils.clamp((z - zEdgePos + edgeSmoothZone) / (edgeSmoothZone * 2f), 0f, 1f);

                float strength = (float) Math.pow(1.0f - (dist / influenceRadius), 2);
                float uphill = MathUtils.lerp(heights[x][z], Math.max(targetHeight, heights[x][z] - 2f), strength * 0.5f);

                float downhill = heights[x][z];
                if (dist < dynamicRadius + shelfFalloff) {
                    float sStr = dist <= dynamicRadius ? 1.0f : 1.0f - ((dist - dynamicRadius) / shelfFalloff);
                    sStr = sStr * sStr * (3 - 2 * sStr);
                    downhill = MathUtils.lerp(heights[x][z], targetHeight, sStr);
                }

                heights[x][z] = MathUtils.lerp(downhill, uphill, blendFactor);

                if (z <= zEdgePos && dist < dynamicRadius) {
                    if (map[x][z] != Terrain.TerrainType.GREEN && map[x][z] != Terrain.TerrainType.TEE) {
                        map[x][z] = (dist < dynamicRadius - 0.75f) ? centerType : edgeType;
                    }
                }
            }
        }
    }

    private void applyStoneNoise(Terrain.TerrainType[][] map, float[][] heights) {
        float noiseAmount = 0.08f;
        for (int x = 0; x < map.length; x++) {
            for (int z = 0; z < map[0].length; z++) {
                if (map[x][z] == Terrain.TerrainType.STONE) {
                    heights[x][z] += (rng.nextFloat() - 0.5f) * noiseAmount;
                }
            }
        }
    }

    private Terrain.TerrainType getRandomPadType() {
        int r = rng.nextInt(3);
        return r == 0 ? Terrain.TerrainType.FAIRWAY : (r == 1 ? Terrain.TerrainType.ROUGH : Terrain.TerrainType.SAND);
    }
}