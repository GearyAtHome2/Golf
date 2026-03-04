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

    public static final float BALL_RADIUS = 0.2f;
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

    private static final float MAX_STEP_UP = 1.0f;
    private static final int SUB_STEPS = 4;

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

    private final Vector3 tempV1 = new Vector3();
    private final Vector3 tempV2 = new Vector3();
    private final Vector3 vNormal = new Vector3();
    private final Vector3 vTangent = new Vector3();

    public Ball(Vector3 startPosition) {
        ModelBuilder builder = new ModelBuilder();
        model = builder.createSphere(BALL_RADIUS, BALL_RADIUS, BALL_RADIUS, 20, 20,
                new Material(ColorAttribute.createDiffuse(Color.WHITE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        instance = new ModelInstance(model);

        trailPointModel = builder.createSphere(0.08f, 0.08f, 0.08f, 8, 8,
                new Material(ColorAttribute.createDiffuse(Color.YELLOW), new BlendingAttribute(0.6f)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        trailInstance = new ModelInstance(trailPointModel);

        this.position.set(startPosition);
        this.lastStationaryPosition.set(startPosition);
        updateVisuals();
    }

    public void update(float delta, Terrain terrain, Vector3 baseWind) {
        if (state == State.STATIONARY) {
            updateVisuals();
            return;
        }

        if (hitCooldown > 0) hitCooldown -= delta;
        updateTrail(delta);
        recordTrailPoint();

        float subDelta = delta / SUB_STEPS;
        for (int i = 0; i < SUB_STEPS; i++) {
            processPhysicsStep(subDelta, terrain, baseWind);
            if (state == State.STATIONARY) break;
        }

        updateVisuals();
    }

    private void processPhysicsStep(float delta, Terrain terrain, Vector3 baseWind) {
        float preStepY = position.y;
        float waterLevel = terrain.getWaterLevel();

        // PARTICLE HOOK: Water Entry
        if (preStepY >= waterLevel && (position.y + velocity.y * delta) < waterLevel) {
            lastInteraction = Interaction.WATER;
        }

        // Apply Water Physics (Meniscus + Hydrodynamics)
        if (position.y < waterLevel + BALL_RADIUS) {
            BallPhysics.applyWaterPhysics(position, velocity, spin, waterLevel, delta);
        }

        float terrainHeight = terrain.getHeightAt(position.x, position.z) + BALL_RADIUS / 3;

        boolean touchingTerrain = (state == State.ROLLING && position.y <= terrainHeight + 0.1f)
                || (position.y < terrainHeight - 0.2f);

        if (touchingTerrain) {
            handleTerrainPhysics(delta, terrain, terrainHeight);
            position.add(tempV1.set(velocity).scl(delta));
        } else {
            handleAirPhysicsWithSweep(delta, terrain, baseWind);
        }

        handleTreeCollisions(terrain, delta);

        float stepYDelta = position.y - preStepY;
        if (Math.abs(stepYDelta) > 2.0f && hitCooldown <= 0f) {
            position.y = preStepY + MathUtils.clamp(stepYDelta, -1f, 1f);
        }
    }

    private void handleAirPhysicsWithSweep(float delta, Terrain terrain, Vector3 baseWind) {
        state = State.AIR;
        Vector3 windAtHeight = BallPhysics.getWindAtHeight(baseWind, position.y);

        tempV1.setZero();
        tempV1.add(BallPhysics.getGravityForce(GRAVITY));
        tempV1.add(BallPhysics.getAirDrag(velocity, windAtHeight, AIR_DRAG_COEFF));
        tempV1.add(BallPhysics.getMagnusForce(velocity, windAtHeight, spin, LIFT_COEFF, SIDE_COEFF));

        velocity.add(tempV1.scl(delta));
        tempV2.set(velocity).scl(delta);

        boolean collisionFound = false;
        Vector3 bestNormal = new Vector3();

        for (float t = 0.33f; t <= 1.0f; t += 0.33f) {
            float checkX = position.x + (tempV2.x * t);
            float checkZ = position.z + (tempV2.z * t);
            float checkY = position.y + (tempV2.y * t);
            float hAtPoint = terrain.getHeightAt(checkX, checkZ) + BALL_RADIUS / 3;

            if (checkY < hAtPoint) {
                Vector3 nAtPoint = terrain.getNormalAt(checkX, checkZ);
                float angle = MathUtils.acos(nAtPoint.y) * MathUtils.radiansToDegrees;
                float hDelta = hAtPoint - checkY;

                if (angle > 45f || hDelta > MAX_STEP_UP) {
                    collisionFound = true;
                    bestNormal.set(nAtPoint);
                    break;
                }
            }
        }

        if (collisionFound && hitCooldown <= 0f) {
            Vector3 wallNormal = new Vector3(bestNormal.x, 0, bestNormal.z).nor();
            if (wallNormal.len() < 0.1f) wallNormal.set(-velocity.x, 0, -velocity.z).nor();

            velocity.set(BallPhysics.calculateBounce(velocity, wallNormal, BOUNCE_RESTITUTION));
            position.add(tempV1.set(wallNormal).scl(0.15f));
            hitCooldown = 0.05f;
            lastInteraction = Interaction.TERRAIN;
        } else {
            float nextHeight = terrain.getHeightAt(position.x + tempV2.x, position.z + tempV2.z) + BALL_RADIUS / 3;
            if (position.y + tempV2.y < nextHeight && hitCooldown <= 0f) {
                handleTerrainPhysics(delta, terrain, nextHeight);
                position.add(tempV1.set(velocity).scl(delta));
            } else {
                position.add(tempV2);
            }
        }

        if (velocity.len() > 0.1f) {
            float spinDrag = 0.05f + (velocity.len() * velocity.len() * 0.0002f);
            spin.scl(1.0f - (spinDrag * delta));
        }
    }

    private void handleTerrainPhysics(float delta, Terrain terrain, float terrainHeight) {
        Vector3 normal = terrain.getNormalAt(position.x, position.z).nor();
        Terrain.TerrainType type = terrain.getTerrainTypeAt(position.x, position.z);
        float slope = MathUtils.acos(normal.y) * MathUtils.radiansToDegrees;

        if (terrainHeight - position.y > MAX_STEP_UP) {
            tempV1.set(normal.x, 0, normal.z).nor();
            if (tempV1.len() < 0.1f) {
                position.y = terrainHeight;
            } else {
                position.add(tempV1.scl(0.4f));
                velocity.set(BallPhysics.calculateBounce(velocity, tempV1, 0.3f));
            }
            return;
        }

        if (position.y < terrainHeight && slope < 45f) {
            position.y = terrainHeight;
        }

        float vDotN = velocity.dot(normal);

        if (state == State.ROLLING) {
            vNormal.set(normal).scl(vDotN);
            velocity.sub(vNormal);
            vTangent.set(velocity);
            vNormal.setZero();
        } else {
            vNormal.set(normal).scl(vDotN);
            vTangent.set(velocity).sub(vNormal);
        }

        if (state != State.ROLLING && Math.abs(vDotN) > MIN_BOUNCE_VY) {
            applyBounce(normal, type);
        } else {
            state = State.ROLLING;
            applyRollingPhysics(vTangent, normal, type, delta);
        }

        velocity.set(vTangent).add(vNormal);
        checkStationaryCondition(type, normal);
    }

    private void applyBounce(Vector3 normal, Terrain.TerrainType type) {
        float frictionImpact = (type.kineticFriction - 0.2f) * 0.08f;
        float localRestitution = MathUtils.clamp(BOUNCE_RESTITUTION - frictionImpact, 0.05f, BOUNCE_RESTITUTION);
        velocity.set(BallPhysics.calculateBounce(velocity, normal, localRestitution));

        float vDotN = velocity.dot(normal);
        vNormal.set(normal).scl(vDotN);
        vTangent.set(velocity).sub(vNormal);

        if (vTangent.len() > 0.1f) {
            tempV1.set(vTangent).nor().scl(-spin.x * 0.015f);
            vTangent.add(tempV1);
        }

        velocity.set(vTangent).add(vNormal);
        state = State.CONTACT;
        lastInteraction = Interaction.TERRAIN;
        spin.scl(0.4f);
    }

    private void applyRollingPhysics(Vector3 tangentVel, Vector3 normal, Terrain.TerrainType type, float delta) {
        Vector3 slopeForce = BallPhysics.getSlopeForce(BallPhysics.getGravityForce(GRAVITY), normal);
        Vector3 friction = BallPhysics.getRollingFriction(tangentVel, normal, type, GRAVITY);
        tangentVel.add(slopeForce.scl(delta));
        tangentVel.add(friction.scl(delta));
        spin.setZero();
    }

    private void checkStationaryCondition(Terrain.TerrainType type, Vector3 normal) {
        float slopeMag = BallPhysics.getSlopeForce(BallPhysics.getGravityForce(GRAVITY), normal).len();
        float staticFriction = type.kineticFriction * Math.abs(normal.y * GRAVITY) * type.staticMultiplier;
        if (velocity.len() < STOP_SPEED && slopeMag < staticFriction) {
            velocity.setZero();
            spin.setZero();
            state = State.STATIONARY;
        }
    }

    private void handleTreeCollisions(Terrain terrain, float delta) {
        for (Terrain.Tree tree : terrain.getTrees()) {
            Vector3 treePos = tree.getPosition();
            float trunkRad = tree.getTrunkRadius();
            float trunkHeight = tree.getTrunkHeight();
            float dx = position.x - treePos.x;
            float dz = position.z - treePos.z;
            float distXZ = (float) Math.sqrt(dx * dx + dz * dz);

            if (distXZ < BALL_RADIUS + trunkRad && position.y < treePos.y + trunkHeight && position.y > treePos.y) {
                tempV1.set(dx, 0, dz).nor();
                float speedXZ = tempV2.set(velocity.x, 0, velocity.z).len();
                velocity.x = tempV1.x * speedXZ;
                velocity.z = tempV1.z * speedXZ;
                velocity.scl(0.8f);
                spin.scl(0.2f);
                position.x += tempV1.x * 0.05f;
                position.z += tempV1.z * 0.05f;
                lastInteraction = Interaction.TERRAIN;
                continue;
            }

            float fY = treePos.y + trunkHeight + tree.getFoliageRadius();
            if (position.dst(treePos.x, fY, treePos.z) < tree.getFoliageRadius() + BALL_RADIUS) {
                applyFoliagePhysics(delta);
            }
        }
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
        state = (loft < 1.0f) ? State.ROLLING : State.AIR;
        hitCooldown = (state == State.ROLLING) ? 0.02f : 0.1f;
        trail.clear();
    }

    public void resetToLastPosition() {
        this.position.set(lastStationaryPosition);
        this.velocity.setZero();
        this.spin.setZero();
        this.state = State.STATIONARY;
        this.trail.clear();
        updateVisuals();
    }

    public boolean checkVictory(Vector3 holePos, float holeSize) {
        float holeRadius = holeSize / 2f;
        float distToHole = position.dst(holePos);
        if (distToHole < holeRadius) {
            if (state == State.ROLLING || state == State.CONTACT) return velocity.len() < 12f;
            if (state == State.AIR && (position.y - BALL_RADIUS - holePos.y) < 0f) return true;
        }
        return false;
    }

    private void updateVisuals() {
        instance.transform.setToTranslation(position);
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
            m.set(new BlendingAttribute(color.a));
        }
    }

    public void render(ModelBatch batch, Environment env) { batch.render(instance, env); }

    public void renderTrail(ModelBatch batch, Environment env) {
        for (TrailPoint p : trail) {
            float lifeRatio = MathUtils.clamp(p.life / 4.0f, 0f, 1f);
            float scale = 0.2f + (lifeRatio * 0.8f);
            trailInstance.transform.setToTranslation(p.pos).scale(scale, scale, scale);
            ((BlendingAttribute)trailInstance.materials.get(0).get(BlendingAttribute.Type)).opacity = lifeRatio * 0.7f;
            batch.render(trailInstance, env);
        }
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

    public Vector3 getPosition() { return position; }
    public Vector3 getVelocity() { return velocity; }
    public Vector3 getSpin() { return spin; }
    public State getState() { return state; }
    public void clearInteraction() { lastInteraction = Interaction.NONE; }
    public Interaction getLastInteraction() { return lastInteraction; }

    public void dispose() {
        model.dispose();
        trailPointModel.dispose();
    }
}