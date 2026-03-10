package org.example.ball;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain;

import java.util.List;

public class Ball {

    public enum State {AIR, CONTACT, ROLLING, STATIONARY}
    public enum Interaction {NONE, LEAVES, WATER, TERRAIN}

    public static final float BALL_RADIUS = 0.2f;
    private static final float GRAVITY = -9.81f;
    private static final float AIR_DRAG_COEFF = 0.0048f;
    private static final float LIFT_COEFF = 4.2f;
    private static final float SIDE_COEFF = 4.2f;
    private static final float STOP_SPEED = 0.15f;
    private static final float MIN_BOUNCE_VY = 0.3f;
    private static final float BOUNCE_RESTITUTION = 0.35f;
    private static final float FOLIAGE_DRAG_SQU = 0.04f;
    private static final float FOLIAGE_DRAG_LIN = 0.04f;
    private static final float DEFLECTION_CHANCE_PER_METER = 0.6f;
    private static final float DEFLECTION_MAGNITUDE = 10f;

    private static final float MAX_STEP_UP = 1.0f;
    private static final int SUB_STEPS = 4;

    private final Vector3 position = new Vector3();
    private final Vector3 velocity = new Vector3();
    private final Vector3 spin = new Vector3();
    private final Vector3 lastStationaryPosition = new Vector3();
    private final Vector3 lastShotPosition = new Vector3();

    private final BallRenderer renderer;
    private boolean isGoodShot = false;
    private State state = State.STATIONARY;
    private Interaction lastInteraction = Interaction.NONE;
    private float hitCooldown = 0f;

    private final Vector3 tempV1 = new Vector3();
    private final Vector3 tempV2 = new Vector3();
    private final Vector3 vNormal = new Vector3();
    private final Vector3 vTangent = new Vector3();

    public Ball(Vector3 startPosition) {
        this.renderer = new BallRenderer(BALL_RADIUS);
        this.position.set(startPosition);
        this.lastStationaryPosition.set(startPosition);
        this.lastShotPosition.set(startPosition);
        renderer.updateVisuals(position, state);
    }

    public void update(float delta, Terrain terrain, Vector3 baseWind) {
        if (state == State.STATIONARY) {
            renderer.updateVisuals(position, state);
            return;
        }

        if (hitCooldown > 0) hitCooldown -= delta;
        renderer.updateTrail(delta);
        renderer.recordTrailPoint(position, 1.0f);

        float subDelta = delta / SUB_STEPS;
        for (int i = 0; i < SUB_STEPS; i++) {
            processPhysicsStep(subDelta, terrain, baseWind);
            if (state == State.STATIONARY) break;
        }
        renderer.updateVisuals(position, state);
    }

    private void processPhysicsStep(float delta, Terrain terrain, Vector3 baseWind) {
        float preStepY = position.y;
        handleWaterTransition(terrain);
        handleEnvironmentPhysics(delta, terrain, baseWind);
        handleTreeCollisions(terrain, delta);
        handleMonolithCollisions(terrain);
        applyYConstraints(preStepY);
    }

    private void handleWaterTransition(Terrain terrain) {
        float waterLevel = terrain.getWaterLevel();
        if (BallPhysics.checkWaterTransition(position, velocity, waterLevel)) {
            lastInteraction = Interaction.WATER;
            renderer.getActiveTrailColor().set(Color.CYAN);
            renderer.spawnBurst(position, 3, 1.5f);
        }
    }

    private void handleEnvironmentPhysics(float delta, Terrain terrain, Vector3 baseWind) {
        float waterLevel = terrain.getWaterLevel();
        if (position.y < waterLevel + BALL_RADIUS) {
            lastInteraction = Interaction.WATER;
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
    }

    private void applyYConstraints(float preStepY) {
        float stepYDelta = position.y - preStepY;
        if (Math.abs(stepYDelta) > 2.0f && hitCooldown <= 0f) {
            position.y = preStepY + MathUtils.clamp(stepYDelta, -1f, 1f);
        }
    }

    private void handleAirPhysicsWithSweep(float delta, Terrain terrain, Vector3 baseWind) {
        state = State.AIR;
        Vector3 windAtHeight = BallPhysics.getWindAtHeight(baseWind, position.y);
        applyAirForces(delta, windAtHeight);

        tempV2.set(velocity).scl(delta);
        if (!handleAirCollisions(terrain)) {
            applyAirMovement(delta, terrain);
        }
        applySpinDrag(delta);
    }

    private void applyAirForces(float delta, Vector3 windAtHeight) {
        tempV1.setZero().add(BallPhysics.getGravityForce(GRAVITY))
                .add(BallPhysics.getAirDrag(velocity, windAtHeight, AIR_DRAG_COEFF))
                .add(BallPhysics.getMagnusForce(velocity, windAtHeight, spin, LIFT_COEFF, SIDE_COEFF));
        velocity.add(tempV1.scl(delta));
    }

    private boolean handleAirCollisions(Terrain terrain) {
        Vector3 bestNormal = new Vector3();
        boolean collisionFound = false;

        for (float t = 0.33f; t <= 1.0f; t += 0.33f) {
            float checkX = position.x + (tempV2.x * t);
            float checkZ = position.z + (tempV2.z * t);
            float checkY = position.y + (tempV2.y * t);
            float hAtPoint = terrain.getHeightAt(checkX, checkZ) + BALL_RADIUS / 3;

            if (checkY < hAtPoint) {
                Vector3 nAtPoint = terrain.getNormalAt(checkX, checkZ);
                if (BallPhysics.isWallCollision(nAtPoint, 45f) || (hAtPoint - checkY) > MAX_STEP_UP) {
                    collisionFound = true;
                    bestNormal.set(nAtPoint);
                    break;
                }
            }
        }

        if (collisionFound && hitCooldown <= 0f) {
            applyWallBounce(bestNormal);
            return true;
        }
        return false;
    }

    private void applyWallBounce(Vector3 bestNormal) {
        Vector3 wallNormal = new Vector3(bestNormal.x, 0, bestNormal.z).nor();
        if (wallNormal.len() < 0.1f) wallNormal.set(-velocity.x, 0, -velocity.z).nor();

        velocity.set(BallPhysics.calculateBounceWithSpin(velocity, wallNormal, spin, BOUNCE_RESTITUTION, 0.4f, 0f));
        position.add(tempV1.set(wallNormal).scl(0.15f));
        hitCooldown = 0.05f;
        lastInteraction = Interaction.TERRAIN;
        renderer.lerpTrailColor(Color.GRAY, 0.4f);
    }

    private void applyAirMovement(float delta, Terrain terrain) {
        float nextHeight = terrain.getHeightAt(position.x + tempV2.x, position.z + tempV2.z) + BALL_RADIUS / 3;
        if (position.y + tempV2.y < nextHeight && hitCooldown <= 0f) {
            handleTerrainPhysics(delta, terrain, nextHeight);
            position.add(tempV1.set(velocity).scl(delta));
        } else {
            position.add(tempV2);
        }
    }

    private void applySpinDrag(float delta) {
        if (velocity.len() > 0.1f) {
            float spinDrag = 0.05f + (velocity.len() * velocity.len() * 0.0002f);
            spin.scl(1.0f - (spinDrag * delta));
        }
    }

    private void handleTerrainPhysics(float delta, Terrain terrain, float terrainHeight) {
        Vector3 normal = terrain.getNormalAt(position.x, position.z).nor();
        Terrain.TerrainType type = terrain.getTerrainTypeAt(position.x, position.z);

        if (handleStepUp(terrainHeight, normal, type)) return;
        if (shouldLaunch(normal, type)) {
            state = State.AIR;
            return;
        }

        if (position.y < terrainHeight && !BallPhysics.isWallCollision(normal, 45f)) {
            position.y = terrainHeight;
        }

        calculateTerrainVelocities(normal);

        if (state != State.ROLLING && Math.abs(velocity.dot(normal)) > MIN_BOUNCE_VY) {
            applyBounce(normal, type);
        } else {
            state = State.ROLLING;
            applyRollingPhysics(vTangent, normal, type, delta);
        }

        velocity.set(vTangent).add(vNormal);
        checkStationaryCondition(type, normal);
    }

    private boolean handleStepUp(float terrainHeight, Vector3 normal, Terrain.TerrainType type) {
        if (terrainHeight - position.y > MAX_STEP_UP) {
            tempV1.set(normal.x, 0, normal.z).nor();
            if (tempV1.len() < 0.1f) position.y = terrainHeight;
            else {
                position.add(tempV1.scl(0.4f));
                velocity.set(BallPhysics.calculateBounceWithSpin(velocity, tempV1, spin, 0.3f, type.kineticFriction, type.softness));
            }
            return true;
        }
        return false;
    }

    private boolean shouldLaunch(Vector3 normal, Terrain.TerrainType type) {
        float vDotN = velocity.dot(normal);
        return state == State.ROLLING && vDotN > 1.5f && type != Terrain.TerrainType.GREEN;
    }

    private void calculateTerrainVelocities(Vector3 normal) {
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
    }

    private void applyBounce(Vector3 normal, Terrain.TerrainType type) {
        float friction = type.kineticFriction;
        float frictionImpact = (friction - 0.2f) * 0.08f;
        float localRestitution = MathUtils.clamp(BOUNCE_RESTITUTION - frictionImpact, 0.05f, BOUNCE_RESTITUTION);

        velocity.set(BallPhysics.calculateBounceWithSpin(velocity, normal, spin, localRestitution, friction, type.softness));

        float vDotN = velocity.dot(normal);
        vNormal.set(normal).scl(vDotN);
        vTangent.set(velocity).sub(vNormal);

        state = State.CONTACT;
        lastInteraction = Interaction.TERRAIN;

        float spinLoss = 0.4f + (friction * 0.3f);
        spin.scl(MathUtils.clamp(1.0f - spinLoss, 0f, 1.0f));

        if (isGoodShot) renderer.spawnBurst(position, 5, 2.0f);
        renderer.lerpTrailColor(Color.GRAY, 0.25f);
    }

    private void applyRollingPhysics(Vector3 tangentVel, Vector3 normal, Terrain.TerrainType type, float delta) {
        Vector3 slopeForce = BallPhysics.getSlopeForce(BallPhysics.getGravityForce(GRAVITY), normal);
        Vector3 friction = BallPhysics.getRollingFriction(tangentVel, normal, type, GRAVITY);

        tangentVel.add(slopeForce.scl(delta)).add(friction.scl(delta));
        spin.setZero();

        if (renderer.getActiveTrailColor().r > 0.3f || renderer.getActiveTrailColor().g > 0.3f) {
            renderer.lerpTrailColor(new Color(0.2f, 0.2f, 0.2f, 1f), delta * 1.5f);
        }
    }

    private void checkStationaryCondition(Terrain.TerrainType type, Vector3 normal) {
        float slopeMag = BallPhysics.getSlopeForce(BallPhysics.getGravityForce(GRAVITY), normal).len();
        float staticFriction = type.kineticFriction * Math.abs(normal.y * GRAVITY) * type.staticMultiplier;
        if (velocity.len() < STOP_SPEED && slopeMag < staticFriction) {
            velocity.setZero();
            spin.setZero();
            state = State.STATIONARY;
            renderer.getActiveTrailColor().set(Color.WHITE);
            lastStationaryPosition.set(position);
        }
    }

    private void handleTreeCollisions(Terrain terrain, float delta) {
        if (hitCooldown > 0) return;
        List<Terrain.Tree> nearbyTrees = terrain.getTreesAt(position.x, position.z);
        for (Terrain.Tree tree : nearbyTrees) {
            Vector3 treePos = tree.getPosition();
            float dx = position.x - treePos.x, dz = position.z - treePos.z;
            float distXZ = (float) Math.sqrt(dx * dx + dz * dz);

            if (distXZ < BALL_RADIUS + tree.getTrunkRadius() && position.y < treePos.y + tree.getTrunkHeight() && position.y > treePos.y) {
                if (BallPhysics.handleTrunkCollision(position, velocity, spin, dx, dz)) {
                    lastInteraction = Interaction.TERRAIN;
                    renderer.getActiveTrailColor().set(Color.BROWN);
                }
                continue;
            }

            if (tree.isInsideFoliage(position, BALL_RADIUS)) {
                lastInteraction = Interaction.LEAVES;
                BallPhysics.applyFoliagePhysics(velocity, spin, delta, FOLIAGE_DRAG_LIN, FOLIAGE_DRAG_SQU, DEFLECTION_CHANCE_PER_METER, DEFLECTION_MAGNITUDE);
                renderer.lerpTrailColor(Color.FOREST, 0.2f);
            }
        }
    }

    private void handleMonolithCollisions(Terrain terrain) {
        if (hitCooldown > 0) return;
        List<Terrain.Monolith> nearbyMonoliths = terrain.getMonolithsAt(position.x, position.z);
        for (Terrain.Monolith m : nearbyMonoliths) {
            boolean hit = BallPhysics.handleMonolithCollision(position, velocity, spin, m.getPosition(), m.getWidth(), m.getHeight(), m.getDepth(), m.getRotationY());
            if (hit) {
                hitCooldown = 0.05f;
                lastInteraction = Interaction.TERRAIN;
                renderer.getActiveTrailColor().set(Color.DARK_GRAY);
                break;
            }
        }
    }

    public void hit(Vector3 shootDir, float power, float loft, float powerMult, MinigameResult.Rating rating) {
        if (state != State.STATIONARY) return;
        capturePosition();
        spin.setZero();

        isGoodShot = (rating == MinigameResult.Rating.PERFECTION || rating == MinigameResult.Rating.SUPER || rating == MinigameResult.Rating.GREAT);
        renderer.setShotRatingColor(rating);

        float finalSpeed = power * powerMult;
        float angleRad = loft * MathUtils.degreesToRadians;
        velocity.set(shootDir).nor();
        velocity.y = MathUtils.sin(angleRad);
        float hLen = MathUtils.cos(angleRad);
        velocity.x *= hLen;
        velocity.z *= hLen;
        velocity.scl(finalSpeed);

        state = (loft < 1.0f) ? State.ROLLING : State.AIR;
        hitCooldown = (state == State.ROLLING) ? 0.02f : 0.1f;
        renderer.resetTrail(renderer.getActiveTrailColor());
    }

    public void capturePosition() { lastShotPosition.set(this.position); }

    public void resetToLastPosition() {
        this.position.set(lastShotPosition);
        this.velocity.setZero();
        this.spin.setZero();
        this.state = State.STATIONARY;
        renderer.resetTrail(Color.WHITE);
        renderer.updateVisuals(position, state);
    }

    public boolean isInWater(Terrain terrain) { return position.y < terrain.getWaterLevel(); }

    public boolean checkVictory(Vector3 holePos, float holeSize) {
        float distToHole = position.dst(holePos);
        if (distToHole < holeSize / 2f) {
            if (state == State.ROLLING || state == State.CONTACT) return velocity.len() < 12f;
            if (state == State.AIR && (position.y - BALL_RADIUS - holePos.y) < 0f) return true;
        }
        return false;
    }

    public void render(ModelBatch batch, Environment env) { renderer.render(batch, env); }
    public void renderTrail(ModelBatch batch, Environment env) { renderer.renderTrail(batch, env); }
    public float getFlatDistanceToHole(Terrain terrain) { return (terrain == null) ? 0 : Vector2.dst(position.x, position.z, terrain.getHolePosition().x, terrain.getHolePosition().z); }
    public float getShotDistance() { return Vector2.dst(lastStationaryPosition.x, lastStationaryPosition.z, position.x, position.z); }
    public Vector3 getPosition() { return position; }
    public Vector3 getVelocity() { return velocity; }
    public Vector3 getSpin() { return spin; }
    public State getState() { return state; }
    public void clearInteraction() { lastInteraction = Interaction.NONE; }
    public Interaction getLastInteraction() { return lastInteraction; }
    public void dispose() { renderer.dispose(); }
}