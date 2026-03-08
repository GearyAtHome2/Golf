package org.example.terrain;

import com.badlogic.gdx.math.Vector2;

import java.util.List;

public class TerrainUtils {

    public static boolean isUnmodifiable(Terrain.TerrainType type) {
        return type == Terrain.TerrainType.TEE || type == Terrain.TerrainType.GREEN;
    }

    public static boolean isNearProtectedZone(int cx, int cz, Terrain.TerrainType[][] map, float teeBuffer, float greenBuffer) {
        int maxR = (int) Math.max(teeBuffer, greenBuffer);
        for (int x = cx - maxR; x <= cx + maxR; x++) {
            for (int z = cz - maxR; z <= cz + maxR; z++) {
                if (x >= 0 && x < map.length && z >= 0 && z < map[0].length) {
                    float dstSq = (x - cx) * (x - cx) + (z - cz) * (z - cz);
                    if (map[x][z] == Terrain.TerrainType.TEE && dstSq <= teeBuffer * teeBuffer) return true;
                    if (map[x][z] == Terrain.TerrainType.GREEN && dstSq <= greenBuffer * greenBuffer) return true;
                }
            }
        }
        return false;
    }

    public static boolean isInsideProtectedZone(int cx, int cz, float checkRadius, Terrain.TerrainType[][] map) {
        int r = (int) Math.ceil(checkRadius);
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (x >= 0 && x < map.length && z >= 0 && z < map[0].length && (x - cx) * (x - cx) + (z - cz) * (z - cz) <= checkRadius * checkRadius) {
                    if (isUnmodifiable(map[x][z])) return true;
                }
            }
        }
        return false;
    }

    public static float[] getDistanceAndProgress(float px, float pz, List<Vector2> path) {
        float minDistSq = Float.MAX_VALUE;
        float progress = 0;
        int segments = path.size() - 1;
        for (int i = 0; i < segments; i++) {
            Vector2 a = path.get(i);
            Vector2 b = path.get(i + 1);
            float dx = b.x - a.x, dz = b.y - a.y;
            float lenSq = dx * dx + dz * dz;
            if (lenSq == 0) continue;
            float t = Math.max(0, Math.min(1, ((px - a.x) * dx + (pz - a.y) * dz) / lenSq));
            float cx = a.x + t * dx, cz = a.y + t * dz;
            float dSq = (px - cx) * (px - cx) + (pz - cz) * (pz - cz);
            if (dSq < minDistSq) {
                minDistSq = dSq;
                progress = (i + t) / (float) segments;
            }
        }
        return new float[]{(float) Math.sqrt(minDistSq), progress};
    }

    public static int[] getSpineBounds(List<Vector2> spine, int maxX, int maxZ, int pad) {
        int minX = maxX, resMaxX = 0, minZ = maxZ, resMaxZ = 0;
        for (Vector2 p : spine) {
            minX = Math.min(minX, (int) p.x);
            resMaxX = Math.max(resMaxX, (int) p.x);
            minZ = Math.min(minZ, (int) p.y);
            resMaxZ = Math.max(resMaxZ, (int) p.y);
        }
        return new int[]{
                Math.max(0, minX - pad), Math.min(maxX - 1, resMaxX + pad),
                Math.max(0, minZ - pad), Math.min(maxZ - 1, resMaxZ + pad)
        };
    }
}