package org.example.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain;
import org.example.terrain.TerrainUtils;
import org.example.terrain.level.LevelData;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WhistlingIslesGenerator {
    // --- TWEAKABLE VARIABLES ---
    private static final int ISLE_COUNT = 30;            // Total islands to attempt to place
    private static final float MIN_SPACING = 85.0f;     // Prevents islands from merging into one giant blob
    private static final float SIZE_MULTIPLIER = 2.5f;  // Global scale for all island dimensions

    private static final float ELONGATION_RATIO = 3.0f; // Ratio of length to width (X:Z in local space)
    private static final float FAIRWAY_WIDTH = 20.0f;   // Base fairway width before noise/scaling
    private static final float ROUGH_BORDER = 12.0f;    // Thickness of the rough around the fairway
    private static final float BEACH_LENGTH = 60.0f;    // Length of the gentle slope into the water

    private static final float MAX_EXTRA_HEIGHT = 2.5f; // Max random height added above sea level
    private static final float NOISE_STRENGTH = 12.0f;   // How much the coastline "wobbles"
    private static final float SEA_FLOOR_DEPTH = -3.0f; // How deep the transition goes
    // ---------------------------

    private final LevelData data;
    private final Random rng;
    private final float[] waveAngles, waveFreqs, waveAmps, waveOffsets;
    private final float islandAngle;

    public WhistlingIslesGenerator(LevelData data, Random rng,
                                   float[] waveAngles, float[] waveFreqs, float[] waveAmps, float[] waveOffsets) {
        this.data = data;
        this.rng = rng;
        this.waveAngles = waveAngles;
        this.waveFreqs = waveFreqs;
        this.waveAmps = waveAmps;
        this.waveOffsets = waveOffsets;

        this.islandAngle = (45f - rng.nextFloat() * 90f) * MathUtils.degreesToRadians;
    }

    public void generateWhistlingIsles(Terrain.TerrainType[][] map, float[][] heights, int gX, int gZ, float water) {
        int SX = map.length, SZ = map[0].length;
        List<Vector3> points = new ArrayList<>();

        // Index 0: Tee, Index 1: Green
        points.add(new Vector3(SX / 2f, data.getTeeHeight(), SZ * 0.08f));
        points.add(new Vector3(gX, data.getGreenHeight(), gZ));

        int attempts = 0;
        while (points.size() < ISLE_COUNT && attempts++ < 600) {
            float z = MathUtils.lerp(SZ * 0.12f, SZ * 0.88f, rng.nextFloat());
            float x = (SX * 0.15f) + rng.nextFloat() * (SX * 0.7f);

            if (isSpaceForIsle(x, z, points, MIN_SPACING)) {
                float t = MathUtils.clamp((z - (SZ * 0.05f)) / (SZ * 0.9f), 0, 1);
                float h = MathUtils.lerp(data.getTeeHeight(), data.getGreenHeight(), t) + (rng.nextFloat() * MAX_EXTRA_HEIGHT);
                points.add(new Vector3(x, h, z));
            }
        }

        float cosA = MathUtils.cos(islandAngle);
        float sinA = MathUtils.sin(islandAngle);

        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                if (TerrainUtils.isUnmodifiable(map[x][z])) continue;

                float h = water + SEA_FLOOR_DEPTH;
                Terrain.TerrainType type = Terrain.TerrainType.ROUGH;
                float noise = generateMultiWaveNoise(x * 0.5f, z * 0.5f, 0.2f) * NOISE_STRENGTH;

                for (int i = 0; i < points.size(); i++) {
                    Vector3 p = points.get(i);
                    float dx = x - p.x;
                    float dz = z - p.z;

                    float dist;
                    float fR, rR, tR;

                    if (i == 1) {
                        // --- GREEN (Always circular) ---
                        dist = Vector2.dst(x, z, p.x, p.z);
                        fR = (25f + noise);
                        rR = fR + 12f;
                        tR = rR + 25f;
                    } else {
                        // --- ISLANDS (Elongated) ---
                        float localX = (dx * cosA + dz * sinA) * ELONGATION_RATIO;
                        float localZ = (-dx * sinA + dz * cosA);
                        dist = (float) Math.sqrt(localX * localX + localZ * localZ);

                        fR = (FAIRWAY_WIDTH * SIZE_MULTIPLIER) + noise;
                        rR = fR + (ROUGH_BORDER * SIZE_MULTIPLIER);
                        tR = rR + (BEACH_LENGTH * SIZE_MULTIPLIER) + (noise * 2f);
                    }

                    if (dist < rR) {
                        float t = 1.0f - (dist / rR);
                        float curH = MathUtils.lerp(water + 0.1f, p.y, t);
                        if (curH > h) {
                            h = curH;
                            type = (dist < fR) ? Terrain.TerrainType.FAIRWAY : Terrain.TerrainType.ROUGH;
                        }
                    } else if (dist < tR) {
                        float t = (dist - rR) / (tR - rR);
                        float curH = MathUtils.lerp(water + 0.1f, water + SEA_FLOOR_DEPTH, t);
                        if (curH > h) {
                            h = curH;
                            type = Terrain.TerrainType.ROUGH;
                        }
                    }
                }

                heights[x][z] = h;
                map[x][z] = type;
            }
        }
    }

    private boolean isSpaceForIsle(float x, float z, List<Vector3> points, float min) {
        for (Vector3 b : points) if (Vector2.dst(x, z, b.x, b.z) < min) return false;
        return true;
    }

    public float generateMultiWaveNoise(float x, float z, float bF) {
        float total = 0, totalA = 0;
        for (int i = 0; i < 10; i++) {
            float coord = (x * MathUtils.cos(waveAngles[i]) + z * MathUtils.sin(waveAngles[i])) * (bF * waveFreqs[i]);
            total += MathUtils.sin(coord + waveOffsets[i]) * waveAmps[i];
            totalA += waveAmps[i];
        }
        return total / totalA;
    }
}