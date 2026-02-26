package org.example;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

public class Terrain_depr_sharp {

    public enum TerrainType {TEE, FAIRWAY, ROUGH, BUNKER, GREEN}

    private final List<ModelInstance> chunks = new ArrayList<>();
    private final List<int[]> chunkOffsets = new ArrayList<>();
    private final int SIZE_X = 120;
    public static final int SIZE_Z = 500;
    private final int CHUNK_Z = 64;
    private final float SCALE = 1f;
    private final float HEIGHT_SCALE = 5f;
    private Vector3 teeCenter;
    private TerrainType[][] terrainMap;
    private final List<Bunker> bunkers = new ArrayList<>();

    private float[][] heightMap;

    public Terrain_depr_sharp() {
        terrainMap = new TerrainType[SIZE_X][SIZE_Z];
        heightMap  = new float[SIZE_X][SIZE_Z];   // ← NEW
        generateTerrainTypes();
        generateHeightMap();                      // ← NEW

        int z = 0;
        while (z < SIZE_Z) {
            int zEnd = Math.min(z + CHUNK_Z, SIZE_Z);
            chunks.add(buildChunk(z, zEnd));
            chunkOffsets.add(new int[]{0, z});
            z = zEnd;
        }
    }

    private void generateTerrainTypes() {
        int teeWidth = 10;
        int teeLength = 5;

        // --- Tee box ---
        int teeStartX = SIZE_X / 2 - teeWidth / 2;
        int teeEndX = teeStartX + teeWidth - 1;
        int teeStartZ = 0;
        int teeEndZ = teeStartZ + teeLength - 1;

        // Fill all with rough
        for (int x = 0; x < SIZE_X; x++)
            for (int z = 0; z < SIZE_Z; z++)
                terrainMap[x][z] = TerrainType.ROUGH;

        // Tee
        for (int x = teeStartX; x <= teeEndX; x++)
            for (int z = teeStartZ; z <= teeEndZ; z++)
                terrainMap[x][z] = TerrainType.TEE;

        // --- Fairway generation ---
        int fairwayStartZ = teeEndZ + MathUtils.random(10, 20);
        int fairwayEndZ   = SIZE_Z; // extend fully to the end
        float center = SIZE_X / 2f;
        float minWidth = 20f;
        float maxWidth = 75f;
        float noise = 0f;

        for (int z = fairwayStartZ; z < fairwayEndZ; z++) {
            // Smooth centerline
            noise += MathUtils.random(-0.3f, 0.3f);
            center += MathUtils.sin(z * 0.015f) * 0.8f + noise * 0.2f;
            center = MathUtils.clamp(center, maxWidth / 2f + 5, SIZE_X - maxWidth / 2f - 5);

            // Width curve: narrower near tee, wider mid-hole, slightly narrower near green
            float t = (float)(z - fairwayStartZ) / (fairwayEndZ - fairwayStartZ);
            float width = minWidth + (maxWidth - minWidth) * MathUtils.sin(t * MathUtils.PI);
            width += MathUtils.random(-2f, 2f);
            width = MathUtils.clamp(width, minWidth, maxWidth);

            int left  = MathUtils.clamp((int)(center - width / 2), 0, SIZE_X - 1);
            int right = MathUtils.clamp((int)(center + width / 2), 0, SIZE_X - 1);
            for (int x = left; x <= right; x++)
                terrainMap[x][z] = TerrainType.FAIRWAY;
        }

        // --- Green ---
        int greenLength = 10;
        int greenStartZ = SIZE_Z - 20; // green closer to end
        int greenEndZ = SIZE_Z - 1;
        int greenCenterX = SIZE_X / 2;
        int greenRadiusX = 8;
        int greenRadiusZ = greenLength / 2;

        for (int x = 0; x < SIZE_X; x++)
            for (int z = greenStartZ; z <= greenEndZ; z++) {
                float dx = (x - greenCenterX) / (float) greenRadiusX;
                float dz = (z - (greenStartZ + greenRadiusZ)) / (float) greenRadiusZ;
                if (dx*dx + dz*dz <= 1f){
                    terrainMap[x][z] = TerrainType.GREEN; // fairway is still underneath
                    // Surround green with fairway
                    for (int fx = Math.max(0, x - 4); fx <= Math.min(SIZE_X-1, x + 4); fx++)
                        for (int fz = Math.max(0, z - 4); fz <= Math.min(SIZE_Z-1, z + 4); fz++)
                            if (terrainMap[fx][fz] == TerrainType.ROUGH)
                                terrainMap[fx][fz] = TerrainType.FAIRWAY;
                }
            }

        // --- Store tee center ---
        int teeCenterX = teeStartX + teeWidth / 2;
        int teeCenterZ = teeStartZ + teeLength / 2;
        float worldX = teeCenterX * SCALE - SIZE_X * SCALE / 2f;
        float worldZ = teeCenterZ * SCALE - SIZE_Z * SCALE / 2f;
        float worldY = getHeightAt(worldX, worldZ);
        teeCenter = new Vector3(worldX, worldY, worldZ);

        // --- Bunkers ---
        int numBunkers = 5;
        for (int b = 0; b < numBunkers; b++) {
            int centerZ = MathUtils.random(10, SIZE_Z - 25);
            int centerX = MathUtils.random(5, SIZE_X - 6);
            if (centerX < SIZE_X / 2) centerX = MathUtils.random(5, 15);
            else centerX = MathUtils.random(SIZE_X - 16, SIZE_X - 6);

            int radiusX = MathUtils.random(3, 6);
            int radiusZ = MathUtils.random(3, 6);

            Bunker bunker = new Bunker(centerX, centerZ, radiusX, radiusZ);
            bunkers.add(bunker);

            for (int x = centerX - radiusX; x <= centerX + radiusX; x++)
                for (int z = centerZ - radiusZ; z <= centerZ + radiusZ; z++) {
                    if (x < 0 || x >= SIZE_X || z < 0 || z >= SIZE_Z) continue;
                    float dx = (x - centerX) / (float) radiusX;
                    float dz = (z - centerZ) / (float) radiusZ;
                    if (dx*dx + dz*dz <= 1f)
                        terrainMap[x][z] = TerrainType.BUNKER;
                }
        }
    }

    /** Builds chunk mesh */
    private ModelInstance buildChunk(int zStart, int zEnd) {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();

        MeshPartBuilder meshBuilder = builder.part(
                "terrain_chunk",
                GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position |
                        VertexAttributes.Usage.Normal |
                        VertexAttributes.Usage.ColorPacked,
                new Material()
        );

        for (int x = 0; x < SIZE_X - 1; x++) {
            for (int z = zStart; z < zEnd; z++) {
                if (z + 1 >= SIZE_Z) continue;
                Vector3 v00 = vertex(x, z);
                Vector3 v10 = vertex(x + 1, z);
                Vector3 v01 = vertex(x, z + 1);
                Vector3 v11 = vertex(x + 1, z + 1);

                buildTriangle(meshBuilder, v00, v10, v11, x, z);
                buildTriangle(meshBuilder, v00, v11, v01, x, z);
            }
        }

        Model model = builder.end();
        ModelInstance instance = new ModelInstance(model);
        instance.transform.setToTranslation(-SIZE_X * SCALE / 2f, 0, -SIZE_Z * SCALE / 2f);
        return instance;
    }

    private Vector3 vertex(int x, int z) {

        float worldX = x * SCALE;
        float worldZ = z * SCALE;
        TerrainType type = terrainMap[x][z];
        float height;
        if (type == TerrainType.TEE) {
            height = 0.2f;
        }
        else {
            height = baseHeightAt(x, z);
        }

        for (Bunker b : bunkers) {
            float dx = (x - b.centerX) / (float) b.radiusX;
            float dz = (z - b.centerZ) / (float) b.radiusZ;
            float distSq = dx * dx + dz * dz;

            if (distSq <= 1f) {
                float factor = 0.5f * (MathUtils.cos(distSq * MathUtils.PI) + 1f);
                height -= factor;
                break;
            }
        }

        return new Vector3(worldX, height, worldZ);
    }

    private float baseHeightAt(int x, int z) {
        return heightMap[x][z];
    }

    private void generateHeightMap() {

        java.util.Random rng = new java.util.Random(System.nanoTime());

        // Random global phase offsets (critical so waves differ each run)
        float off1 = rng.nextFloat() * 1000f;
        float off2 = rng.nextFloat() * 1000f;
        float off3 = rng.nextFloat() * 1000f;
        float off4 = rng.nextFloat() * 1000f;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {

                float worldX = x * SCALE;
                float worldZ = z * SCALE;

                // Large scale rolling hills
                float hills =
                        MathUtils.sin((worldX + off1) * 0.035f) * 2.0f +
                                MathUtils.cos((worldZ + off2) * 0.02f)  * 1.7f +
                                MathUtils.sin((worldX + worldZ + off3) * 0.018f) * 1.2f +
                                MathUtils.cos((worldX - worldZ + off4) * 0.015f) * 1.0f;

                // Small per-tile variation
                hills += (rng.nextFloat() - 0.5f) * 0.5f;

                // --- Envelope flattening near tee and green ---
                float zNorm = z / (float) SIZE_Z;

                float teeFade = MathUtils.clamp((zNorm - 0.05f) / 0.15f, 0f, 1f);
                teeFade = teeFade * teeFade * (3f - 2f * teeFade);

                float greenFade = MathUtils.clamp((1f - zNorm - 0.05f) / 0.15f, 0f, 1f);
                greenFade = greenFade * greenFade * (3f - 2f * greenFade);

                float envelope = teeFade * greenFade;

                heightMap[x][z] = hills * HEIGHT_SCALE * envelope;
            }
        }
    }
    private void buildTriangle(MeshPartBuilder builder, Vector3 v1, Vector3 v2, Vector3 v3, int gridX, int gridZ) {
        Vector3 normal = new Vector3(v3).sub(v1).crs(new Vector3(v2).sub(v1)).nor();
        float slope = 1f - Math.abs(normal.y);

        TerrainType type = terrainMap[MathUtils.clamp(gridX, 0, SIZE_X - 1)][MathUtils.clamp(gridZ, 0, SIZE_Z - 1)];
        Color color;
        switch (type) {
            case TEE -> color = new Color(0.3f, 0.8f, 0.3f, 1f);
            case FAIRWAY -> color = new Color(0.1f, 0.6f, 0.1f, 1f);
            case ROUGH -> color = new Color(0.05f, 0.3f, 0.05f, 1f);
            case BUNKER -> color = new Color(0.95f, 0.9f, 0.5f, 1f);
            case GREEN -> color = new Color(0.5f, 1f, 0.5f, 1f);
            default -> color = new Color(0.1f, 0.6f, 0.1f, 1f);
        }

        float brightness = MathUtils.clamp(0.5f - slope * 0.4f, 0.3f, 0.7f);
        color.r = MathUtils.clamp(color.r * brightness, 0f, 1f);
        color.g = MathUtils.clamp(color.g * brightness, 0f, 1f);
        color.b = MathUtils.clamp(color.b * brightness, 0f, 1f);

        builder.setColor(color);
        builder.triangle(v1, v3, v2);
    }

    public TerrainType getTerrainTypeAt(float worldX, float worldZ) {
        int ix = MathUtils.clamp((int)((worldX + SIZE_X * SCALE / 2f) / SCALE), 0, SIZE_X - 1);
        int iz = MathUtils.clamp((int)((worldZ + SIZE_Z * SCALE / 2f) / SCALE), 0, SIZE_Z - 1);
        return terrainMap[ix][iz];
    }

    public Vector3 getTeePosition() { return teeCenter.cpy(); }

    public float getHeightAt(float worldX, float worldZ) {
        float localX = worldX + SIZE_X * SCALE / 2f;
        float localZ = worldZ + SIZE_Z * SCALE / 2f;
        int ix = MathUtils.clamp((int)(localX / SCALE), 0, SIZE_X - 2);
        int iz = MathUtils.clamp((int)(localZ / SCALE), 0, SIZE_Z - 2);

        float fx = (localX / SCALE) - ix;
        float fz = (localZ / SCALE) - iz;

        float h00 = vertex(ix, iz).y;
        float h10 = vertex(ix + 1, iz).y;
        float h01 = vertex(ix, iz + 1).y;
        float h11 = vertex(ix + 1, iz + 1).y;

        return MathUtils.lerp(MathUtils.lerp(h00, h10, fx), MathUtils.lerp(h01, h11, fx), fz);
    }

    public Vector3 getNormalAt(float worldX, float worldZ) {
        float localX = worldX + SIZE_X * SCALE / 2f;
        float localZ = worldZ + SIZE_Z * SCALE / 2f;

        int ix = MathUtils.clamp((int)(localX / SCALE), 0, SIZE_X - 2);
        int iz = MathUtils.clamp((int)(localZ / SCALE), 0, SIZE_Z - 2);

        Vector3 v00 = vertex(ix, iz);
        Vector3 v10 = vertex(ix + 1, iz);
        Vector3 v01 = vertex(ix, iz + 1);
        Vector3 v11 = vertex(ix + 1, iz + 1);

        Vector3 nTri1 = new Vector3(v10).sub(v00).crs(new Vector3(v01).sub(v00)).nor();
        Vector3 nTri2 = new Vector3(v11).sub(v10).crs(new Vector3(v01).sub(v10)).nor();

        Vector3 n0 = nTri1.cpy().lerp(nTri2, localX / SCALE - ix);
        Vector3 n1 = nTri1.cpy().lerp(nTri2, localX / SCALE - ix);

        return n0.cpy().lerp(n1, localZ / SCALE - iz).nor();
    }

    public void render(ModelBatch batch, Environment env) {
        for (ModelInstance chunk : chunks) batch.render(chunk, env);
    }

    public void dispose() {
        for (ModelInstance chunk : chunks) chunk.model.dispose();
    }

    private static class Bunker {
        int centerX, centerZ;
        int radiusX, radiusZ;
        Bunker(int cx, int cz, int rx, int rz) { centerX = cx; centerZ = cz; radiusX = rx; radiusZ = rz; }
    }
}