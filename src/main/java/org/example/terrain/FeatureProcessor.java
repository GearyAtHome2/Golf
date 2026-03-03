package org.example.terrain;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import org.example.LevelData;
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

    public float getFairwayRounding(float zNorm, boolean isIsland, float islandCutoff, int SIZE_Z) {
        if (isIsland && zNorm > islandCutoff) {
            float distPast = zNorm - islandCutoff;
            float radiusNorm = 15f / SIZE_Z;
            if (distPast > radiusNorm) {
                return 0f;
            }
            float ratio = distPast / radiusNorm;
            return (float) Math.sqrt(1.0f - (ratio * ratio));
        }
        return 1.0f;
    }

    public float getCanyonGapMask(int z, int SIZE_Z) {
        float maskFreq = 0.015f;
        float maskThreshold = -0.2f;
        float noise = generateMultiWaveNoise(z * maskFreq, off1 + 500f, 1.0f);

        if (noise <= maskThreshold) return 0f;

        float closestGapDist = 1.0f;
        int window = 15;
        for (int offset = -window; offset <= window; offset++) {
            int checkZ = MathUtils.clamp(z + offset, 0, SIZE_Z - 1);
            float checkNoise = generateMultiWaveNoise(checkZ * maskFreq, off1 + 500f, 1.0f);
            if (checkNoise <= maskThreshold) {
                closestGapDist = Math.min(closestGapDist, Math.abs(offset) / (float) window);
            }
        }
        return (float) Math.sqrt(closestGapDist);
    }

    public void generateBunkerIslands(Terrain.TerrainType[][] map, float[][] heights, int gX, int gZ) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        int attempts = data.getnBunkers();
        int placed = 0;
        int maxIslands = data.getnBunkers() / 3;

        for (int i = 0; i < attempts && placed < maxIslands; i++) {
            float zP = 0.2f + rng.nextFloat() * 0.65f;
            int cz = (int) (SIZE_Z * zP);
            float centerlineX = MathUtils.lerp(SIZE_X / 2f, (float) gX, zP);
            int cx = (int) (centerlineX + (rng.nextFloat() - 0.5f) * 60f);
            cx = MathUtils.clamp(cx, 20, SIZE_X - 20);

            if (Math.abs(heights[cx + 1][cz] - heights[cx - 1][cz]) > 0.8f) continue;

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
                    float angle = (float) Math.atan2(dz, dx);
                    float wobble = MathUtils.sin(angle * 3 + i) * 1.5f + MathUtils.cos(angle * 5 - i) * 1.0f;
                    float dist = (float) Math.sqrt(dx * dx + dz * dz);
                    float effectiveDist = dist + wobble;

                    if (dist < flattenRadius) {
                        float fT = 1.0f - MathUtils.clamp(dist / flattenRadius, 0, 1);
                        heights[x][z] = MathUtils.lerp(heights[x][z], baseH, Interpolation.pow2In.apply(fT) * 0.8f);
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
                        float dipT = 1.0f - Math.abs((islandRadiusOuter + moatWidth / 2f) - effectiveDist) / (moatWidth / 2f);
                        heights[x][z] -= MathUtils.sin(dipT * MathUtils.PI / 2f) * 1.5f;
                    }
                }
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