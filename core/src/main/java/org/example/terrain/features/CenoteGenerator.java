package org.example.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import org.example.terrain.Terrain;
import org.example.terrain.TerrainUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CenoteGenerator {
    private final Random rng;

    private static final float CENOTE_EXPONENT = 4f;
    private static final float TEE_BUFFER = 30.0f;
    private static final float GREEN_BUFFER = 30.0f;

    public CenoteGenerator(Random rng) {
        this.rng = rng;
    }

    public void generateRoughCenotes(Terrain.TerrainType[][] map, float[][] heights, float waterLevel) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        float targetDepth = waterLevel - 0.4f;
        int gridSize = 50;

        for (int gx = gridSize; gx < SIZE_X - gridSize; gx += gridSize) {
            for (int gz = gridSize; gz < SIZE_Z - gridSize; gz += gridSize) {
                int cx = gx + rng.nextInt(gridSize / 2) - gridSize / 4;
                int cz = gz + rng.nextInt(gridSize / 2) - gridSize / 4;

                if (map[cx][cz] != Terrain.TerrainType.ROUGH || TerrainUtils.isNearProtectedZone(cx, cz, map, TEE_BUFFER, GREEN_BUFFER))
                    continue;
                applyCenoteOval(map, heights, cx, cz, targetDepth);
            }
        }
    }

    private void applyCenoteOval(Terrain.TerrainType[][] map, float[][] heights, int cx, int cz, float targetDepth) {
        float angle = rng.nextFloat() * MathUtils.PI2;
        float majorR = 10f + rng.nextFloat() * 5f;
        float minorR = majorR * (0.6f + rng.nextFloat() * 0.3f);
        float cosA = MathUtils.cos(angle), sinA = MathUtils.sin(angle);
        int scan = (int) majorR + 1;

        for (int x = Math.max(0, cx - scan); x <= Math.min(map.length - 1, cx + scan); x++) {
            for (int z = Math.max(0, cz - scan); z <= Math.min(map[0].length - 1, cz + scan); z++) {
                if (TerrainUtils.isUnmodifiable(map[x][z])) continue;

                float dx = x - cx, dz = z - cz;
                float lx = dx * cosA + dz * sinA, lz = -dx * sinA + dz * cosA;
                float dNorm = (float) Math.sqrt((lx * lx) / (majorR * majorR) + (lz * lz) / (minorR * minorR));

                if (dNorm < 1.0f) {
                    float factor = (float) Math.pow(1.0f - MathUtils.clamp((dNorm - 0.5f) / 0.5f, 0, 1), CENOTE_EXPONENT);
                    heights[x][z] = MathUtils.lerp(heights[x][z], targetDepth, factor);
                    if (factor > 0.1f && map[x][z] != Terrain.TerrainType.FAIRWAY)
                        map[x][z] = Terrain.TerrainType.STONE;
                }
            }
        }
    }

    public void generatePlungeCenotes(Terrain.TerrainType[][] map, float[][] heights, float waterLevel, float maxRadius) {
        int SIZE_X = map.length, SIZE_Z = map[0].length;
        boolean[][] edgeMask = new boolean[SIZE_X][SIZE_Z];
        List<Vector2> seeds = new ArrayList<>();

        for (int x = 1; x < SIZE_X - 1; x++) {
            for (int z = 1; z < SIZE_Z - 1; z++) {
                if (map[x][z] == Terrain.TerrainType.FAIRWAY && isBorderingRough(x, z, map)) {
                    if (!TerrainUtils.isNearProtectedZone(x, z, map, TEE_BUFFER, GREEN_BUFFER)) {
                        edgeMask[x][z] = true;
                        seeds.add(new Vector2(x, z));
                    }
                }
            }
        }

        if (seeds.isEmpty()) return;
        int fissureCount = (int) (SIZE_Z / 80.0f);
        for (int i = 0; i < fissureCount; i++) {
            List<Vector2> spine = buildSpine(seeds.get(rng.nextInt(seeds.size())), edgeMask, 20 + rng.nextInt(20));
            if (spine.size() >= 5) applyFissure(map, heights, spine, waterLevel - 0.4f, maxRadius);
        }
    }

    private void applyFissure(Terrain.TerrainType[][] map, float[][] heights, List<Vector2> spine, float depth, float maxR) {
        int[] b = TerrainUtils.getSpineBounds(spine, map.length, map[0].length, (int) maxR + 1);
        for (int x = b[0]; x <= b[1]; x++) {
            for (int z = b[2]; z <= b[3]; z++) {
                if (TerrainUtils.isUnmodifiable(map[x][z])) continue;
                float dist = TerrainUtils.getDistanceAndProgress((float) x, (float) z, spine)[0];
                if (dist < maxR) {
                    float factor = (float) Math.pow(1.0f - (dist / maxR), CENOTE_EXPONENT);
                    heights[x][z] = MathUtils.lerp(heights[x][z], depth, factor);
                    if (factor > 0.4f && map[x][z] != Terrain.TerrainType.FAIRWAY)
                        map[x][z] = Terrain.TerrainType.STONE;
                }
            }
        }
    }


    private boolean isBorderingRough(int x, int z, Terrain.TerrainType[][] map) {
        return map[x + 1][z] == Terrain.TerrainType.ROUGH || map[x - 1][z] == Terrain.TerrainType.ROUGH || map[x][z + 1] == Terrain.TerrainType.ROUGH || map[x][z - 1] == Terrain.TerrainType.ROUGH;
    }

    private List<Vector2> buildSpine(Vector2 start, boolean[][] mask, int maxLen) {
        List<Vector2> spine = new ArrayList<>();
        boolean[][] v = new boolean[mask.length][mask[0].length];
        Vector2 cur = start.cpy();
        spine.add(cur);
        v[(int) cur.x][(int) cur.y] = true;
        for (int i = 0; i < maxLen; i++) {
            Vector2 next = null;
            search:
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    int nx = (int) cur.x + dx, nz = (int) cur.y + dz;
                    if (nx >= 0 && nx < mask.length && nz >= 0 && nz < mask[0].length && mask[nx][nz] && !v[nx][nz]) {
                        next = new Vector2(nx, nz);
                        break search;
                    }
                }
            if (next == null) break;
            spine.add(next);
            v[(int) next.x][(int) next.y] = true;
            cur = next;
        }
        return spine;
    }
}