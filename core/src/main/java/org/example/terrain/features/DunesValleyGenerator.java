package org.example.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain;
import org.example.terrain.level.LevelData;

import java.util.Random;

/**
 * Archetype generator for DUNES_VALLEY (oasis map).
 *
 * v1 behaviour:
 *   1. Converts all ROUGH → SAND.
 *   2. Locates the natural low point in the central 60% × 40% of the map.
 *      If it already sits below water level, uses it as-is; otherwise carves
 *      a Gaussian bowl deep enough for the water level to flood it.
 *   3. Raises 1–2 Gaussian dune mounds away from the oasis.
 *   4. Marks tiles within SHORE_BAND tile-distance of water as ROUGH (oasis shore),
 *      using a multi-octave wobble so the boundary looks organic.
 *   5. Applies slope-aligned ripples to remaining SAND tiles.
 *
 *   Tree spawning is handled by ClassicGenerator.generateOasisTrees().
 */
public class DunesValleyGenerator {

    // Tuning constants — adjust freely
    private static final float RIPPLE_FREQ      = 0.45f;
    private static final float RIPPLE_AMP       = 0.25f;
    private static final float SLOPE_MIN        = 0.15f;
    private static final float SHORE_BAND       = 16.0f;  // tile-distance from water edge → ROUGH
    private static final float BOWL_RADIUS_FRAC = 0.25f;  // bowl radius as fraction of map width
    private static final float BOWL_EXTRA_DEPTH = 2.5f;   // how far below water the oasis floor sits

    public int   oasisX, oasisZ;
    public float oasisRadius;

    private final LevelData data;
    private final Random    rng;
    private final float     rippleAngle, ripplePhase;
    private final float     p1, p2, p3, p4; // shore wobble phases (4 terms → more organic)

    public DunesValleyGenerator(LevelData data) {
        this.data = data;
        this.rng  = new Random(data.getSeed() ^ 0x0A515000L);

        Vector3 wind = data.getWind();
        float len = (float) Math.sqrt(wind.x * wind.x + wind.z * wind.z);
        rippleAngle = len > 0.001f
            ? MathUtils.atan2(wind.z / len, wind.x / len) + MathUtils.HALF_PI
            : MathUtils.PI * 0.25f;

        Random phaseRng = new Random(data.getSeed() ^ 0xD00E5000L);
        ripplePhase = phaseRng.nextFloat() * MathUtils.PI2;
        p1          = phaseRng.nextFloat() * MathUtils.PI2;
        p2          = phaseRng.nextFloat() * MathUtils.PI2;
        p3          = phaseRng.nextFloat() * MathUtils.PI2;
        p4          = phaseRng.nextFloat() * MathUtils.PI2;
    }

    public void generateDunesValley(Terrain.TerrainType[][] map, float[][] heights) {
        int   SIZE_X     = map.length;
        int   SIZE_Z     = map[0].length;
        float waterLevel = data.getWaterLevel();

        // Oasis must sit in the central region, away from tee/green ends and side edges
        int xMin = (int) (SIZE_X * 0.20f);
        int xMax = (int) (SIZE_X * 0.80f);
        int zMin = (int) (SIZE_Z * 0.30f);
        int zMax = (int) (SIZE_Z * 0.70f);

        // ── 1. Find the natural low point in the central region ───────────────
        int   lowestX = SIZE_X / 2, lowestZ = (zMin + zMax) / 2;
        float lowestH = Float.MAX_VALUE;
        for (int x = xMin; x < xMax; x++) {
            for (int z = zMin; z < zMax; z++) {
                if (heights[x][z] < lowestH) {
                    lowestH = heights[x][z];
                    lowestX = x;
                    lowestZ = z;
                }
            }
        }

        // ── 2. Carve bowl only if no natural depression exists in central zone ─
        if (lowestH >= waterLevel) {
            float bowlDepth    = (lowestH - waterLevel) + BOWL_EXTRA_DEPTH;
            float bowlRadius   = SIZE_X * BOWL_RADIUS_FRAC;
            float bowlRadiusSq = bowlRadius * bowlRadius;
            for (int x = 0; x < SIZE_X; x++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    float dx = x - lowestX, dz = z - lowestZ;
                    heights[x][z] -= bowlDepth * (float) Math.exp(-(dx * dx + dz * dz) / bowlRadiusSq);
                }
            }
        }

        oasisX = lowestX;
        oasisZ = lowestZ;

        float baseRadius = 40f * (data.getDistance() / 390f);
        oasisRadius = baseRadius * (0.9f + rng.nextFloat() * 0.2f);

        // ── 3. Raise 1–2 dune mounds away from the oasis ─────────────────────
        int numDunes = 1 + rng.nextInt(2);
        for (int d = 0; d < numDunes; d++) {
            int bumpX = SIZE_X / 2, bumpZ = SIZE_Z / 2;
            float minSep = oasisRadius + 15f;
            for (int attempt = 0; attempt < 15; attempt++) {
                int cx = (int) (SIZE_X * (0.15f + rng.nextFloat() * 0.7f));
                int cz = (int) (SIZE_Z * (0.2f  + rng.nextFloat() * 0.6f));
                float ddx = cx - oasisX, ddz = cz - oasisZ;
                if (Math.sqrt(ddx * ddx + ddz * ddz) >= minSep) {
                    bumpX = cx; bumpZ = cz; break;
                }
            }
            float duneH   = 5f + rng.nextFloat() * 4f;
            float duneR   = SIZE_X * (0.07f + rng.nextFloat() * 0.05f);
            float duneRSq = duneR * duneR;
            for (int x = 0; x < SIZE_X; x++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    float dx = x - bumpX, dz = z - bumpZ;
                    heights[x][z] += duneH * (float) Math.exp(-(dx * dx + dz * dz) / duneRSq);
                }
            }
        }

        // ── 4a. Suppress stray water pockets outside the oasis zone ──────────
        // Rolling-dunes noise creates random dips that dip below water level.
        // Raise any below-water tile that is too far from the oasis centre.
        float suppressR = oasisRadius * 2.5f;
        float suppressRSq = suppressR * suppressR;
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (heights[x][z] < waterLevel) {
                    float ddx = x - oasisX, ddz = z - oasisZ;
                    if (ddx * ddx + ddz * ddz > suppressRSq) {
                        heights[x][z] = waterLevel + 0.5f;
                    }
                }
            }
        }

        // ── 4. Convert all ROUGH → SAND ───────────────────────────────────────
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (map[x][z] == Terrain.TerrainType.ROUGH) {
                    map[x][z] = Terrain.TerrainType.SAND;
                }
            }
        }

        // ── 5. Retype shore tiles as ROUGH: distance-to-water + multi-octave wobble ─
        boolean[][] isWater = new boolean[SIZE_X][SIZE_Z];
        for (int x = 0; x < SIZE_X; x++)
            for (int z = 0; z < SIZE_Z; z++)
                isWater[x][z] = heights[x][z] < waterLevel;

        int searchR = (int) SHORE_BAND + 2;
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (map[x][z] != Terrain.TerrainType.SAND) continue;

                float nearestWater = Float.MAX_VALUE;
                search:
                for (int dx = -searchR; dx <= searchR; dx++) {
                    for (int dz = -searchR; dz <= searchR; dz++) {
                        int nx = x + dx, nz = z + dz;
                        if (nx < 0 || nx >= SIZE_X || nz < 0 || nz >= SIZE_Z) continue;
                        if (isWater[nx][nz]) {
                            float d = (float) Math.sqrt(dx * dx + dz * dz);
                            if (d < nearestWater) {
                                nearestWater = d;
                                if (nearestWater <= 1f) break search;
                            }
                        }
                    }
                }

                if (nearestWater == Float.MAX_VALUE) continue;

                float wobble = (MathUtils.sin(x * 0.30f + p1) * 0.6f
                              + MathUtils.cos(z * 0.37f + p2) * 0.5f
                              + MathUtils.sin(x * 0.71f + p3) * 0.3f
                              + MathUtils.cos(z * 0.63f + p4) * 0.3f) * 0.5f;
                float threshold = SHORE_BAND + wobble;

                if (nearestWater <= threshold) {
                    map[x][z] = Terrain.TerrainType.ROUGH;
                }
            }
        }

        // ── 6. Ripple micro-relief on SAND slopes ─────────────────────────────
        float cosA = MathUtils.cos(rippleAngle);
        float sinA = MathUtils.sin(rippleAngle);
        for (int x = 1; x < SIZE_X - 1; x++) {
            for (int z = 1; z < SIZE_Z - 1; z++) {
                if (map[x][z] != Terrain.TerrainType.SAND) continue;

                float dx    = heights[x + 1][z] - heights[x - 1][z];
                float dz    = heights[x][z + 1] - heights[x][z - 1];
                float slope = (float) Math.sqrt(dx * dx + dz * dz);
                if (slope < SLOPE_MIN) continue;

                float proj   = x * cosA + z * sinA;
                float ripple = MathUtils.sin(proj * RIPPLE_FREQ + ripplePhase) * RIPPLE_AMP;
                float blend  = MathUtils.clamp((slope - SLOPE_MIN) / 1.0f, 0f, 1f);
                heights[x][z] += ripple * blend;
            }
        }
    }
}
