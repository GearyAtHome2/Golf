package org.example.terrain.practiceRange;

import com.badlogic.gdx.math.Vector3;
import org.example.terrain.ITerrainGenerator;
import org.example.terrain.Terrain;
import org.example.terrain.objects.Monolith;
import org.example.terrain.objects.Tree;

import java.util.ArrayList;
import java.util.List;

public class PracticeRangeGenerator implements ITerrainGenerator {

    public static class SignData {
        public Vector3 position;
        public int distance;

        public SignData(Vector3 pos, int dist) {
            this.position = pos;
            this.distance = dist;
        }
    }

    private final List<SignData> signPositions = new ArrayList<>();
    private final List<Float> markerZPositions = new ArrayList<>();

    @Override
    public void generate(Terrain.TerrainType[][] map, float[][] heights, List<Tree> trees, List<Monolith> monoliths, Vector3 teePos, Vector3 holePos) {
        int width = map.length;
        int depth = map[0].length;
        float halfWidth = width / 2f;
        float halfDepth = depth / 2f;

        float teeZ = 25f - halfDepth;
        float teeAreaZLimit = teeZ + 10f;
        int greenZoneStartIdx = (int) (depth * 0.9f);

        // Define radial strip widths (distance from the center line)
        float waterHalfWidth = width * 0.10f;
        float stoneMaxDist = waterHalfWidth + (width * 0.08f);
        float greenMaxDist = stoneMaxDist + (width * 0.08f);
        float fairwayMaxDist = greenMaxDist + (width * 0.08f);
        float roughMaxDist = fairwayMaxDist + (width * 0.08f);

        // Tee is back in the center (x=0), but we'll ensure height is above water
        teePos.set(0, 0.38f, teeZ);
        holePos.set(0, 0, (depth - 50f) - halfDepth + 2);

        signPositions.clear();
        monoliths.clear(); // Clear existing buildings before adding new ones

        // Move the building to 30 units directly in front of the tee box, 10 units up
        // x = 0 (direct line), y = tee height + 10, z = tee position + 30
        Monolith monolith = new Monolith(teePos.x, teePos.y + 10.0f, teePos.z + 90.0f, 10.0f, 10.0f, 10.0f, 45);
        monoliths.add(monolith);

        markerZPositions.clear();

        for (int z = 0; z < depth; z++) {
            float worldZ = z - halfDepth;
            float distFromTee = worldZ - teeZ;

            for (int x = 0; x < width; x++) {
                float distFromCenter = Math.abs(x - (width / 2f));
                Terrain.TerrainType type;
                float h = 0.0f;

                // Priority 1: The final 10% target area
                if (z >= greenZoneStartIdx) {
                    type = Terrain.TerrainType.GREEN;
                    h = 0.0f;
                }
                // Priority 2: The hitting Tee Box (Raised platform over the water)
                else if (distFromCenter < 10 && worldZ < teeAreaZLimit && worldZ > teeZ - 5f) {
                    type = Terrain.TerrainType.TEE;
                    h = 0.2f; // Above the water level
                }
                // Priority 3: Radial Layout (Center-Out)
                else {
                    if (distFromCenter < waterHalfWidth) {
                        type = Terrain.TerrainType.SAND;
                        h = -2.0f; // Central Water Zone
                    } else if (distFromCenter < stoneMaxDist) {
                        type = Terrain.TerrainType.STONE;
                    } else if (distFromCenter < greenMaxDist) {
                        type = Terrain.TerrainType.GREEN;
                    } else if (distFromCenter < fairwayMaxDist) {
                        type = Terrain.TerrainType.FAIRWAY;
                    } else if (distFromCenter < roughMaxDist) {
                        type = Terrain.TerrainType.ROUGH;
                    } else {
                        type = Terrain.TerrainType.SAND; // Edge Sand
                    }
                }

                map[x][z] = type;
                heights[x][z] = h;
            }

            if (distFromTee > 10 && distFromTee % 50 < 0.5f) {
                markerZPositions.add(worldZ);
            }

            if (distFromTee > 40 && (int) distFromTee % 50 == 0) {
                // Signs on the outer edge of the stone strip
                signPositions.add(new SignData(new Vector3(stoneMaxDist, 0, worldZ), (int) distFromTee));
                signPositions.add(new SignData(new Vector3(-stoneMaxDist, 0, worldZ), (int) distFromTee));
            }
        }
    }

    public List<SignData> getSignPositions() {
        return signPositions;
    }

    public List<Float> getMarkerZPositions() {
        return markerZPositions;
    }
}