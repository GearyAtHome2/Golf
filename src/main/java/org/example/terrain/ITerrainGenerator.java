package org.example.terrain;

import com.badlogic.gdx.math.Vector3;
import org.example.terrain.objects.Monolith;
import org.example.terrain.objects.Tree;

import java.util.List;

public interface ITerrainGenerator {
    void generate(Terrain.TerrainType[][] map, float[][] heights, List<Tree> trees, List<Monolith> monoliths, Vector3 teePos, Vector3 holePos);
}