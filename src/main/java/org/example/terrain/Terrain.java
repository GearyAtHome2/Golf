package org.example.terrain;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.ball.ShotDifficulty;

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

    private float greenMinH = Float.MAX_VALUE;
    private float greenMaxH = -Float.MAX_VALUE;

    public Terrain(ITerrainGenerator generator, float waterLevel) {
        this.waterLevel = waterLevel;
        this.terrainMap = new TerrainType[SIZE_X][SIZE_Z];
        this.heightMap = new float[SIZE_X][SIZE_Z];
        this.holeSize = 0.6f;

        generator.generate(terrainMap, heightMap, trees, teeCenter = new Vector3(), holePosition = new Vector3());

        boolean greenFound = false;
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (terrainMap[x][z] == TerrainType.GREEN) {
                    float h = heightMap[x][z];
                    if (h < greenMinH) greenMinH = h;
                    if (h > greenMaxH) greenMaxH = h;
                    greenFound = true;
                }
            }
        }
        if (!greenFound) {
            greenMinH = 0;
            greenMaxH = 10;
        }

        createWaterPlane();

        int z = 0;
        while (z < SIZE_Z) {
            int zEnd = Math.min(z + CHUNK_Z, SIZE_Z);
            chunks.add(buildChunk(z, zEnd));
            z = zEnd;
        }

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
        fb.rect(0.1f, 1.5f, 0f, 0.1f, 2.5f, 0f, 1.6f, 2.5f, 0f, 1.6f, 1.5f, 0f, 0f, 0f, 1f);
        Model flagModel = mb.end();
        flagInstance = new ModelInstance(flagModel);
        flagInstance.transform.setToTranslation(pos.x, pos.y + 2.5f, pos.z);
    }

    public void updateFlag(Vector3 cameraPosition) {
        if (flagInstance == null) return;
        float distance = cameraPosition.dst(holePosition);
        float threshold = 50f;
        float growthRate = 0.015f;
        float dynamicScale = 1.0f;
        if (distance > threshold) dynamicScale = 1.0f + ((distance - threshold) * growthRate);
        dynamicScale = Math.min(dynamicScale, 4.0f);
        Vector3 direction = new Vector3(cameraPosition).sub(holePosition);
        direction.y = 0;
        float angle = MathUtils.atan2(direction.x, direction.z) * MathUtils.radiansToDegrees;
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
        MeshPartBuilder meshBuilder = builder.part("terrain_chunk", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.ColorPacked,
                new Material());

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
        float height = heightMap[x][z];
        for (Bunker b : bunkers) {
            float dx = (x - b.centerX) / (float) b.radiusX;
            float dz = (z - b.centerZ) / (float) b.radiusZ;
            float distSq = dx * dx + dz * dz;
            if (distSq <= 1f) height -= 0.5f * (MathUtils.cos(distSq * MathUtils.PI) + 1f);
        }
        return new Vector3(x * SCALE, height, z * SCALE);
    }

    private void buildTriangle(MeshPartBuilder builder, Vector3 v1, Vector3 v2, Vector3 v3, int gridX, int gridZ) {
        // v3-v1 cross v2-v1 logic results in the upward normal for the visual mesh
        Vector3 normal = new Vector3(v3).sub(v1).crs(new Vector3(v2).sub(v1)).nor();
        TerrainType type = terrainMap[MathUtils.clamp(gridX, 0, SIZE_X - 1)][MathUtils.clamp(gridZ, 0, SIZE_Z - 1)];
        Color color = new Color();

        if (type == TerrainType.GREEN) {
            float avgY = (v1.y + v2.y + v3.y) / 3.0f;
            float range = greenMaxH - greenMinH;
            float t = (range > 0.01f) ? MathUtils.clamp((avgY - greenMinH) / range, 0f, 1f) : 0.5f;
            color.set(0.1f * t, 0.4f + (0.4f * t), 0.1f * t, 1f);
        } else {
            switch (type) {
                case TEE -> color.set(0.2f, 0.5f, 0.2f, 1f);
                case FAIRWAY -> color.set(0.1f, 0.4f, 0.1f, 1f);
                case ROUGH -> color.set(0.02f, 0.15f, 0.02f, 1f);
                case BUNKER -> color.set(0.85f, 0.8f, 0.5f, 1f);
                default -> color.set(0.1f, 0.4f, 0.1f, 1f);
            }
        }

        Vector3 customLight = new Vector3(-0.5f, 1.0f, -0.5f).nor();
        float dot = normal.dot(customLight);
        float slope = 1f - normal.y;
        float shading = 0.8f + (dot * 0.3f) - (slope * 1.2f);
        shading = MathUtils.clamp(shading, 0.2f, 1.1f);

        color.r = MathUtils.clamp(color.r * shading, 0, 1);
        color.g = MathUtils.clamp(color.g * shading, 0, 1);
        color.b = MathUtils.clamp(color.b * shading, 0, 1);

        builder.setColor(color);
        builder.triangle(v1, v3, v2);
    }

    public void render(ModelBatch batch, Environment env) {
        for (ModelInstance chunk : chunks) batch.render(chunk, env);
        if (waterInstance != null) batch.render(waterInstance, env);
        for (Tree t : trees) t.render(batch, env);
        if (holeMarker != null) batch.render(holeMarker, env);
        if (flagInstance != null) batch.render(flagInstance, env);
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

        // Sync with buildTriangle winding: (v3-v1) crs (v2-v1)
        // For Triangle 1 (v00, v10, v11): (v11-v00) crs (v10-v00)
        Vector3 nTri1 = new Vector3(v11).sub(v00).crs(new Vector3(v10).sub(v00)).nor();
        // For Triangle 2 (v00, v11, v01): (v01-v00) crs (v11-v00)
        Vector3 nTri2 = new Vector3(v01).sub(v00).crs(new Vector3(v11).sub(v00)).nor();

        return nTri1.lerp(nTri2, localX / SCALE - ix).nor();
    }

    public void dispose() {
        for (ModelInstance chunk : chunks) chunk.model.dispose();
        if (waterInstance != null) waterInstance.model.dispose();
        for (Tree t : trees) t.dispose();
        if (holeMarker != null) holeMarker.model.dispose();
        if (flagInstance != null) flagInstance.model.dispose();
    }

    public ShotDifficulty getShotDifficulty(float x, float z, Vector3 aimDir) {
        ShotDifficulty diff = new ShotDifficulty();

        // 1. Terrain Component
        TerrainType type = getTerrainTypeAt(x, z);
        diff.terrainDifficulty = type.difficulty;

        // 2. Slope Component
        Vector3 normal = getNormalAt(x, z);
        float steepness = 1.0f - normal.y;
        Vector3 right = new Vector3(aimDir).crs(Vector3.Y).nor();
        float sideHill = Math.abs(normal.dot(right));

        // Pure slope logic
        diff.slopeDifficulty = 1.0f + (steepness * 2.0f) + (sideHill * 1.5f);

        return diff;
    }

    private static class Bunker {
        int centerX, centerZ, radiusX, radiusZ;
        Bunker(int cx, int cz, int rx, int rz) {
            centerX = cx; centerZ = cz; radiusX = rx; radiusZ = rz;
        }
    }

    private void createWaterPlane() {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        MeshPartBuilder meshBuilder = builder.part("water", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.ColorPacked,
                new Material(ColorAttribute.createDiffuse(Color.BLUE)));
        float w = SIZE_X * SCALE;
        float h = SIZE_Z * SCALE;
        Vector3 v00 = new Vector3(0, waterLevel, 0);
        Vector3 v10 = new Vector3(w, waterLevel, 0);
        Vector3 v01 = new Vector3(0, waterLevel, h);
        Vector3 v11 = new Vector3(w, waterLevel, h);
        meshBuilder.triangle(v00, v01, v11);
        meshBuilder.triangle(v00, v11, v10);
        waterInstance = new ModelInstance(builder.end());
        waterInstance.transform.setToTranslation(-w / 2f, 0, -h / 2f);
    }

    public List<Tree> getTrees() { return trees; }

    public enum TerrainType {
        TEE(0.40f, 2.0f, 1.1f, 0.8f),
        FAIRWAY(0.6f, 0.2f, 1.05f, 1.0f),
        ROUGH(1.70f, 4.5f, 1.5f, 1.4f),
        BUNKER(4.2f, 12.0f, 2.5f, 1.8f),
        GREEN(0.2f, 0.1f, 1.05f, 0.9f);

        public final float kineticFriction, rollingResistance, staticMultiplier, difficulty;
        TerrainType(float kf, float rr, float sm, float diff) {
            this.kineticFriction = kf;
            this.rollingResistance = rr;
            this.staticMultiplier = sm;
            this.difficulty = diff;
        }
    }

    public static class Tree {
        private final ModelInstance trunkInstance;
        private final List<ModelInstance> foliageInstances = new ArrayList<>();
        private final Vector3 position;
        private final float trunkHeight, trunkRadius, foliageHeight, foliageRadius;

        Tree(float x, float y, float z, float trunkHeight, float trunkRadius, float foliageRadius) {
            this.position = new Vector3(x, y, z);
            this.trunkHeight = trunkHeight;
            this.trunkRadius = trunkRadius;
            this.foliageHeight = trunkHeight;
            this.foliageRadius = foliageRadius;
            ModelBuilder builder = new ModelBuilder();
            builder.begin();
            MeshPartBuilder trunkBuilder = builder.part("trunk", GL20.GL_TRIANGLES,
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.ColorPacked,
                    new Material(ColorAttribute.createDiffuse(new Color(0.55f, 0.27f, 0.07f, 1f))));
            trunkBuilder.cylinder(trunkRadius * 2, trunkHeight, trunkRadius * 2, 16);
            trunkInstance = new ModelInstance(builder.end());
            trunkInstance.transform.setToTranslation(x, y + trunkHeight / 2f, z);
            builder.begin();
            MeshPartBuilder foliageBuilder = builder.part("foliage", GL20.GL_TRIANGLES,
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.ColorPacked,
                    new Material(ColorAttribute.createDiffuse(Color.GREEN)));
            foliageBuilder.sphere(foliageRadius * 2, foliageRadius * 2, foliageRadius * 2, 16, 16);
            ModelInstance fInst = new ModelInstance(builder.end());
            fInst.transform.setToTranslation(x, y + trunkHeight + foliageRadius, z);
            foliageInstances.add(fInst);
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