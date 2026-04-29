package org.example.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain;
import org.example.terrain.TerrainUtils;

public class CoastlineGenerator {


    public CoastlineGenerator() {
    }

    public float applyIslandCoastline(int x, int z, float zN, float curH, Terrain.TerrainType type, float water, float o1, float o2) {
        float thresh = 0.45f + MathUtils.sin(x * 0.05f + o1) * 0.04f + MathUtils.cos(x * 0.4f + o2) * 0.01f;
        boolean playable = !TerrainUtils.isUnmodifiable(type) && type != Terrain.TerrainType.FAIRWAY;
        if (zN > thresh && playable) {
            if (type == Terrain.TerrainType.ROUGH || type == Terrain.TerrainType.DEEP_ROUGH) curH -= 20.0f;
            else if (type == Terrain.TerrainType.SAND)
                curH = MathUtils.lerp(water - 1.5f, curH, MathUtils.clamp((curH - water) / 5.0f, 0f, 1f));
        }
        return (!playable && curH < water + 1.0f) ? water + 1.0f : curH;
    }

    public void applySlopeBasedStone(Terrain.TerrainType[][] map, float[][] heights, float threshold) {
        for (int x = 0; x < map.length - 1; x++) {
            for (int z = 0; z < map[0].length - 1; z++) {
                float dx = heights[x + 1][z] - heights[x][z], dz = heights[x][z + 1] - heights[x][z];
                if (1.0f - new Vector3(-dx, 1.0f, -dz).nor().y > threshold && (map[x][z] == Terrain.TerrainType.ROUGH || map[x][z] == Terrain.TerrainType.DEEP_ROUGH))
                    map[x][z] = Terrain.TerrainType.STONE;
            }
        }
    }

    public void applyFairwayWaterBuffer(Terrain.TerrainType[][] map, float[][] heights, float water, float tileDist) {
        float dSqLimit = tileDist * tileDist;
        int r = (int) Math.ceil(tileDist);
        for (int x = 0; x < map.length; x++) {
            for (int z = 0; z < map[0].length; z++) {
                if (map[x][z] == Terrain.TerrainType.FAIRWAY) {
                    if (heights[x][z] <= water + 0.5f) {
                        map[x][z] = Terrain.TerrainType.ROUGH;
                        continue;
                    }
                    search:
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            int nx = x + dx, nz = z + dz;
                            if (nx >= 0 && nx < map.length && nz >= 0 && nz < map[0].length && heights[nx][nz] < water && (dx * dx + dz * dz) <= dSqLimit) {
                                map[x][z] = Terrain.TerrainType.ROUGH;
                                break search;
                            }
                        }
                    }
                }
            }
        }
    }
}