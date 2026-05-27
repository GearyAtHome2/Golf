package com.gearygolf.golf.terrain.tutorial;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.gearygolf.golf.terrain.ITerrainGenerator;
import com.gearygolf.golf.terrain.Terrain;
import com.gearygolf.golf.terrain.objects.Monolith;
import com.gearygolf.golf.terrain.objects.Tree;

import java.util.List;
import java.util.Random;

/**
 * Par-4 tutorial hole (~600 yards) for the third tutorial level.
 * Identical layout to TutorialGenerator2 but shorter, with a 5-degree right-to-left slope
 * across the entire centre of the map. Wind: (0, 0, -8) — 8 m/s pure headwind.
 * Teaches: keeping flight path low into a headwind, and reading slope from the shot projection.
 */
public class TutorialGenerator3 implements ITerrainGenerator {

    private static final long  INTERNAL_SEED   = 47L;
    private static final int   FAIRWAY_HALF_W  = 18;
    private static final float GREEN_RADIUS    = 18f;
    private static final float BUNKER_RADIUS   = 8f;
    private static final float TEE_ELEVATION   = 5f;
    private static final float WATER_LEVEL     = -10f;

    // 5-degree slope right→left: tan(5°) ≈ 0.0875 height units per grid unit
    private static final float SLOPE_PER_UNIT  = 0.0875f;

    private static final float[] WA = {0.55f, 1.30f, 2.20f, 0.85f, 1.60f};
    private static final float[] WF = {1.0f,  1.4f,  0.9f,  2.0f,  1.2f};
    private static final float[] WM = {1.0f,  0.6f,  0.8f,  0.4f,  0.5f};
    private static final float[] WO = {0.7f,  2.1f,  1.3f,  0.9f,  1.8f};
    private static final float   W_TOTAL = 1.0f + 0.6f + 0.8f + 0.4f + 0.5f;

    @Override
    public void generate(Terrain.TerrainType[][] map, float[][] heights,
                         List<Tree> trees, List<Monolith> monoliths,
                         Vector3 teePos, Vector3 holePos) {
        int SX = map.length;
        int SZ = map[0].length;
        float halfX = SX / 2f;
        float halfZ = SZ / 2f;

        int teeGZ   = (int)(SZ * 0.08f);
        int greenGZ = (int)(SZ * 0.82f);
        int greenGX = SX / 2;

        Random rng = new Random(INTERNAL_SEED);

        // 1. Base height field with right-to-left slope across the full map.
        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                float t = MathUtils.clamp((z - teeGZ) / (float)(greenGZ - teeGZ), 0f, 1f);
                float rise = (t < 0.4f) ? t * 0.8f : 0.32f;
                // Slope: right side (high x) is higher than left side
                float slope = (x - halfX) * SLOPE_PER_UNIT;
                heights[x][z] = 2.0f + rise + slope + noise(x, z) * 1.4f;
                map[x][z] = Terrain.TerrainType.ROUGH;
            }
        }

        // 2. Fairway corridor
        for (int z = Math.max(0, teeGZ - 3); z < Math.min(SZ, greenGZ + 5); z++) {
            for (int x = 0; x < SX; x++) {
                if (Math.abs(x - halfX) < FAIRWAY_HALF_W) {
                    map[x][z] = Terrain.TerrainType.FAIRWAY;
                }
            }
        }

        // 3. Green — flat circle (override slope so putting is fair)
        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                if (Vector2.dst(x, z, greenGX, greenGZ) < GREEN_RADIUS) {
                    map[x][z] = Terrain.TerrainType.GREEN;
                    heights[x][z] = 1.5f + noise(x, z) * 0.25f;
                }
            }
        }

        // 4. Tee box — flat elevated platform
        for (int z = Math.max(0, teeGZ - 5); z <= Math.min(SZ - 1, teeGZ + 5); z++) {
            for (int x = Math.max(0, (int)(halfX - 6)); x <= Math.min(SX - 1, (int)(halfX + 6)); x++) {
                map[x][z] = Terrain.TerrainType.TEE;
                heights[x][z] = TEE_ELEVATION;
            }
        }

        // 5. Fairway bunker — right of centre at ~55% down fairway
        int fairwayBunkerZ = teeGZ + (int)((greenGZ - teeGZ) * 0.55f);
        int fairwayBunkerX = greenGX + FAIRWAY_HALF_W + (int)BUNKER_RADIUS + 2;
        placeBunker(map, heights, fairwayBunkerX, fairwayBunkerZ, (int)BUNKER_RADIUS, SX, SZ);

        // 6. Greenside bunkers
        int bunkerOffset = (int)(GREEN_RADIUS + BUNKER_RADIUS + 2);
        placeBunker(map, heights, greenGX - bunkerOffset, greenGZ, (int)BUNKER_RADIUS, SX, SZ);
        placeBunker(map, heights, greenGX + bunkerOffset, greenGZ, (int)BUNKER_RADIUS, SX, SZ);

        // 7. Tee and hole world positions
        teePos.set(0f, TEE_ELEVATION, teeGZ - halfZ);
        holePos.set(0f, 1.5f, greenGZ - halfZ);

        // 8. Six framing trees on rough edges
        int[][] treeGrid = {
            {greenGX - 38, (int)(SZ * 0.20f)},
            {greenGX + 38, (int)(SZ * 0.25f)},
            {greenGX - 42, (int)(SZ * 0.42f)},
            {greenGX + 42, (int)(SZ * 0.48f)},
            {greenGX - 36, (int)(SZ * 0.65f)},
            {greenGX + 36, (int)(SZ * 0.70f)},
        };
        for (int[] tc : treeGrid) {
            int tx = MathUtils.clamp(tc[0], 5, SX - 6);
            int tz = MathUtils.clamp(tc[1], 5, SZ - 6);
            float worldX = tx - halfX;
            float worldZ = tz - halfZ;
            float h = heights[tx][tz];
            float trunkH = 5f + rng.nextFloat() * 3f;
            float foliageR = 2.5f + rng.nextFloat() * 1.5f;
            trees.add(new Tree(worldX, h, worldZ, trunkH, 0.4f, foliageR,
                    Tree.TreeScheme.OAK, rng, rng.nextFloat() * 360f));
        }
    }

    private void placeBunker(Terrain.TerrainType[][] map, float[][] heights,
                             int cx, int cz, int radius, int SX, int SZ) {
        float centerH = (cx >= 0 && cx < SX && cz >= 0 && cz < SZ) ? heights[cx][cz] : 2f;
        float bunkerH = Math.max(centerH - 2f, WATER_LEVEL + 0.1f);

        for (int x = Math.max(0, cx - radius); x < Math.min(SX, cx + radius + 1); x++) {
            for (int z = Math.max(0, cz - radius); z < Math.min(SZ, cz + radius + 1); z++) {
                if (Vector2.dst(x, z, cx, cz) < radius) {
                    map[x][z] = Terrain.TerrainType.SAND;
                    heights[x][z] = bunkerH;
                }
            }
        }
    }

    private float noise(float x, float z) {
        float total = 0;
        for (int i = 0; i < 5; i++) {
            float c = (x * MathUtils.cos(WA[i]) + z * MathUtils.sin(WA[i])) * (0.04f * WF[i]);
            total += MathUtils.sin(c + WO[i]) * WM[i];
        }
        return total / W_TOTAL;
    }
}
