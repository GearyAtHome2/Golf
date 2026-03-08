package org.example.terrain;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.level.LevelData;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FeatureProcessor {
    private final LevelData data;
    private final Random rng;
    private final float off1, off2;
    private final float[] waveAngles;
    private final float[] waveFreqs;
    private final float[] waveAmps;
    private final float[] waveOffsets;

    private final int SMOOTH_RADIUS = 2;
    private final float SMOOTH_FAIRWAY_BUFFER = 6.0f;
    private final float SMOOTH_STRENGTH = 0.9f;
    private final float MOGUL_BASE_DAMPING = 0.3f;
    private final float MOGUL_FADE_DISTANCE = 18.0f;

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

    public float calculateMogulNoise(int x, int z, float scale, float o1, float o2, float freq, float und, float maxH, boolean[][] pathMask) {
        float wX = x * scale + o1, wZ = z * scale + o2;
        float rawMogul = (float) Math.pow(Math.abs(generateMultiWaveNoise(wX, wZ, freq)), 1.2f) * und * maxH * 6.0f;

        float dist = getDistanceToPath(x, z, pathMask, MOGUL_FADE_DISTANCE);
        float t = MathUtils.clamp(dist / MOGUL_FADE_DISTANCE, 0f, 1f);
        float mogulIntensity = MathUtils.lerp(MOGUL_BASE_DAMPING, 1.0f, t * t * (3 - 2 * t));

        return rawMogul * mogulIntensity;
    }

    public void applyFairwayGaussianSmoothing(Terrain.TerrainType[][] map, float[][] heights) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        float[][] smoothed = new float[SIZE_X][SIZE_Z];
        boolean[][] pathMask = getPathMask(map);

        float[][] kernel = {
                {1 / 256f, 4 / 256f, 6 / 256f, 4 / 256f, 1 / 256f},
                {4 / 256f, 16 / 256f, 24 / 256f, 16 / 256f, 4 / 256f},
                {6 / 256f, 24 / 256f, 36 / 256f, 24 / 256f, 6 / 256f},
                {4 / 256f, 16 / 256f, 24 / 256f, 16 / 256f, 4 / 256f},
                {1 / 256f, 4 / 256f, 6 / 256f, 4 / 256f, 1 / 256f}
        };

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                float dist = getDistanceToPath(x, z, pathMask, SMOOTH_FAIRWAY_BUFFER);
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
                    float falloff = MathUtils.clamp(1.0f - (dist / SMOOTH_FAIRWAY_BUFFER), 0, 1);
                    smoothed[x][z] = MathUtils.lerp(heights[x][z], heightSum / weightSum, SMOOTH_STRENGTH * falloff);
                } else {
                    smoothed[x][z] = heights[x][z];
                }
            }
        }
        for (int x = 0; x < SIZE_X; x++) System.arraycopy(smoothed[x], 0, heights[x], 0, SIZE_Z);
    }

    public float applyIslandCoastline(int x, int z, float zNorm, float currentHeight, Terrain.TerrainType type, float water, float o1, float o2) {
        float coastThreshold = 0.45f + MathUtils.sin(x * 0.05f + o1) * 0.04f + MathUtils.cos(x * 0.4f + o2) * 0.01f;
        boolean isPlayable = (type == Terrain.TerrainType.FAIRWAY || type == Terrain.TerrainType.TEE || type == Terrain.TerrainType.GREEN);

        if (zNorm > coastThreshold && !isPlayable) {
            if (type == Terrain.TerrainType.ROUGH) currentHeight -= 20.0f;
            else if (type == Terrain.TerrainType.SAND) currentHeight = MathUtils.lerp(water - 1.5f, currentHeight, MathUtils.clamp((currentHeight - water) / 5.0f, 0f, 1f));
        }
        return (isPlayable && currentHeight < water + 1.0f) ? water + 1.0f : currentHeight;
    }

    public void applySlopeBasedStone(Terrain.TerrainType[][] map, float[][] heights, float threshold) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        for (int x = 0; x < SIZE_X - 1; x++) {
            for (int z = 0; z < SIZE_Z - 1; z++) {
                float dx = heights[x + 1][z] - heights[x][z], dz = heights[x][z + 1] - heights[x][z];
                if (1.0f - new Vector3(-dx, 1.0f, -dz).nor().y > threshold && map[x][z] == Terrain.TerrainType.ROUGH) {
                    map[x][z] = Terrain.TerrainType.STONE;
                }
            }
        }
    }

    public void applyFairwayWaterBuffer(Terrain.TerrainType[][] map, float[][] heights, float waterLevel, float tileDistance) {
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
                            if (nx >= 0 && nx < SIZE_X && nz >= 0 && nz < SIZE_Z && heights[nx][nz] < waterLevel && (dx * dx + dz * dz) <= distSqThreshold) {
                                nearWater = true; break;
                            }
                        }
                        if (nearWater) break;
                    }
                    if (nearWater) map[x][z] = Terrain.TerrainType.ROUGH;
                }
            }
        }
    }

    public List<ClassicGenerator.CraterRecord> generateCraterField(Terrain.TerrainType[][] map, float[][] heights, int targetCraterCount) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        List<ClassicGenerator.CraterRecord> newCraters = new ArrayList<>();
        float clusterBuffer = 25.0f;
        int seedsNeeded = Math.max(2, targetCraterCount / 3);
        int attempts = 0;

        while (newCraters.size() < seedsNeeded && attempts < 1000) {
            attempts++;
            int cx = rng.nextInt(SIZE_X);
            float bias = rng.nextFloat();
            int cz = (int) (SIZE_Z * 0.15f) + (int) (bias * bias * (SIZE_Z * 0.75f));
            float radius = 18f + rng.nextFloat() * 10f;

            if (isInsideProtectedZone(cx, cz, radius * 1.4f + 8f, map)) continue;

            boolean tooClose = false;
            for (ClassicGenerator.CraterRecord existing : newCraters) {
                if (Vector2.dst(cx, cz, existing.x(), existing.z()) < (radius * 1.4f + existing.radius() * 1.4f + clusterBuffer)) {
                    tooClose = true; break;
                }
            }

            if (!tooClose) {
                applyCrater(map, heights, cx, cz, radius, 4f + rng.nextFloat() * 3f, rng.nextBoolean());
                ClassicGenerator.CraterRecord seed = new ClassicGenerator.CraterRecord(cx, cz, radius);
                newCraters.add(seed);
                generateSatellites(map, heights, seed, newCraters);
            }
        }
        return newCraters;
    }

    private void generateSatellites(Terrain.TerrainType[][] map, float[][] heights, ClassicGenerator.CraterRecord parent, List<ClassicGenerator.CraterRecord> list) {
        int numSatellites = (parent.radius() > 22f) ? 3 + rng.nextInt(3) : 1 + rng.nextInt(3);
        for (int i = 0; i < numSatellites; i++) {
            float angle = rng.nextFloat() * MathUtils.PI2, sRadius = 4f + rng.nextFloat() * 6f;
            float distFromParent = (parent.radius() * 1.4f) + (sRadius * 1.4f) + 2f;
            int sx = (int)(parent.x() + MathUtils.cos(angle) * distFromParent), sz = (int)(parent.z() + MathUtils.sin(angle) * distFromParent);

            if (sx < 0 || sx >= map.length || sz < 0 || sz >= map[0].length || isInsideProtectedZone(sx, sz, sRadius * 1.4f + 2f, map)) continue;

            boolean overlaps = false;
            for (ClassicGenerator.CraterRecord e : list) {
                if (Vector2.dst(sx, sz, e.x(), e.z()) < (sRadius * 1.4f + e.radius() * 1.4f + 1f)) { overlaps = true; break; }
            }
            if (!overlaps) {
                applyCrater(map, heights, sx, sz, sRadius, 2f + rng.nextFloat() * 2f, false);
                list.add(new ClassicGenerator.CraterRecord(sx, sz, sRadius));
            }
        }
    }

    private boolean isInsideProtectedZone(int cx, int cz, float checkRadius, Terrain.TerrainType[][] map) {
        int r = (int) Math.ceil(checkRadius);
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (x >= 0 && x < map.length && z >= 0 && z < map[0].length && (x - cx) * (x - cx) + (z - cz) * (z - cz) <= checkRadius * checkRadius) {
                    if (map[x][z] == Terrain.TerrainType.GREEN || map[x][z] == Terrain.TerrainType.TEE) return true;
                }
            }
        }
        return false;
    }

    public void applyCrater(Terrain.TerrainType[][] map, float[][] heights, int cX, int cZ, float r, float d, boolean isDeep) {
        float rW = r * 0.4f, tR = r + rW, rH = isDeep ? d * 0.7f : d * 0.3f, pR = r * 0.2f, pH = d * 0.6f;
        for (int x = MathUtils.clamp((int)(cX - tR), 0, map.length - 1); x <= MathUtils.clamp((int)(cX + tR), 0, map.length - 1); x++) {
            for (int z = MathUtils.clamp((int)(cZ - tR), 0, map[0].length - 1); z <= MathUtils.clamp((int)(cZ + tR), 0, map[0].length - 1); z++) {
                float dist = (float) Math.sqrt((x - cX) * (x - cX) + (z - cZ) * (z - cZ));
                if (dist > tR) continue;
                float mod = 0; Terrain.TerrainType type = null;
                if (dist < r) {
                    mod = -d * (1.0f - (dist / r) * (dist / r)); type = Terrain.TerrainType.SAND;
                    if (dist < pR) { mod += pH * (float) Math.cos((dist / pR) * MathUtils.PI / 2); if (r > 10f) type = Terrain.TerrainType.STONE; }
                } else { mod = rH * (float) Math.sin(((dist - r) / rW) * MathUtils.PI); type = Terrain.TerrainType.ROUGH; }
                heights[x][z] += mod;
                if (type != null && map[x][z] != Terrain.TerrainType.GREEN && map[x][z] != Terrain.TerrainType.TEE) map[x][z] = type;
            }
        }
    }

    private boolean[][] getPathMask(Terrain.TerrainType[][] map) {
        boolean[][] mask = new boolean[map.length][map[0].length];
        for (int x = 0; x < map.length; x++) {
            for (int z = 0; z < map[0].length; z++) {
                Terrain.TerrainType t = map[x][z];
                mask[x][z] = (t == Terrain.TerrainType.FAIRWAY || t == Terrain.TerrainType.GREEN || t == Terrain.TerrainType.TEE);
            }
        }
        return mask;
    }

    private float getDistanceToPath(int x, int z, boolean[][] mask, float maxDist) {
        float minDistSq = maxDist * maxDist;
        int r = (int) maxDist + 1;
        for (int ix = Math.max(0, x - r); ix <= Math.min(mask.length - 1, x + r); ix++) {
            for (int iz = Math.max(0, z - r); iz <= Math.min(mask[0].length - 1, z + r); iz++) {
                if (mask[ix][iz]) {
                    float dSq = (x - ix) * (x - ix) + (z - iz) * (z - iz);
                    if (dSq < minDistSq) minDistSq = dSq;
                }
            }
        }
        return (float) Math.sqrt(minDistSq);
    }

    public void generateSegmentedFairway(Terrain.TerrainType[][] map, int gX, int gZ, float fWidth) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        float breakT = 0.2f - (data.getFairwayCohesion() * 1.5f) + (data.getFairwayWiggle() * 0.2f);
        for (int z = 0; z < SIZE_Z; z++) {
            float zN = z / (float) (SIZE_Z - 1), bF = 0.015f + (data.getFairwayWiggle() * 0.02f), amp = (SIZE_X * 0.05f) + (data.getFairwayWiggle() * SIZE_X * 0.12f);
            float cX = MathUtils.lerp(SIZE_X / 2f, (float) gX, zN) + (MathUtils.sin(z * bF + off1) + MathUtils.sin(z * bF * 2.1f + off2) * 0.5f) * amp;
            float wN = (MathUtils.sin(z * 0.05f + off2 * 0.5f) + MathUtils.sin(z * 0.09f + off1 * 0.3f) * 0.5f);
            float finalW = fWidth * (float) Math.sqrt(1.0f - Math.pow(1.0f - MathUtils.clamp((wN - breakT) * 0.4f, 0, 1), 2));
            if (finalW <= 1.0f) continue;
            for (int x = (int)(cX - finalW / 2); x <= (int)(cX + finalW / 2); x++) {
                if (x >= 0 && x < SIZE_X && Math.pow((x - cX) / (finalW / 2), 2) < 0.95f && map[x][z] == Terrain.TerrainType.ROUGH) map[x][z] = Terrain.TerrainType.FAIRWAY;
            }
        }
    }

    public void generateContinuousFairway(Terrain.TerrainType[][] map, int gX, int gZ, float maxW, float minW, float wMult, boolean isIsland, boolean startFull) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        float wF = (0.015f + rng.nextFloat() * 0.02f) * (40.0f / Math.max(20.0f, maxW));
        float[] ws = new float[SIZE_Z], cls = new float[SIZE_Z];
        for (int z = 0; z < SIZE_Z; z++) {
            float zN = z / (float) (SIZE_Z - 1), bF = 0.02f + (wMult * 0.03f), amp = (SIZE_X * 0.08f) + (wMult * SIZE_X * 0.15f);
            float tC = MathUtils.lerp(SIZE_X / 2f, (float) gX, zN) + (MathUtils.sin(z * bF + off1) + MathUtils.sin(z * bF * 2.33f + off2) * 0.5f) * amp;
            float targetW = MathUtils.lerp(-15f, maxW, ((MathUtils.sin(z * wF + off2) + MathUtils.sin(z * wF * 1.77f + off1) * 0.4f) + 1.2f) / 2.4f);
            float rM = 1.0f;
            if (isIsland) {
                if (zN >= 0.65f && zN <= 0.88f) rM = 0;
                else if (zN > 0.59f && zN < 0.65f) rM = (float) Math.sqrt(1.0f - Math.pow(1.0f - (0.65f - zN) / 0.06f, 2));
                else if (zN > 0.88f && zN < 0.94f) rM = (float) Math.sqrt(1.0f - Math.pow(1.0f - (zN - 0.88f) / 0.06f, 2));
            }
            float finalW = Math.max(minW * rM, targetW * rM);
            if (startFull && zN < 0.15f) {
                float t = zN / 0.15f; t = t * t * (3 - 2 * t);
                finalW = MathUtils.lerp(SIZE_X, finalW, t); cls[z] = MathUtils.lerp(SIZE_X / 2f, tC, t);
            } else cls[z] = tC;
            ws[z] = finalW;
        }
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                if (map[x][z] == Terrain.TerrainType.ROUGH && ws[z] > 0 && Math.abs(x - cls[z]) < ws[z] / 2f) map[x][z] = Terrain.TerrainType.FAIRWAY;
            }
        }
    }

    public float generateMultiWaveNoise(float x, float z, float bF) {
        float total = 0, totalA = 0;
        for (int i = 0; i < 10; i++) {
            float coord = (x * MathUtils.cos(waveAngles[i]) + z * MathUtils.sin(waveAngles[i])) * (bF * waveFreqs[i]);
            total += MathUtils.sin(coord + waveOffsets[i]) * waveAmps[i]; totalA += waveAmps[i];
        }
        return total / totalA;
    }
}