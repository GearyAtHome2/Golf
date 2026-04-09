package org.example.terrain.features;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain;
import org.example.terrain.level.LevelData;

import java.util.Random;

public class ForestEdgeGenerator {

    private final LevelData data;
    private final Random rng;
    private final float seedOffset;

    public ForestEdgeGenerator(LevelData data) {
        this.data = data;
        this.rng = new Random(data.getSeed() ^ 0xF012E57L);
        this.seedOffset = rng.nextFloat() * 1000f;
    }

    public void generate(Terrain.TerrainType[][] map, float[][] heights, Vector3 teePos, Vector3 holePos) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        Vector2 foundTee = null;
        Vector2 foundGreen = null;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (map[x][z] == Terrain.TerrainType.TEE) {
                    foundTee = new Vector2(x, z);
                } else if (map[x][z] == Terrain.TerrainType.GREEN) {
                    foundGreen = new Vector2(x, z);
                }
            }
        }

        if (foundTee == null || foundGreen == null) return;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (isNonOverrideable(map[x][z])) continue;
                map[x][z] = Terrain.TerrainType.ROUGH;
            }
        }

        Vector2 dir = new Vector2(foundGreen).sub(foundTee).nor();
        float shiftAmount = (rng.nextFloat() * 20f) - 10f;
        Vector2 shift = new Vector2(dir).scl(shiftAmount);

        float midX = ((foundTee.x + foundGreen.x) / 2f) + shift.x;
        float midZ = ((foundTee.y + foundGreen.y) / 2f) + shift.y;

        float dist = foundTee.dst(foundGreen);
        float baseRadius = ((dist / 2f) + 5f) * 0.75f;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (isNonOverrideable(map[x][z])) continue;

                float dx = x - midX;
                float dz = z - midZ;
                float currentDistSq = dx * dx + dz * dz;
                float currentDist = (float) Math.sqrt(currentDistSq);

                // Calculate angle for radial wobble
                float angle = (float) Math.atan2(dz, dx);

                // Overlapping sine waves for "organic" edge wobble
                float wobble = (float) (
                        Math.sin(angle * 3 + seedOffset) * 5.0 +
                                Math.sin(angle * 7 - seedOffset * 0.5) * 3.0 +
                                Math.sin(angle * 13 + seedOffset * 2) * 1.5
                );

                float dynamicRadius = baseRadius + wobble;

                if (currentDist < dynamicRadius) {
                    map[x][z] = Terrain.TerrainType.FAIRWAY;
                }

                if (foundTee.dst(x, z) < 6f) map[x][z] = Terrain.TerrainType.FAIRWAY;
                if (foundGreen.dst(x, z) < 8f) map[x][z] = Terrain.TerrainType.FAIRWAY;
            }
        }
    }

    private boolean isNonOverrideable(Terrain.TerrainType type) {
        return type == Terrain.TerrainType.TEE || type == Terrain.TerrainType.GREEN;
    }
}