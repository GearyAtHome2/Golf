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
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Quaternion;
import org.example.ball.ShotDifficulty;
import org.example.terrain.features.PuttingGreenGenerator;

import java.util.ArrayList;
import java.util.List;

public class Terrain {

    private final int SIZE_X = 160;
    private final int SIZE_Z;
    private final int CHUNK_Z = 64;
    private final float SCALE = 1f;
    private final float CUP_DEPTH = 0.5f;

    private ModelInstance waterInstance;
    private final List<ModelInstance> chunks = new ArrayList<>();
    private TerrainType[][] terrainMap;
    private float[][] heightMap;
    private final List<Tree> trees = new ArrayList<>();
    private final List<Monolith> monoliths = new ArrayList<>();

    private Vector3 teeCenter;
    private Vector3 holePosition;
    private Hole physicalHole;
    private ModelInstance flagInstance;
    private ModelInstance teeInstance;
    private float holeSize = 0.7f;
    private float waterLevel;
    private float greenMinH = Float.MAX_VALUE, greenMaxH = -Float.MAX_VALUE;

    private final float CELL_SIZE = 10.0f;
    private List<Tree>[][] treeGrid;
    private List<Monolith>[][] monolithGrid;
    private int gridCols, gridRows;

    private final Vector3 tempV1 = new Vector3();

    public Terrain(ITerrainGenerator generator, float initialWaterLevel, int dynamicSizeZ) {
        this.SIZE_Z = dynamicSizeZ;
        this.waterLevel = initialWaterLevel;
        this.terrainMap = new TerrainType[SIZE_X][SIZE_Z];
        this.heightMap = new float[SIZE_X][SIZE_Z];
        this.teeCenter = new Vector3();
        this.holePosition = new Vector3();

        initializeGrids();
        generator.generate(terrainMap, heightMap, trees, monoliths, teeCenter, holePosition);

        // Capture the surface height for positioning
        float surfaceY = getSurfaceHeightAt(holePosition.x, holePosition.z);
        this.holePosition.set(holePosition.x, surfaceY, holePosition.z);

        // Initialize the Hole with the correct surface normal and radius
        this.physicalHole = new Hole(holePosition, getNormalAt(holePosition.x, holePosition.z), holeSize / 2f);

        this.teeCenter.y = getSurfaceHeightAt(teeCenter.x, teeCenter.z);

        if (generator instanceof ClassicGenerator) {
            this.waterLevel = ((ClassicGenerator) generator).getData().getWaterLevel();
        } else {
            this.waterLevel = -1;
        }

        populateGrids();
        calculateGreenBounds();
        createWaterPlane();

        for (int z = 0; z < SIZE_Z; z += CHUNK_Z) {
            chunks.add(buildChunk(z, Math.min(z + CHUNK_Z, SIZE_Z)));
        }

        createFlag(holePosition);

        if (!(generator instanceof PuttingGreenGenerator)) {
            createTee(teeCenter);
        }
    }

    @SuppressWarnings("unchecked")
    private void initializeGrids() {
        gridCols = MathUtils.ceil((SIZE_X * SCALE) / CELL_SIZE);
        gridRows = MathUtils.ceil((SIZE_Z * SCALE) / CELL_SIZE);
        treeGrid = new ArrayList[gridCols][gridRows];
        monolithGrid = new ArrayList[gridCols][gridRows];

        for (int x = 0; x < gridCols; x++) {
            for (int z = 0; z < gridRows; z++) {
                treeGrid[x][z] = new ArrayList<>();
                monolithGrid[x][z] = new ArrayList<>();
            }
        }
    }

    private void populateGrids() {
        float offsetX = (SIZE_X * SCALE) / 2f;
        float offsetZ = (SIZE_Z * SCALE) / 2f;

        for (Tree t : trees) {
            int gx = MathUtils.clamp((int)((t.pos.x + offsetX) / CELL_SIZE), 0, gridCols - 1);
            int gz = MathUtils.clamp((int)((t.pos.z + offsetZ) / CELL_SIZE), 0, gridRows - 1);
            treeGrid[gx][gz].add(t);
        }

        for (Monolith m : monoliths) {
            int gx = MathUtils.clamp((int)((m.pos.x + offsetX) / CELL_SIZE), 0, gridCols - 1);
            int gz = MathUtils.clamp((int)((m.pos.z + offsetZ) / CELL_SIZE), 0, gridRows - 1);
            monolithGrid[gx][gz].add(m);
        }
    }

    private void calculateGreenBounds() {
        List<Float> greenHeights = new ArrayList<>();
        float sum = 0;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (terrainMap[x][z] == TerrainType.GREEN) {
                    float h = heightMap[x][z];
                    greenHeights.add(h);
                    sum += h;
                }
            }
        }

        if (greenHeights.isEmpty()) {
            greenMinH = 0; greenMaxH = 10;
            return;
        }

        float averageHeight = sum / greenHeights.size();
        float threshold = 5.0f;

        greenMinH = Float.MAX_VALUE;
        greenMaxH = -Float.MAX_VALUE;
        boolean validFound = false;

        for (float h : greenHeights) {
            if (Math.abs(h - averageHeight) > threshold) continue;
            greenMinH = Math.min(greenMinH, h);
            greenMaxH = Math.max(greenMaxH, h);
            validFound = true;
        }

        if (!validFound) {
            greenMinH = averageHeight - 1;
            greenMaxH = averageHeight + 1;
        }
    }

    public float getMaxDistanceX() { return (SIZE_X * SCALE) / 2f; }
    public float getMaxDistanceZ() { return (SIZE_Z * SCALE) / 2f; }

    public boolean isPointOutOfBounds(float worldX, float worldZ) {
        return Math.abs(worldX) > getMaxDistanceX() || Math.abs(worldZ) > getMaxDistanceZ();
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
        float y = heightMap[x][z];
        return new Vector3(x * SCALE, y, z * SCALE);
    }

    public void render(ModelBatch batch, Environment env) {
        for (ModelInstance chunk : chunks) batch.render(chunk, env);
        if (waterInstance != null) batch.render(waterInstance, env);
        for (Tree t : trees) t.render(batch, env);
        for (Monolith b : monoliths) b.render(batch, env);
        if (physicalHole != null) physicalHole.render(batch, env);
        if (flagInstance != null) batch.render(flagInstance, env);
        if (teeInstance != null) batch.render(teeInstance, env);
    }

    public float getSurfaceHeightAt(float worldX, float worldZ) {
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

    /**
     * Corrected height logic. We use the holePosition's Y (the surface)
     * as the starting point for the sink depth.
     */
    public float getHeightAt(float worldX, float worldZ) {
        float baseHeight = getSurfaceHeightAt(worldX, worldZ);

        float distToHole = Vector2.dst(worldX, worldZ, holePosition.x, holePosition.z);
        float rimRadius = holeSize / 2.0f;

        if (distToHole < rimRadius) {
            float ratio = distToHole / rimRadius;
            // The sink happens relative to the surface height AT the hole center
            return baseHeight - (CUP_DEPTH * (1.0f - ratio));
        }

        return baseHeight;
    }

    public Vector3 getNormalAt(float worldX, float worldZ) {
        float rimRadius = holeSize / 2.0f;
        float influenceRange = 0.15f;
        float pullStrength = 0.4f;
        float sampleEps = 0.02f;

        float distToHole = Vector2.dst(worldX, worldZ, holePosition.x, holePosition.z);

        // We use getHeightAt here because the normal needs to represent the "pit"
        // to suck the ball in physically.
        float hL = getHeightAt(worldX - sampleEps, worldZ);
        float hR = getHeightAt(worldX + sampleEps, worldZ);
        float hD = getHeightAt(worldX, worldZ - sampleEps);
        float hU = getHeightAt(worldX, worldZ + sampleEps);

        Vector3 normal = new Vector3(hL - hR, 2.0f * sampleEps, hD - hU).nor();

        if (distToHole > rimRadius && distToHole < rimRadius + influenceRange) {
            float t = 1.0f - ((distToHole - rimRadius) / influenceRange);
            tempV1.set(holePosition.x - worldX, 0, holePosition.z - worldZ).nor();

            normal.x += tempV1.x * t * pullStrength;
            normal.z += tempV1.z * t * pullStrength;
            normal.nor();
        }

        return normal;
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
        updateFlagTransform(pos, 1.0f, 0f);
    }

    private void updateFlagTransform(Vector3 pos, float scale, float rotationY) {
        if (flagInstance == null) return;
        flagInstance.transform.setToTranslation(pos);
        flagInstance.transform.rotate(Vector3.Y, rotationY);
        flagInstance.transform.scale(scale, scale, scale);
        flagInstance.transform.translate(0, 2.5f, 0);
    }

    private void createTee(Vector3 pos) {
        ModelBuilder mb = new ModelBuilder();
        Model teeModel = mb.createCylinder(0.03f, 0.15f, 0.03f, 8,
                new Material(ColorAttribute.createDiffuse(Color.WHITE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        teeInstance = new ModelInstance(teeModel);
        teeInstance.transform.setToTranslation(pos.x, pos.y + 0.075f, pos.z);
    }

    public void updateFlag(Vector3 cameraPosition) {
        if (flagInstance == null) return;
        float dist = cameraPosition.dst(holePosition);
        float scale = Math.min(1.0f + (Math.max(0, dist - 50f) * 0.015f), 4.0f);
        Vector3 dir = new Vector3(cameraPosition).sub(holePosition);
        float angle = MathUtils.atan2(dir.x, dir.z) * MathUtils.radiansToDegrees;
        updateFlagTransform(holePosition, scale, angle);
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
        for (Monolith b : monoliths) b.dispose();
        if (physicalHole != null) physicalHole.dispose();
        if (flagInstance != null) flagInstance.model.dispose();
        if (teeInstance != null) teeInstance.model.dispose();
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

    public List<Tree> getTreesAt(float worldX, float worldZ) {
        int gx = MathUtils.clamp((int)((worldX + (SIZE_X * SCALE / 2f)) / CELL_SIZE), 0, gridCols - 1);
        int gz = MathUtils.clamp((int)((worldZ + (SIZE_Z * SCALE / 2f)) / CELL_SIZE), 0, gridRows - 1);
        List<Tree> nearby = new ArrayList<>(treeGrid[gx][gz]);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                int nx = gx + x, nz = gz + z;
                if (nx >= 0 && nx < gridCols && nz >= 0 && nz < gridRows) nearby.addAll(treeGrid[nx][nz]);
            }
        }
        return nearby;
    }

    public List<Monolith> getMonolithsAt(float worldX, float worldZ) {
        int gx = MathUtils.clamp((int)((worldX + (SIZE_X * SCALE / 2f)) / CELL_SIZE), 0, gridCols - 1);
        int gz = MathUtils.clamp((int)((worldZ + (SIZE_Z * SCALE / 2f)) / CELL_SIZE), 0, gridRows - 1);
        List<Monolith> nearby = new ArrayList<>(monolithGrid[gx][gz]);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                int nx = gx + x, nz = gz + z;
                if (nx >= 0 && nx < gridCols && nz >= 0 && nz < gridRows) nearby.addAll(monolithGrid[nx][nz]);
            }
        }
        return nearby;
    }

    public void setHolePosition(Vector3 newPos) {
        this.holePosition.set(newPos.x, getSurfaceHeightAt(newPos.x, newPos.z), newPos.z);
        if (physicalHole != null) physicalHole.dispose();
        this.physicalHole = new Hole(holePosition, getNormalAt(holePosition.x, holePosition.z), holeSize / 2f);
        if (flagInstance != null) updateFlagTransform(holePosition, 1.0f, 0f);
    }

    public void setTeePosition(Vector3 newPos) {
        this.teeCenter.set(newPos.x, getSurfaceHeightAt(newPos.x, newPos.z), newPos.z);
        if (teeInstance != null) teeInstance.transform.setToTranslation(teeCenter.x, teeCenter.y + 0.075f, teeCenter.z);
    }

    public Vector3 getTeePosition() { return teeCenter; }
    public Vector3 getHolePosition() { return holePosition; }
    public float getHoleSize() { return holeSize; }
    public float getWaterLevel() { return waterLevel; }
    public List<Tree> getTrees() { return trees; }
    public List<Monolith> getMonoliths() { return monoliths; }

    public enum TerrainType {
        TEE(0.4f, 2.0f, 1.1f, 0.8f, 0.2f, new Color(0.2f, 0.5f, 0.2f, 1f)),
        FAIRWAY(0.4f, 0.2f, 1.05f, 1.0f, 0.2f, new Color(0.1f, 0.4f, 0.1f, 1f)),
        ROUGH(1.2f, 4.5f, 1.5f, 1.4f, 0.7f, new Color(0.02f, 0.15f, 0.02f, 1f)),
        SAND(2.5f, 12.0f, 2.5f, 1.8f, 0.9f, new Color(0.85f, 0.8f, 0.5f, 1f)),
        GREEN(0.2f, 0.1f, 1.05f, 0.9f, 0.6f, new Color(0.1f, 0.6f, 0.1f, 1f)),
        STONE(0.1f, 0.1f, 1.05f, 1.5f, 0.02f, new Color(0.18f, 0.18f, 0.2f, 1f));
        public final float kineticFriction, rollingResistance, staticMultiplier, difficulty, softness;
        public final Color color;
        TerrainType(float kf, float rr, float sm, float diff, float softness, Color col) {
            this.kineticFriction = kf;
            this.rollingResistance = rr;
            this.staticMultiplier = sm;
            this.difficulty = diff;
            this.softness = softness;
            this.color = col;
        }
    }

    public static class Hole {
        private final ModelInstance cupInstance;
        public Hole(Vector3 pos, Vector3 surfaceNormal, float radius) {
            ModelBuilder mb = new ModelBuilder();
            Model discModel = mb.createCylinder(radius * 2f, 0.01f, radius * 2f, 24,
                    new Material(ColorAttribute.createDiffuse(new Color(0.02f, 0.02f, 0.02f, 1f))),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

            cupInstance = new ModelInstance(discModel);
            cupInstance.transform.idt();
            Quaternion q = new Quaternion();
            q.setFromCross(Vector3.Y, surfaceNormal);
            cupInstance.transform.rotate(q);
            Vector3 finalPos = new Vector3(pos).add(new Vector3(surfaceNormal).scl(0.005f));
            cupInstance.transform.setTranslation(finalPos);
        }
        public void render(ModelBatch b, Environment e) { b.render(cupInstance, e); }
        public void dispose() { cupInstance.model.dispose(); }
    }

    public static class Tree {
        private final ModelInstance trunk, foliage;
        private final Vector3 pos;
        private final float tH, tR, fR;
        private final TreeScheme scheme;
        private float actualFoliageHeight;
        public Tree(float x, float y, float z, float th, float tr, float fr, TreeScheme scheme, java.util.Random rng) {
            this.pos = new Vector3(x, y, z);
            this.tH = th; this.tR = tr; this.fR = fr;
            this.scheme = scheme;
            ModelBuilder mb = new ModelBuilder();
            Material barkMat = new Material(ColorAttribute.createDiffuse(scheme.getRandomBark()));
            Material leafMat = new Material(ColorAttribute.createDiffuse(scheme.getRandomFoliage()));
            trunk = new ModelInstance(mb.createCylinder(tr * 2, th, tr * 2, 16, barkMat, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));
            trunk.transform.setToTranslation(x, y + th / 2, z);
            if (scheme == TreeScheme.FIR) {
                float skirtMultiplier = 0.6f + (rng.nextFloat() * 0.31f);
                this.actualFoliageHeight = (th * skirtMultiplier) + (fr * 2);
                foliage = new ModelInstance(mb.createCone(fr * 2.5f, actualFoliageHeight, fr * 2.5f, 16, leafMat, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));
                foliage.transform.setToTranslation(x, y + th + (fr * 2) - (actualFoliageHeight / 2f), z);
            } else {
                this.actualFoliageHeight = fr * 2;
                foliage = new ModelInstance(mb.createSphere(fr * 2, fr * 2, fr * 2, 16, 16, leafMat, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));
                foliage.transform.setToTranslation(x, y + th+fr/4, z);
            }
        }
        public boolean isInsideFoliage(Vector3 ballPos, float ballRadius) {
            if (scheme == TreeScheme.FIR) {
                float topY = pos.y + tH + (fR * 2);
                float baseY = topY - actualFoliageHeight;
                if (ballPos.y > topY + ballRadius || ballPos.y < baseY - ballRadius) return false;
                float relativeY = MathUtils.clamp((topY - ballPos.y) / actualFoliageHeight, 0, 1);
                return Vector3.dst(ballPos.x, 0, ballPos.z, pos.x, 0, pos.z) < (relativeY * (fR * 1.25f)) + ballRadius;
            } else return ballPos.dst(pos.x, pos.y + tH + fR, pos.z) < (fR + ballRadius);
        }
        public void render(ModelBatch b, Environment e) { b.render(trunk, e); b.render(foliage, e); }
        public void dispose() { trunk.model.dispose(); foliage.model.dispose(); }
        public Vector3 getPosition() { return pos; }
        public float getTrunkHeight() { return tH; }
        public float getTrunkRadius() { return tR; }
    }

    public static class Monolith {
        private final ModelInstance instance;
        private final Vector3 pos;
        private final float width, height, depth;
        private float rotationY;
        public Monolith(float x, float y, float z, float w, float h, float d, float rotation) {
            this.pos = new Vector3(x, y, z);
            this.width = w; this.height = h; this.depth = d; this.rotationY = rotation;
            ModelBuilder mb = new ModelBuilder();
            instance = new ModelInstance(mb.createBox(w, h, d, new Material(ColorAttribute.createDiffuse(new Color(0.15f, 0.15f, 0.16f, 1f))), VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));
            updateTransform();
        }
        private void updateTransform() { instance.transform.setToTranslation(pos.x, pos.y + height / 2f, pos.z); instance.transform.rotate(Vector3.Y, rotationY); }
        public void render(ModelBatch b, Environment e) { b.render(instance, e); }
        public void dispose() { if (instance.model != null) instance.model.dispose(); }
        public Vector3 getPosition() { return pos; }
        public float getWidth() { return width; }
        public float getHeight() { return height; }
        public float getDepth() { return depth; }
        public float getRotationY() { return rotationY; }
    }

    public enum TreeScheme {
        OAK(new Color(0.35f, 0.25f, 0.15f, 1f), new Color(0.45f, 0.35f, 0.25f, 1f), new Color(0.1f, 0.4f, 0.1f, 1f), new Color(0.2f, 0.5f, 0.2f, 1f)),
        BIRCH(new Color(0.85f, 0.85f, 0.85f, 1f), new Color(1f, 1f, 1f, 1f), new Color(0.3f, 0.6f, 0.1f, 1f), new Color(0.5f, 0.8f, 0.2f, 1f)),
        REDWOOD(new Color(0.3f, 0.15f, 0.1f, 1f), new Color(0.5f, 0.2f, 0.15f, 1f), new Color(0.05f, 0.25f, 0.05f, 1f), new Color(0.15f, 0.35f, 0.15f, 1f)),
        FIR(new Color(0.2f, 0.15f, 0.1f, 1f), new Color(0.3f, 0.25f, 0.2f, 1f), new Color(0.05f, 0.2f, 0.05f, 1f), new Color(0.1f, 0.3f, 0.1f, 1f)),
        AUTUMN_MAPLE(new Color(0.25f, 0.2f, 0.15f, 1f), new Color(0.35f, 0.3f, 0.25f, 1f), new Color(0.8f, 0.2f, 0.1f, 1f), new Color(1f, 0.6f, 0.0f, 1f)),
        CHERRY_BLOSSOM(new Color(0.2f, 0.18f, 0.18f, 1f), new Color(0.3f, 0.25f, 0.25f, 1f), new Color(1f, 0.7f, 0.75f, 1f), new Color(1f, 0.85f, 0.9f, 1f)),
        DEAD_GRAY(new Color(0.2f, 0.2f, 0.2f, 1f), new Color(0.4f, 0.4f, 0.4f, 1f), new Color(0.1f, 0.1f, 0.1f, 1f), new Color(0.3f, 0.3f, 0.3f, 1f));
        private final Color barkMin, barkMax, leafMin, leafMax;
        TreeScheme(Color bMin, Color bMax, Color lMin, Color lMax) { this.barkMin = bMin; this.barkMax = bMax; this.leafMin = lMin; this.leafMax = lMax; }
        public Color getRandomBark() { return barkMin.cpy().lerp(barkMax, MathUtils.random()); }
        public Color getRandomFoliage() { return leafMin.cpy().lerp(leafMax, MathUtils.random()); }
    }
}