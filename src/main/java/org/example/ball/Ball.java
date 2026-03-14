package org.example.ball;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.example.GameConfig;
import org.example.glamour.ParticleManager;
import org.example.performance.PhysicsProfiler;
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
    private static final float MIN_BOUNCE_VY = 0.5f;
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
    private final Vector3 lastTrailPos = new Vector3();

    private final BallRenderer renderer;
    private final ParticleManager particleManager;
    private final GameConfig config;
    private boolean isGoodShot = false;

    private State state = State.STATIONARY;

    private Interaction lastInteraction = Interaction.NONE;
    private float hitCooldown = 0f;

    private final Vector3 tempV1 = new Vector3();
    private final Vector3 tempV2 = new Vector3();
    private final Vector3 vNormal = new Vector3();
    private final Vector3 vTangent = new Vector3();
    private float timeElapsed = 0;
    private boolean isInitialFlight = false;
    private MinigameResult.Rating shotRating = MinigameResult.Rating.POOR;

    public Ball(Vector3 startPosition, ParticleManager particleManager, GameConfig config) {
        this.renderer = new BallRenderer(BALL_RADIUS);
        this.particleManager = particleManager;
        this.config = config;
        this.position.set(startPosition);
        this.lastStationaryPosition.set(startPosition);
        this.lastShotPosition.set(startPosition);
        this.lastTrailPos.set(startPosition);
        this.velocity.setZero();
        renderer.updateVisuals(position, state);
    }

    public void update(float delta, Terrain terrain, Vector3 baseWind) {
        timeElapsed += delta;
        PhysicsProfiler.startFrame();
        PhysicsProfiler.startSection("TrailUpdate");

        float agingScale = (shotRating == MinigameResult.Rating.PERFECTION) ? 0.65f : 1.0f;
        float speedMult = (config.animSpeed == GameConfig.AnimSpeed.NONE) ? 1.0f : config.animSpeed.mult;
        renderer.updateTrail(delta * agingScale * speedMult);

        PhysicsProfiler.endSection("TrailUpdate");

        if (state == State.STATIONARY && !renderer.hasVisibleTrail()) {
            return;
        }

        if (hitCooldown > 0) hitCooldown -= delta;

        float subDelta = delta / SUB_STEPS;
        PhysicsProfiler.startSection("PhysicsSubsteps");
        for (int i = 0; i < SUB_STEPS; i++) {
            if (state != State.STATIONARY) {
                processPhysicsStep(subDelta, terrain, baseWind);
            }

            float speed = velocity.len();
            float dynamicStep = MathUtils.clamp(speed * 0.06f, 0.05f, 2.0f);

            if (position.dst2(lastTrailPos) > (dynamicStep * dynamicStep)) {
                renderer.recordTrailPoint(position, 1.0f);
                lastTrailPos.set(position);
            }

            if (state == State.STATIONARY) break;
        }
        PhysicsProfiler.endSection("PhysicsSubsteps");

        particleManager.handleBallInteraction(this, terrain);

        PhysicsProfiler.startSection("VisualSync");
        renderer.updateVisuals(position, state);
        PhysicsProfiler.endSection("VisualSync");
    }

    private void processPhysicsStep(float delta, Terrain terrain, Vector3 baseWind) {
        float preStepY = position.y;
        handleWaterTransition(terrain);

        PhysicsProfiler.startSection("EnvPhysics");
        handleEnvironmentPhysics(delta, terrain, baseWind);
        PhysicsProfiler.endSection("EnvPhysics");

        // Flight streams remain gated by config for performance
        if (state == State.AIR && isInitialFlight && config.particlesEnabled) {
            float velMag = velocity.len();
            Color trailColor = renderer.getActiveTrailColor();

            if (shotRating == MinigameResult.Rating.PERFECTION) {
                float throb = MathUtils.sin(timeElapsed * 10f);
                float throbFactor = 1.0f + (throb * 0.7f);
                float force = MathUtils.clamp(velMag / 20f, 0.4f, 1.2f) * throbFactor;
                int count = (throb > 0.4f) ? 4 : (velMag > 15f ? 2 : 1);
                particleManager.spawnRatingBurst(position, trailColor, count, force);
            } else if (shotRating == MinigameResult.Rating.SUPER) {
                if (MathUtils.random() < 0.25f) particleManager.spawnRatingBurst(position, trailColor, 1, 0.4f);
            } else if (shotRating == MinigameResult.Rating.GREAT) {
                if (MathUtils.random() < 0.07f) particleManager.spawnRatingBurst(position, trailColor, 1, 0.3f);
            }
        }

        handleTreeCollisions(terrain, delta);
        handleMonolithCollisions(terrain);
        applyYConstraints(preStepY, terrain);
    }

    private void handleWaterTransition(Terrain terrain) {
        float waterLevel = terrain.getWaterLevel();
        if (BallPhysics.checkWaterTransition(position, velocity, waterLevel)) {
            lastInteraction = Interaction.WATER;
            renderer.getActiveTrailColor().set(Color.CYAN);
            // Impact particles: enabled even if general particles are off
            particleManager.spawnRatingBurst(position, Color.CYAN, 5, 1.5f);
        }
    }

    private void handleEnvironmentPhysics(float delta, Terrain terrain, Vector3 baseWind) {
        if (state == State.STATIONARY) return;

        float waterLevel = terrain.getWaterLevel();
        if (position.y < waterLevel + BALL_RADIUS) {
            lastInteraction = Interaction.WATER;
            BallPhysics.applyWaterPhysics(position, velocity, spin, waterLevel, delta);
        }

        float terrainHeight = terrain.getHeightAt(position.x, position.z) + BALL_RADIUS / 3;
        boolean touchingTerrain = (state == State.ROLLING && position.y <= terrainHeight + 0.05f)
                || (position.y < terrainHeight);

        if (touchingTerrain) {
            handleTerrainPhysics(delta, terrain, terrainHeight);
            position.add(tempV1.set(velocity).scl(delta));
        } else {
            handleAirPhysicsWithSweep(delta, terrain, baseWind);
        }
    }

    private void applyYConstraints(float preStepY, Terrain terrain) {
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
        velocity.set(BallPhysics.calculateBounceWithSpin(velocity, wallNormal, spin, BOUNCE_RESTITUTION, 0.4f, 0f));
        position.add(tempV1.set(wallNormal).scl(0.15f));
        hitCooldown = 0.05f;
        lastInteraction = Interaction.TERRAIN;
        if (!isGoodShot) renderer.lerpTrailColor(Color.GRAY, 0.4f);

        // Always spawn impact particles for wall hits
        particleManager.spawnRatingBurst(position, Color.LIGHT_GRAY, 3, 0.8f);
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

        float vDotN = velocity.dot(normal);
        if (state == State.AIR && vDotN < -MIN_BOUNCE_VY) {
            applyBounce(normal, type);
        } else {
            state = State.ROLLING;
            calculateTerrainVelocities(normal);
            applyRollingPhysics(vTangent, normal, type, delta);
            velocity.set(vTangent);
        }
        checkStationaryCondition(type, normal);
    }

    private boolean handleStepUp(float terrainHeight, Vector3 normal, Terrain.TerrainType type) {
        if (terrainHeight - position.y > MAX_STEP_UP) {
            tempV1.set(normal.x, 0, normal.z).nor();
            if (tempV1.len() < 0.1f) position.y = terrainHeight;
            else {
                position.add(tempV1.scl(0.4f));
                velocity.set(BallPhysics.calculateBounceWithSpin(velocity, tempV1, spin, 0.3f, type.kineticFriction, type.softness));
                particleManager.spawnRatingBurst(position, Color.BROWN, 2, 0.5f);
            }
            return true;
        }
        return false;
    }

    private boolean shouldLaunch(Vector3 normal, Terrain.TerrainType type) {
        float vDotN = velocity.dot(normal);
        return state == State.ROLLING && vDotN > 1.5f;
    }

    private void calculateTerrainVelocities(Vector3 normal) {
        float vDotN = velocity.dot(normal);
        vNormal.set(normal).scl(vDotN);
        velocity.sub(vNormal);
        vTangent.set(velocity);
        vNormal.setZero();
    }

    private void applyBounce(Vector3 normal, Terrain.TerrainType type) {
        // Impact burst: Always allowed on first landing
        if (isInitialFlight) {
            int landingCount = switch (shotRating) {
                case PERFECTION -> 8;
                case SUPER -> 5;
                case GREAT -> 3;
                default -> 1;
            };
            particleManager.spawnRatingBurst(position, renderer.getActiveTrailColor(), landingCount, 1.2f);
        }

        isInitialFlight = false;
        float friction = type.kineticFriction;
        float localRestitution = MathUtils.clamp(BOUNCE_RESTITUTION - ((friction - 0.2f) * 0.08f), 0.05f, BOUNCE_RESTITUTION);
        velocity.set(BallPhysics.calculateBounceWithSpin(velocity, normal, spin, localRestitution, friction, type.softness));
        state = State.CONTACT;
        lastInteraction = Interaction.TERRAIN;
        spin.scl(MathUtils.clamp(1.0f - (0.4f + (friction * 0.3f)), 0f, 1.0f));
    }

    private void applyRollingPhysics(Vector3 tangentVel, Vector3 normal, Terrain.TerrainType type, float delta) {
        Vector3 slopeForce = BallPhysics.getSlopeForce(BallPhysics.getGravityForce(GRAVITY), normal);
        Vector3 friction = BallPhysics.getRollingFriction(tangentVel, normal, type, GRAVITY);
        tangentVel.add(slopeForce.scl(delta)).add(friction.scl(delta));
    }

    private void checkStationaryCondition(Terrain.TerrainType type, Vector3 normal) {
        if (state != State.ROLLING) return;
        float slopeMag = BallPhysics.getSlopeForce(BallPhysics.getGravityForce(GRAVITY), normal).len();
        float staticFriction = type.kineticFriction * Math.abs(normal.y * GRAVITY) * type.staticMultiplier;
        if (velocity.len() < STOP_SPEED && slopeMag < staticFriction) {
            velocity.setZero();
            spin.setZero();
            state = State.STATIONARY;
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
                    isGoodShot = false;
                    particleManager.spawnRatingBurst(position, Color.BROWN, 4, 1.0f);
                }
                continue;
            }
            if (tree.isInsideFoliage(position, BALL_RADIUS)) {
                lastInteraction = Interaction.LEAVES;
                BallPhysics.applyFoliagePhysics(velocity, spin, delta, FOLIAGE_DRAG_LIN, FOLIAGE_DRAG_SQU, DEFLECTION_CHANCE_PER_METER, DEFLECTION_MAGNITUDE);
                if (MathUtils.random() < 0.1f) particleManager.spawnRatingBurst(position, Color.FOREST, 1, 0.3f);
            }
        }
    }

    private void handleMonolithCollisions(Terrain terrain) {
        if (hitCooldown > 0) return;
        List<Terrain.Monolith> nearbyMonoliths = terrain.getMonolithsAt(position.x, position.z);
        for (Terrain.Monolith m : nearbyMonoliths) {
            if (BallPhysics.handleMonolithCollision(position, velocity, spin, m.getPosition(), m.getWidth(), m.getHeight(), m.getDepth(), m.getRotationY())) {
                hitCooldown = 0.05f;
                lastInteraction = Interaction.TERRAIN;
                isGoodShot = false;
                particleManager.spawnRatingBurst(position, Color.GRAY, 5, 1.1f);
                break;
            }
        }
    }

    public void hit(Vector3 shootDir, float power, float loft, float powerMult, MinigameResult.Rating rating) {
        if (state != State.STATIONARY) return;
        capturePosition();
        spin.setZero();
        this.shotRating = rating;
        this.isGoodShot = (rating == MinigameResult.Rating.PERFECTION || rating == MinigameResult.Rating.SUPER || rating == MinigameResult.Rating.GREAT);
        this.isInitialFlight = true;
        renderer.setShotRatingColor(rating);

        // Initial hit burst: Always allowed
        int burstCount = switch (rating) {
            case PERFECTION -> 15;
            case SUPER -> 10;
            case GREAT -> 5;
            default -> 2;
        };
        particleManager.spawnRatingBurst(position, renderer.getActiveTrailColor(), burstCount, (power / 50f) * 2.0f);

        float finalSpeed = power * powerMult;
        float angleRad = loft * MathUtils.degreesToRadians;
        velocity.set(shootDir).nor();
        velocity.y = MathUtils.sin(angleRad);
        float hLen = MathUtils.cos(angleRad);
        velocity.x *= hLen;
        velocity.z *= hLen;
        velocity.scl(finalSpeed);
        state = (loft < 1f) ? State.ROLLING : State.AIR;
        hitCooldown = (state == State.ROLLING) ? 0.02f : 0.1f;
        renderer.resetTrail(renderer.getActiveTrailColor());
        lastTrailPos.set(position);
    }

    public void capturePosition() {
        lastShotPosition.set(this.position);
    }

    public void resetToLastPosition() {
        this.position.set(lastShotPosition);
        this.lastStationaryPosition.set(lastShotPosition);
        velocity.setZero();
        spin.setZero();
        this.state = State.STATIONARY;
        renderer.updateVisuals(position, state);
    }

    public boolean isInWater(Terrain terrain) {
        return position.y < terrain.getWaterLevel();
    }

    public boolean checkVictory(Terrain terrain) {
        Vector3 holePos = terrain.getHolePosition();
        float dist = position.dst(holePos);
        float holeRadius = terrain.getHoleSize() / 2f;
        return dist < holeRadius && position.y < holePos.y - 0.22f;
    }

    public Vector3 getPosition() {
        return position;
    }

    public Vector3 getVelocity() {
        return velocity;
    }

    public Vector3 getSpin() {
        return spin;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Interaction getLastInteraction() {
        return lastInteraction;
    }

    public void clearInteraction() {
        lastInteraction = Interaction.NONE;
    }

    public float getFlatDistanceToHole(Terrain terrain) {
        if (terrain == null) return 0;
        return Vector2.dst(position.x, position.z, terrain.getHolePosition().x, terrain.getHolePosition().z);
    }

    public float getShotDistance() {
        return Vector2.dst(lastStationaryPosition.x, lastStationaryPosition.z, position.x, position.z);
    }

    public void render(ModelBatch batch, Environment env) {
        renderer.render(batch, env);
    }

    public void renderTrail(ModelBatch batch, Environment env) {
        renderer.renderTrail(batch, env);
    }

    public void dispose() {
        renderer.dispose();
    }
}