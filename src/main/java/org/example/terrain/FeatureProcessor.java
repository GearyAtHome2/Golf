package org.example.terrain;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
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

    /**
     * Generates clustered craters with guaranteed safe zones between clusters.
     */
    /**
     * Generates clustered craters with guaranteed safe zones between clusters.
     * Probability of crater placement increases as Z increases.
     */
    public List<ClassicGenerator.CraterRecord> generateCraterField(Terrain.TerrainType[][] map, float[][] heights, int targetCraterCount) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        List<ClassicGenerator.CraterRecord> newCraters = new ArrayList<>();

        float clusterBuffer = 25.0f;
        int seedsNeeded = Math.max(2, targetCraterCount / 3);

        int attempts = 0;
        int maxAttempts = 1000; // Increased to find valid spots for higher density clusters

        while (newCraters.size() < seedsNeeded && attempts < maxAttempts) {
            attempts++;
            int cx = rng.nextInt(SIZE_X);

            // INCREASED PROBABILITY AT HIGHER Z:
            // Squaring the random value (0.0 to 1.0) biases the distribution heavily toward 1.0.
            float bias = rng.nextFloat();
            bias = bias * bias; // Probability curve: x^2
            int cz = (int) (SIZE_Z * 0.15f) + (int) (bias * (SIZE_Z * 0.75f));

            float radius = 18f + rng.nextFloat() * 10f;
            // The visual impact of a crater includes its ridge (radius * 1.4)
            float totalVisualRadius = radius * 1.4f;

            if (isInsideProtectedZone(cx, cz, totalVisualRadius + 8f, map)) continue;

            boolean tooCloseToCluster = false;
            for (ClassicGenerator.CraterRecord existing : newCraters) {
                float dist = Vector2.dst(cx, cz, existing.x(), existing.z());
                // Check against total ridges + buffer to ensure safe zones
                if (dist < (radius * 1.4f + existing.radius() * 1.4f + clusterBuffer)) {
                    tooCloseToCluster = true;
                    break;
                }
            }

            if (!tooCloseToCluster) {
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
            float angle = rng.nextFloat() * MathUtils.PI2;

            // RIM PROTECTION:
            // parent.radius * 1.4 is the edge of the ridge.
            // We ensure the child center is at least (parent ridge + child ridge) away.
            float sRadius = 4f + rng.nextFloat() * 6f;
            float parentRidge = parent.radius() * 1.4f;
            float childRidge = sRadius * 1.4f;

            // Place satellite center far enough so ridges do not overlap
            float distFromParent = parentRidge + childRidge + 2f;

            int sx = (int)(parent.x() + MathUtils.cos(angle) * distFromParent);
            int sz = (int)(parent.z() + MathUtils.sin(angle) * distFromParent);

            if (sx < 0 || sx >= map.length || sz < 0 || sz >= map[0].length) continue;
            if (isInsideProtectedZone(sx, sz, childRidge + 2f, map)) continue;

            boolean overlaps = false;
            for (ClassicGenerator.CraterRecord existing : list) {
                float dist = Vector2.dst(sx, sz, existing.x(), existing.z());
                // Strict ridge-to-ridge check
                if (dist < (sRadius * 1.4f + existing.radius() * 1.4f + 1f)) {
                    overlaps = true;
                    break;
                }
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
                if (x >= 0 && x < map.length && z >= 0 && z < map[0].length) {
                    float dx = x - cx;
                    float dz = z - cz;
                    if (dx * dx + dz * dz <= checkRadius * checkRadius) {
                        Terrain.TerrainType type = map[x][z];
                        if (type == Terrain.TerrainType.GREEN || type == Terrain.TerrainType.TEE) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public void applyCrater(Terrain.TerrainType[][] map, float[][] heights, int centerX, int centerZ, float radius, float depth, boolean isDeep) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        float ridgeWidth = radius * 0.4f;
        float totalRadius = radius + ridgeWidth;
        float ridgeHeight = isDeep ? depth * 0.7f : depth * 0.3f;
        float peakRadius = radius * 0.2f;
        float peakHeight = depth * 0.6f;

        int xStart = MathUtils.clamp((int)(centerX - totalRadius), 0, SIZE_X - 1);
        int xEnd = MathUtils.clamp((int)(centerX + totalRadius), 0, SIZE_X - 1);
        int zStart = MathUtils.clamp((int)(centerZ - totalRadius), 0, SIZE_Z - 1);
        int zEnd = MathUtils.clamp((int)(centerZ + totalRadius), 0, SIZE_Z - 1);

        for (int x = xStart; x <= xEnd; x++) {
            for (int z = zStart; z <= zEnd; z++) {
                float dx = x - centerX;
                float dz = z - centerZ;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                if (dist > totalRadius) continue;

                float finalHeightMod = 0;
                Terrain.TerrainType typeOverride = null;

                if (dist < radius) {
                    float normDist = dist / radius;
                    finalHeightMod = -depth * (1.0f - (normDist * normDist));
                    typeOverride = Terrain.TerrainType.SAND;
                    if (dist < peakRadius) {
                        float peakNorm = dist / peakRadius;
                        float peakShape = (float) Math.cos(peakNorm * MathUtils.PI / 2);
                        finalHeightMod += peakHeight * peakShape;
                        if (radius > 10f && dist < peakRadius) {
                            typeOverride = Terrain.TerrainType.STONE;
                        }
                    }
                } else {
                    float ridgeNorm = (dist - radius) / ridgeWidth;
                    float ridgeShape = (float) Math.sin(ridgeNorm * MathUtils.PI);
                    finalHeightMod = ridgeHeight * ridgeShape;
                    typeOverride = Terrain.TerrainType.ROUGH;
                }

                heights[x][z] += finalHeightMod;
                if (typeOverride != null && map[x][z] != Terrain.TerrainType.GREEN && map[x][z] != Terrain.TerrainType.TEE) {
                    map[x][z] = typeOverride;
                }
            }
        }
    }

    // --- Original methods preserved below ---

    public void smoothFairwayMoguls(Terrain.TerrainType[][] map, float[][] heights) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        float[][] buffer = new float[SIZE_X][SIZE_Z];
        int radius = 5;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (map[x][z] == Terrain.TerrainType.FAIRWAY) {
                    float sumWeights = 0;
                    float weightedHeight = 0;
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            int nx = x + dx;
                            int nz = z + dz;
                            if (nx >= 0 && nx < SIZE_X && nz >= 0 && nz < SIZE_Z) {
                                float distSq = dx * dx + dz * dz;
                                if (distSq <= radius * radius) {
                                    float distance = (float) Math.sqrt(distSq);
                                    float weight = 1.0f - (distance / radius);
                                    weight = weight * weight * (3 - 2 * weight);
                                    weightedHeight += heights[nx][nz] * weight;
                                    sumWeights += weight;
                                }
                            }
                        }
                    }
                    buffer[x][z] = weightedHeight / sumWeights;
                } else {
                    buffer[x][z] = heights[x][z];
                }
            }
        }
        for (int x = 0; x < SIZE_X; x++) {
            System.arraycopy(buffer[x], 0, heights[x], 0, SIZE_Z);
        }
    }

    public void generateSegmentedFairway(Terrain.TerrainType[][] map, int gX, int gZ, float fWidth) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        float wiggle = data.getFairwayWiggle();
        float cohesion = data.getFairwayCohesion();
        float widthNoiseFreq = 0.05f;
        float breakageThreshold = 0.2f - (cohesion * 1.5f) + (wiggle * 0.2f);
        for (int z = 0; z < SIZE_Z; z++) {
            float zNorm = z / (float) (SIZE_Z - 1);
            float baseFreq = 0.015f + (wiggle * 0.02f);
            float amplitude = (SIZE_X * 0.05f) + (wiggle * SIZE_X * 0.12f);
            float wiggleOffset = (MathUtils.sin(z * baseFreq + off1) + MathUtils.sin(z * baseFreq * 2.1f + off2) * 0.5f) * amplitude;
            float cx = MathUtils.lerp(SIZE_X / 2f, (float) gX, zNorm) + wiggleOffset;
            float widthNoise = (MathUtils.sin(z * widthNoiseFreq + off2 * 0.5f) + MathUtils.sin(z * widthNoiseFreq * 1.8f + off1 * 0.3f) * 0.5f);
            float blobT = MathUtils.clamp((widthNoise - breakageThreshold) * 0.4f, 0f, 1f);
            float finalWidth = fWidth * (float) Math.sqrt(1.0f - (1.0f - blobT) * (1.0f - blobT));
            if (finalWidth <= 1.0f) continue;
            int xExtent = (int) (finalWidth / 2f);
            for (int x = (int)cx - xExtent; x <= (int)cx + xExtent; x++) {
                if (x < 0 || x >= SIZE_X) continue;
                float dx = (x - cx) / (finalWidth / 2f);
                if (dx * dx < 0.95f) {
                    if (map[x][z] == Terrain.TerrainType.ROUGH) map[x][z] = Terrain.TerrainType.FAIRWAY;
                }
            }
        }
    }

    public void generateContinuousFairway(Terrain.TerrainType[][] map, int gX, int gZ, float maxFairwayWidth, float minFairwayWidth, float wiggleMult, boolean isIsland, boolean startFullWidth) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        float widthBaseFreq = 0.015f + (rng.nextFloat() * 0.02f);
        float widthFreq = widthBaseFreq * (40.0f / Math.max(20.0f, maxFairwayWidth));
        float[] rawWidths = new float[SIZE_Z];
        float[] centerlines = new float[SIZE_Z];
        float GAP_START = 0.65f, GAP_END = 0.88f, ROUNDING_RANGE = 0.06f;

        for (int z = 0; z < SIZE_Z; z++) {
            float zNorm = z / (float) (SIZE_Z - 1);
            float baseFreq = 0.02f + (wiggleMult * 0.03f);
            float amplitude = (SIZE_X * 0.08f) + (wiggleMult * SIZE_X * 0.15f);
            float wiggle = (MathUtils.sin(z * baseFreq + off1) + MathUtils.sin(z * baseFreq * 2.33f + off2) * 0.5f) * amplitude;
            float targetCenter = MathUtils.lerp(SIZE_X / 2f, (float) gX, zNorm) + wiggle;
            float wNoise = (MathUtils.sin(z * widthFreq + off2) + MathUtils.sin(z * widthFreq * 1.77f + off1) * 0.4f);
            float targetWidth = MathUtils.lerp(-15f, maxFairwayWidth, (wNoise + 1.2f) / 2.4f);
            float roundMult = 1.0f;
            if (isIsland) {
                if (zNorm >= GAP_START && zNorm <= GAP_END) roundMult = 0;
                else if (zNorm > GAP_START - ROUNDING_RANGE && zNorm < GAP_START) {
                    float t = (GAP_START - zNorm) / ROUNDING_RANGE;
                    roundMult = (float) Math.sqrt(1.0f - (1.0f - t) * (1.0f - t));
                } else if (zNorm > GAP_END && zNorm < GAP_END + ROUNDING_RANGE) {
                    float t = (zNorm - GAP_END) / ROUNDING_RANGE;
                    roundMult = (float) Math.sqrt(1.0f - (1.0f - t) * (1.0f - t));
                }
            }
            if (roundMult < 0.05f) roundMult = 0;
            float finalWidth = Math.max(minFairwayWidth * roundMult, targetWidth * roundMult);
            if (startFullWidth && zNorm < 0.15f) {
                float transition = zNorm / 0.15f;
                float smoothTransition = transition * transition * (3 - 2 * transition);
                finalWidth = MathUtils.lerp(SIZE_X, finalWidth, smoothTransition);
                centerlines[z] = MathUtils.lerp(SIZE_X / 2f, targetCenter, smoothTransition);
            } else centerlines[z] = targetCenter;
            rawWidths[z] = finalWidth;
        }

        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                if (map[x][z] != Terrain.TerrainType.ROUGH) continue;
                if (rawWidths[z] > 0 && Math.abs(x - centerlines[z]) < rawWidths[z] / 2f) map[x][z] = Terrain.TerrainType.FAIRWAY;
            }
        }
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