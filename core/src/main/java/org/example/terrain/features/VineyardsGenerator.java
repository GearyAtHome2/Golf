package org.example.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import org.example.terrain.Terrain;
import org.example.terrain.TerrainUtils;

import java.util.Random;

public class VineyardsGenerator {

    public void generateVineyards(float[][] heights, Terrain.TerrainType[][] map, Random rng,
                                  float rowWidth, float riserWidth, float teeZ) {
        int SX = heights.length;
        int SZ = heights[0].length;

        float totalCycle = rowWidth + riserWidth;
        float globalOffset = rng.nextFloat() * 100f;

        // 1. Identify "Path" tiles for proximity smoothing
        boolean[][] isPath = new boolean[SX][SZ];
        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                isPath[x][z] = (map[x][z] == Terrain.TerrainType.FAIRWAY ||
                        map[x][z] == Terrain.TerrainType.GREEN ||
                        map[x][z] == Terrain.TerrainType.TEE);
            }
        }

        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                if (TerrainUtils.isUnmodifiable(map[x][z]) || map[x][z] != Terrain.TerrainType.ROUGH) continue;
                if (z < teeZ + 30) continue;

                // 2. Calculate Distance to Fairway for Smoothing
                // If we are within 8 units of a fairway, we fade the vineyard effect out.
                float distToPath = getDistanceToPath(x, z, isPath, 8.0f);
                float fairwaySmooth = MathUtils.clamp(distToPath / 8.0f, 0f, 1f);
                fairwaySmooth = TerrainUtils.smoothstep(fairwaySmooth);

                // 3. Regional Influence (Existing dual-sine mask)
                float maskValue = MathUtils.sin(x * 0.04f) + MathUtils.sin(z * 0.08f);
                float regionInfluence = MathUtils.clamp((maskValue + 0.5f), 0, 1);

                // 4. Combine all influences
                float edgeFade = MathUtils.clamp(z / 40.0f, 0, 1) * MathUtils.clamp((SZ - z) / 40.0f, 0, 1);
                float finalInfluence = regionInfluence * edgeFade * fairwaySmooth;

                if (finalInfluence <= 0.05f) continue;

                // 5. Row Logic (Step-quantizing)
                float zPos = z + globalOffset;
                float posInCycle = zPos % totalCycle;
                float currentH = heights[x][z];
                float targetH;

                if (posInCycle < rowWidth) {
                    int stepStartZ = MathUtils.clamp((int) (z - posInCycle), 0, SZ - 1);
                    targetH = heights[x][stepStartZ];
                } else {
                    float riserProgress = (posInCycle - rowWidth) / riserWidth;
                    int currentStepZ = MathUtils.clamp((int) (z - (posInCycle - rowWidth)), 0, SZ - 1);
                    int nextStepZ = MathUtils.clamp((int) (z + (totalCycle - posInCycle)), 0, SZ - 1);

                    float hStart = heights[x][currentStepZ];
                    float hEnd = heights[x][nextStepZ];

                    float smoothedT = TerrainUtils.smoothstep(riserProgress);
                    targetH = MathUtils.lerp(hStart, hEnd, smoothedT);

                    if (riserProgress > 0.2f && riserProgress < 0.8f && finalInfluence > 0.6f) {
                        map[x][z] = Terrain.TerrainType.STONE;
                    }
                }

                heights[x][z] = MathUtils.lerp(currentH, targetH, finalInfluence);
            }
        }
    }

    private float getDistanceToPath(int x, int z, boolean[][] mask, float maxDist) {
        float minDistSq = maxDist * maxDist;
        int range = (int) maxDist + 1;
        for (int ix = x - range; ix <= x + range; ix++) {
            if (ix < 0 || ix >= mask.length) continue;
            for (int iz = z - range; iz <= z + range; iz++) {
                if (iz >= 0 && iz < mask[0].length && mask[ix][iz]) {
                    float dSq = (x - ix) * (x - ix) + (z - iz) * (z - iz);
                    if (dSq < minDistSq) minDistSq = dSq;
                }
            }
        }
        return (float) Math.sqrt(minDistSq);
    }
}