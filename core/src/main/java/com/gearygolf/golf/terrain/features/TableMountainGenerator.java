package com.gearygolf.golf.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.gearygolf.golf.terrain.Terrain;
import com.gearygolf.golf.terrain.level.LevelData;

import java.util.Random;

/**
 * Applies table-top mountain shaping to an in-progress ClassicGenerator map.
 * Called from ClassicGenerator.applyPostProcessing — all standard pipeline steps
 * (stone detection, deep rough, bunkers, trees, green undulation) run afterwards
 * through the normal ClassicGenerator path.
 *
 * Layout: Tee (low) → rough flats → steep cliff walls → flat plateau (FAIRWAY)
 *         → steep cliff walls → rough flats → green (low)
 */
public class TableMountainGenerator {

    private final LevelData data;
    private final Random rng;

    // ── Seeded mountain parameters ────────────────────────────────────────────
    private final float plateauH;        // plateau top height: 45–55
    private final float baseH;           // base ground height: 1–5
    private final float wallWidth;       // cliff slope width in cells: 12–16
    private final float mtnCenterDelta;  // lateral offset from 42% of SIZE_Z: ±50 cells
    private final float mtnHalfLen;      // half the mountain depth: 60 ± 20%  (48–72 cells)
    private final float parabolaStrength;// max height drop at cliff edges vs centre: 3–9 units

    // Low-frequency wall-edge wobble (per column X)
    private final float wobAmp;
    private final float wobFront1, wobFront2;
    private final float wobPhiFront1, wobPhiFront2, wobPhiBack1, wobPhiBack2;

    // High-frequency cliff-edge detail
    private final float wobHighFreq, wobHighAmp, wobHighPhiFront, wobHighPhiBack;

    // Flat-area undulation — 4 stacked sinusoids
    private final float[] flatFreqs = new float[4];
    private final float[] flatAmps  = new float[4];
    private final float[] flatPhiX  = new float[4];
    private final float[] flatPhiZ  = new float[4];

    // Plateau undulation — 3 stacked sinusoids (smaller amplitude)
    private final float[] platFreqs = new float[3];
    private final float[] platAmps  = new float[3];
    private final float[] platPhiX  = new float[3];
    private final float[] platPhiZ  = new float[3];

    // Green-area undulation (same algorithm as ClassicGenerator)
    private final float[] greenWaveAngles  = new float[4];
    private final float[] greenWaveOffsets = new float[4];
    private final float[] greenWaveFreqs   = new float[4];
    private final float   greenTiltDx, greenTiltDz;

    // Fairway apron shape seed (independent of the green shape)
    private final float apronSeedOff;

    public TableMountainGenerator(LevelData data) {
        this.data = data;
        this.rng  = new Random(data.getSeed());

        plateauH         = 45f + rng.nextFloat() * 10f;
        baseH            = 1f  + rng.nextFloat() * 4f;
        wallWidth        = 12f + rng.nextFloat() * 4f;
        mtnCenterDelta   = (rng.nextFloat() * 2f - 1f) * 50f;
        mtnHalfLen       = 70f * (0.8f + rng.nextFloat() * 0.4f);  // 48–72 cells
        parabolaStrength = 3f + rng.nextFloat() * 6f;

        wobAmp       = 8f    + rng.nextFloat() * 4f;
        wobFront1    = 0.04f + rng.nextFloat() * 0.03f;
        wobFront2    = 0.07f + rng.nextFloat() * 0.04f;
        wobPhiFront1 = rng.nextFloat() * MathUtils.PI2;
        wobPhiFront2 = rng.nextFloat() * MathUtils.PI2;
        wobPhiBack1  = rng.nextFloat() * MathUtils.PI2;
        wobPhiBack2  = rng.nextFloat() * MathUtils.PI2;

        float flatTotalAmp = 0.8f + rng.nextFloat() * 0.6f;
        for (int i = 0; i < 4; i++) {
            flatFreqs[i] = 0.018f + rng.nextFloat() * 0.025f;
            flatAmps[i]  = flatTotalAmp / 4f;
            flatPhiX[i]  = rng.nextFloat() * MathUtils.PI2;
            flatPhiZ[i]  = rng.nextFloat() * MathUtils.PI2;
        }

        float platTotalAmp = 0.6f + rng.nextFloat() * 0.7f;
        for (int i = 0; i < 3; i++) {
            platFreqs[i] = 0.022f + rng.nextFloat() * 0.022f;
            platAmps[i]  = platTotalAmp / 3f;
            platPhiX[i]  = rng.nextFloat() * MathUtils.PI2;
            platPhiZ[i]  = rng.nextFloat() * MathUtils.PI2;
        }

        // consume the rng slot that was greenXOffset in the old standalone generator
        rng.nextFloat();

        for (int i = 0; i < 4; i++) {
            greenWaveAngles[i]  = rng.nextFloat() * MathUtils.PI2;
            greenWaveOffsets[i] = rng.nextFloat() * 100f;
            greenWaveFreqs[i]   = 0.32f * (1f + (rng.nextFloat() - 0.5f) * 0.1f);
        }
        float tiltAngle = rng.nextFloat() * MathUtils.PI2;
        float tiltMag   = 0.006f + rng.nextFloat() * 0.009f;
        greenTiltDx = MathUtils.cos(tiltAngle) * tiltMag;
        greenTiltDz = MathUtils.sin(tiltAngle) * tiltMag;

        wobHighFreq     = 0.18f + rng.nextFloat() * 0.10f;
        wobHighAmp      = 2.0f  + rng.nextFloat() * 2.0f;
        wobHighPhiFront = rng.nextFloat() * MathUtils.PI2;
        wobHighPhiBack  = rng.nextFloat() * MathUtils.PI2;

        apronSeedOff = rng.nextFloat() * MathUtils.PI2;
    }

    /**
     * Called from ClassicGenerator.applyPostProcessing.
     * Overrides the height map with the mountain shape, then sets terrain types
     * (plateau → FAIRWAY, everything else → ROUGH, preserving TEE/GREEN/FRINGE).
     * All subsequent ClassicGenerator pipeline steps (stone, deep rough, bunkers,
     * trees) then run normally on the corrected map.
     */
    public void apply(Terrain.TerrainType[][] map, float[][] heights, int gX, int gZ) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        float mtnCenter = SIZE_Z * 0.4f + mtnCenterDelta;
        float mtnStart  = mtnCenter - mtnHalfLen;
        float mtnEnd    = mtnCenter + mtnHalfLen;
        float greenInfluenceR = data.getGreenRadius() * 4.5f;

        // Pre-compute per-column wall offsets
        float[] wF = new float[SIZE_X];
        float[] wB = new float[SIZE_X];
        for (int x = 0; x < SIZE_X; x++) {
            wF[x] = wobAmp * (MathUtils.sin(x * wobFront1 + wobPhiFront1) * 0.6f
                            + MathUtils.sin(x * wobFront2 + wobPhiFront2) * 0.4f)
                    + wobHighAmp * MathUtils.sin(x * wobHighFreq + wobHighPhiFront);
            wB[x] = wobAmp * (MathUtils.sin(x * wobFront1 + wobPhiBack1) * 0.6f
                            + MathUtils.sin(x * wobFront2 + wobPhiBack2) * 0.4f)
                    + wobHighAmp * MathUtils.sin(x * wobHighFreq + wobHighPhiBack);
        }

        // ── Height map ────────────────────────────────────────────────────────
        for (int x = 0; x < SIZE_X; x++) {
            float frontFoot = mtnStart + wF[x];
            float frontTop  = frontFoot + wallWidth;
            float backTop   = mtnEnd + wB[x] - wallWidth;
            float backFoot  = mtnEnd + wB[x];

            for (int z = 0; z < SIZE_Z; z++) {
                float flat = flatNoise(x, z);
                float plat = plateauNoise(x, z);
                float h;

                if (z <= frontFoot) {
                    h = baseH + flat;
                } else if (z <= frontTop) {
                    float wallT = (z - frontFoot) / wallWidth;
                    h = MathUtils.lerp(baseH + flat, plateauH + plat - parabolaStrength, smoothstep(wallT));
                } else if (z <= backTop) {
                    float plateauCentre = (frontTop + backTop) * 0.5f;
                    float halfW = (backTop - frontTop) * 0.5f;
                    float t = (halfW > 0f) ? Math.abs(z - plateauCentre) / halfW : 0f;
                    h = plateauH + plat - parabolaStrength * t * t;
                } else if (z <= backFoot) {
                    float wallT = (z - backTop) / wallWidth;
                    h = MathUtils.lerp(plateauH + plat - parabolaStrength, baseH + flat, smoothstep(wallT));
                } else {
                    h = baseH + flat;
                }

                // Smooth tee approach to flat baseH
                float zNorm = z / (float)(SIZE_Z - 1);
                h = MathUtils.lerp(baseH, h, smoothstep(MathUtils.clamp(zNorm / 0.12f, 0f, 1f)));

                // Green-area undulation (replicates ClassicGenerator's green wave pass)
                float distGreen = Vector2.dst(x, z, gX, gZ);
                float gMask = (float) Math.pow(1f - MathUtils.clamp(distGreen / greenInfluenceR, 0f, 1f), 4f);
                float tilt  = (x - gX) * greenTiltDx + (z - gZ) * greenTiltDz;
                h += (GreenHelper.calculateUndulation(x, z, greenWaveAngles, greenWaveOffsets, greenWaveFreqs) + tilt) * gMask;

                heights[x][z] = h;
            }
        }

        // ── Terrain types: plateau → FAIRWAY; clear stray winding fairway ────
        float greenRadius = data.getGreenRadius();
        for (int x = 0; x < SIZE_X; x++) {
            float frontTop = mtnStart + wF[x] + wallWidth;
            float backTop  = mtnEnd + wB[x] - wallWidth;

            for (int z = 0; z < SIZE_Z; z++) {
                Terrain.TerrainType t = map[x][z];
                if (t == Terrain.TerrainType.TEE
                        || t == Terrain.TerrainType.GREEN
                        || t == Terrain.TerrainType.FRINGE) continue;

                if (z > frontTop && z < backTop) {
                    float platFrac = Math.min(z - frontTop, backTop - z) / 15f;
                    map[x][z] = (platFrac > 0.5f) ? Terrain.TerrainType.FAIRWAY : Terrain.TerrainType.ROUGH;
                } else {
                    map[x][z] = Terrain.TerrainType.ROUGH;
                }
            }
        }

        // Dense DEEP_ROUGH at the mountain base for tree cover.
        // Height filter keeps this off the walls and plateau — only base-level cells.
        float baseAltCeiling = baseH + (plateauH - baseH) * 0.25f;
        float baseRoughDepth = 35f;
        for (int x = 0; x < SIZE_X; x++) {
            float frontFoot = mtnStart + wF[x];
            float backFoot  = mtnEnd   + wB[x];
            for (int z = 0; z < SIZE_Z; z++) {
                if (map[x][z] != Terrain.TerrainType.ROUGH) continue;
                if (heights[x][z] > baseAltCeiling) continue;
                float distToFront = Math.abs(z - frontFoot);
                float distToBack  = Math.abs(z - backFoot);
                if (Math.min(distToFront, distToBack) < baseRoughDepth) {
                    map[x][z] = Terrain.TerrainType.DEEP_ROUGH;
                }
            }
        }

        // Organic fairway apron around the green
        float greenSeedOff = (float)(data.getSeed() & 0xFFFFF);
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                Terrain.TerrainType t = map[x][z];
                if (t == Terrain.TerrainType.GREEN || t == Terrain.TerrainType.FRINGE
                        || t == Terrain.TerrainType.TEE) continue;
                float d     = Vector2.dst(x, z, gX, gZ);
                float angle = MathUtils.atan2((z - gZ) + 0.00001f, (x - gX) + 0.00001f);
                float dynR  = greenRadius * 1.55f
                        + MathUtils.sin(angle * 4 + apronSeedOff) * (greenRadius * 0.28f)
                        + MathUtils.sin(angle * 7 + apronSeedOff * 1.3f) * (greenRadius * 0.10f);
                if (d < dynR) map[x][z] = Terrain.TerrainType.FAIRWAY;
            }
        }
    }

    // ── Noise helpers ─────────────────────────────────────────────────────────

    private float flatNoise(int x, int z) {
        float s = 0f;
        for (int i = 0; i < 4; i++)
            s += flatAmps[i] * (MathUtils.sin(x * flatFreqs[i] + flatPhiX[i])
                              + MathUtils.sin(z * flatFreqs[i] + flatPhiZ[i]));
        return s * 0.5f;
    }

    private float plateauNoise(int x, int z) {
        float s = 0f;
        for (int i = 0; i < 3; i++)
            s += platAmps[i] * (MathUtils.sin(x * platFreqs[i] + platPhiX[i])
                              + MathUtils.sin(z * platFreqs[i] + platPhiZ[i]));
        return s * 0.5f;
    }

    private static float smoothstep(float t) {
        t = MathUtils.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
