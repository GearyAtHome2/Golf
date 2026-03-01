package org.example.terrain;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.LevelData;

import java.util.ArrayList;
import java.util.List;

public class Terrain {

    private ModelInstance waterInstance;
    java.util.Random rng = new java.util.Random(System.nanoTime());
    private final List<ModelInstance> chunks = new ArrayList<>();
    private final List<int[]> chunkOffsets = new ArrayList<>();
    private final int SIZE_X = 160;
    public static final int SIZE_Z = 500;
    private final int CHUNK_Z = 64;
    private final float SCALE = 1f;
    private Vector3 teeCenter;
    private TerrainType[][] terrainMap;
    private final List<Bunker> bunkers = new ArrayList<>();
    private float waterLevel;
    private final List<Tree> trees = new ArrayList<>();
    private float[][] heightMap;
    private Vector3 holePosition;
    private ModelInstance flagInstance;
    private ModelInstance holeMarker;
    private float holeSize;


    public Terrain(ITerrainGenerator generator, float waterLevel) {
        this.waterLevel = waterLevel;
        this.terrainMap = new TerrainType[SIZE_X][SIZE_Z];
        this.heightMap = new float[SIZE_X][SIZE_Z];
        this.holeSize = 0.6f;

        generator.generate(terrainMap, heightMap, trees, teeCenter = new Vector3(), holePosition = new Vector3());

        createWaterPlane();

        int z = 0;
        while (z < SIZE_Z) {
            int zEnd = Math.min(z + CHUNK_Z, SIZE_Z);
            chunks.add(buildChunk(z, zEnd));
            z = zEnd;
        }

        // 4. DECORATIONS
        createFlag(holePosition);
        createHoleMarker(holePosition);
    }

    private void createHoleMarker(Vector3 pos) {
        ModelBuilder mb = new ModelBuilder();
        Model disc = mb.createCylinder(holeSize, 0.01f, holeSize, 20,
                new Material(ColorAttribute.createDiffuse(Color.BLACK)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        holeMarker = new ModelInstance(disc);
        holeMarker.transform.setToTranslation(pos.x, pos.y + 0.02f, pos.z);
    }

    private void createFlag(Vector3 pos) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();

        MeshPartBuilder pb = mb.part("pole", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
                new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        pb.cylinder(0.1f, 5f, 0.1f, 8);

        Material flagMaterial = new Material(ColorAttribute.createDiffuse(Color.RED));
        flagMaterial.set(new com.badlogic.gdx.graphics.g3d.attributes.IntAttribute(
                com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.CullFace, 0));

        MeshPartBuilder fb = mb.part("flag", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, flagMaterial);

        fb.rect(
                0.1f, 1.5f, 0f,
                0.1f, 2.5f, 0f,
                1.6f, 2.5f, 0f,
                1.6f, 1.5f, 0f,
                0f, 0f, 1f
        );

        Model flagModel = mb.end();
        flagInstance = new ModelInstance(flagModel);
        // Initial placement
        flagInstance.transform.setToTranslation(pos.x, pos.y + 2.5f, pos.z);
    }

    public void updateFlag(Vector3 cameraPosition) {
        if (flagInstance == null) return;

        float distance = cameraPosition.dst(holePosition);
        float threshold = 50f;
        float growthRate = 0.015f;

        float dynamicScale = 1.0f;
        if (distance > threshold) {
            dynamicScale = 1.0f + ((distance - threshold) * growthRate);
        }
        dynamicScale = Math.min(dynamicScale, 4.0f);

        // Billboarding logic
        Vector3 direction = new Vector3(cameraPosition).sub(holePosition);
        direction.y = 0;
        float angle = MathUtils.atan2(direction.x, direction.z) * MathUtils.radiansToDegrees;

        // Rebuild the transform matrix correctly: Scale -> Rotate -> Translate
        flagInstance.transform.setToScaling(dynamicScale, dynamicScale, dynamicScale);
        flagInstance.transform.rotate(Vector3.Y, angle);
        flagInstance.transform.setTranslation(holePosition.x, holePosition.y + (2.5f * dynamicScale), holePosition.z);
    }

    public ModelInstance getFlagInstance() { return flagInstance; }
    public Vector3 getHolePosition() { return holePosition; }
    public float getHoleSize() { return holeSize; }

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
        float height = baseHeightAt(x, z);

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
        int ix = MathUtils.clamp((int) ((worldX + SIZE_X * SCALE / 2f) / SCALE), 0, SIZE_X - 1);
        int iz = MathUtils.clamp((int) ((worldZ + SIZE_Z * SCALE / 2f) / SCALE), 0, SIZE_Z - 1);
        return terrainMap[ix][iz];
    }

    public Vector3 getTeePosition() { return teeCenter.cpy(); }
    public float getWaterLevel() { return waterLevel; }

    public float getHeightAt(float worldX, float worldZ) {
        float localX = worldX + SIZE_X * SCALE / 2f;
        float localZ = worldZ + SIZE_Z * SCALE / 2f;
        int ix = MathUtils.clamp((int) (localX / SCALE), 0, SIZE_X - 2);
        int iz = MathUtils.clamp((int) (localZ / SCALE), 0, SIZE_Z - 2);

        float fx = (localX / SCALE) - ix;
        float fz = (localZ / SCALE) - iz;

        float h00 = vertex(ix, iz).y;
        float h10 = vertex(ix + 1, iz).y;
        float h01 = vertex(ix, iz + 1).y;
        float h11 = vertex(ix + 1, iz + 1).y;

        return MathUtils.lerp(MathUtils.lerp(h00, h10, fx), MathUtils.lerp(h01, h11, fz), fz);
    }

    public Vector3 getNormalAt(float worldX, float worldZ) {
        float localX = worldX + SIZE_X * SCALE / 2f;
        float localZ = worldZ + SIZE_Z * SCALE / 2f;

        int ix = MathUtils.clamp((int) (localX / SCALE), 0, SIZE_X - 2);
        int iz = MathUtils.clamp((int) (localZ / SCALE), 0, SIZE_Z - 2);

        Vector3 v00 = vertex(ix, iz);
        Vector3 v10 = vertex(ix + 1, iz);
        Vector3 v01 = vertex(ix, iz + 1);
        Vector3 v11 = vertex(ix + 1, iz + 1);

        Vector3 nTri1 = new Vector3(v10).sub(v00).crs(new Vector3(v01).sub(v00)).nor();
        Vector3 nTri2 = new Vector3(v11).sub(v10).crs(new Vector3(v01).sub(v10)).nor();

        return nTri1.lerp(nTri2, localX / SCALE - ix).nor();
    }

    public void render(ModelBatch batch, Environment env) {
        for (ModelInstance chunk : chunks) batch.render(chunk, env);
        if (waterInstance != null) batch.render(waterInstance, env);
        for (Tree t : trees) t.render(batch, env);
        if (holeMarker != null) batch.render(holeMarker, env);
        if (flagInstance != null) batch.render(flagInstance, env);
    }

    public void dispose() {
        for (ModelInstance chunk : chunks) chunk.model.dispose();
        if (waterInstance != null) waterInstance.model.dispose();
        for (Tree t : trees) t.dispose();
        if (holeMarker != null) holeMarker.model.dispose();
        if (flagInstance != null) flagInstance.model.dispose();
    }

    private static class Bunker {
        int centerX, centerZ;
        int radiusX, radiusZ;

        Bunker(int cx, int cz, int rx, int rz) {
            centerX = cx;
            centerZ = cz;
            radiusX = rx;
            radiusZ = rz;
        }
    }

    private void createWaterPlane() {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();

        MeshPartBuilder meshBuilder = builder.part(
                "water",
                GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position |
                        VertexAttributes.Usage.Normal |
                        VertexAttributes.Usage.ColorPacked,
                new Material(ColorAttribute.createDiffuse(Color.BLUE))
        );

        float w = SIZE_X * SCALE;
        float h = SIZE_Z * SCALE;

        Vector3 v00 = new Vector3(0, waterLevel, 0);
        Vector3 v10 = new Vector3(w, waterLevel, 0);
        Vector3 v01 = new Vector3(0, waterLevel, h);
        Vector3 v11 = new Vector3(w, waterLevel, h);

        meshBuilder.triangle(v00, v01, v11);
        meshBuilder.triangle(v00, v11, v10);

        Model model = builder.end();
        waterInstance = new ModelInstance(model);
        waterInstance.transform.setToTranslation(-w / 2f, 0, -h / 2f);
    }

    public List<Tree> getTrees() { return trees; }

    public enum TerrainType {
        TEE(0.40f, 2.0f, 1.1f),
        FAIRWAY(1.1f, 0.2f, 1.05f),
        ROUGH(1.70f, 4.5f, 1.5f),
        BUNKER(1.2f, 12.0f, 2.5f),
        GREEN(0.25f, 0.4f, 1.2f);

        public final float kineticFriction;
        public final float rollingResistance;
        public final float staticMultiplier;

        TerrainType(float kf, float rr, float sm) {
            this.kineticFriction = kf;
            this.rollingResistance = rr;
            this.staticMultiplier = sm;
        }
    }

    public static class Tree {
        private final ModelInstance trunkInstance;
        private final List<ModelInstance> foliageInstances = new ArrayList<>();
        private final Vector3 position;
        private final float trunkHeight;
        private final float trunkRadius;
        private final float foliageHeight;
        private final float foliageRadius;

        Tree(float x, float y, float z, float trunkHeight, float trunkRadius, float foliageRadius) {
            this.position = new Vector3(x, y, z);
            this.trunkHeight = trunkHeight;
            this.trunkRadius = trunkRadius;

            ModelBuilder builder = new ModelBuilder();
            builder.begin();
            MeshPartBuilder trunkBuilder = builder.part(
                    "trunk",
                    GL20.GL_TRIANGLES,
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.ColorPacked,
                    new Material(ColorAttribute.createDiffuse(new Color(0.55f, 0.27f, 0.07f, 1f)))
            );
            trunkBuilder.cylinder(trunkRadius * 2, trunkHeight, trunkRadius * 2, 16);
            Model trunkModel = builder.end();
            trunkInstance = new ModelInstance(trunkModel);
            trunkInstance.transform.setToTranslation(x, y + trunkHeight / 2f, z);

            builder = new ModelBuilder();
            builder.begin();
            MeshPartBuilder foliageBuilder = builder.part(
                    "foliage",
                    GL20.GL_TRIANGLES,
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.ColorPacked,
                    new Material(ColorAttribute.createDiffuse(Color.GREEN))
            );
            foliageBuilder.sphere(foliageRadius * 2, foliageRadius * 2, foliageRadius * 2, 16, 16);
            Model foliageModel = builder.end();
            ModelInstance foliageInstance = new ModelInstance(foliageModel);
            foliageInstance.transform.setToTranslation(x, y + trunkHeight + foliageRadius, z);
            this.foliageHeight = trunkHeight;
            this.foliageRadius = foliageRadius;
            foliageInstances.add(foliageInstance);
        }

        void render(ModelBatch batch, Environment env) {
            batch.render(trunkInstance, env);
            for (ModelInstance f : foliageInstances) batch.render(f, env);
        }

        void dispose() {
            trunkInstance.model.dispose();
            for (ModelInstance f : foliageInstances) f.model.dispose();
        }

        public Vector3 getPosition() { return position.cpy(); }
        public float getTrunkHeight() { return trunkHeight; }
        public float getTrunkRadius() { return trunkRadius; }
        public float getFoliageHeight() { return foliageHeight; }
        public float getFoliageRadius() { return foliageRadius; }
    }
}