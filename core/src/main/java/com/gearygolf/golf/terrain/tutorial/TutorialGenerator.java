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
 * Hardcoded links par-3 tutorial hole (~185 yards).
 * Fully isolated from ClassicGenerator — changes to the main generator never affect this layout.
 *
 * Layout: wide flat fairway, gentle downslope from tee to green, two greenside bunkers,
 * five sparse trees on rough edges. No water. Gentle crosswind supplied via LevelData.
 */
public class TutorialGenerator implements ITerrainGenerator {

    private static final long  INTERNAL_SEED    = 42L;
    private static final int   FAIRWAY_HALF_W   = 12;   // half-width of 24-cell corridor
    private static final float GREEN_RADIUS     = 20f;
    private static final float BUNKER_RADIUS    = 8f;
    private static final float TEE_ELEVATION    = 3f;
    private static final float WATER_LEVEL      = -1f;  // Terrain hardcodes -1 for non-ClassicGenerator

    // Five hardcoded waves for deterministic multi-wave terrain noise
    private static final float[] WA = {0.30f, 1.10f, 2.00f, 0.70f, 1.80f}; // angles
    private static final float[] WF = {1.0f,  1.5f,  0.8f,  2.1f,  1.3f};  // freq multipliers
    private static final float[] WM = {1.0f,  0.6f,  0.8f,  0.4f,  0.5f};  // amplitudes
    private static final float[] WO = {0.0f,  1.2f,  0.5f,  2.1f,  0.9f};  // phase offsets
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

        // 1. Base height field with a gentle downslope from tee to green.
        //    Formula: 2.5 - 0.8*t + noise*1.5  →  range [1.0, 4.0] at tee, [0.2, 3.2] at green.
        //    Minimum is 0.2f, safely above the -1f water level on all cells.
        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                float t = MathUtils.clamp((z - teeGZ) / (float)(greenGZ - teeGZ), 0f, 1f);
                heights[x][z] = 2.5f - 0.8f * t + noise(x, z) * 1.5f;
                map[x][z] = Terrain.TerrainType.ROUGH;
            }
        }

        // 2. Fairway corridor — 24 cells wide, same height as surrounding rough.
        //    No height change here: the terrain type alone distinguishes it visually,
        //    and keeping the same heights avoids any raised-platform look.
        for (int z = Math.max(0, teeGZ - 3); z < Math.min(SZ, greenGZ + 5); z++) {
            for (int x = 0; x < SX; x++) {
                if (Math.abs(x - halfX) < FAIRWAY_HALF_W) {
                    map[x][z] = Terrain.TerrainType.FAIRWAY;
                }
            }
        }

        // 3. Green — flat circle at hole position, slightly lower than fairway for a gentle
        //    'bowl into the green' effect when the ball runs on.
        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                if (Vector2.dst(x, z, greenGX, greenGZ) < GREEN_RADIUS) {
                    map[x][z] = Terrain.TerrainType.GREEN;
                    heights[x][z] = 1.4f + noise(x, z) * 0.3f;
                }
            }
        }

        // 4. Tee box — elevated 3f platform so the tee stands clear of the surrounding rough.
        for (int z = Math.max(0, teeGZ - 5); z <= Math.min(SZ - 1, teeGZ + 5); z++) {
            for (int x = Math.max(0, (int)(halfX - 6)); x <= Math.min(SX - 1, (int)(halfX + 6)); x++) {
                map[x][z] = Terrain.TerrainType.TEE;
                heights[x][z] = TEE_ELEVATION;
            }
        }

        // 5. Greenside bunkers — offset so they sit just outside the green edge.
        //    Heights are depressed from surrounding terrain but clamped above water level.
        int bunkerOffset = (int)(GREEN_RADIUS + BUNKER_RADIUS + 2);  // 30 cells, no green overlap
        placeBunker(map, heights, greenGX - bunkerOffset, greenGZ, (int) BUNKER_RADIUS, SX, SZ);
        placeBunker(map, heights, greenGX + bunkerOffset, greenGZ, (int) BUNKER_RADIUS, SX, SZ);

        // 6. Set tee and hole world positions (y corrected to surface height by Terrain after generate)
        teePos.set(0f, TEE_ELEVATION, teeGZ - halfZ);
        holePos.set(0f, 1.4f, greenGZ - halfZ);

        // 7. Five sparse trees on rough edges, well clear of the fairway corridor
        int[][] treeGrid = {
            {greenGX - 36, (int)(SZ * 0.25f)},
            {greenGX + 36, (int)(SZ * 0.30f)},
            {greenGX - 40, (int)(SZ * 0.50f)},
            {greenGX + 40, (int)(SZ * 0.55f)},
            {greenGX - 34, (int)(SZ * 0.70f)},
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
        // Sample the average height around the bunker center to anchor its depth.
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

    /** Normalized multi-wave noise in [-1, 1]. */
    private float noise(float x, float z) {
        float total = 0;
        for (int i = 0; i < 5; i++) {
            float c = (x * MathUtils.cos(WA[i]) + z * MathUtils.sin(WA[i])) * (0.04f * WF[i]);
            total += MathUtils.sin(c + WO[i]) * WM[i];
        }
        return total / W_TOTAL;
    }
}
