package org.example.terrain;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.List;
import java.util.Random;

public class ClassicGenerator implements ITerrainGenerator {

    private final Random rng = new Random();
    private final float SCALE = 1.0f;
    private final float HEIGHT_SCALE = 5.0f;

    @Override
    public void generate(Terrain.TerrainType[][] map, float[][] heights, List<Terrain.Tree> trees, Vector3 teePos, Vector3 holePos) {
        int SIZE_X = map.length;
        int SIZE_Z = map[0].length;

        // 1. Generate Height Map (Hills)
        float off1 = rng.nextFloat() * 1000f;
        float off2 = rng.nextFloat() * 1000f;
        float off3 = rng.nextFloat() * 1000f;
        float off4 = rng.nextFloat() * 1000f;
        float undulationLevel = (rng.nextFloat() * 0.8f) + 0.2f;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                float worldX = x * SCALE;
                float worldZ = z * SCALE;
                float hills = MathUtils.sin((worldX + off1) * 0.035f) * 2.0f +
                        MathUtils.cos((worldZ + off2) * 0.02f) * 1.7f +
                        MathUtils.sin((worldX + worldZ + off3) * 0.018f) * 1.2f +
                        MathUtils.cos((worldX - worldZ + off4) * 0.015f) * 1.0f;
                hills *= undulationLevel;

                // Envelope flattening
                float zNorm = z / (float) SIZE_Z;
                float teeFade = MathUtils.clamp((zNorm - 0.05f) / 0.15f, 0f, 1f);
                float greenFade = MathUtils.clamp((1f - zNorm - 0.05f) / 0.15f, 0f, 1f);
                heights[x][z] = hills * HEIGHT_SCALE * (teeFade * teeFade * (3f - 2f * teeFade)) * (greenFade * greenFade * (3f - 2f * greenFade));
            }
        }

        // 2. Generate Terrain Types
        for (int x = 0; x < SIZE_X; x++)
            for (int z = 0; z < SIZE_Z; z++)
                map[x][z] = Terrain.TerrainType.ROUGH;

        // Tee Box
        int teeWidth = 10, teeLength = 5;
        int teeStartX = SIZE_X / 2 - teeWidth / 2;
        int teeStartZ = teeLength * 4;
        for (int x = teeStartX; x < teeStartX + teeWidth; x++)
            for (int z = teeStartZ; z < teeStartZ + teeLength; z++)
                map[x][z] = Terrain.TerrainType.TEE;

        teePos.set((teeStartX + teeWidth / 2) * SCALE - SIZE_X * SCALE / 2f, 0.2f, (teeStartZ + teeLength / 2) * SCALE - SIZE_Z * SCALE / 2f);

        // Fairway
        float greenOffset = MathUtils.random(-SIZE_X * 0.3f, SIZE_X * 0.3f);
        float greenCenterX = MathUtils.clamp(SIZE_X / 2f + greenOffset, 10, SIZE_X - 10);

        float center = SIZE_X / 2f;
        float noise = 0f;
        for (int z = teeStartZ + teeLength + 15; z < SIZE_Z; z++) {
            noise += MathUtils.random(-0.3f, 0.3f);
            center += MathUtils.sin(z * 0.015f) * 0.8f + noise * 0.2f;
            float t = (float) (z - (teeStartZ + teeLength + 15)) / (SIZE_Z - (teeStartZ + teeLength + 15));
            if (t > 0.75f) center = MathUtils.lerp(center, greenCenterX, (t - 0.75f) / 0.25f);

            float width = 20f + (55f * MathUtils.sin(t * MathUtils.PI));
            int left = MathUtils.clamp((int) (center - width / 2), 0, SIZE_X - 1);
            int right = MathUtils.clamp((int) (center + width / 2), 0, SIZE_X - 1);
            for (int x = left; x <= right; x++) map[x][z] = Terrain.TerrainType.FAIRWAY;
        }

        // Green
        int greenStartZ = SIZE_Z - 20;
        int greenRadiusX = 8, greenRadiusZ = 5;
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = greenStartZ; z < SIZE_Z; z++) {
                float dx = (x - greenCenterX) / (float) greenRadiusX;
                float dz = (z - (greenStartZ + greenRadiusZ)) / (float) greenRadiusZ;
                if (dx * dx + dz * dz <= 1f) map[x][z] = Terrain.TerrainType.GREEN;
            }
        }

        holePos.set(greenCenterX * SCALE - SIZE_X * SCALE / 2f, heights[(int)greenCenterX][greenStartZ + 5], (greenStartZ + 5) * SCALE - SIZE_Z * SCALE / 2f);

        // 3. Add Trees
        for (int i = 0; i < 30; i++) {
            int tx = MathUtils.random(0, SIZE_X - 1);
            int tz = MathUtils.random(0, SIZE_Z - 1);
            if (map[tx][tz] == Terrain.TerrainType.ROUGH) {
                float wx = tx * SCALE - SIZE_X * SCALE / 2f;
                float wz = tz * SCALE - SIZE_Z * SCALE / 2f;
                trees.add(new Terrain.Tree(wx, heights[tx][tz], wz, MathUtils.random(6, 12), 0.4f, MathUtils.random(2, 4)));
            }
        }
    }
}