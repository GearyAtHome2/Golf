package org.example.terrain;

import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;

public class PracticeRangeGenerator implements ITerrainGenerator {

    public static class SignData {
        public Vector3 position;
        public int distance;
        public SignData(Vector3 pos, int dist) { this.position = pos; this.distance = dist; }
    }

    private final List<SignData> signPositions = new ArrayList<>();

    @Override
    public void generate(Terrain.TerrainType[][] map, float[][] heights, List<Terrain.Tree> trees, Vector3 teePos, Vector3 holePos) {
        int width = map.length;
        int depth = map[0].length;
        float halfWidth = width / 2f;
        float halfDepth = depth / 2f;

        float teeZ = 25f - halfDepth;
        teePos.set(0, 0.2f, teeZ);
        holePos.set(0, 0, (depth - 50f) - halfDepth);

        signPositions.clear();

        for (int z = 0; z < depth; z++) {
            float worldZ = z - halfDepth;
            float distFromTee = worldZ - teeZ;

            for (int x = 0; x < width; x++) {
                int distFromCenter = Math.abs(x - (width / 2));
                Terrain.TerrainType type;

                if (distFromCenter < 12) type = Terrain.TerrainType.TEE;
                else if (distFromCenter < 35) type = Terrain.TerrainType.FAIRWAY;
                else type = Terrain.TerrainType.ROUGH;

                // Thinner horizontal yardage bars (0.5 units wide)
                if (distFromTee > 10 && distFromTee % 50 < 0.5f) {
                    type = Terrain.TerrainType.GREEN;
                }

                map[x][z] = type;
                heights[x][z] = 0;
            }

            // Mark position for signs every 50m
            if (distFromTee > 40 && (int)distFromTee % 50 == 0) {
                // Place signs on both sides of the center lane
                signPositions.add(new SignData(new Vector3(14, 0, worldZ), (int)distFromTee));
                signPositions.add(new SignData(new Vector3(-14, 0, worldZ), (int)distFromTee));
            }
        }
    }

    public List<SignData> getSignPositions() { return signPositions; }
}