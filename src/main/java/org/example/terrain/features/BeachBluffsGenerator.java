package org.example.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.ClassicGenerator;
import org.example.terrain.Terrain;
import org.example.terrain.TerrainUtils;
import org.example.terrain.level.LevelData;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BeachBluffsGenerator {
    private final LevelData data;
    private final Random rng;
    private final float[] waveAngles, waveFreqs, waveAmps, waveOffsets;

    public BeachBluffsGenerator(LevelData data, Random rng,
                                float[] waveAngles, float[] waveFreqs, float[] waveAmps, float[] waveOffsets) {
        this.data = data;
        this.rng = rng;
        this.waveAngles = waveAngles;
        this.waveFreqs = waveFreqs;
        this.waveAmps = waveAmps;
        this.waveOffsets = waveOffsets;
    }

    /* --- BLUFFS & BEACHES --- */

    public void generateBeachBLuffs(Terrain.TerrainType[][] map, float[][] heights, int gX, int gZ, float water) {
        int SX = map.length, SZ = map[0].length;
        List<Vector3> points = new ArrayList<>();
        points.add(new Vector3(SX / 2f, data.getTeeHeight(), SZ * 0.05f));
        points.add(new Vector3(gX, data.getGreenHeight(), gZ));

        int attempts = 0;
        while (points.size() < 5 && attempts++ < 300) {
            float z = MathUtils.lerp(SZ * 0.2f, SZ * 0.8f, rng.nextFloat());
            float x = (SX * 0.15f) + rng.nextFloat() * (SX * 0.7f);
            if (isSpaceForBluff(x, z, points, 65.0f)) {
                float t = MathUtils.clamp((z - (SZ * 0.05f)) / (SZ * 0.87f), 0, 1);
                points.add(new Vector3(x, MathUtils.lerp(data.getTeeHeight(), data.getGreenHeight(), t) + (rng.nextFloat() - 0.5f) * 5f, z));
            }
        }

        float beachH = water + 4.0f;
        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                if (TerrainUtils.isUnmodifiable(map[x][z])) continue;
                float h = water - 1.0f; Terrain.TerrainType type = Terrain.TerrainType.ROUGH;
                float wobble = generateMultiWaveNoise(x * 0.8f, z * 0.8f, 0.15f) * 5.0f;

                for (Vector3 b : points) {
                    float d = Vector2.dst(x, z, b.x, b.z);
                    float fR = (12f + (b.y * 0.3f)) + (wobble * 0.5f), rR = fR + 15f + wobble, bR = rR + 17f;

                    if (d < rR) {
                        float curH = MathUtils.lerp(beachH, b.y, 1.0f - (float) Math.pow(d / rR, 14.0f));
                        if (curH > h) { h = curH; type = (d < fR) ? Terrain.TerrainType.FAIRWAY : Terrain.TerrainType.ROUGH; }
                    } else if (d < bR) {
                        float curH = MathUtils.lerp(beachH, water - 1.0f, (d - rR) / (bR - rR));
                        if (curH > h) { h = curH; type = (curH > water) ? Terrain.TerrainType.SAND : Terrain.TerrainType.ROUGH; }
                    }
                }
                heights[x][z] = h; map[x][z] = type;
            }
        }
    }

    private boolean isSpaceForBluff(float x, float z, List<Vector3> points, float min) {
        for (Vector3 b : points) if (Vector2.dst(x, z, b.x, b.z) < min) return false;
        return true;
    }


    public float generateMultiWaveNoise(float x, float z, float bF) {
        float total = 0, totalA = 0;
        for (int i = 0; i < 10; i++) {
            float coord = (x * MathUtils.cos(waveAngles[i]) + z * MathUtils.sin(waveAngles[i])) * (bF * waveFreqs[i]);
            total += MathUtils.sin(coord + waveOffsets[i]) * waveAmps[i]; totalA += waveAmps[i];
        }
        return total / totalA;
    }
}