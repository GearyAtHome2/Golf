package org.example.terrain.features;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.ITerrainGenerator;
import org.example.terrain.Terrain;

import java.util.List;
import java.util.Random;

public class PuttingGreenGenerator implements ITerrainGenerator {

    private final Random rng;
    private final float[] greenWaveAngles = new float[4];
    private final float[] greenWaveOffsets = new float[4];
    private final float seedOffset;

    public PuttingGreenGenerator(long seed) {
        this.rng = new Random(seed);
        this.seedOffset = rng.nextFloat() * 1000f;

        for (int i = 0; i < 4; i++) {
            greenWaveAngles[i] = rng.nextFloat() * MathUtils.PI * 2;
            greenWaveOffsets[i] = rng.nextFloat() * 100f;
        }
    }

    @Override
    public void generate(Terrain.TerrainType[][] map, float[][] heights, List<Terrain.Tree> trees, List<Terrain.Monolith> monoliths, Vector3 teePos, Vector3 holePos) {
        int width = map.length;
        int depth = map[0].length;

        // We calculate the center of the map to place the green
        int midX = width / 2;
        int midZ = depth / 2;

        // 1. Clear the canvas
        // Trees and Monoliths are usually empty for a pure putting green
        trees.clear();
        monoliths.clear();

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                // Default everything to Rough/Flat to start
                map[x][z] = Terrain.TerrainType.ROUGH;
                heights[x][z] = 0f;
            }
        }

        // 2. Map the "Apron/Fringe" (The larger area around the green)
        // We use a radius of 40 with a wobble of 5
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                float dist = Vector3.dst(x, z, 0, midX, midZ, 0);
                float angle = MathUtils.atan2(z - midZ, x - midX);
                float fringeRadius = 40f + (MathUtils.sin(angle * 3 + seedOffset) * 5f);

                if (dist <= fringeRadius) {
                    map[x][z] = Terrain.TerrainType.FAIRWAY; // Visualizing fringe as Fairway
                }
            }
        }

        // 3. Map the actual Putting Green (The central target)
        // We use the same helper the ClassicGenerator now uses
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                GreenHelper.applySingleTileGreen(map, x, z, midX, midZ, seedOffset);
            }
        }

        // 4. Apply the Height/Breaks
        // We only want the "breaks" to exist on the green and fade out at the fringe
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                float dist = Vector3.dst(x, z, 0, midX, midZ, 0);

                // Create a falloff so the rough remains flat
                float mask = MathUtils.clamp(1.0f - (dist / 45f), 0f, 1f);
                mask = (float) Math.pow(mask, 2); // Smoother transition

                if (mask > 0) {
                    float undulation = GreenHelper.calculateUndulation(x, z, greenWaveAngles, greenWaveOffsets);
                    heights[x][z] = undulation * 1.2f * mask;
                }
            }
        }

        float startRight =(rng.nextFloat()*16) -8;
        float endRight =(rng.nextFloat()*16) -8;

        float startX = midX+startRight;
        float startZ = midZ-20;
        float endX = midX+endRight;
        float endZ = midZ+10 ;

        // Note: We subtract half width/depth if your world coordinates are centered at 0,0
        float worldCenterX = width / 2f;
        float worldCenterZ = depth / 2f;

        teePos.set(startX - worldCenterX, heights[(int)startX][(int)startZ]+0.15f, startZ - worldCenterZ);
        holePos.set(endX - worldCenterX, heights[(int)endX][(int)endZ], endZ - worldCenterZ);
    }
}