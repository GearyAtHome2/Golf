package com.gearygolf.golf.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.gearygolf.golf.terrain.Terrain;
import com.gearygolf.golf.terrain.level.LevelData;

import java.util.Arrays;
import java.util.Random;

/**
 * Generates Badlands terrain features: a spider-web crack network of dry fissures
 * radiating from node points, with perpendicular micro-fissures and occasional
 * splits, plus rotated-sinusoid stone patches across the rough.
 *
 * Network topology:
 *  - Each node spawns 4-8 fissures at evenly-spaced angles.
 *  - 50 % of fissures home in on the nearest node in their quadrant;
 *    50 % travel straight to the map edge.
 *  - Every 10-20 units: 50 % chance of a short perpendicular micro-fissure.
 *  - Every 10-20 units on edge-bound fissures: 10 % chance of a 2-way split
 *    (±15-25°), each branch independently deciding whether to seek a node.
 *
 * Fissure cross-section: SAND along the spine, STONE on the flanking tiles.
 * Heights collected into a min-floor array — applied once at the end.
 */
public class BadlandsGenerator {

    private final Random rng;

    // ── Constructor fields ────────────────────────────────────────────────────
    private final int   nodeCount;
    private final float connectRadius;
    @SuppressWarnings("unused")
    private final float fissureHalfWidth; // kept for tuning reference
    private final float fissureDepth;
    private final float wobbleAmp;
    private final float wobbleFreq;

    private static final float MAIN_HALF_WIDTH  = 0.75f; // ~1-2 tile wide main crack
    private static final float MICRO_HALF_WIDTH = 0.5f;  // 1 tile wide micro-crack

    // 8 randomly-rotated sinusoids for stone patch noise
    private final float[] stoneAngles = new float[8];
    private final float[] stonePhases = new float[8];
    // Separate sinusoids for scattered sand (different seed offset so patterns don't overlap)
    private final float[] sandAngles  = new float[8];
    private final float[] sandPhases  = new float[8];

    public BadlandsGenerator(LevelData data) {
        this.rng = new Random(data.getSeed() ^ 0xBAD1A4D5L);

        nodeCount        = 2 + rng.nextInt(2);
        connectRadius    = 150f + rng.nextFloat() * 25f;
        fissureHalfWidth = 2f + rng.nextFloat() * 2f;
        fissureDepth     = 2f + rng.nextFloat() * 2f;
        wobbleAmp        = 3f + rng.nextFloat() * 4f;
        wobbleFreq       = 0.08f + rng.nextFloat() * 0.06f;

        for (int i = 0; i < 8; i++) {
            stoneAngles[i] = rng.nextFloat() * MathUtils.PI2;
            stonePhases[i] = rng.nextFloat() * 100f;
        }
        for (int i = 0; i < 8; i++) {
            sandAngles[i] = rng.nextFloat() * MathUtils.PI2;
            sandPhases[i] = rng.nextFloat() * 100f;
        }
    }

    public void apply(Terrain.TerrainType[][] map, float[][] heights) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        // ── 1. Place nodes ────────────────────────────────────────────────────
        float margin = 25f;
        float[] nodeX = new float[nodeCount];
        float[] nodeZ = new float[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            nodeX[i] = margin + rng.nextFloat() * (SIZE_X - 2 * margin);
            nodeZ[i] = margin + rng.nextFloat() * (SIZE_Z - 2 * margin);
        }

        // floor[x][z] = target height (min-wins at intersections).
        // heights[] is never written during carving so it always returns the original value.
        float[][] floor     = new float[SIZE_X][SIZE_Z];
        // isSand[x][z] = true if the spine of any fissure passes through this cell.
        // SAND wins over STONE — once marked sand, further wall-carves don't override it.
        boolean[][] isSand  = new boolean[SIZE_X][SIZE_Z];
        for (int x = 0; x < SIZE_X; x++)
            Arrays.fill(floor[x], Float.MAX_VALUE);

        // ── 2. Spawn fissures from each node ──────────────────────────────────
        for (int n = 0; n < nodeCount; n++) {
            int   fissureCount = 4 + rng.nextInt(5); // 4-8
            float angleStep    = MathUtils.PI2 / fissureCount;
            float baseAngle    = rng.nextFloat() * MathUtils.PI2;

            for (int fi = 0; fi < fissureCount; fi++) {
                float jitter = (rng.nextFloat() - 0.5f) * angleStep * 0.25f;
                float angle  = baseAngle + fi * angleStep + jitter;

                boolean connectToNode = rng.nextFloat() < 0.5f;
                int targetNode = connectToNode
                        ? findNearestNodeInDirection(nodeX, nodeZ, n, nodeX[n], nodeZ[n], angle)
                        : -1;

                traceFissure(floor, isSand, heights, nodeX, nodeZ,
                        nodeX[n], nodeZ[n], angle, targetNode,
                        /*allowSplit*/ true, /*depth*/ 0, SIZE_X, SIZE_Z);
            }
        }

        // ── 3. Apply floors: spine cells → SAND, flanking cells → STONE ──────
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (floor[x][z] == Float.MAX_VALUE) continue;
                Terrain.TerrainType t = map[x][z];
                if (t == Terrain.TerrainType.TEE
                        || t == Terrain.TerrainType.GREEN
                        || t == Terrain.TerrainType.FRINGE) continue;
                heights[x][z] = floor[x][z];
                map[x][z] = isSand[x][z] ? Terrain.TerrainType.SAND : Terrain.TerrainType.STONE;
            }
        }

        // ── 4. Scattered stone patches via rotated-sinusoid noise ─────────────
        final float STONE_THRESHOLD = 0.54f;
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                Terrain.TerrainType t = map[x][z];
                if (t != Terrain.TerrainType.ROUGH && t != Terrain.TerrainType.DEEP_ROUGH) continue;
                if (stonePatchNoise(x, z) > STONE_THRESHOLD)
                    map[x][z] = Terrain.TerrainType.STONE;
            }
        }

        // ── 5. Scattered sand patches — altitude-biased toward lower ground ───
        // Compute height range so the altitude factor is relative, not absolute.
        float minH = Float.MAX_VALUE, maxH = -Float.MAX_VALUE;
        for (int x = 0; x < SIZE_X; x++)
            for (int z = 0; z < SIZE_Z; z++) {
                if (heights[x][z] < minH) minH = heights[x][z];
                if (heights[x][z] > maxH) maxH = heights[x][z];
            }
        float heightRange = Math.max(maxH - minH, 1f);

        // At the highest ground: threshold = 0.65 (~12% coverage).
        // At the lowest ground:  threshold = 0.53 (~34% coverage).
        // The sinusoid noise distribution is centred at 0.5 with std ~0.125,
        // so this gives a soft, gradual increase rather than a hard border.
        final float SAND_HIGH_THRESHOLD = 0.65f;
        final float SAND_LOW_THRESHOLD  = 0.53f;
        // BLEND_WIDTH controls how wide the probabilistic edge zone is.
        // Cells with noise > threshold+half → always sand.
        // Cells with noise < threshold-half → never sand.
        // Cells in between → probability ramps linearly, breaking up the hard border.
        final float BLEND_WIDTH = 0.10f;
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                Terrain.TerrainType t = map[x][z];
                if (t != Terrain.TerrainType.ROUGH && t != Terrain.TerrainType.DEEP_ROUGH) continue;
                float normH     = (heights[x][z] - minH) / heightRange; // 0 = lowest, 1 = highest
                float threshold = MathUtils.lerp(SAND_LOW_THRESHOLD, SAND_HIGH_THRESHOLD, normH);
                float prob      = MathUtils.clamp((sandPatchNoise(x, z) - threshold) / BLEND_WIDTH + 0.5f, 0f, 1f);
                if (cellHash(x, z) < prob)
                    map[x][z] = Terrain.TerrainType.SAND;
            }
        }
    }

    // ── Fissure tracing ───────────────────────────────────────────────────────

    private void traceFissure(float[][] floor, boolean[][] isSand, float[][] heights,
                               float[] nodeX, float[] nodeZ,
                               float startX, float startZ,
                               float angle, int targetNode,
                               boolean allowSplit, int depth,
                               int SIZE_X, int SIZE_Z) {
        if (depth > 2) return;

        float dirX = MathUtils.cos(angle);
        float dirZ = MathUtils.sin(angle);

        float endX, endZ;
        if (targetNode >= 0) {
            endX = nodeX[targetNode];
            endZ = nodeZ[targetNode];
        } else {
            endX = startX + dirX * (SIZE_X + SIZE_Z);
            endZ = startZ + dirZ * (SIZE_X + SIZE_Z);
        }

        float maxLen   = Vector2.dst(startX, startZ, endX, endZ);
        float wobPhase = startX * 0.031f + startZ * 0.017f;

        float nextMicro = 10f + rng.nextFloat() * 10f;
        float nextSplit = 10f + rng.nextFloat() * 10f;

        for (float t = 0; t <= maxLen; t += 0.5f) {
            float wobble = wobbleAmp * 0.15f * MathUtils.sin(t * wobbleFreq + wobPhase);
            float cx = startX + dirX * t + (-dirZ) * wobble;
            float cz = startZ + dirZ * t + ( dirX) * wobble;

            if (cx < 0 || cx >= SIZE_X - 1 || cz < 0 || cz >= SIZE_Z - 1) break;

            if (targetNode >= 0
                    && Vector2.dst(cx, cz, nodeX[targetNode], nodeZ[targetNode]) < 3f) break;

            carvePoint(floor, isSand, heights, cx, cz, angle, MAIN_HALF_WIDTH, fissureDepth, SIZE_X, SIZE_Z);

            if (t >= nextMicro) {
                if (rng.nextFloat() < 0.5f) {
                    float side = rng.nextBoolean() ? MathUtils.PI / 2f : -MathUtils.PI / 2f;
                    spawnMicroFissure(floor, isSand, heights, cx, cz, angle + side, SIZE_X, SIZE_Z);
                }
                nextMicro = t + 10f + rng.nextFloat() * 10f;
            }

            if (allowSplit && targetNode < 0 && depth == 0 && t >= nextSplit) {
                if (rng.nextFloat() < 0.10f) {
                    float splitRad = (15f + rng.nextFloat() * 10f) * MathUtils.degreesToRadians;
                    float a1 = angle + splitRad;
                    float a2 = angle - splitRad;

                    int t1 = (rng.nextFloat() < 0.5f)
                            ? findNearestNodeInDirection(nodeX, nodeZ, -1, cx, cz, a1) : -1;
                    int t2 = (rng.nextFloat() < 0.5f)
                            ? findNearestNodeInDirection(nodeX, nodeZ, -1, cx, cz, a2) : -1;

                    traceFissure(floor, isSand, heights, nodeX, nodeZ, cx, cz, a1, t1, false, depth + 1, SIZE_X, SIZE_Z);
                    traceFissure(floor, isSand, heights, nodeX, nodeZ, cx, cz, a2, t2, false, depth + 1, SIZE_X, SIZE_Z);
                    break;
                }
                nextSplit = t + 10f + rng.nextFloat() * 10f;
            }
        }
    }

    private void spawnMicroFissure(float[][] floor, boolean[][] isSand, float[][] heights,
                                    float startX, float startZ, float angle,
                                    int SIZE_X, int SIZE_Z) {
        float len  = 4f + rng.nextFloat() * 6f;
        float maxD = fissureDepth * 0.4f;
        float dirX = MathUtils.cos(angle);
        float dirZ = MathUtils.sin(angle);

        for (float t = 0; t <= len; t += 0.5f) {
            float taper = 1f - (t / len);
            float cx = startX + dirX * t;
            float cz = startZ + dirZ * t;
            carvePoint(floor, isSand, heights, cx, cz, angle, MICRO_HALF_WIDTH, maxD * taper, SIZE_X, SIZE_Z);
        }
    }

    /**
     * Records the fissure cross-section into floor[] and isSand[].
     * Spine cells (wi == 0) are flagged as SAND; flanking tiles are STONE.
     * SAND is sticky — once a cell is flagged sand, a later wall-carve won't clear it.
     */
    private void carvePoint(float[][] floor, boolean[][] isSand, float[][] heights,
                             float cx, float cz, float angle,
                             float halfWidth, float depth,
                             int SIZE_X, int SIZE_Z) {
        float px = -MathUtils.sin(angle);
        float pz =  MathUtils.cos(angle);

        int wSteps = Math.max(1, (int)(halfWidth / 0.5f));
        for (int wi = -wSteps; wi <= wSteps; wi++) {
            float w = wi * 0.5f;
            if (Math.abs(w) > halfWidth) continue;

            int gx = MathUtils.round(cx + px * w);
            int gz = MathUtils.round(cz + pz * w);
            if (gx < 1 || gx >= SIZE_X - 1 || gz < 1 || gz >= SIZE_Z - 1) continue;

            float target = heights[gx][gz] - depth;
            if (target < floor[gx][gz]) floor[gx][gz] = target;

            // Spine (wi == 0) → SAND floor. Flanks → STONE. SAND wins if set by any pass.
            if (wi == 0) isSand[gx][gz] = true;
        }
    }

    private int findNearestNodeInDirection(float[] nodeX, float[] nodeZ,
                                            int excludeIdx,
                                            float fromX, float fromZ, float angle) {
        float dirX   = MathUtils.cos(angle);
        float dirZ   = MathUtils.sin(angle);
        float cosLim = MathUtils.cos(MathUtils.PI / 4f);
        float minDist = connectRadius;
        int   best   = -1;

        for (int i = 0; i < nodeX.length; i++) {
            if (i == excludeIdx) continue;
            float dx   = nodeX[i] - fromX;
            float dz   = nodeZ[i] - fromZ;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            if (dist < 5f || dist > connectRadius) continue;
            float dot  = (dx / dist) * dirX + (dz / dist) * dirZ;
            if (dot >= cosLim && dist < minDist) {
                minDist = dist;
                best    = i;
            }
        }
        return best;
    }

    private float stonePatchNoise(int x, int z) {
        float sum = 0f;
        for (int i = 0; i < 8; i++) {
            float proj = x * MathUtils.cos(stoneAngles[i]) + z * MathUtils.sin(stoneAngles[i]);
            sum += MathUtils.sin(proj * 0.055f + stonePhases[i]);
        }
        return sum / 8f * 0.5f + 0.5f;
    }

    /** Deterministic per-cell value in [0,1] — used for probabilistic edge dithering. */
    private float cellHash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    private float sandPatchNoise(int x, int z) {
        float sum = 0f;
        for (int i = 0; i < 8; i++) {
            float proj = x * MathUtils.cos(sandAngles[i]) + z * MathUtils.sin(sandAngles[i]);
            sum += MathUtils.sin(proj * 0.045f + sandPhases[i]); // slightly lower freq → larger patches
        }
        return sum / 8f * 0.5f + 0.5f;
    }
}
