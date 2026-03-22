package org.example.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain;

import java.util.List;
import java.util.Random;

public class BunkerGenerator {

    public enum BunkerType {
        BEAN,       // Multi-lobe organic shape
        POT,        // Small, circular, steep walls
        ASYMMETRIC  // Steeper near green, shallower away
    }

    private final Random rng;
    private static final String TAG = "BUNKER_GEN";

    private float greensideBunkerRatio = 0.4f;

    // Shape Parameters
    private static final float WALL_RELATIVE_WIDTH = 0.28f;
    private static final float LIP_STEEPNESS = 4.0f;
    private static final float LOBE_SMOOTH_K = 0.45f;
    private static final float LOBE_SPACING_FACTOR = 0.9f;

    // Placement Parameters
    private static final float MIN_RAD = 7.0f;
    private static final float MAX_RAD = 11.0f;
    private static final float MIN_BUF = 3.5f;
    private static final float MAX_BUF = 6.0f;
    private static final float WATER_SAFETY_MARGIN = 0.1f;

    public BunkerGenerator(Random rng) {
        this.rng = rng;
    }

    public void generateBunkers(Terrain.TerrainType[][] map, float[][] heights, int count, float depth, int gX, int gZ, Vector3 teePos, List<BunkerRecord> records, float waterLevel) {
        int greensideCount = Math.round(count * greensideBunkerRatio);
        BunkerType[] types = BunkerType.values();

        for (int i = 0; i < count; i++) {
            boolean greensideTarget = (i == 0) || (i < greensideCount);

            BunkerType type = types[rng.nextInt(types.length)];

            float radius = MIN_RAD + rng.nextFloat() * (MAX_RAD - MIN_RAD);
            float currentBuffer = MIN_BUF + rng.nextFloat() * (MAX_BUF - MIN_BUF);

            // For Asymmetric, we increase the footprint slightly to account for the "long tail" shallow side
            float sizeMultiplier = (type == BunkerType.ASYMMETRIC) ? 1.3f : 1.0f;
            float footprint = ((radius * (1.0f + LOBE_SPACING_FACTOR)) + currentBuffer) * sizeMultiplier;

            float depthVariation = 0.8f + (rng.nextFloat() * 0.4f);
            float localDepth = depth * depthVariation;

            Vector2 pos = findBunkerPlacement(map, heights, gX, gZ, teePos, greensideTarget, records, radius, currentBuffer, footprint, localDepth, waterLevel);

            if (pos != null) {
                carveBunkerShape(type, map, heights, pos.x, pos.y, radius, localDepth, gX, gZ, currentBuffer, waterLevel);
                records.add(new BunkerRecord(pos.x, pos.y, footprint));
            }
        }
    }

    private void carveBunkerShape(BunkerType type, Terrain.TerrainType[][] map, float[][] heights, float cx, float cz, float radius, float depth, int gX, int gZ, float buffer, float waterLevel) {
        Vector2 toGreen = new Vector2(gX - cx, gZ - cz).nor();
        Vector2 tangent = new Vector2(-toGreen.y, toGreen.x);

        Lobe[] lobes = new Lobe[] {
                new Lobe(new Vector2(cx, cz), radius),
                new Lobe(new Vector2(cx, cz).add(tangent.cpy().scl(radius * LOBE_SPACING_FACTOR)), radius * 0.85f),
                new Lobe(new Vector2(cx, cz).add(tangent.cpy().scl(-radius * LOBE_SPACING_FACTOR)), radius * 0.85f)
        };

        // Increase rLimit for asymmetric to ensure the shallow tail isn't cut off
        float limitMult = (type == BunkerType.ASYMMETRIC) ? 5.5f : 4.0f;
        int rLimit = (int) (radius * limitMult);

        for (int x = (int)cx - rLimit; x <= (int)cx + rLimit; x++) {
            for (int z = (int)cz - rLimit; z <= (int)cz + rLimit; z++) {
                if (x < 0 || x >= map.length || z < 0 || z >= map[0].length) continue;
                if (map[x][z] == Terrain.TerrainType.GREEN || map[x][z] == Terrain.TerrainType.TEE) continue;
                if (isTooCloseToGreen(map, x, z, 1.2f)) continue;

                float minDistance = calculateShapeDistance(type, x, z, lobes);

                if (minDistance < 1.0f) {
                    map[x][z] = Terrain.TerrainType.SAND;

                    float currentSteepness = LIP_STEEPNESS;
                    float currentWallWidth = WALL_RELATIVE_WIDTH;

                    if (type == BunkerType.ASYMMETRIC) {
                        Vector2 toPixel = new Vector2(x - cx, z - cz).nor();
                        // 1.0 = looking at green, -1.0 = looking away
                        float dot = toPixel.dot(toGreen);

                        // Map dot [-1, 1] to a width multiplier.
                        // Green side (1.0) gets ~0.15 width (steep)
                        // Away side (-1.0) gets ~0.8 width (very shallow tail)
                        currentWallWidth = MathUtils.lerp(0.55f, 0.15f, (dot + 1f) / 2f);

                        // Slightly increase steepness on the green side for that "plugged" look
                        currentSteepness = MathUtils.lerp(2.0f, 6.0f, (dot + 1f) / 2f);
                    }

                    float slopeHeight = applyCustomSteepWallHeight(minDistance, depth, currentSteepness, currentWallWidth);
                    float targetHeight = heights[x][z] - slopeHeight;
                    heights[x][z] = Math.max(targetHeight, waterLevel + WATER_SAFETY_MARGIN);
                }
            }
        }
    }

    private float calculateShapeDistance(BunkerType type, int x, int z, Lobe[] lobes) {
        switch (type) {
            case BEAN:
            case ASYMMETRIC:
                float beanDist = 1.0f;
                for (Lobe l : lobes) {
                    float d = Vector2.dst(x, z, l.pos.x, l.pos.y) / l.rad;
                    beanDist = smoothMin(beanDist, d, LOBE_SMOOTH_K);
                }
                return beanDist;
            case POT:
                return Vector2.dst(x, z, lobes[0].pos.x, lobes[0].pos.y) / lobes[0].rad;
            default:
                return Vector2.dst(x, z, lobes[0].pos.x, lobes[0].pos.y) / lobes[0].rad;
        }
    }

    private float applyCustomSteepWallHeight(float d, float maxDepth, float steepness, float wallWidth) {
        float intensity = 1.0f - d;

        if (intensity >= wallWidth) {
            return maxDepth;
        } else {
            float t = intensity / wallWidth;
            float curve = 1.0f - (1.0f / (1.0f + steepness * t));
            float normalizationFactor = 1.0f - (1.0f / (1.0f + steepness));
            return maxDepth * (curve / normalizationFactor);
        }
    }

    private Vector2 findBunkerPlacement(Terrain.TerrainType[][] map, float[][] heights, int gX, int gZ, Vector3 teePos, boolean greenside, List<BunkerRecord> records, float rad, float buf, float footprint, float depth, float waterLevel) {
        if (greenside) return findGreensideSpot(map, heights, gX, gZ, teePos, records, rad, buf, footprint, depth, waterLevel);

        int SX = map.length, SZ = map[0].length;
        for (int attempt = 0; attempt < 40; attempt++) {
            int tx = 20 + rng.nextInt(SX - 40);
            int tz = (int) (SZ * 0.25f + rng.nextFloat() * SZ * 0.45f);

            if (heights[tx][tz] <= waterLevel + WATER_SAFETY_MARGIN) continue;

            if (map[tx][tz] == Terrain.TerrainType.ROUGH && isNearFairway(map, tx, tz)) {
                if (!isOverlapping(tx, tz, records, footprint)) return new Vector2(tx, tz);
            }
        }
        return null;
    }

    private Vector2 findGreensideSpot(Terrain.TerrainType[][] map, float[][] heights, int gX, int gZ, Vector3 teePos, List<BunkerRecord> records, float rad, float buf, float footprint, float depth, float waterLevel) {
        float angleToTee = MathUtils.atan2(teePos.z - gZ, teePos.x - gX);

        for (int attempt = 0; attempt < 80; attempt++) {
            float angle = angleToTee + (float)rng.nextGaussian() * (MathUtils.PI / 3f);
            Vector2 dir = new Vector2(MathUtils.cos(angle), MathUtils.sin(angle));

            Vector2 edgePoint = null;
            for (float d = 0; d < 60; d += 1.0f) {
                int cx = (int)(gX + dir.x * d);
                int cz = (int)(gZ + dir.y * d);
                if (cx < 0 || cx >= map.length || cz < 0 || cz >= map[0].length) break;
                if (map[cx][cz] != Terrain.TerrainType.GREEN) {
                    edgePoint = new Vector2(cx, cz);
                    break;
                }
            }

            if (edgePoint != null) {
                Vector2 bunkerCenter = edgePoint.cpy().add(dir.cpy().scl(rad + buf));
                int tx = (int)bunkerCenter.x;
                int tz = (int)bunkerCenter.y;

                if (tx < 15 || tx >= map.length - 15 || tz < 15 || tz >= map[0].length - 15) continue;
                if (map[tx][tz] == Terrain.TerrainType.GREEN) continue;

                if (isInsideGreenFringe(map, tx, tz, rad + 1.0f)) continue;
                if (heights[tx][tz] <= waterLevel + WATER_SAFETY_MARGIN) continue;
                if (isOverlapping(tx, tz, records, footprint)) continue;

                return new Vector2(tx, tz);
            }
        }
        return null;
    }

    private boolean isInsideGreenFringe(Terrain.TerrainType[][] map, int cx, int cz, float rad) {
        int r = (int)Math.ceil(rad);
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (x < 0 || x >= map.length || z < 0 || z >= map[0].length) continue;
                if (map[x][z] == Terrain.TerrainType.GREEN && Vector2.dst(cx, cz, x, z) < rad) return true;
            }
        }
        return false;
    }

    private boolean isOverlapping(float x, float z, List<BunkerRecord> records, float currentFootprint) {
        for (BunkerRecord br : records) {
            float dist = Vector2.dst(x, z, br.x(), br.z());
            if (dist < (currentFootprint + br.radius() + 2.0f)) return true;
        }
        return false;
    }

    private boolean isTooCloseToGreen(Terrain.TerrainType[][] map, int x, int z, float buffer) {
        int b = (int) Math.ceil(buffer);
        for (int dx = -b; dx <= b; dx++) {
            for (int dz = -b; dz <= b; dz++) {
                int nx = x + dx;
                int nz = z + dz;
                if (nx >= 0 && nx < map.length && nz >= 0 && nz < map[0].length) {
                    if (map[nx][nz] == Terrain.TerrainType.GREEN) {
                        if (Vector2.dst(x, z, nx, nz) < buffer) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isNearFairway(Terrain.TerrainType[][] map, int tx, int tz) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int nx = MathUtils.clamp(tx + dx, 0, map.length - 1);
                int nz = MathUtils.clamp(tz + dz, 0, map[0].length - 1);
                if (map[nx][nz] == Terrain.TerrainType.FAIRWAY) return true;
            }
        }
        return false;
    }

    private float smoothMin(float a, float b, float k) {
        float h = MathUtils.clamp(0.5f + 0.5f * (b - a) / k, 0.0f, 1.0f);
        return MathUtils.lerp(b, a, h) - k * h * (1.0f - h);
    }

    public float getGreensideBunkerRatio() { return greensideBunkerRatio; }
    public void setGreensideBunkerRatio(float greensideBunkerRatio) { this.greensideBunkerRatio = greensideBunkerRatio; }

    private record Lobe(Vector2 pos, float rad) {}
    public static record BunkerRecord(float x, float z, float radius) {}
}