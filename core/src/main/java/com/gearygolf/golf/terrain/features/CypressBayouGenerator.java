package com.gearygolf.golf.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.gearygolf.golf.terrain.Terrain;
import com.gearygolf.golf.terrain.TerrainUtils;
import com.gearygolf.golf.terrain.level.LevelData;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Generates the Cypress Bayou par-3 layout.
 * <p>
 * Terrain profile (all heights relative to water = 0):
 * Fairway / fringe pads  : +1.4 – +1.6  (1–2 units above water)
 * Rough                  : ROUGH_H ± ROUGH_VAR, centred at water → ~50% submerged
 * MUD                    : applied post-pass by ClassicGenerator for rough ≤ mudHeight (0.6)
 * <p>
 * Fairway shape: quadratic-bezier path from tee to green with a random lateral
 * control-point offset.  Width varies smoothly along the path using two slow
 * sine waves seeded from the RNG, averaging WIDTH_SCALE × the base corridor
 * width with ±WIDTH_VAR variation.  No water gaps — the organic narrowing and
 * widening replaces them with a continuously flowing, natural-looking fairway.
 */
public class CypressBayouGenerator {

    // ── Geometry constants ────────────────────────────────────────────────────
    /**
     * Half-width of the FAIRWAY corridor in terrain cells.
     */
    private static final float FAIRWAY_HW = 11f;
    /**
     * Half-width of the FRINGE band beyond the fairway edge.
     */
    private static final float FRINGE_HW = 15f;
    /**
     * Fairway surface height above water.
     */
    private static final float FAIRWAY_H = 1.5f;
    /**
     * Fringe surface height above water (slight step down toward water).
     */
    private static final float FRINGE_H = 1.3f;
    /**
     * Base rough height above water.
     * Set to 0 so that the noise distribution is centred exactly at water level,
     * giving ~50% of rough cells below water (submerged) and ~50% above.
     */
    private static final float ROUGH_H = 0.0f;
    /**
     * Half-amplitude of rough noise (±).
     * With ROUGH_H=0 and mudHeight=0.6 in the spec, the distribution is roughly:
     * ~50% below water   (height < 0)
     * ~27% MUD           (0 ≤ height ≤ 0.6)
     * ~23% stays ROUGH   (height > 0.6)  ← hosts trees
     */
    private static final float ROUGH_VAR = 1.1f;
    /**
     * Average width multiplier applied to both FAIRWAY_HW and FRINGE_HW.
     */
    private static final float WIDTH_SCALE = 1.15f;
    /**
     * Amplitude of the sinusoidal width variation as a fraction of the scaled base.
     * 0.25 means the corridor width swings ±25 % around its average.
     */
    private static final float WIDTH_VAR = 0.25f;
    /**
     * Max lateral offset of the bezier midpoint (in cells).
     */
    private static final float CURVE_SWING = 80f;
    /**
     * Bezier approximation resolution (higher = more accurate path-distance).
     */
    private static final int BEZIER_STEPS = 64;

    // ── State ─────────────────────────────────────────────────────────────────
    private final LevelData data;
    private final Random rng;
    private final float[] waveAngles, waveFreqs, waveAmps, waveOffsets;
    /**
     * Grid cells that fall inside a water gap — trees must not spawn here.
     */
    private final Set<Long> gapCells = new HashSet<>();

    /**
     * Packed (x, z) key used for gapCells lookup.
     */
    public static long packCell(int x, int z) {
        return ((long) x << 20) | (z & 0xFFFFF);
    }

    public Set<Long> getGapCells() {
        return gapCells;
    }

    public CypressBayouGenerator(LevelData data, Random rng,
                                 float[] waveAngles, float[] waveFreqs,
                                 float[] waveAmps, float[] waveOffsets) {
        this.data = data;
        this.rng = rng;
        this.waveAngles = waveAngles;
        this.waveFreqs = waveFreqs;
        this.waveAmps = waveAmps;
        this.waveOffsets = waveOffsets;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * @param gX green centre X in grid coordinates
     * @param gZ green centre Z in grid coordinates
     */
    public void generateCypressBayou(Terrain.TerrainType[][] map, float[][] heights,
                                     int gX, int gZ, float water) {
        gapCells.clear();
        int SX = map.length, SZ = map[0].length;

        // mapStandardFeatures runs before us and marks the standard-path border cells as
        // FRINGE, which isUnmodifiable() treats as locked.  That would freeze the corridor
        // width to the standard path and prevent any variation.  Reset every non-TEE,
        // non-GREEN cell to ROUGH so the bayou generator has full control of the terrain.
        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                Terrain.TerrainType t = map[x][z];
                if (t != Terrain.TerrainType.TEE && t != Terrain.TerrainType.GREEN) {
                    map[x][z] = Terrain.TerrainType.ROUGH;
                }
            }
        }

        // Tee grid position — matches the hardcoded position used by finalizePositionsAndTrees
        float ax = SX / 2f, az = SZ * 0.05f;
        // Green grid position
        float cx = gX, cz = gZ;

        // Perpendicular offset for the control point — creates the dogleg curve
        float len = (float) Math.sqrt((cx - ax) * (cx - ax) + (cz - az) * (cz - az));
        float px = -(cz - az) / len;  // unit perpendicular
        float pz = (cx - ax) / len;
        // Guarantee a minimum bend of 50% CURVE_SWING for a visible dogleg
        float sign = rng.nextBoolean() ? 1f : -1f;
        float swing = sign * (0.5f + rng.nextFloat() * 0.5f) * CURVE_SWING;
        float bx = (ax + cx) / 2f + px * swing;
        float bz = (az + cz) / 2f + pz * swing;

        // Precompute bezier sample points
        float[] sampleX = new float[BEZIER_STEPS + 1];
        float[] sampleZ = new float[BEZIER_STEPS + 1];
        for (int i = 0; i <= BEZIER_STEPS; i++) {
            float t = i / (float) BEZIER_STEPS;
            sampleX[i] = bezier(ax, bx, cx, t);
            sampleZ[i] = bezier(az, bz, cz, t);
        }

        // Two slow sine waves give a smooth, organic width variation along the path.
        // Frequencies chosen so there is roughly 0.5 and 1.1 full cycles over 0..1,
        // producing gentle widening/narrowing that never looks mechanical.
        float phaseA = rng.nextFloat() * MathUtils.PI2;
        float phaseB = rng.nextFloat() * MathUtils.PI2;
        float phaseC = rng.nextFloat() * MathUtils.PI2;
        float phaseD = rng.nextFloat() * MathUtils.PI2;

        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                if (TerrainUtils.isUnmodifiable(map[x][z])) continue;

                // Nearest bezier sample → gives distance from path and progress
                float bestDist2 = Float.MAX_VALUE;
                int bestIdx = 0;
                for (int i = 0; i <= BEZIER_STEPS; i++) {
                    float ddx = x - sampleX[i], ddz = z - sampleZ[i];
                    float d2 = ddx * ddx + ddz * ddz;
                    if (d2 < bestDist2) {
                        bestDist2 = d2;
                        bestIdx = i;
                    }
                }
                float dist = (float) Math.sqrt(bestDist2);
                float progress = bestIdx / (float) BEZIER_STEPS;

                // Height noise
                float noise = multiWaveNoise(x * 0.28f, z * 0.28f, 0.13f) * ROUGH_VAR;

                // Smooth width variation: average WIDTH_SCALE × base, ±WIDTH_VAR
                float widthMod = 0.17f * MathUtils.sin(progress * 6.5f + phaseA)
                        + 0.1f * MathUtils.sin(progress * 12.0f + phaseB)
                        + 0.1f * MathUtils.sin(progress * 15.0f + phaseC)
                        + 0.1f * MathUtils.sin(progress * 18.0f + phaseD);
                float scale = WIDTH_SCALE * (1f + widthMod);
                float fwHW = FAIRWAY_HW * scale;
                float frHW = FRINGE_HW * scale;

                if (dist <= frHW) {
                    if (dist <= fwHW) {
                        heights[x][z] = water + FAIRWAY_H + noise * 0.10f;
                        map[x][z] = Terrain.TerrainType.FAIRWAY;
                    } else {
                        float blend = (dist - fwHW) / (frHW - fwHW);
                        heights[x][z] = water + MathUtils.lerp(FAIRWAY_H, FRINGE_H, blend) + noise * 0.15f;
                        map[x][z] = Terrain.TerrainType.FRINGE;
                    }
                } else {
                    heights[x][z] = water + ROUGH_H + noise;
                    map[x][z] = Terrain.TerrainType.ROUGH;
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private float bezier(float a, float b, float c, float t) {
        float ti = 1f - t;
        return ti * ti * a + 2f * ti * t * b + t * t * c;
    }

    private float multiWaveNoise(float x, float z, float baseFreq) {
        float total = 0f, totalAmp = 0f;
        for (int i = 0; i < 10; i++) {
            float coord = (x * MathUtils.cos(waveAngles[i]) + z * MathUtils.sin(waveAngles[i]))
                    * (baseFreq * waveFreqs[i]);
            total += MathUtils.sin(coord + waveOffsets[i]) * waveAmps[i];
            totalAmp += waveAmps[i];
        }
        return total / totalAmp;
    }
}
