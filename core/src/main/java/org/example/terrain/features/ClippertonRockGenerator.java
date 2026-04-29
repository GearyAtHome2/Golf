package org.example.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import org.example.terrain.Terrain;
import org.example.terrain.TerrainUtils;
import org.example.terrain.level.LevelData;

import java.util.Random;

public class ClippertonRockGenerator {
    private static final float ATOLL_WIDTH_MAX = 40.0f;
    private static final float ATOLL_RING_THICKNESS = 18.0f;
    private static final float NOISE_STRENGTH = 9.0f;
    private static final float BEACH_TRANSITION = 8.0f;

    private static final float TEE_PLINTH_RADIUS = 9.0f;
    private static final float PLINTH_SLOPE_RADIUS = 5.0f;

    private static final float GREEN_SMOOTH_RADIUS = 50.0f;
    private static final float GREEN_PHYSICAL_RADIUS = 25.0f;

    private final LevelData data;
    private final Random rng;
    private final float[] waveAngles, waveFreqs, waveAmps, waveOffsets;

    public ClippertonRockGenerator(LevelData data, Random rng,
                                   float[] waveAngles, float[] waveFreqs, float[] waveAmps, float[] waveOffsets) {
        this.data = data;
        this.rng = rng;
        this.waveAngles = waveAngles;
        this.waveFreqs = waveFreqs;
        this.waveAmps = waveAmps;
        this.waveOffsets = waveOffsets;
    }

    public void generateClippertonRock(Terrain.TerrainType[][] map, float[][] heights, int gX, int gZ, float water) {
        int SX = map.length;
        int SZ = map[0].length;

        Vector2 teePos = new Vector2(SX / 2f, SZ * 0.05f);
        Vector2 greenPos = new Vector2(gX, gZ);
        float actualGreenH = data.getGreenHeight();

        Vector2 center = new Vector2().set(teePos).add(greenPos).scl(0.5f);
        float distTeeToGreen = teePos.dst(greenPos);

        float axisY = (distTeeToGreen / 2f) + 40f;
        float axisX = Math.min(SX * 0.45f, ATOLL_WIDTH_MAX);

        float totalPlinthBounds = TEE_PLINTH_RADIUS + PLINTH_SLOPE_RADIUS;

        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                if (TerrainUtils.isUnmodifiable(map[x][z])) continue;

                float dx = x - center.x;
                float dz = z - center.y;
                float noise = generateMultiWaveNoise(x * 0.4f, z * 0.4f, 0.15f) * NOISE_STRENGTH;

                float ellipticalDist = (float) Math.sqrt((dx * dx) / (axisX * axisX) + (dz * dz) / (axisY * axisY));
                float distToRing = Math.abs(ellipticalDist - 1.0f) * axisX;
                float adjustedRingDist = distToRing + noise;

                // 1. Sloping water transition
                float slopeOut = Math.max(0, (adjustedRingDist - ATOLL_RING_THICKNESS) / 15f);
                float currentH = water - (slopeOut * 5f);
                Terrain.TerrainType currentType = Terrain.TerrainType.SAND;

                // 2. Land Generation
                if (adjustedRingDist < ATOLL_RING_THICKNESS) {
                    float t = 1.0f - (adjustedRingDist / ATOLL_RING_THICKNESS);
                    currentH = water + (t * 1.8f) + (noise * 0.1f);

                    if (adjustedRingDist > ATOLL_RING_THICKNESS - BEACH_TRANSITION) {
                        currentType = Terrain.TerrainType.SAND;
                    } else {
                        // Terrain painting logic
                        float pNoise = generateMultiWaveNoise(x * 0.8f, z * 0.8f, 0.1f);

                        // New: Rock Distribution Logic
                        float rockNoise = generateMultiWaveNoise(x * 1.2f, z * 1.2f, 0.2f);
                        float distToTee = Vector2.dst(x, z, teePos.x, teePos.y);

                        // Increase rock probability near the tee area (just outside plinth)
                        float rockThreshold = (distToTee > TEE_PLINTH_RADIUS && distToTee < TEE_PLINTH_RADIUS + 30f) ? 0.3f : 0.55f;

                        if (rockNoise > rockThreshold) {
                            currentType = Terrain.TerrainType.STONE;
                            currentH += Math.abs(rockNoise) * 1.5f; // Make rocks slightly elevated and "noisy"
                        } else if (pNoise > 0.2f) {
                            currentType = Terrain.TerrainType.FAIRWAY;
                        } else {
                            currentType = Terrain.TerrainType.ROUGH;
                        }
                    }
                }

                // 3. Green Smoothing
                float distToGreen = Vector2.dst(x, z, greenPos.x, greenPos.y);
                if (distToGreen < GREEN_PHYSICAL_RADIUS + GREEN_SMOOTH_RADIUS) {
                    if (distToGreen <= GREEN_PHYSICAL_RADIUS) {
                        currentH = actualGreenH;
                    } else {
                        float blendT = 1.0f - ((distToGreen - GREEN_PHYSICAL_RADIUS) / GREEN_SMOOTH_RADIUS);
                        blendT = MathUtils.clamp(blendT, 0f, 1f);
                        blendT = TerrainUtils.smoothstep(blendT);
                        currentH = MathUtils.lerp(currentH, actualGreenH, blendT);
                    }
                }

                // 4. Tee Plinth (Final override for the actual tee box area)
                float distToTee = Vector2.dst(x, z, teePos.x, teePos.y);
                if (distToTee < totalPlinthBounds) {
                    float plinthH;
                    Terrain.TerrainType plinthType;
                    float jitter = (rng.nextFloat() - 0.5f) * 0.5f;

                    if (distToTee <= TEE_PLINTH_RADIUS) {
                        plinthH = data.getTeeHeight() + jitter;
                        plinthType = Terrain.TerrainType.TEE;
                    } else {
                        float t = 1.0f - ((distToTee - TEE_PLINTH_RADIUS) / PLINTH_SLOPE_RADIUS);
                        plinthH = MathUtils.lerp(water - 1.0f, data.getTeeHeight(), t) + jitter;
                        plinthType = Terrain.TerrainType.STONE;
                    }

                    if (plinthH > currentH) {
                        currentH = plinthH;
                        currentType = plinthType;
                    }
                }

                heights[x][z] = currentH;
                map[x][z] = currentType;
            }
        }
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