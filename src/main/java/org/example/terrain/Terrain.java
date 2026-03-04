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
import org.example.ball.ShotDifficulty;

import java.util.ArrayList;
import java.util.List;

public class Terrain {

    private final int SIZE_X = 160;
    public static final int SIZE_Z = 500;
    private final int CHUNK_Z = 64;
    private final float SCALE = 1f;

    private ModelInstance waterInstance;
    private final List<ModelInstance> chunks = new ArrayList<>();
    private TerrainType[][] terrainMap;
    private float[][] heightMap;
    private final List<Tree> trees = new ArrayList<>();

    private Vector3 teeCenter;
    private Vector3 holePosition;
    private ModelInstance flagInstance;
    private ModelInstance holeMarker;
    private float holeSize = 0.6f;
    private float waterLevel;
    private float greenMinH = Float.MAX_VALUE, greenMaxH = -Float.MAX_VALUE;

    public Terrain(ITerrainGenerator generator, float initialWaterLevel) {
        this.waterLevel = initialWaterLevel;
        this.terrainMap = new TerrainType[SIZE_X][SIZE_Z];
        this.heightMap = new float[SIZE_X][SIZE_Z];

        generator.generate(terrainMap, heightMap, trees, teeCenter = new Vector3(), holePosition = new Vector3());

        if (generator instanceof ClassicGenerator) {
            this.waterLevel = ((ClassicGenerator) generator).getData().getWaterLevel();
        }

        calculateGreenBounds();
        createWaterPlane();

        for (int z = 0; z < SIZE_Z; z += CHUNK_Z) {
            chunks.add(buildChunk(z, Math.min(z + CHUNK_Z, SIZE_Z)));
        }

        createHoleMarker(holePosition);
        createFlag(holePosition);
    }

    private void calculateGreenBounds() {
        boolean greenFound = false;
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (terrainMap[x][z] == TerrainType.GREEN) {
                    greenMinH = Math.min(greenMinH, heightMap[x][z]);
                    greenMaxH = Math.max(greenMaxH, heightMap[x][z]);
                    greenFound = true;
                }
            }
        }
        if (!greenFound) { greenMinH = 0; greenMaxH = 10; }
    }

    private ModelInstance buildChunk(int zStart, int zEnd) {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        MeshPartBuilder meshBuilder = builder.part("terrain_chunk", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.ColorPacked,
                new Material());

        for (int x = 0; x < SIZE_X - 1; x++) {
            for (int z = zStart; z < zEnd && z < SIZE_Z - 1; z++) {
                Vector3 v00 = vertex(x, z);
                Vector3 v10 = vertex(x + 1, z);
                Vector3 v01 = vertex(x, z + 1);
                Vector3 v11 = vertex(x + 1, z + 1);

                TerrainType type = terrainMap[x][z];
                TerrainMeshHelper.buildTriangle(meshBuilder, v00, v10, v11, type, greenMinH, greenMaxH);
                TerrainMeshHelper.buildTriangle(meshBuilder, v00, v11, v01, type, greenMinH, greenMaxH);
            }
        }
        ModelInstance instance = new ModelInstance(builder.end());
        instance.transform.setToTranslation(-SIZE_X * SCALE / 2f, 0, -SIZE_Z * SCALE / 2f);
        return instance;
    }

    private Vector3 vertex(int x, int z) {
        return new Vector3(x * SCALE, heightMap[x][z], z * SCALE);
    }

    public void render(ModelBatch batch, Environment env) {
        for (ModelInstance chunk : chunks) batch.render(chunk, env);
        if (waterInstance != null) batch.render(waterInstance, env);
        for (Tree t : trees) t.render(batch, env);
        if (holeMarker != null) batch.render(holeMarker, env);
        if (flagInstance != null) batch.render(flagInstance, env);
    }

    public float getHeightAt(float worldX, float worldZ) {
        float localX = worldX + (SIZE_X * SCALE / 2f);
        float localZ = worldZ + (SIZE_Z * SCALE / 2f);
        int ix = MathUtils.clamp((int) (localX / SCALE), 0, SIZE_X - 2);
        int iz = MathUtils.clamp((int) (localZ / SCALE), 0, SIZE_Z - 2);

        float fx = (localX / SCALE) - ix;
        float fz = (localZ / SCALE) - iz;

        float h00 = heightMap[ix][iz];
        float h10 = heightMap[ix + 1][iz];
        float h01 = heightMap[ix][iz + 1];
        float h11 = heightMap[ix + 1][iz + 1];

        return MathUtils.lerp(MathUtils.lerp(h00, h10, fx), MathUtils.lerp(h01, h11, fx), fz);
    }

    public Vector3 getNormalAt(float worldX, float worldZ) {
        float localX = worldX + (SIZE_X * SCALE / 2f);
        float localZ = worldZ + (SIZE_Z * SCALE / 2f);
        int ix = MathUtils.clamp((int) (localX / SCALE), 0, SIZE_X - 2);
        int iz = MathUtils.clamp((int) (localZ / SCALE), 0, SIZE_Z - 2);

        Vector3 v00 = vertex(ix, iz);
        Vector3 v10 = vertex(ix + 1, iz);
        Vector3 v01 = vertex(ix, iz + 1);
        Vector3 v11 = vertex(ix + 1, iz + 1);

        Vector3 n1 = new Vector3(v11).sub(v00).crs(new Vector3(v10).sub(v00)).nor();
        Vector3 n2 = new Vector3(v01).sub(v00).crs(new Vector3(v11).sub(v00)).nor();
        return n1.lerp(n2, (localX / SCALE) - ix).nor();
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
        mb.part("pole", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
                new Material(ColorAttribute.createDiffuse(Color.WHITE))).cylinder(0.1f, 5f, 0.1f, 8);

        Material flagMat = new Material(ColorAttribute.createDiffuse(Color.RED));
        flagMat.set(new com.badlogic.gdx.graphics.g3d.attributes.IntAttribute(com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.CullFace, 0));

        mb.part("flag", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, flagMat)
                .rect(0.1f, 1.5f, 0f, 0.1f, 2.5f, 0f, 1.6f, 2.5f, 0f, 1.6f, 1.5f, 0f, 0f, 0f, 1f);

        flagInstance = new ModelInstance(mb.end());
        flagInstance.transform.setToTranslation(pos.x, pos.y + 2.5f, pos.z);
    }

    public void updateFlag(Vector3 cameraPosition) {
        if (flagInstance == null) return;
        float dist = cameraPosition.dst(holePosition);
        float scale = Math.min(1.0f + (Math.max(0, dist - 50f) * 0.015f), 4.0f);

        Vector3 dir = new Vector3(cameraPosition).sub(holePosition);
        float angle = MathUtils.atan2(dir.x, dir.z) * MathUtils.radiansToDegrees;

        flagInstance.transform.setToScaling(scale, scale, scale);
        flagInstance.transform.rotate(Vector3.Y, angle);
        flagInstance.transform.setTranslation(holePosition.x, holePosition.y + (2.5f * scale), holePosition.z);
    }

    private void createWaterPlane() {
        ModelBuilder builder = new ModelBuilder();
        float w = SIZE_X * SCALE;
        float h = SIZE_Z * SCALE;

        Model water = builder.createRect(
                0, waterLevel, h,
                w, waterLevel, h,
                w, waterLevel, 0,
                0, waterLevel, 0,
                0, 1, 0,
                new Material(ColorAttribute.createDiffuse(new Color(0.2f, 0.4f, 0.8f, 0.6f))),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        waterInstance = new ModelInstance(water);
        waterInstance.transform.setToTranslation(-w / 2f, 0, -h / 2f);
    }

    public void dispose() {
        for (ModelInstance c : chunks) c.model.dispose();
        if (waterInstance != null) waterInstance.model.dispose();
        for (Tree t : trees) t.dispose();
        if (holeMarker != null) holeMarker.model.dispose();
        if (flagInstance != null) flagInstance.model.dispose();
    }

    public TerrainType getTerrainTypeAt(float worldX, float worldZ) {
        int ix = MathUtils.clamp((int) ((worldX + SIZE_X * SCALE / 2f) / SCALE), 0, SIZE_X - 1);
        int iz = MathUtils.clamp((int) ((worldZ + SIZE_Z * SCALE / 2f) / SCALE), 0, SIZE_Z - 1);
        return terrainMap[ix][iz];
    }

    public ShotDifficulty getShotDifficulty(float x, float z, Vector3 aimDir) {
        ShotDifficulty diff = new ShotDifficulty();
        diff.terrainDifficulty = getTerrainTypeAt(x, z).difficulty;
        Vector3 n = getNormalAt(x, z);
        float sideHill = Math.abs(n.dot(new Vector3(aimDir).crs(Vector3.Y).nor()));
        diff.slopeDifficulty = 1.0f + ((1f - n.y) * 2.0f) + (sideHill * 1.5f);
        return diff;
    }

    public Vector3 getTeePosition() { return teeCenter.cpy(); }
    public Vector3 getHolePosition() { return holePosition; }
    public float getHoleSize() { return holeSize; }
    public float getWaterLevel() { return waterLevel; }
    public List<Tree> getTrees() { return trees; }

    public enum TerrainType {
        TEE(0.40f, 2.0f, 1.1f, 0.8f, new Color(0.2f, 0.5f, 0.2f, 1f)),
        FAIRWAY(0.6f, 0.2f, 1.05f, 1.0f, new Color(0.1f, 0.4f, 0.1f, 1f)),
        ROUGH(1.70f, 4.5f, 1.5f, 1.4f, new Color(0.02f, 0.15f, 0.02f, 1f)),
        SAND(4.2f, 12.0f, 2.5f, 1.8f, new Color(0.85f, 0.8f, 0.5f, 1f)),
        GREEN(0.2f, 0.1f, 1.05f, 0.9f, new Color(0.1f, 0.6f, 0.1f, 1f)),
        STONE(0.1f, 0.1f, 1.05f, 1.5f, new Color(0.2f, 0.2f, 0.23f, 1f));

        public final float kineticFriction, rollingResistance, staticMultiplier, difficulty;
        public final Color color;
        TerrainType(float kf, float rr, float sm, float diff, Color col) {
            this.kineticFriction = kf; this.rollingResistance = rr;
            this.staticMultiplier = sm; this.difficulty = diff; this.color = col;
        }
    }

    public static class Tree {
        private final ModelInstance trunk, foliage;
        private final Vector3 pos;
        private final float tH, tR, fH, fR;

        Tree(float x, float y, float z, float th, float tr, float fr) {
            this.pos = new Vector3(x, y, z);
            this.tH = th; this.tR = tr; this.fH = th; this.fR = fr;
            ModelBuilder mb = new ModelBuilder();
            trunk = new ModelInstance(mb.createCylinder(tr*2, th, tr*2, 16, new Material(ColorAttribute.createDiffuse(new Color(0.4f, 0.2f, 0.1f, 1f))), VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));
            trunk.transform.setToTranslation(x, y + th/2, z);
            foliage = new ModelInstance(mb.createSphere(fr*2, fr*2, fr*2, 16, 16, new Material(ColorAttribute.createDiffuse(Color.GREEN)), VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));
            foliage.transform.setToTranslation(x, y + th + fr, z);
        }

        void render(ModelBatch b, Environment e) { b.render(trunk, e); b.render(foliage, e); }
        void dispose() { trunk.model.dispose(); foliage.model.dispose(); }
        public Vector3 getPosition() { return pos.cpy(); }
        public float getTrunkHeight() { return tH; }
        public float getTrunkRadius() { return tR; }
        public float getFoliageRadius() { return fR; }
    }
}