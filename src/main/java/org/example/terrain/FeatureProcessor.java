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

    public void generateSegmentedFairway(Terrain.TerrainType[][] map, int gX, int gZ, float fWidth) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        float wiggle = data.getFairwayWiggle();

        float cohesion = data.getFairwayCohesion();
        float breakDensity = data.getFairwayBreakDensity();

        float widthNoiseFreq = 0.015f + (breakDensity * 0.065f);

        float breakageThreshold = 0.2f - (cohesion * 1.5f) + (wiggle * 0.2f);

        boolean wasLastRowEmpty = true;

        for (int z = 0; z < SIZE_Z; z++) {
            float zNorm = z / (float) (SIZE_Z - 1);

            float baseFreq = 0.015f + (wiggle * 0.02f);
            float amplitude = (SIZE_X * 0.05f) + (wiggle * SIZE_X * 0.12f);
            float wiggleOffset = (MathUtils.sin(z * baseFreq + off1) + MathUtils.sin(z * baseFreq * 2.1f + off2) * 0.5f) * amplitude;
            float cx = MathUtils.lerp(SIZE_X / 2f, (float) gX, zNorm) + wiggleOffset;

            // The raw noise oscillates between approx -1.5 and 1.5
            float widthNoise = (MathUtils.sin(z * widthNoiseFreq + off2 * 0.5f) +
                    MathUtils.sin(z * widthNoiseFreq * 1.8f + off1 * 0.3f) * 0.5f);

            // TWEAK 3: Applied your 0.4f slope.
            // This ensures the soft organic "grow" effect remains.
            float blobT = MathUtils.clamp((widthNoise - breakageThreshold) * 0.4f, 0f, 1f);

            // TWEAK 4: Added a slight width boost if blobT > 0 to compensate for the soft slope
            float finalWidth = fWidth * (float) Math.sqrt(1.0f - (1.0f - blobT) * (1.0f - blobT));

            if (finalWidth > 0.1f && wasLastRowEmpty) {
                System.out.println("FAIRWAY_LOG: Segment START at Z=" + z + " (Norm:" + zNorm + ")");
                wasLastRowEmpty = false;
            } else if (finalWidth <= 0.1f && !wasLastRowEmpty) {
                System.out.println("FAIRWAY_LOG: Segment END at Z=" + z + " (Norm:" + zNorm + ")");
                wasLastRowEmpty = true;
            }

            if (finalWidth <= 1.0f) continue;

            int xExtent = (int) (finalWidth / 2f);
            for (int x = (int)cx - xExtent; x <= (int)cx + xExtent; x++) {
                if (x < 0 || x >= SIZE_X) continue;

                float dx = (x - cx) / (finalWidth / 2f);
                if (dx * dx < 0.95f) {
                    if (map[x][z] == Terrain.TerrainType.ROUGH) {
                        map[x][z] = Terrain.TerrainType.FAIRWAY;
                    }
                }
            }
        }
    }

    public void generateContinuousFairway(Terrain.TerrainType[][] map, int gX, int gZ, float maxFairwayWidth, float minFairwayWidth, float wiggleMult, boolean isIsland) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        float widthBaseFreq = 0.015f + (rng.nextFloat() * 0.02f);
        float widthFreq = widthBaseFreq * (40.0f / Math.max(20.0f, maxFairwayWidth));
        float[] rawWidths = new float[SIZE_Z];
        float[] centerlines = new float[SIZE_Z];

        float GAP_START = 0.65f;
        float GAP_END = 0.88f;
        float ROUNDING_RANGE = 0.06f;

        // First pass: Calculate centerlines and widths for every Z row
        for (int z = 0; z < SIZE_Z; z++) {
            float zNorm = z / (float) (SIZE_Z - 1);
            float baseFreq = 0.02f + (wiggleMult * 0.03f);
            float amplitude = (SIZE_X * 0.08f) + (wiggleMult * SIZE_X * 0.15f);

            // Use the offsets passed in or stored in the processor
            float wiggle = (MathUtils.sin(z * baseFreq + off1) + MathUtils.sin(z * baseFreq * 2.33f + off2) * 0.5f) * amplitude;
            centerlines[z] = MathUtils.lerp(SIZE_X / 2f, (float) gX, zNorm) + wiggle;

            float wNoise = (MathUtils.sin(z * widthFreq + off2) + MathUtils.sin(z * widthFreq * 1.77f + off1) * 0.4f);
            float targetWidth = MathUtils.lerp(-15f, maxFairwayWidth, (wNoise + 1.2f) / 2.4f);

            float roundMult = 1.0f;

            if (isIsland) {
                if (zNorm >= GAP_START && zNorm <= GAP_END) {
                    roundMult = 0;
                } else if (zNorm > GAP_START - ROUNDING_RANGE && zNorm < GAP_START) {
                    float t = (GAP_START - zNorm) / ROUNDING_RANGE;
                    roundMult = (float) Math.sqrt(1.0f - (1.0f - t) * (1.0f - t));
                } else if (zNorm > GAP_END && zNorm < GAP_END + ROUNDING_RANGE) {
                    float t = (zNorm - GAP_END) / ROUNDING_RANGE;
                    roundMult = (float) Math.sqrt(1.0f - (1.0f - t) * (1.0f - t));
                }
            }

            if (roundMult < 0.05f) roundMult = 0;

            float finalMin = minFairwayWidth * roundMult;
            rawWidths[z] = Math.max(finalMin, targetWidth * roundMult);
        }

        // Second pass: Map to the terrain grid
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                if (map[x][z] != Terrain.TerrainType.ROUGH) continue;
                if (rawWidths[z] > 0 && Math.abs(x - centerlines[z]) < rawWidths[z] / 2f) {
                    map[x][z] = Terrain.TerrainType.FAIRWAY;
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