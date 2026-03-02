package org.example.ball;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import org.example.terrain.Terrain;

public class Ball {

    public enum State { AIR, CONTACT, ROLLING, STATIONARY }
    public enum Interaction { NONE, LEAVES, WATER, TERRAIN }

    private static final float RADIUS = 0.2f;
    private static final float GRAVITY = -9.81f;
    private static final float AIR_DRAG_COEFF = 0.006f;
    private static final float LIFT_COEFF = 0.015f;
    private static final float SIDE_COEFF = 0.015f;
    private static final float STOP_SPEED = 0.15f;
    private static final float MIN_BOUNCE_VY = 0.3f;
    private static final float BOUNCE_RESTITUTION = 0.35f;
    private static final float FOLIAGE_DRAG_SQU = 0.04f;
    private static final float FOLIAGE_DRAG_LIN = 0.04f;
    private static final float DEFLECTION_CHANCE_PER_METER = 0.6f;
    private static final float DEFLECTION_MAGNITUDE = 10f;

    private final Model model;
    private final ModelInstance instance;
    private final Vector3 position = new Vector3();
    private final Vector3 velocity = new Vector3();
    private final Vector3 spin = new Vector3();

    private final Vector3 lastStationaryPosition = new Vector3();

    private Model trailPointModel;
    private ModelInstance trailInstance;

    private final Array<TrailPoint> trail = new Array<>();
    private static class TrailPoint {
        Vector3 pos = new Vector3();
        float life = 4.0f;
    }

    private State state = State.STATIONARY;
    private Interaction lastInteraction = Interaction.NONE;
    private float hitCooldown = 0f;
    private boolean wasInFoliageLastFrame = false;

    private final Vector3 tempV1 = new Vector3();
    private final Vector3 tempV2 = new Vector3();
    private final Vector3 vNormal = new Vector3();
    private final Vector3 vTangent = new Vector3();

    public Ball(Vector3 startPosition) {
        ModelBuilder builder = new ModelBuilder();
        model = builder.createSphere(RADIUS, RADIUS, RADIUS, 20, 20,
                new Material(ColorAttribute.createDiffuse(Color.WHITE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        instance = new ModelInstance(model);

        trailPointModel = builder.createSphere(0.08f, 0.08f, 0.08f, 8, 8,
                new Material(ColorAttribute.createDiffuse(Color.YELLOW), new BlendingAttribute(0.6f)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        trailInstance = new ModelInstance(trailPointModel);

        this.position.set(startPosition);
        this.lastStationaryPosition.set(startPosition);
        updateTransform();
    }

    public void update(float delta, Terrain terrain, Vector3 baseWind) {
        if (hitCooldown > 0) hitCooldown -= delta;
        updateTrail(delta);

        if (state == State.STATIONARY) {
            updateTransform();
            return;
        }

        recordTrailPoint();
        handleWater(delta, terrain);

        float terrainHeight = terrain.getHeightAt(position.x, position.z) + RADIUS / 3;

        // STICKY LOGIC: If we are rolling, we are MUCH more lenient about being "in the air"
        // This prevents micro-bounces over triangle seams.
        float stickiness = (state == State.ROLLING) ? 0.15f : 0.02f;
        boolean touchingTerrain = hitCooldown <= 0f && position.y <= (terrainHeight + stickiness);

        if (touchingTerrain) {
            handleTerrainPhysics(delta, terrain, terrainHeight);
        } else {
            handleAirPhysics(delta, baseWind);
        }

        handleTreeCollisions(terrain, delta);
        applyIntegration(delta);

        updateColor();
        updateTransform();
    }

    private void handleAirPhysics(float delta, Vector3 baseWind) {
        state = State.AIR;
        Vector3 windAtHeight = BallPhysics.getWindAtHeight(baseWind, position.y);

        tempV1.setZero();
        tempV1.add(BallPhysics.getGravityForce(GRAVITY));
        tempV1.add(BallPhysics.getAirDrag(velocity, windAtHeight, AIR_DRAG_COEFF));
        tempV1.add(BallPhysics.getMagnusForce(velocity, windAtHeight, spin, LIFT_COEFF, SIDE_COEFF));

        velocity.add(tempV1.scl(delta));

        if (velocity.len() > 0.1f) {
            float spinDrag = 0.05f + (velocity.len() * velocity.len() * 0.0002f);
            spin.scl(1.0f - (spinDrag * delta));
        }
    }

    private void handleTerrainPhysics(float delta, Terrain terrain, float terrainHeight) {
        Vector3 normal = terrain.getNormalAt(position.x, position.z).nor();
        Terrain.TerrainType type = terrain.getTerrainTypeAt(position.x, position.z);
        boolean isGreen = (type == Terrain.TerrainType.GREEN);

        if (position.y < terrainHeight) position.y = terrainHeight;

        float vDotN = velocity.dot(normal);

        if (isGreen) {
            Gdx.app.log("PUTT_LOG", "--- COLLISION START ---");
            Gdx.app.log("PUTT_LOG", "Pre-Vel: " + velocity.len() + " vDotN: " + vDotN + " State: " + state);
        }

        // FORCE ALIGNMENT: If rolling, we eliminate any velocity into or away from the normal
        if (state == State.ROLLING) {
            vNormal.set(normal).scl(vDotN);
            velocity.sub(vNormal); // Strip the vertical jitter
            vTangent.set(velocity);
            vNormal.setZero();
        } else {
            vNormal.set(normal).scl(vDotN);
            vTangent.set(velocity).sub(vNormal);
        }

        if (state != State.ROLLING && Math.abs(vDotN) > MIN_BOUNCE_VY) {
            if (isGreen) Gdx.app.log("PUTT_LOG", "ACTION: BOUNCING");
            applyBounce(normal);
        } else {
            if (isGreen) Gdx.app.log("PUTT_LOG", "ACTION: ROLLING");
            vNormal.setZero();
            state = State.ROLLING;
            applyRollingPhysics(vTangent, normal, delta, terrain);
        }

        velocity.set(vTangent).add(vNormal);

        if (isGreen) Gdx.app.log("PUTT_LOG", "Post-Vel: " + velocity.len());

        checkStationaryCondition(terrain, normal);
    }

    private void applyBounce(Vector3 normal) {
        velocity.set(BallPhysics.calculateBounce(velocity, normal, BOUNCE_RESTITUTION));
        float vDotN = velocity.dot(normal);
        vNormal.set(normal).scl(vDotN);
        vTangent.set(velocity).sub(vNormal);

        tempV1.set(vTangent).nor().scl(-spin.x * 0.015f);
        vTangent.add(tempV1);

        state = State.CONTACT;
        lastInteraction = Interaction.TERRAIN;
        spin.scl(0.4f);
    }

    private void applyRollingPhysics(Vector3 tangentVel, Vector3 normal, float delta, Terrain terrain) {
        Terrain.TerrainType type = terrain.getTerrainTypeAt(position.x, position.z);

        tempV1.set(BallPhysics.getSlopeForce(BallPhysics.getGravityForce(GRAVITY), normal));
        tangentVel.add(tempV1.scl(delta));

        Vector3 friction = BallPhysics.getRollingFriction(tangentVel, normal, type, GRAVITY);
        if (type == Terrain.TerrainType.GREEN) {
            Gdx.app.log("PUTT_LOG", "Slope Force: " + tempV1.len() + " Friction Force: " + friction.len());
        }

        tangentVel.add(friction.scl(delta));
        spin.setZero();
    }

    private void checkStationaryCondition(Terrain terrain, Vector3 normal) {
        Terrain.TerrainType type = terrain.getTerrainTypeAt(position.x, position.z);
        float slopeMag = BallPhysics.getSlopeForce(BallPhysics.getGravityForce(GRAVITY), normal).len();
        float staticFriction = type.kineticFriction * Math.abs(normal.y * GRAVITY) * type.staticMultiplier;

        if (velocity.len() < STOP_SPEED && slopeMag < staticFriction) {
            if (type == Terrain.TerrainType.GREEN) Gdx.app.log("PUTT_LOG", "STOPPING: Speed below threshold.");
            velocity.setZero();
            spin.setZero();
            state = State.STATIONARY;
        }
    }

    private void handleWater(float delta, Terrain terrain) {
        if (position.y < terrain.getWaterLevel()) {
            lastInteraction = Interaction.WATER;
            velocity.scl(0.9f);
            velocity.y += -4f * GRAVITY * delta;
            spin.scl(0.5f);
        }
    }

    private void handleTreeCollisions(Terrain terrain, float delta) {
        boolean currentlyInFoliage = false;
        for (Terrain.Tree tree : terrain.getTrees()) {
            Vector3 treePos = tree.getPosition();
            float trunkRad = tree.getTrunkRadius();
            float trunkHeight = tree.getTrunkHeight();
            float dx = position.x - treePos.x;
            float dz = position.z - treePos.z;
            float distXZ = (float) Math.sqrt(dx * dx + dz * dz);

            if (distXZ < RADIUS + trunkRad && position.y < treePos.y + trunkHeight && position.y > treePos.y) {
                tempV1.set(dx, 0, dz).nor();
                float speedXZ = tempV2.set(velocity.x, 0, velocity.z).len();
                velocity.x = tempV1.x * speedXZ;
                velocity.z = tempV1.z * speedXZ;
                velocity.scl(0.8f);
                spin.scl(0.2f);
                position.x += tempV1.x * 0.01f;
                position.z += tempV1.z * 0.01f;
                lastInteraction = Interaction.TERRAIN;
                continue;
            }

            float fY = treePos.y + trunkHeight + tree.getFoliageRadius();
            if (position.dst(treePos.x, fY, treePos.z) < tree.getFoliageRadius() + RADIUS) {
                currentlyInFoliage = true;
                applyFoliagePhysics(delta);
            }
        }
        wasInFoliageLastFrame = currentlyInFoliage;
    }

    private void applyFoliagePhysics(float delta) {
        lastInteraction = Interaction.LEAVES;
        float speed = velocity.len();
        if (speed < 0.1f) return;
        velocity.scl(Math.max(0, 1 - (FOLIAGE_DRAG_LIN + FOLIAGE_DRAG_SQU * speed) * delta));
        spin.scl(0.8f);
        if (MathUtils.random() < (1.0f - (float) Math.pow(1.0f - DEFLECTION_CHANCE_PER_METER, speed * delta))) {
            velocity.rotate(Vector3.X, MathUtils.random(-DEFLECTION_MAGNITUDE, DEFLECTION_MAGNITUDE));
            velocity.rotate(Vector3.Y, MathUtils.random(-DEFLECTION_MAGNITUDE, DEFLECTION_MAGNITUDE));
            velocity.rotate(Vector3.Z, MathUtils.random(-DEFLECTION_MAGNITUDE, DEFLECTION_MAGNITUDE));
            velocity.scl(0.95f);
        }
    }

    private void applyIntegration(float delta) {
        position.add(tempV1.set(velocity).scl(delta));
    }

    public void hit(Vector3 shootDir, float power, float loft, float powerMult) {
        if (state != State.STATIONARY) return;
        lastStationaryPosition.set(position);
        spin.setZero();
        float finalSpeed = power * powerMult;
        float angleRad = loft * MathUtils.degreesToRadians;
        velocity.set(shootDir).nor();
        velocity.y = MathUtils.sin(angleRad);
        float hLen = MathUtils.cos(angleRad);
        velocity.x *= hLen; velocity.z *= hLen;
        velocity.scl(finalSpeed);

        if (loft < 1.0f) {
            state = State.ROLLING;
            hitCooldown = 0.02f;
        } else {
            state = State.AIR;
            hitCooldown = 0.1f;
        }
        trail.clear();
    }

    public void resetToLastPosition() {
        this.position.set(lastStationaryPosition);
        this.velocity.setZero();
        this.spin.setZero();
        this.state = State.STATIONARY;
        this.trail.clear();
        updateTransform();
    }

    public boolean checkVictory(Vector3 holePos, float holeSize) {
        float holeRadius = holeSize / 2f;
        float distToHole = position.dst(holePos);
        if (distToHole < holeRadius) {
            if (state == State.ROLLING || state == State.CONTACT) if (velocity.len() < 6f) return true;
            if (state == State.AIR && (position.y - RADIUS - holePos.y) < 0f) return true;
        }
        return false;
    }

    private void updateTrail(float delta) {
        for (int i = trail.size - 1; i >= 0; i--) {
            trail.get(i).life -= delta;
            if (trail.get(i).life <= 0) trail.removeIndex(i);
        }
    }

    private void recordTrailPoint() {
        TrailPoint p = new TrailPoint();
        p.pos.set(position);
        trail.add(p);
    }

    private void updateColor() {
        Color color = switch (state) {
            case AIR -> Color.WHITE;
            case CONTACT -> Color.GREEN;
            case ROLLING -> Color.RED;
            case STATIONARY -> Color.ORANGE;
        };
        for (Material m : instance.materials) ((ColorAttribute) m.get(ColorAttribute.Diffuse)).color.set(color);
    }

    public void setGhostColor(Color color) {
        for (Material m : instance.materials) {
            m.set(ColorAttribute.createDiffuse(color));
            m.set(new BlendingAttribute(0.7f));
        }
    }

    private void updateTransform() { instance.transform.setToTranslation(position); }
    public void render(ModelBatch batch, Environment env) { batch.render(instance, env);}

    public void renderTrail(ModelBatch batch, Environment env) {
        if (trail.size < 2) return;
        for (int i = 0; i < trail.size; i++) {
            TrailPoint p = trail.get(i);
            float lifeRatio = MathUtils.clamp(p.life / 4.0f, 0f, 1f);
            trailInstance.transform.setToTranslation(p.pos);
            float scale = 0.2f + (lifeRatio * 0.8f);
            trailInstance.transform.scale(scale, scale, scale);
            BlendingAttribute ba = (BlendingAttribute)trailInstance.materials.get(0).get(BlendingAttribute.Type);
            ba.opacity = lifeRatio * 0.7f;
            batch.render(trailInstance, env);
        }
    }

    public Vector3 getPosition() { return position; }
    public Vector3 getVelocity() { return velocity; }
    public Vector3 getSpin() { return spin; }
    public State getState() { return state; }
    public void clearInteraction() { lastInteraction = Interaction.NONE; }
    public Interaction getLastInteraction() { return lastInteraction; }
    public void dispose() {
        model.dispose();
        if (trailPointModel != null) trailPointModel.dispose();
    }
}