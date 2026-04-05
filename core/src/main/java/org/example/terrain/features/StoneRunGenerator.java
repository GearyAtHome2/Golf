package org.example.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import org.example.terrain.Terrain;
import org.example.terrain.level.LevelData;

import java.util.Random;

public class StoneRunGenerator {
    private final LevelData data;
    private final Random rng;

    public StoneRunGenerator(LevelData data) {
        this.data = data;
        this.rng = new Random(data.getSeed() ^ 0x0A515000L);
    }

    public void generateStoneRun(Terrain.TerrainType[][] map, float[][] heights) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        float baseCentreDip = SIZE_Z * 0.08f;
        float wave1Freq = 0.05f;
        float wave1Amp = 4.0f;
        float wave2Freq = 0.12f;
        float wave2Amp = 1.5f;

        float seedOffset = rng.nextFloat() * 1000f;

        for (int x = 0; x < SIZE_X; x++) {
            float waveShift = MathUtils.sin((x + seedOffset) * wave1Freq) * wave1Amp
                    + MathUtils.cos((x + seedOffset) * wave2Freq) * wave2Amp;
            float currentCentreDip = baseCentreDip + waveShift;

            for (int z = 0; z < SIZE_Z; z++) {
                float distFromCentre = Math.abs(z - currentCentreDip);
                float moatRadius = 20f;
                float flatFloorRadius = 3f;
                float targetWaterDepth = -3f;

                if (distFromCentre <= moatRadius) {
                    float strength = 0;
                    if (distFromCentre <= flatFloorRadius) {
                        strength = 1.0f;
                    } else {
                        float range = moatRadius - flatFloorRadius;
                        float localDist = distFromCentre - flatFloorRadius;
                        strength = 1.0f - (localDist / range);
                        strength = strength * strength * (3 - 2 * strength);
                    }
                    heights[x][z] = MathUtils.lerp(heights[x][z], targetWaterDepth, strength);
                }
            }
        }

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (map[x][z] != Terrain.TerrainType.GREEN && map[x][z] != Terrain.TerrainType.TEE) {
                    map[x][z] = Terrain.TerrainType.STONE;
                }
            }
        }

        float sharpThreshold = 0.045f;
        float spreadChance = 0.75f;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (map[x][z] == Terrain.TerrainType.GREEN || map[x][z] == Terrain.TerrainType.TEE) continue;

                int w = Math.max(0, x - 1);
                int e = Math.min(SIZE_X - 1, x + 1);
                int s = Math.max(0, z - 1);
                int n = Math.min(SIZE_Z - 1, z + 1);

                float centerHeight = heights[x][z];
                float neighborAvg = (heights[w][z] + heights[e][z] + heights[x][s] + heights[x][n]) / 4.0f;
                float curvature = Math.abs(centerHeight - neighborAvg);

                if (curvature > sharpThreshold) {
                    map[x][z] = Terrain.TerrainType.ROUGH;
                    int[][] neighbors = {{x-1, z}, {x+1, z}, {x, z-1}, {x, z+1}};
                    for (int[] pos : neighbors) {
                        int nx = pos[0], nz = pos[1];
                        if (nx >= 0 && nx < SIZE_X && nz >= 0 && nz < SIZE_Z) {
                            if (map[nx][nz] == Terrain.TerrainType.STONE && rng.nextFloat() < spreadChance) {
                                map[nx][nz] = Terrain.TerrainType.ROUGH;
                            }
                        }
                    }
                }
            }
        }

        int padCount = MathUtils.floor((SIZE_Z - 150) * 0.03f);
        padCount = MathUtils.clamp(padCount, 1, 10);
        Array<Vector2> placedCenters = new Array<>();

        for (int i = 0; i < padCount * 10; i++) {
            if (placedCenters.size >= padCount) break;

            int padX = (int) (SIZE_X * (0.35f + rng.nextFloat() * 0.3f));
            int padZ = (int) (SIZE_Z * (0.25f + rng.nextFloat() * 0.5f));

            boolean tooClose = false;
            for (Vector2 center : placedCenters) {
                if (Vector2.dst(padX, padZ, center.x, center.y) < 60f) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) continue;

            int zOffset = 5;
            if (padZ + zOffset < SIZE_Z && padZ - zOffset >= 0) {
                float diff = Math.abs(heights[padX][padZ + zOffset] - heights[padX][padZ - zOffset]);
                if (diff < 1.8f) continue;
            }

            placedCenters.add(new Vector2(padX, padZ));

            Terrain.TerrainType centerType = Terrain.TerrainType.FAIRWAY;
            Terrain.TerrainType edgeType = Terrain.TerrainType.ROUGH;

            if (rng.nextFloat() < 0.30f) {
                centerType = getRandomPadType();
                edgeType = getRandomPadType();
            }

            applyIntegratedPad(map, heights, padX, padZ, centerType, edgeType);
        }

        applyStoneNoise(map, heights);
    }

    private void applyStoneNoise(Terrain.TerrainType[][] map, float[][] heights) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        // Intensity of the noise (+/- 0.2 units)
        float noiseAmount = 0.08f;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (map[x][z] == Terrain.TerrainType.STONE) {
                    // Pure tile-based jitter
                    heights[x][z] += (rng.nextFloat() - 0.5f) * noiseAmount;
                }
            }
        }
    }

    private Terrain.TerrainType getRandomPadType() {
        int r = rng.nextInt(3);
        if (r == 0) return Terrain.TerrainType.FAIRWAY;
        if (r == 1) return Terrain.TerrainType.ROUGH;
        return Terrain.TerrainType.SAND;
    }

    private void applyIntegratedPad(Terrain.TerrainType[][] map, float[][] heights, int cx, int cz, Terrain.TerrainType centerType, Terrain.TerrainType edgeType) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;
        float targetHeight = heights[cx][cz];

        float baseRadius = 10f + (rng.nextFloat() * 10f);
        float stretchX = 0.85f + rng.nextFloat() * 0.3f;
        float stretchZ = 0.85f + rng.nextFloat() * 0.3f;

        float noiseSeverity = rng.nextFloat();
        float wobbleFreq1 = 1.5f + rng.nextFloat() * 2.0f;
        float wobbleAmp1 = (1.5f + rng.nextFloat() * 3.0f) * noiseSeverity;
        float wobbleFreq2 = 4.0f + rng.nextFloat() * 4.0f;
        float wobbleAmp2 = (0.5f + rng.nextFloat() * 1.5f) * noiseSeverity;

        float backFreq1 = 0.25f + rng.nextFloat() * 0.15f;
        float backAmp1 = 0.5f + rng.nextFloat() * 1.0f;
        float backFreq2 = 0.6f + rng.nextFloat() * 0.4f;
        float backAmp2 = 0.2f + rng.nextFloat() * 0.5f;
        float backEdgeTilt = (rng.nextFloat() - 0.5f) * 0.15f;

        float maxRadius = (baseRadius + wobbleAmp1 + wobbleAmp2) * Math.max(stretchX, stretchZ);
        float influenceRadius = maxRadius * 2.5f;
        float shelfFalloff = 5f;
        float edgeSmoothZone = 3.0f;

        for (int x = (int)(cx - influenceRadius); x <= cx + influenceRadius; x++) {
            for (int z = (int)(cz - influenceRadius); z <= cz + influenceRadius; z++) {
                if (x < 0 || x >= SIZE_X || z < 0 || z >= SIZE_Z) continue;

                float dx = (x - cx) / stretchX;
                float dz = (z - cz) / stretchZ;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                if (dist > influenceRadius) continue;

                float angle = MathUtils.atan2(dz, dx);
                float noise = (MathUtils.sin(angle * wobbleFreq1) * wobbleAmp1)
                        + (MathUtils.sin(angle * wobbleFreq2) * wobbleAmp2);
                float dynamicRadius = baseRadius + noise;

                float xLocal = x - cx;
                float zEdgePos = cz + (MathUtils.sin(xLocal * backFreq1) * backAmp1)
                        + (MathUtils.sin(xLocal * backFreq2) * backAmp2)
                        + (xLocal * backEdgeTilt);

                float zDiff = z - zEdgePos;
                float blendFactor = MathUtils.clamp((zDiff + edgeSmoothZone) / (edgeSmoothZone * 2f), 0f, 1f);

                float heightStrength = 1.0f - (dist / influenceRadius);
                heightStrength = heightStrength * heightStrength;

                float uphillHeight = MathUtils.lerp(heights[x][z], Math.max(targetHeight, heights[x][z] - 2f), heightStrength * 0.5f);

                float downhillHeight = heights[x][z];
                if (dist < dynamicRadius + shelfFalloff) {
                    float shelfStrength = dist <= dynamicRadius ? 1.0f : 1.0f - ((dist - dynamicRadius) / shelfFalloff);
                    shelfStrength = shelfStrength * shelfStrength * (3 - 2 * shelfStrength);
                    downhillHeight = MathUtils.lerp(heights[x][z], targetHeight, shelfStrength);
                }

                heights[x][z] = MathUtils.lerp(downhillHeight, uphillHeight, blendFactor);

                if (z <= zEdgePos && dist < dynamicRadius) {
                    if (map[x][z] != Terrain.TerrainType.GREEN && map[x][z] != Terrain.TerrainType.TEE) {
                        if (dist < dynamicRadius - 0.75f) {
                            map[x][z] = centerType;
                        } else {
                            map[x][z] = edgeType;
                        }
                    }
                }
            }
        }
    }
}