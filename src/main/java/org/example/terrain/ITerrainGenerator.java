package org.example.terrain;

import com.badlogic.gdx.math.Vector3;

import java.util.List;

public interface ITerrainGenerator {
    void generate(Terrain.TerrainType[][] map, float[][] heights, List<Terrain.Tree> trees, List<Terrain.Monolith> monoliths, Vector3 teePos, Vector3 holePos);
}