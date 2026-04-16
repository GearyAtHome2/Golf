package org.example.terrain;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.example.ball.ShotDifficulty;
import org.example.terrain.features.PuttingGreenGenerator;
import org.example.terrain.objects.Monolith;
import org.example.terrain.objects.TerrainObject;
import org.example.terrain.objects.Tree;
import org.example.util.PerfLog;

import java.util.ArrayList;
import java.util.List;

public class Terrain {

    private final int SIZE_X = 160;
    private final int SIZE_Z;
    private final int CHUNK_Z = 64;
    private final float SCALE = 1f;

    private ModelInstance waterInstance;
    private final List<ModelInstance> chunks = new ArrayList<>();
    private TerrainType[][] terrainMap;
    private float[][] heightMap;

    private final List<TerrainObject> allObjects = new ArrayList<>();
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

    // Flag pole wobble — damped sinusoidal oscillation triggered on ball impact
    private float wobbleAmplitude = 0f;
    private long  wobbleStartNanos = 0L;
    private static final float WOBBLE_FREQUENCY = 12f; // rad/s (~2 Hz)
    private static final float WOBBLE_DECAY     = 3f;  // e^-3 ≈ 5% after 1 s

    // Flag pole raise — lifts the flag when the ball approaches so it doesn't obstruct the putt
    private float flagRaiseT = 0f;
    private static final float FLAG_RAISE_RADIUS = 4f;  // units — raise begins within this distance
    private static final float FLAG_RAISE_MAX    = 0.5f; // units lifted when fully raised
    private static final float FLAG_RAISE_SPEED  = 5f;  // lerp speed (reaches ~95% in ~0.6 s)

    private final float CELL_SIZE = 10.0f;
    private List<TerrainObject>[][] objectGrid;
    private int gridCols, gridRows;
    private final Vector3 tempRayDir = new Vector3();

    public Terrain(ITerrainGenerator generator, float initialWaterLevel, int dynamicSizeZ) {
        this.SIZE_Z = dynamicSizeZ;
        this.waterLevel = initialWaterLevel;
        this.terrainMap = new TerrainType[SIZE_X][SIZE_Z];
        this.heightMap = new float[SIZE_X][SIZE_Z];
        this.teeCenter = new Vector3();
        this.holePosition = new Vector3();

        long tTotal = PerfLog.now();
        PerfLog.snapshot("Terrain() start  SIZE_X=" + SIZE_X + " SIZE_Z=" + SIZE_Z);

        initializeGrids();

        long tGen = PerfLog.now();
        generator.generate(terrainMap, heightMap, trees, monoliths, teeCenter, holePosition);
        PerfLog.log("generator.generate()", tGen);

        allObjects.addAll(trees);
        allObjects.addAll(monoliths);

        float surfaceY = getSurfaceHeightAt(holePosition.x, holePosition.z);
        this.holePosition.set(holePosition.x, surfaceY, holePosition.z);
        this.physicalHole = new Hole(holePosition, getNormalAt(holePosition.x, holePosition.z), holeSize / 2f);
        this.teeCenter.y = getSurfaceHeightAt(teeCenter.x, teeCenter.z);

        if (generator instanceof ClassicGenerator) {
            this.waterLevel = ((ClassicGenerator) generator).getData().getWaterLevel();
        } else {
            this.waterLevel = -1;
        }

        long tGrid = PerfLog.now();
        populateGrids();
        PerfLog.log("populateGrids()", tGrid);

        long tGreenBounds = PerfLog.now();
        calculateGreenBounds();
        PerfLog.log("calculateGreenBounds()", tGreenBounds);

        long tWater = PerfLog.now();
        createWaterPlane();
        PerfLog.log("createWaterPlane()", tWater);

        PerfLog.snapshot("before buildChunks  count=" + (int) Math.ceil((float) SIZE_Z / CHUNK_Z));
        long tChunks = PerfLog.now();
        for (int z = 0; z < SIZE_Z; z += CHUNK_Z) {
            chunks.add(buildChunk(z, Math.min(z + CHUNK_Z, SIZE_Z)));
        }
        PerfLog.log("buildChunks (all)", tChunks);

        long tFlag = PerfLog.now();
        createFlag(holePosition);
        PerfLog.log("createFlag()", tFlag);

        if (!(generator instanceof PuttingGreenGenerator)) {
            long tTee = PerfLog.now();
            createTee(teeCenter);
            PerfLog.log("createTee()", tTee);
        }

        PerfLog.total("Terrain() constructor TOTAL", tTotal);
    }

    @SuppressWarnings("unchecked")
    private void initializeGrids() {
        gridCols = MathUtils.ceil((SIZE_X * SCALE) / CELL_SIZE);
        gridRows = MathUtils.ceil((SIZE_Z * SCALE) / CELL_SIZE);
        objectGrid = new ArrayList[gridCols][gridRows];

        for (int x = 0; x < gridCols; x++) {
            for (int z = 0; z < gridRows; z++) {
                objectGrid[x][z] = new ArrayList<>();
            }
        }
    }

    private void populateGrids() {
        float offsetX = (SIZE_X * SCALE) / 2f;
        float offsetZ = (SIZE_Z * SCALE) / 2f;

        for (TerrainObject obj : allObjects) {
            int gx = MathUtils.clamp((int) ((obj.getPosition().x + offsetX) / CELL_SIZE), 0, gridCols - 1);
            int gz = MathUtils.clamp((int) ((obj.getPosition().z + offsetZ) / CELL_SIZE), 0, gridRows - 1);
            objectGrid[gx][gz].add(obj);
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
            greenMinH = 0;
            greenMaxH = 10;
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

    public void render(ModelBatch batch, Environment env) {
        for (ModelInstance chunk : chunks) {
            batch.render(chunk, env);
        }
        batch.flush();

        if (waterInstance != null) {
            Gdx.gl.glEnable(GL20.GL_POLYGON_OFFSET_FILL);
            Gdx.gl.glPolygonOffset(-0.1f, -0.002f);
            Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);

            batch.render(waterInstance, env);
            batch.flush();

            Gdx.gl.glDisable(GL20.GL_POLYGON_OFFSET_FILL);
            Gdx.gl.glDepthFunc(GL20.GL_LESS);
        }

        for (TerrainObject obj : allObjects) obj.render(batch, env);
        if (physicalHole != null) physicalHole.render(batch, env);
        if (flagInstance != null) batch.render(flagInstance, env);
        if (teeInstance != null) batch.render(teeInstance, env);
    }

    public void updateCameraOcclusion(Vector3 cameraPos, Vector3 ballPos, float delta) {
        float offsetX = (SIZE_X * SCALE) / 2f;
        float offsetZ = (SIZE_Z * SCALE) / 2f;

        for (TerrainObject obj : allObjects) {
            obj.setTargetAlpha(1.0f);
        }

        int x1 = MathUtils.clamp((int) ((cameraPos.x + offsetX) / CELL_SIZE), 0, gridCols - 1);
        int z1 = MathUtils.clamp((int) ((cameraPos.z + offsetZ) / CELL_SIZE), 0, gridRows - 1);
        int x2 = MathUtils.clamp((int) ((ballPos.x + offsetX) / CELL_SIZE), 0, gridCols - 1);
        int z2 = MathUtils.clamp((int) ((ballPos.z + offsetZ) / CELL_SIZE), 0, gridRows - 1);

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        tempRayDir.set(ballPos).sub(cameraPos).nor();
        float rayLength = cameraPos.dst(ballPos);

        for (int gx = minX; gx <= maxX; gx++) {
            for (int gz = minZ; gz <= maxZ; gz++) {
                for (TerrainObject obj : objectGrid[gx][gz]) {
                    float radius = (obj instanceof Tree) ? ((Tree) obj).fR :
                            (obj instanceof Monolith) ? Math.max(((Monolith) obj).width, ((Monolith) obj).depth) : 1.0f;

                    if (isObjectOccluding(cameraPos, tempRayDir, rayLength, obj.getPosition(), radius)) {
                        obj.setTargetAlpha(0.3f);
                    }
                }
            }
        }

        for (TerrainObject obj : allObjects) {
            obj.update(delta);
        }
    }

    private boolean isObjectOccluding(Vector3 cam, Vector3 dir, float len, Vector3 objPos, float radius) {
        float camX = cam.x, camZ = cam.z;
        float ballX = camX + dir.x * len, ballZ = camZ + dir.z * len;
        float objX = objPos.x, objZ = objPos.z;
        float l2 = Vector2.dst2(camX, camZ, ballX, ballZ);
        if (l2 == 0) return Vector2.dst(camX, camZ, objX, objZ) < radius;
        float t = MathUtils.clamp(((objX - camX) * (ballX - camX) + (objZ - camZ) * (ballZ - camZ)) / l2, 0, 1);
        float distSq = Vector2.dst2(objX, objZ, camX + t * (ballX - camX), camZ + t * (ballZ - camZ));
        return distSq < (radius * radius);
    }

    public List<Tree> getTreesAt(float worldX, float worldZ) {
        List<Tree> result = new ArrayList<>();
        for (TerrainObject obj : getNearbyObjects(worldX, worldZ)) {
            if (obj instanceof Tree) result.add((Tree) obj);
        }
        return result;
    }

    public List<Monolith> getMonolithsAt(float worldX, float worldZ) {
        List<Monolith> result = new ArrayList<>();
        for (TerrainObject obj : getNearbyObjects(worldX, worldZ)) {
            if (obj instanceof Monolith) result.add((Monolith) obj);
        }
        return result;
    }

    private List<TerrainObject> getNearbyObjects(float worldX, float worldZ) {
        int gx = MathUtils.clamp((int) ((worldX + (SIZE_X * SCALE / 2f)) / CELL_SIZE), 0, gridCols - 1);
        int gz = MathUtils.clamp((int) ((worldZ + (SIZE_Z * SCALE / 2f)) / CELL_SIZE), 0, gridRows - 1);
        List<TerrainObject> nearby = new ArrayList<>();
        int radius = 2;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int nx = gx + x, nz = gz + z;
                if (nx >= 0 && nx < gridCols && nz >= 0 && nz < gridRows) {
                    nearby.addAll(objectGrid[nx][nz]);
                }
            }
        }
        return nearby;
    }

    public float getSurfaceHeightAt(float worldX, float worldZ) {
        float localX = worldX + (SIZE_X * SCALE / 2f);
        float localZ = worldZ + (SIZE_Z * SCALE / 2f);
        int ix = MathUtils.clamp((int) (localX / SCALE), 0, SIZE_X - 2);
        int iz = MathUtils.clamp((int) (localZ / SCALE), 0, SIZE_Z - 2);
        float fx = (localX / SCALE) - ix;
        float fz = (localZ / SCALE) - iz;
        float h00 = heightMap[ix][iz], h10 = heightMap[ix + 1][iz];
        float h01 = heightMap[ix][iz + 1], h11 = heightMap[ix + 1][iz + 1];
        return MathUtils.lerp(MathUtils.lerp(h00, h10, fx), MathUtils.lerp(h01, h11, fx), fz);
    }

    public float getHeightAt(float worldX, float worldZ) {
        float baseHeight = getSurfaceHeightAt(worldX, worldZ);
        float distToHole = Vector2.dst(worldX, worldZ, holePosition.x, holePosition.z);
        float rimRadius = holeSize / 2.0f;
        float floorBoundary = rimRadius * 0.8f;
        float outerBoundary = rimRadius * 1.2f;
        float targetDepth = 0.4f;
        if (distToHole <= floorBoundary) return baseHeight - targetDepth;
        if (distToHole < outerBoundary) {
            float t = (distToHole - floorBoundary) / (outerBoundary - floorBoundary);
            return baseHeight - (targetDepth * (1.0f - t));
        }
        return baseHeight;
    }

    public Vector3 getNormalAt(float worldX, float worldZ) {
        return getNormalAt(worldX, worldZ, true);
    }

    public Vector3 getNormalAt(float worldX, float worldZ, boolean forPhysics) {
        float rimRadius = holeSize / 2.0f;
        float sampleEps = 0.005f;
        float distToHole = Vector2.dst(worldX, worldZ, holePosition.x, holePosition.z);
        float hL = getHeightAt(worldX - sampleEps, worldZ), hR = getHeightAt(worldX + sampleEps, worldZ);
        float hD = getHeightAt(worldX, worldZ - sampleEps), hU = getHeightAt(worldX, worldZ + sampleEps);
        float yWeight = 2.0f;
        if (forPhysics && distToHole < rimRadius * 1.2f && distToHole > rimRadius * 0.8f) yWeight = 1.5f;
        Vector3 normal = new Vector3(hL - hR, yWeight * sampleEps, hD - hU).nor();
        if (forPhysics && distToHole <= rimRadius * 0.8f) {
            float bL = getSurfaceHeightAt(worldX - sampleEps, worldZ), bR = getSurfaceHeightAt(worldX + sampleEps, worldZ);
            float bD = getSurfaceHeightAt(worldX, worldZ - sampleEps), bU = getSurfaceHeightAt(worldX, worldZ + sampleEps);
            normal.set(bL - bR, 2.0f * sampleEps, bD - bU).nor();
        }
        return normal;
    }

    private ModelInstance buildChunk(int zStart, int zEnd) {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        MeshPartBuilder meshBuilder = builder.part("terrain_chunk", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.ColorPacked,
                new Material());
        for (int x = 0; x < SIZE_X - 1; x++) {
            for (int z = zStart; z < zEnd && z < SIZE_Z - 1; z++) {
                Vector3 v00 = vertex(x, z), v10 = vertex(x + 1, z), v01 = vertex(x, z + 1), v11 = vertex(x + 1, z + 1);
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
        updateFlagTransform(pos, 1.0f, 0f, 0f, 0f);
    }

    private void updateFlagTransform(Vector3 pos, float scale, float rotationY, float wobbleDegrees, float yOffset) {
        if (flagInstance == null) return;
        flagInstance.transform.setToTranslation(pos.x, pos.y + yOffset, pos.z);
        flagInstance.transform.rotate(Vector3.Y, rotationY);
        // Tilt around X pivoting at the pole base — translate(0, 2.5) keeps base grounded
        flagInstance.transform.rotate(Vector3.X, wobbleDegrees);
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

    public void updateFlag(Vector3 cameraPosition, Vector3 ballPosition) {
        if (flagInstance == null) return;
        float dist = cameraPosition.dst(holePosition);
        float distFactor = Math.max(0, dist - 50f);
        float scale = 1.0f + (float) Math.sqrt(distFactor) * 0.15f;
        scale *= org.example.hud.HUD.UI_SCALE;
        Vector3 dir = new Vector3(cameraPosition).sub(holePosition);
        float angle = MathUtils.atan2(dir.x, dir.z) * MathUtils.radiansToDegrees;

        float t = (System.nanoTime() - wobbleStartNanos) / 1_000_000_000f;
        float wobble = wobbleAmplitude * (float) Math.exp(-WOBBLE_DECAY * t) * MathUtils.sin(WOBBLE_FREQUENCY * t);

        float ballDist = ballPosition.dst(holePosition);
        float raiseTarget = MathUtils.clamp(1f - (ballDist / FLAG_RAISE_RADIUS), 0f, 1f);
        flagRaiseT = MathUtils.lerp(flagRaiseT, raiseTarget, FLAG_RAISE_SPEED * Gdx.graphics.getDeltaTime());

        updateFlagTransform(holePosition, scale, angle, wobble, flagRaiseT * FLAG_RAISE_MAX);
    }

    /** Called by ball physics when the ball strikes the flag pole. */
    public void triggerFlagWobble(float impactSpeed) {
        wobbleAmplitude = Math.min(impactSpeed * 1.5f, 20f); // degrees, capped at 20
        wobbleStartNanos = System.nanoTime();
    }

    private void createWaterPlane() {
        ModelBuilder builder = new ModelBuilder();
        float w = SIZE_X * SCALE, h = SIZE_Z * SCALE;
        float safeWaterY = waterLevel;

        Material waterMaterial = new Material(
                ColorAttribute.createDiffuse(new Color(0.3f, 0.47f, 0.9f, 0.7f)),
                new BlendingAttribute(0.97f)
        );

        Model water = builder.createRect(
                0, safeWaterY, h,
                w, safeWaterY, h,
                w, safeWaterY, 0,
                0, safeWaterY, 0,
                0, 1, 0,
                waterMaterial,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        waterInstance = new ModelInstance(water);
        waterInstance.transform.setToTranslation(-w / 2f, 0, -h / 2f);
    }

    public void dispose() {
        for (ModelInstance c : chunks) c.model.dispose();
        if (waterInstance != null) waterInstance.model.dispose();
        for (TerrainObject obj : allObjects) obj.dispose();
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

    public void setHolePosition(Vector3 newPos) {
        this.holePosition.set(newPos.x, getSurfaceHeightAt(newPos.x, newPos.z), newPos.z);
        if (physicalHole != null) physicalHole.dispose();
        this.physicalHole = new Hole(holePosition, getNormalAt(holePosition.x, holePosition.z, false), holeSize / 2f);
        if (flagInstance != null) updateFlagTransform(holePosition, 1.0f, 0f, 0f, 0f);
    }

    public void setTeePosition(Vector3 newPos) {
        this.teeCenter.set(newPos.x, getSurfaceHeightAt(newPos.x, newPos.z), newPos.z);
        if (teeInstance != null) teeInstance.transform.setToTranslation(teeCenter.x, teeCenter.y + 0.075f, teeCenter.z);
    }

    public Vector3 getTeePosition() {
        return teeCenter;
    }

    public Vector3 getHolePosition() {
        return holePosition;
    }

    public float getHoleSize() {
        return holeSize;
    }

    public float getWaterLevel() {
        return waterLevel;
    }

    public float getMaxDistanceX() {
        return (SIZE_X * SCALE) / 2f;
    }

    public float getMaxDistanceZ() {
        return (SIZE_Z * SCALE) / 2f;
    }

    public boolean isPointOutOfBounds(float worldX, float worldZ) {
        return Math.abs(worldX) > getMaxDistanceX() || Math.abs(worldZ) > getMaxDistanceZ();
    }

    public List<Tree> getTrees() {
        return trees;
    }

    public List<Monolith> getMonoliths() {
        return monoliths;
    }

    public enum TerrainType {
        TEE(0.4f, 2.0f, 1.1f, 0.75f, 0.2f, 0.45f, new Color(0.2f, 0.5f, 0.2f, 1f)),
        FAIRWAY(0.32f, 0.12f, 1.05f, 1.0f, 0.17f, 0.43f, new Color(0.1f, 0.4f, 0.1f, 1f)),
        ROUGH(1.2f, 4.5f, 1.5f, 2.1f, 0.63f, 0.32f, new Color(0.02f, 0.15f, 0.02f, 1f)),
        SAND(1.4f, 12.0f, 2.5f, 4.6f, 0.9f, 0.11f, new Color(0.85f, 0.8f, 0.5f, 1f)),
        GREEN(0.16f, 0.02f, 1.05f, 0.9f, 0.22f, 0.35f, new Color(0.1f, 0.6f, 0.1f, 1f)),
        STONE(0.1f, 0.02f, 1.05f, 1.5f, 0.01f, 0.7f, new Color(0.18f, 0.18f, 0.2f, 1f));

        public final float kineticFriction, rollingResistance, staticMultiplier, difficulty, softness, restitution;
        public final Color color;

        TerrainType(float kf, float rr, float sm, float diff, float softness, float rest, Color col) {
            this.kineticFriction = kf;
            this.rollingResistance = rr;
            this.staticMultiplier = sm;
            this.difficulty = diff;
            this.softness = softness;
            this.restitution = rest; // New field
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

        public void render(ModelBatch b, Environment e) {
            b.render(cupInstance, e);
        }

        public void dispose() {
            cupInstance.model.dispose();
        }
    }
}