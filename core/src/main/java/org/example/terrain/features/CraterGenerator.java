package org.example.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import org.example.terrain.ClassicGenerator;
import org.example.terrain.Terrain;
import org.example.terrain.TerrainUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CraterGenerator {
    // These are now public variables for easier tuning.
    public static float STONE_RADIUS_MULT = 0.32f;
    public static float STONE_FLATNESS = 6.0f;     // Higher = Flatter top

    private final Random rng;
    private final SimplexNoise noise; // Added noise generator

    public CraterGenerator(Random rng) {
        this.rng = rng;
        this.noise = new SimplexNoise(rng.nextLong()); // Seed the noise
    }

    public List<ClassicGenerator.CraterRecord> generateCraterField(Terrain.TerrainType[][] map, float[][] heights, int count) {
        List<ClassicGenerator.CraterRecord> craters = new ArrayList<>();
        int seeds = Math.max(2, count / 3), attempts = 0;
        while (craters.size() < seeds && attempts++ < 1000) {
            int cx = rng.nextInt(map.length);
            float bias = rng.nextFloat();
            int cz = (int) (map[0].length * 0.15f) + (int) (bias * bias * (map[0].length * 0.75f));
            float rad = 18f + rng.nextFloat() * 10f;
            if (TerrainUtils.isInsideProtectedZone(cx, cz, rad * 1.4f + 8f, map)) continue;
            if (isSpaceForCrater(cx, cz, rad, craters)) {
                // Pass RNG to applyCrater for noise variance
                applyCrater(map, heights, cx, cz, rad, 4f + rng.nextFloat() * 3f, rng.nextBoolean(), rng);
                ClassicGenerator.CraterRecord seed = new ClassicGenerator.CraterRecord(cx, cz, rad);
                craters.add(seed);
                generateSatellites(map, heights, seed, craters);
            }
        }
        return craters;
    }

    private boolean isSpaceForCrater(int x, int z, float r, List<ClassicGenerator.CraterRecord> existing) {
        for (ClassicGenerator.CraterRecord e : existing) {
            if (Vector2.dst(x, z, e.x(), e.z()) < (r * 1.4f + e.radius() * 1.4f + 25.0f)) return false;
        }
        return true;
    }

    private void generateSatellites(Terrain.TerrainType[][] map, float[][] heights, ClassicGenerator.CraterRecord p, List<ClassicGenerator.CraterRecord> list) {
        int n = (p.radius() > 22f) ? 3 + rng.nextInt(3) : 1 + rng.nextInt(3);
        for (int i = 0; i < n; i++) {
            float a = rng.nextFloat() * MathUtils.PI2, sR = 4f + rng.nextFloat() * 6f;
            float d = (p.radius() * 1.4f) + (sR * 1.4f) + 2f;
            int sx = (int) (p.x() + MathUtils.cos(a) * d), sz = (int) (p.z() + MathUtils.sin(a) * d);
            if (sx < 0 || sx >= map.length || sz < 0 || sz >= map[0].length || TerrainUtils.isInsideProtectedZone(sx, sz, sR * 1.4f + 2f, map))
                continue;
            boolean over = false;
            for (ClassicGenerator.CraterRecord e : list)
                if (Vector2.dst(sx, sz, e.x(), e.z()) < (sR * 1.4f + e.radius() * 1.4f + 1f)) over = true;
            if (!over) {
                applyCrater(map, heights, sx, sz, sR, 2f + rng.nextFloat() * 2f, false, rng);
                list.add(new ClassicGenerator.CraterRecord(sx, sz, sR));
            }
        }
    }

    public void applyCrater(Terrain.TerrainType[][] map, float[][] heights, int cX, int cZ, float r, float d, boolean deep, Random localRng) {
        float depthFactor = deep ? 0.25f : 0.15f;
        float scaledD = (r * depthFactor) + (localRng.nextFloat() * 1.5f);

        float rW = r * 0.4f;
        float tR = r + rW;

        // Use scaledD for the rim height and peak height
        float rH = deep ? scaledD * 0.7f : scaledD * 0.3f;
        float pR = r * STONE_RADIUS_MULT;
        float pH = scaledD * 0.85f;

        float noiseScale = 25f / r;
        float stoneNoiseAmp = 0.33f*(r * 0.15f) * MathUtils.clamp(r / 20f, 0.5f, 1.0f);
        float rimNoiseAmp = 0.8f;

        for (int x = MathUtils.clamp((int) (cX - tR), 0, map.length - 1); x <= MathUtils.clamp((int) (cX + tR), 0, map.length - 1); x++) {
            for (int z = MathUtils.clamp((int) (cZ - tR), 0, map[0].length - 1); z <= MathUtils.clamp((int) (cZ + tR), 0, map[0].length - 1); z++) {

                float rawDist = Vector2.dst(x, z, cX, cZ);
                float angle = MathUtils.atan2(z - cZ, x - cX);
                float nx = MathUtils.cos(angle) * noiseScale;
                float nz = MathUtils.sin(angle) * noiseScale;

                float stoneNoise = noise.noise(nx, nz) * stoneNoiseAmp;
                float rimNoise = noise.noise(nx + 100f, nz + 100f) * rimNoiseAmp;

                float stoneDist = rawDist + stoneNoise;
                float rimDist = rawDist + rimNoise;

                if (rimDist > tR) continue;

                float mod = 0;
                Terrain.TerrainType type = map[x][z];

                if (rawDist < r) {
                    // Use scaledD here for the bowl depth
                    mod = -scaledD * (1.0f - (rawDist / r) * (rawDist / r));
                    type = Terrain.TerrainType.SAND;

                    if (stoneDist < pR) {
                        float ratio = stoneDist / pR;
                        float peakShape = (float) Math.pow(Math.max(0, 1.0f - ratio), 1.0f / STONE_FLATNESS);
                        mod += pH * peakShape;
                        if (r > 10f) type = Terrain.TerrainType.STONE;
                    }
                } else {
                    mod = rH * (float) Math.sin(((rimDist - r) / rW) * MathUtils.PI);
                    type = Terrain.TerrainType.ROUGH;
                }

                heights[x][z] += mod;
                if (!TerrainUtils.isUnmodifiable(map[x][z])) map[x][z] = type;
            }
        }
    }
}