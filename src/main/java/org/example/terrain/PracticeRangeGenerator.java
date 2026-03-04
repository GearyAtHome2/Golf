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
    private final List<Float> markerZPositions = new ArrayList<>();

    @Override
    public void generate(Terrain.TerrainType[][] map, float[][] heights, List<Terrain.Tree> trees, Vector3 teePos, Vector3 holePos) {
        int width = map.length;
        int depth = map[0].length;
        float halfWidth = width / 2f;
        float halfDepth = depth / 2f;

        float teeZ = 25f - halfDepth;
        // The tee box area (e.g., 10 units deep)
        float teeAreaZLimit = teeZ + 10f;

        // Final 10% of the course
        int greenZoneStartIdx = (int)(depth * 0.9f);

        teePos.set(0, 0.2f, teeZ);
        holePos.set(0, 0, (depth - 50f) - halfDepth);

        signPositions.clear();
        markerZPositions.clear();

        for (int z = 0; z < depth; z++) {
            float worldZ = z - halfDepth;
            float distFromTee = worldZ - teeZ;

            for (int x = 0; x < width; x++) {
                int distFromCenter = Math.abs(x - (width / 2));
                Terrain.TerrainType type;

                if (z >= greenZoneStartIdx) {
                    type = Terrain.TerrainType.GREEN;
                }
                else if (distFromCenter < 12 && worldZ < teeAreaZLimit && worldZ > teeZ - 5f) {
                    type = Terrain.TerrainType.TEE;
                }
                else if (distFromCenter < 8) {
                    type = Terrain.TerrainType.FAIRWAY; // Exact centre
                } else if (distFromCenter < 15) {
                    type = Terrain.TerrainType.SAND;    // Strips either side
                } else if (distFromCenter < 35) {
                    type = Terrain.TerrainType.STONE;   // Replace Green with Stone
                } else {
                    type = Terrain.TerrainType.ROUGH;   // Edges
                }

                map[x][z] = type;
                heights[x][z] = 0;
            }

            if (distFromTee > 10 && distFromTee % 50 < 0.5f) {
                markerZPositions.add(worldZ);
            }

            // Mark position for signs
            if (distFromTee > 40 && (int)distFromTee % 50 == 0) {
                signPositions.add(new SignData(new Vector3(16, 0, worldZ), (int)distFromTee));
                signPositions.add(new SignData(new Vector3(-16, 0, worldZ), (int)distFromTee));
            }
        }
    }

    public List<SignData> getSignPositions() { return signPositions; }
    public List<Float> getMarkerZPositions() { return markerZPositions; }
}