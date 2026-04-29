package org.example.terrain.features;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.ITerrainGenerator;
import org.example.terrain.Terrain;
import org.example.terrain.objects.Monolith;
import org.example.terrain.objects.Tree;

import java.util.List;
import java.util.Random;

public class PuttingGreenGenerator implements ITerrainGenerator {

    private final Random rng;
    private final float[] greenWaveAngles = new float[4];
    private final float[] greenWaveOffsets = new float[4];
    private final float[] greenWaveFreqs = new float[4];
    private final float seedOffset;
    private float greenTiltDx, greenTiltDz;

    public PuttingGreenGenerator(long seed) {
        this.rng = new Random(seed);
        this.seedOffset = rng.nextFloat() * 1000f;

        for (int i = 0; i < 4; i++) {
            greenWaveAngles[i] = rng.nextFloat() * MathUtils.PI * 2;
            greenWaveOffsets[i] = rng.nextFloat() * 100f;
            greenWaveFreqs[i] = 0.32f * (1f + (rng.nextFloat() - 0.5f) * 0.1f); // ±5%
        }
        float tiltAngle = rng.nextFloat() * MathUtils.PI * 2;
        float tiltMag   = 0.006f + rng.nextFloat() * 0.009f;
        greenTiltDx = MathUtils.cos(tiltAngle) * tiltMag;
        greenTiltDz = MathUtils.sin(tiltAngle) * tiltMag;

        // Determinism fingerprint — compare between devices to isolate desync source
        Gdx.app.log("GreenDet", "PuttingGreen seed=" + seed
                + " seedOffset=0x" + Integer.toHexString(Float.floatToRawIntBits(seedOffset))
                + " (" + seedOffset + ")");
        for (int i = 0; i < 4; i++) {
            float a = greenWaveAngles[i];
            Gdx.app.log("GreenDet", "  wave[" + i + "]"
                    + " angle=0x" + Integer.toHexString(Float.floatToRawIntBits(a))
                    + " cos=0x" + Integer.toHexString(Float.floatToRawIntBits(MathUtils.cos(a)))
                    + " sin=0x" + Integer.toHexString(Float.floatToRawIntBits(MathUtils.sin(a)))
                    + " offset=0x" + Integer.toHexString(Float.floatToRawIntBits(greenWaveOffsets[i])));
        }
    }

    @Override
    public void generate(Terrain.TerrainType[][] map, float[][] heights, List<Tree> trees, List<Monolith> monoliths, Vector3 teePos, Vector3 holePos) {
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
                GreenHelper.applySingleTileGreen(map, x, z, midX, midZ, 26f, seedOffset);
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
                    float undulation = GreenHelper.calculateUndulation(x, z, greenWaveAngles, greenWaveOffsets, greenWaveFreqs);
                    float tilt = (x - midX) * greenTiltDx + (z - midZ) * greenTiltDz;
                    heights[x][z] = (undulation + tilt) * 1.2f * mask;
                }
            }
        }

        float startRight = (rng.nextFloat() * 16) - 8;
        float endRight = (rng.nextFloat() * 16) - 8;

        float startX = midX + startRight;
        float startZ = midZ - 20;
        float endX = midX + endRight;
        float endZ = midZ + 10;

        // Note: We subtract half width/depth if your world coordinates are centered at 0,0
        float worldCenterX = width / 2f;
        float worldCenterZ = depth / 2f;

        teePos.set(startX - worldCenterX, heights[(int) startX][(int) startZ] + 0.15f, startZ - worldCenterZ);
        holePos.set(endX - worldCenterX, heights[(int) endX][(int) endZ], endZ - worldCenterZ);

        // Heightmap fingerprint — XOR of all raw float bits plus 9 sample points around centre
        int xorFp = 0;
        for (int x = 0; x < width; x++)
            for (int z = 0; z < depth; z++)
                xorFp ^= Float.floatToRawIntBits(heights[x][z]);
        Gdx.app.log("GreenDet", "PuttingGreen heightmap XOR=0x" + Integer.toHexString(xorFp));

        StringBuilder sb = new StringBuilder("PuttingGreen samples (hex):");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int sx = midX + dx * 8;
                int sz = midZ + dz * 8;
                if (sx >= 0 && sx < width && sz >= 0 && sz < depth) {
                    sb.append(" [").append(sx).append(',').append(sz).append("]=0x")
                      .append(Integer.toHexString(Float.floatToRawIntBits(heights[sx][sz])));
                }
            }
        }
        Gdx.app.log("GreenDet", sb.toString());
    }
}