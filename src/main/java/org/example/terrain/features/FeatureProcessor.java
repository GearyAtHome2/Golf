package org.example.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.ClassicGenerator;
import org.example.terrain.Terrain;
import org.example.terrain.TerrainUtils;
import org.example.terrain.level.LevelData;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FeatureProcessor {
    private final LevelData data;
    private final Random rng;
    private final float off1, off2;
    private final float[] waveAngles, waveFreqs, waveAmps, waveOffsets;

    private final int SMOOTH_RADIUS = 2;
    private final float SMOOTH_FAIRWAY_BUFFER = 6.0f;
    private final float SMOOTH_STRENGTH = 0.9f;
    private final float MOGUL_BASE_DAMPING = 0.3f;
    private final float MOGUL_FADE_DISTANCE = 18.0f;
    private final float CENOTE_EXPONENT = 4f;
    private final float TEE_BUFFER = 30.0f;
    private final float GREEN_BUFFER = 30.0f;

    public FeatureProcessor(LevelData data, Random rng, float off1, float off2,
                            float[] waveAngles, float[] waveFreqs, float[] waveAmps, float[] waveOffsets) {
        this.data = data;
        this.rng = rng;
        this.off1 = off1;
        this.off2 = off2;
        this.waveAngles = waveAngles;
        this.waveFreqs = waveFreqs;
        this.waveAmps = waveAmps;
        this.waveOffsets = waveOffsets;
    }

    /* --- NOISE & SMOOTHING --- */

    public float calculateMogulNoise(int x, int z, float scale, float o1, float o2, float freq, float und, float maxH, boolean[][] pathMask) {
        float rawMogul = (float) Math.pow(Math.abs(generateMultiWaveNoise(x * scale + o1, z * scale + o2, freq)), 1.2f) * und * maxH * 6.0f;
        float t = MathUtils.clamp(getDistanceToPath(x, z, pathMask, MOGUL_FADE_DISTANCE) / MOGUL_FADE_DISTANCE, 0f, 1f);
        return rawMogul * MathUtils.lerp(MOGUL_BASE_DAMPING, 1.0f, t * t * (3 - 2 * t));
    }

    public void applyFairwayGaussianSmoothing(Terrain.TerrainType[][] map, float[][] heights) {
        int SX = map.length, SZ = map[0].length;
        float[][] smoothed = new float[SX][SZ];
        boolean[][] pathMask = getPathMask(map);
        float[][] k = {{1/256f, 4/256f, 6/256f, 4/256f, 1/256f}, {4/256f, 16/256f, 24/256f, 16/256f, 4/256f}, {6/256f, 24/256f, 36/256f, 24/256f, 6/256f}, {4/256f, 16/256f, 24/256f, 16/256f, 4/256f}, {1/256f, 4/256f, 6/256f, 4/256f, 1/256f}};

        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                float dist = getDistanceToPath(x, z, pathMask, SMOOTH_FAIRWAY_BUFFER);
                if (dist < SMOOTH_FAIRWAY_BUFFER) {
                    float wSum = 0, hSum = 0;
                    for (int dx = -SMOOTH_RADIUS; dx <= SMOOTH_RADIUS; dx++) {
                        for (int dz = -SMOOTH_RADIUS; dz <= SMOOTH_RADIUS; dz++) {
                            int nx = x + dx, nz = z + dz;
                            if (nx >= 0 && nx < SX && nz >= 0 && nz < SZ) {
                                float val = k[dx + SMOOTH_RADIUS][dz + SMOOTH_RADIUS];
                                hSum += heights[nx][nz] * val; wSum += val;
                            }
                        }
                    }
                    float falloff = MathUtils.clamp(1.0f - (dist / SMOOTH_FAIRWAY_BUFFER), 0, 1);
                    smoothed[x][z] = MathUtils.lerp(heights[x][z], hSum / wSum, SMOOTH_STRENGTH * falloff);
                } else smoothed[x][z] = heights[x][z];
            }
        }
        for (int x = 0; x < SX; x++) System.arraycopy(smoothed[x], 0, heights[x], 0, SZ);
    }

    /* --- FAIRWAY GENERATION --- */

    public void generateSegmentedFairway(Terrain.TerrainType[][] map, int gX, int gZ, float fWidth) {
        float breakT = 0.2f - (data.getFairwayCohesion() * 1.5f) + (data.getFairwayWiggle() * 0.2f);
        for (int z = 0; z < map[0].length; z++) {
            float zN = z / (float) (map[0].length - 1), bF = 0.015f + (data.getFairwayWiggle() * 0.02f);
            float cX = MathUtils.lerp(map.length / 2f, (float) gX, zN) + (MathUtils.sin(z * bF + off1) + MathUtils.sin(z * bF * 2.1f + off2) * 0.5f) * ((map.length * 0.05f) + (data.getFairwayWiggle() * map.length * 0.12f));
            float wN = (MathUtils.sin(z * 0.05f + off2 * 0.5f) + MathUtils.sin(z * 0.09f + off1 * 0.3f) * 0.5f);
            float fW = fWidth * (float) Math.sqrt(1.0f - Math.pow(1.0f - MathUtils.clamp((wN - breakT) * 0.4f, 0, 1), 2));
            if (fW > 1.0f) for (int x = (int) (cX - fW / 2); x <= (int) (cX + fW / 2); x++) {
                if (x >= 0 && x < map.length && Math.pow((x - cX) / (fW / 2), 2) < 0.95f && map[x][z] == Terrain.TerrainType.ROUGH) map[x][z] = Terrain.TerrainType.FAIRWAY;
            }
        }
    }

    public void generateContinuousFairway(Terrain.TerrainType[][] map, int gX, int gZ, float maxW, float minW, float wMult, boolean isIsland, boolean startFull) {
        float wF = (0.015f + rng.nextFloat() * 0.02f) * (40.0f / Math.max(20.0f, maxW));
        float[] ws = new float[map[0].length], cls = new float[map[0].length];
        for (int z = 0; z < map[0].length; z++) {
            float zN = z / (float) (map[0].length - 1), bF = 0.02f + (wMult * 0.03f);
            float tC = MathUtils.lerp(map.length / 2f, (float) gX, zN) + (MathUtils.sin(z * bF + off1) + MathUtils.sin(z * bF * 2.33f + off2) * 0.5f) * ((map.length * 0.08f) + (wMult * map.length * 0.15f));
            float targetW = MathUtils.lerp(-15f, maxW, ((MathUtils.sin(z * wF + off2) + MathUtils.sin(z * wF * 1.77f + off1) * 0.4f) + 1.2f) / 2.4f);
            float rM = 1.0f;
            if (isIsland) {
                if (zN >= 0.65f && zN <= 0.88f) rM = 0;
                else if (zN > 0.59f && zN < 0.65f) rM = (float) Math.sqrt(1.0f - Math.pow(1.0f - (0.65f - zN) / 0.06f, 2));
                else if (zN > 0.88f && zN < 0.94f) rM = (float) Math.sqrt(1.0f - Math.pow(1.0f - (zN - 0.88f) / 0.06f, 2));
            }
            float fW = Math.max(minW * rM, targetW * rM);
            if (startFull && zN < 0.15f) {
                float t = zN / 0.15f; t = t * t * (3 - 2 * t);
                fW = MathUtils.lerp(map.length, fW, t); cls[z] = MathUtils.lerp(map.length / 2f, tC, t);
            } else cls[z] = tC;
            ws[z] = fW;
        }
        for (int z = 0; z < map[0].length; z++) for (int x = 0; x < map.length; x++) {
            if (map[x][z] == Terrain.TerrainType.ROUGH && ws[z] > 0 && Math.abs(x - cls[z]) < ws[z] / 2f) map[x][z] = Terrain.TerrainType.FAIRWAY;
        }
    }

    /* --- UTILS --- */

    public float generateMultiWaveNoise(float x, float z, float bF) {
        float total = 0, totalA = 0;
        for (int i = 0; i < 10; i++) {
            float coord = (x * MathUtils.cos(waveAngles[i]) + z * MathUtils.sin(waveAngles[i])) * (bF * waveFreqs[i]);
            total += MathUtils.sin(coord + waveOffsets[i]) * waveAmps[i]; totalA += waveAmps[i];
        }
        return total / totalA;
    }

    private boolean isBorderingRough(int x, int z, Terrain.TerrainType[][] map) {
        return map[x+1][z] == Terrain.TerrainType.ROUGH || map[x-1][z] == Terrain.TerrainType.ROUGH || map[x][z+1] == Terrain.TerrainType.ROUGH || map[x][z-1] == Terrain.TerrainType.ROUGH;
    }

    private List<Vector2> buildSpine(Vector2 start, boolean[][] mask, int maxLen) {
        List<Vector2> spine = new ArrayList<>();
        boolean[][] v = new boolean[mask.length][mask[0].length];
        Vector2 cur = start.cpy(); spine.add(cur); v[(int)cur.x][(int)cur.y] = true;
        for (int i = 0; i < maxLen; i++) {
            Vector2 next = null;
            search: for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                int nx = (int)cur.x + dx, nz = (int)cur.y + dz;
                if (nx >= 0 && nx < mask.length && nz >= 0 && nz < mask[0].length && mask[nx][nz] && !v[nx][nz]) {
                    next = new Vector2(nx, nz); break search;
                }
            }
            if (next == null) break;
            spine.add(next); v[(int)next.x][(int)next.y] = true; cur = next;
        }
        return spine;
    }

    private boolean[][] getPathMask(Terrain.TerrainType[][] map) {
        boolean[][] m = new boolean[map.length][map[0].length];
        for (int x = 0; x < map.length; x++) for (int z = 0; z < map[0].length; z++) {
            Terrain.TerrainType t = map[x][z];
            m[x][z] = (t == Terrain.TerrainType.FAIRWAY || t == Terrain.TerrainType.GREEN || t == Terrain.TerrainType.TEE);
        }
        return m;
    }

    private float getDistanceToPath(int x, int z, boolean[][] m, float max) {
        float minSq = max * max;
        int r = (int) max + 1;
        for (int ix = Math.max(0, x-r); ix <= Math.min(m.length-1, x+r); ix++) {
            for (int iz = Math.max(0, z-r); iz <= Math.min(m[0].length-1, z+r); iz++) {
                if (m[ix][iz]) {
                    float dSq = (x-ix)*(x-ix)+(z-iz)*(z-iz);
                    if (dSq < minSq) minSq = dSq;
                }
            }
        }
        return (float) Math.sqrt(minSq);
    }

    public LevelData getData() { return data; }
}