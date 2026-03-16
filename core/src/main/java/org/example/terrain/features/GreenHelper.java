package org.example.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain;

public class GreenHelper {

    /**
     * Logic for checking and applying a single tile's type based on green radius.
     * Extracted for ClassicGenerator's mapStandardFeatures loop.
     */
    public static void applySingleTileGreen(Terrain.TerrainType[][] map, int x, int z, int gX, int gZ, float seedOffset) {
        float dist = Vector3.dst(x, z, 0, gX, gZ, 0);
        float angle = MathUtils.atan2((z - gZ) + 0.00001f, (x - gX) + 0.00001f);//offset prevents discontinuity at Green Center
        float dynamicRadius = 26f + (MathUtils.sin(angle * 3 + seedOffset) * 3.1f);

        if (dist <= dynamicRadius) {
            map[x][z] = Terrain.TerrainType.GREEN;
        }
    }

    /**
     * Used for full-map generation (like your new PuttingGreenGenerator).
     * Iterates the whole map and applies the green shape.
     */
    public static void applyGreenShape(Terrain.TerrainType[][] map, int gX, int gZ, float baseRadius, float variation, float seedOffset) {
        int width = map.length;
        int depth = map[0].length;

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                float dist = Vector3.dst(x, z, 0, gX, gZ, 0);
                float angle = MathUtils.atan2(z - gZ, x - gX);
                float dynamicRadius = baseRadius + (MathUtils.sin(angle * 3 + seedOffset) * variation);

                if (dist <= dynamicRadius) {
                    map[x][z] = Terrain.TerrainType.GREEN;
                }
            }
        }
    }

    /**
     * Extracts the 4-way wave interference logic for putting breaks.
     */
    public static float calculateUndulation(int x, int z, float[] angles, float[] offsets) {
        float total = 0;
        for (int i = 0; i < 4; i++) {
            // 0.32f is the frequency constant used for "readable" breaks
            float coord = (x * MathUtils.cos(angles[i]) + z * MathUtils.sin(angles[i])) * 0.32f;
            total += MathUtils.sin(coord + offsets[i]);
        }
        return total / 4.0f;
    }
}