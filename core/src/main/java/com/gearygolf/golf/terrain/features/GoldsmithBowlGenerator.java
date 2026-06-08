package com.gearygolf.golf.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.gearygolf.golf.terrain.Terrain;
import com.gearygolf.golf.terrain.TerrainUtils;
import com.gearygolf.golf.terrain.level.LevelData;

import java.util.Random;

/**
 * Archetype generator for GOLDSMITH_BOWL.
 *
 * Terrain topology (radial from green centre):
 *   Zone 1 — green platform   (d < innerR):  flat at greenH
 *   Zone 2 — ascending wall   (innerR..rimR): smoothstep ramp up to rimH
 *   Zone 3 — outer descent    (rimR..outerR): smoothstep ramp down to teeH
 *   Zone 4 — tee flats        (d > outerR):  clamp to teeH
 *
 * Both wall zones use the same smoothstep curve, producing zero slope at the
 * rim peak on both sides (no cliff edge).
 *
 * ── Tuning ────────────────────────────────────────────────────────────────────
 * All min/max constants at the top of this class are the primary dial for
 * adjusting the bowl shape.  Seed randomisation distributes uniformly between
 * each MIN and MAX pair.
 */
public class GoldsmithBowlGenerator {

    // ── Bowl shape tuning ──────────────────────────────────────────────────────

    /** Height of the rim above greenH (world units). */
    private static final float RIM_HEIGHT_MIN    = 8f;
    private static final float RIM_HEIGHT_MAX    = 14f;

    /** Flat green platform radius (cells from green centre). */
    private static final float INNER_R_MIN       = 18f;
    private static final float INNER_R_MAX       = 30f;

    /** Inner wall width as a fraction of SIZE_Z (drives how steep the inner slope is). */
    private static final float WALL_WIDTH_MIN    = 0.14f;
    private static final float WALL_WIDTH_MAX    = 0.23f;

    /** Outer slope width = inner wall width × this multiplier.
     *  Values > 1 keep the outer descent angle comparable to the inner ascent
     *  despite the larger height drop (rim → tee vs. green → rim). */
    private static final float OUTER_MULT_MIN    = 1.5f;
    private static final float OUTER_MULT_MAX    = 2.0f;

    // ── Fairway coverage tuning ────────────────────────────────────────────────

    /** Fairway is painted inside this fraction of rimR (50–70 % of bowl radius).
     *  Beyond this fraction the bowl interior becomes rough before the rim crest. */
    private static final float FAIRWAY_FRAC_MIN  = 0.50f;
    private static final float FAIRWAY_FRAC_MAX  = 0.70f;

    // ── Noise tuning ──────────────────────────────────────────────────────────

    /** Noise is damped to this fraction of full amplitude on the green platform. */
    private static final float NOISE_GREEN_DAMP  = 0.04f;
    /** Fine-octave frequency multiplier relative to data.getHillFrequency(). */
    private static final float NOISE_FINE_MULT   = 2.5f;
    /** Mix between coarse (0.65) and fine (0.35) octaves. */
    private static final float NOISE_COARSE_MIX  = 0.65f;

    // ── Seed-stable parameters ─────────────────────────────────────────────────

    private final float rimH;         // absolute rim height
    private final float innerR;       // green platform radius (cells)
    private final float fairwayFrac;  // fraction of rimR that is painted FAIRWAY
    private final float nOff1x, nOff1z, nOff2x, nOff2z;

    private final LevelData    data;
    private final SimplexNoise noiseGen;

    public GoldsmithBowlGenerator(LevelData data) {
        this.data = data;
        Random rng = new Random(data.getSeed() ^ 0xC4B1D053L);

        noiseGen    = new SimplexNoise(rng.nextLong());
        rimH        = data.getGreenHeight() + lerp(RIM_HEIGHT_MIN,   RIM_HEIGHT_MAX,   rng.nextFloat());
        innerR      = lerp(INNER_R_MIN,  INNER_R_MAX,  rng.nextFloat());
        fairwayFrac = lerp(FAIRWAY_FRAC_MIN, FAIRWAY_FRAC_MAX, rng.nextFloat());

        nOff1x = rng.nextFloat() * 500f;
        nOff1z = rng.nextFloat() * 500f;
        nOff2x = rng.nextFloat() * 500f;
        nOff2z = rng.nextFloat() * 500f;
    }

    // ── Main generation call ───────────────────────────────────────────────────

    public void generateBowl(Terrain.TerrainType[][] map, float[][] heights,
                             int greenCenterX, int greenCenterZ) {
        int   SIZE_X = map.length;
        int   SIZE_Z = map[0].length;
        float gH     = data.getGreenHeight();
        float teeH   = data.getTeeHeight();

        // Size-dependent radii use a separate seed (SIZE_Z is not known at construct time).
        Random sRng      = new Random(data.getSeed() ^ 0x50DE512EL);
        float  wallWidth = SIZE_Z * lerp(WALL_WIDTH_MIN, WALL_WIDTH_MAX, sRng.nextFloat());
        float  rimR      = innerR + wallWidth;
        float  outerR    = rimR   + wallWidth * lerp(OUTER_MULT_MIN, OUTER_MULT_MAX, sRng.nextFloat());

        float fairwayR   = rimR * fairwayFrac;
        float fairwayRSq = fairwayR * fairwayR;

        float freq     = data.getHillFrequency();
        float noiseAmp = data.getUndulation() * data.getMaxHeight() * 2.5f;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                float dx    = x - greenCenterX;
                float dz    = z - greenCenterZ;
                float d     = (float) Math.sqrt(dx * dx + dz * dz);
                float zNorm = z / (float) (SIZE_Z - 1);

                float target = bowlProfile(d, gH, teeH, rimH, innerR, rimR, outerR);

                // hillRamp matches ClassicGenerator.generateHeightMap() exactly:
                // noise is zero for z < 12% of the map (tee zone), then blends in
                // over the next 10%.  This means by the time the tee-flatten pass
                // runs below, it is lerping teeSafety against teeSafety — truly flat.
                float hillRamp = MathUtils.clamp((zNorm - 0.12f) / 0.1f, 0f, 1f);
                hillRamp = TerrainUtils.smoothstep(hillRamp);

                float nf = MathUtils.clamp((d - innerR) / Math.max(outerR - innerR, 1f), 0f, 1f);
                nf = nf * nf;  // quadratic fade — quietest on green, full on outer slopes

                float coarse = noiseGen.noise(x * freq                   + nOff1x, z * freq                   + nOff1z);
                float fine   = noiseGen.noise(x * freq * NOISE_FINE_MULT  + nOff2x, z * freq * NOISE_FINE_MULT  + nOff2z);
                float noiseH = (coarse * NOISE_COARSE_MIX + fine * (1f - NOISE_COARSE_MIX))
                             * noiseAmp * MathUtils.lerp(NOISE_GREEN_DAMP, 1f, nf)
                             * hillRamp;

                heights[x][z] = target + noiseH;
            }
        }

        // Paint bowl interior up to fairwayFrac * rimR as FAIRWAY.
        // Beyond that (fairwayR..rimR) the inner wall is rough — adds visual variety
        // and makes the bowl edge feel like a natural hazard boundary.
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (map[x][z] != Terrain.TerrainType.ROUGH) continue;
                float dx = x - greenCenterX;
                float dz = z - greenCenterZ;
                if (dx * dx + dz * dz < fairwayRSq) {
                    map[x][z] = Terrain.TerrainType.FAIRWAY;
                }
            }
        }

        data.setWaterLevel(-8.0f);

        // Replicate the tee-flatten pass from ClassicGenerator.generateHeightMap()
        // (the final lerp at line 203-204 of that method).  Every archetype gets this
        // for free because generateHeightMap runs first — but our bowl generator runs
        // in applyPostProcessing and overwrites those heights, so we must reapply it.
        float teeSafety = teeH + 0.2f;
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                float zNorm = z / (float) (SIZE_Z - 1);
                float teeT  = MathUtils.clamp(zNorm / 0.15f, 0f, 1f);
                heights[x][z] = MathUtils.lerp(teeSafety, heights[x][z], TerrainUtils.smoothstep(teeT));
            }
        }
    }

    // ── Radial bowl profile ────────────────────────────────────────────────────

    private static float bowlProfile(float d,
                                     float greenH, float teeH, float rimH,
                                     float innerR, float rimR, float outerR) {
        if (d <= innerR) {
            return greenH;
        }
        if (d <= rimR) {
            float t = smoothstep((d - innerR) / (rimR - innerR));
            return MathUtils.lerp(greenH, rimH, t);
        }
        if (d <= outerR) {
            float t = smoothstep((d - rimR) / (outerR - rimR));
            return MathUtils.lerp(rimH, teeH, t);
        }
        return teeH;
    }

    /** Cubic ease-in-out: 3t² − 2t³, clamped to [0,1]. */
    private static float smoothstep(float t) {
        t = MathUtils.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
