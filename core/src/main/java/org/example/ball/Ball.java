package org.example.ball;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.example.GameConfig;
import org.example.glamour.ParticleManager;
import org.example.performance.PhysicsProfiler;
import org.example.terrain.Terrain;
import org.example.terrain.objects.Monolith;
import org.example.terrain.objects.Tree;

import java.util.List;
import java.util.Random;

public class Ball {

    public enum State {AIR, CONTACT, ROLLING, STATIONARY}
    public enum Interaction {NONE, LEAVES, WATER, TERRAIN}

    public static final float BALL_RADIUS = 0.2f;
    private static final float GRAVITY = -9.81f;
    private static final float AIR_DRAG_COEFF = 0.0048f;
    private static final float LIFT_COEFF = 5.0f;
    private static final float SIDE_COEFF = 5.0f;
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
    private final Vector3 lastFramePosition = new Vector3();

    private final BallRenderer renderer;
    private final ParticleManager particleManager;
    private final GameConfig config;
    private final Random random;

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

    public Ball(Vector3 startPosition, ParticleManager particleManager, GameConfig config, long seed) {
        this.renderer = new BallRenderer(BALL_RADIUS);
        this.particleManager = particleManager;
        this.config = config;
        this.position.set(startPosition);
        this.lastStationaryPosition.set(startPosition);
        this.lastShotPosition.set(startPosition);
        this.lastTrailPos.set(startPosition);
        this.lastFramePosition.set(startPosition);
        this.velocity.setZero();
        this.random = new Random(seed);
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

        if (state == State.STATIONARY && !renderer.hasVisibleTrail()) return;
        if (hitCooldown > 0) hitCooldown -= delta;

        float stabilizedDelta = Math.min(delta, 0.033f);
        float subDelta = stabilizedDelta / SUB_STEPS;

        PhysicsProfiler.startSection("PhysicsSubstepsTotal");
        for (int i = 0; i < SUB_STEPS; i++) {
            if (state != State.STATIONARY) {
                processPhysicsStep(subDelta, terrain, baseWind);
                PhysicsProfiler.startSection("ParticlesFlight");
                spawnFlightParticles(subDelta);
                PhysicsProfiler.endSection("ParticlesFlight");
            }

            PhysicsProfiler.startSection("TrailRecording");
            float dynamicStep = MathUtils.clamp(velocity.len() * 0.06f, 0.05f, 2.0f);
            if (position.dst2(lastTrailPos) > (dynamicStep * dynamicStep)) {
                renderer.recordTrailPoint(position, 1.0f);
                lastTrailPos.set(position);
            }
            PhysicsProfiler.endSection("TrailRecording");
            if (state == State.STATIONARY) break;
        }
        PhysicsProfiler.endSection("PhysicsSubstepsTotal");

        PhysicsProfiler.startSection("VisualSync");
        renderer.updateVisuals(position, state);
        lastFramePosition.set(position);
        PhysicsProfiler.endSection("VisualSync");
    }

    private void spawnFlightParticles(float subDelta) {
        if (state == State.AIR && isInitialFlight && config.particlesEnabled) {
            float velMag = velocity.len();
            Color trailColor = renderer.getActiveTrailColor();
            float chanceMultiplier = subDelta / 0.0166f;

            tempV1.set(lastFramePosition).lerp(position, random.nextFloat());

            if (shotRating == MinigameResult.Rating.PERFECTION) {
                float throb = MathUtils.sin(timeElapsed * 10f);
                float force = MathUtils.clamp(velMag / 20f, 0.4f, 1.2f) * (1.0f + (throb * 0.7f));
                particleManager.spawnRatingBurst(tempV1, trailColor, (throb > 0.4f) ? 4 : (velMag > 15f ? 2 : 1), force);
            } else {
                float threshold = (shotRating == MinigameResult.Rating.SUPER) ? 0.9f : (shotRating == MinigameResult.Rating.GREAT ? 0.35f : 0f);
                if (random.nextFloat() < threshold * chanceMultiplier) {
                    particleManager.spawnRatingBurst(tempV1, trailColor, 1, shotRating == MinigameResult.Rating.SUPER ? 0.4f : 0.3f);
                }
            }
        }
    }

    private void processPhysicsStep(float delta, Terrain terrain, Vector3 baseWind) {
        float preStepY = position.y;
        handleWaterTransition(terrain);

        PhysicsProfiler.startSection("EnvForces");
        handleEnvironmentPhysics(delta, terrain, baseWind);
        PhysicsProfiler.endSection("EnvForces");

        PhysicsProfiler.startSection("TreeCollision");
        handleTreeCollisions(terrain, delta);
        PhysicsProfiler.endSection("TreeCollision");

        PhysicsProfiler.startSection("MonolithCollision");
        handleMonolithCollisions(terrain);
        PhysicsProfiler.endSection("MonolithCollision");

        PhysicsProfiler.startSection("FlagPoleCollision");
        handleFlagPoleCollision(terrain);
        PhysicsProfiler.endSection("FlagPoleCollision");

        float stepYDelta = position.y - preStepY;
        if (Math.abs(stepYDelta) > 2.0f && hitCooldown <= 0f) {
            position.y = preStepY + MathUtils.clamp(stepYDelta, -1f, 1f);
        }
    }

    private void handleWaterTransition(Terrain terrain) {
        if (BallPhysics.checkWaterTransition(position, velocity, terrain.getWaterLevel())) {
            lastInteraction = Interaction.WATER;
            renderer.getActiveTrailColor().set(Color.CYAN);
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
        if ((state == State.ROLLING && position.y <= terrainHeight + 0.05f) || (position.y < terrainHeight)) {
            handleTerrainPhysics(delta, terrain, terrainHeight);
            position.add(tempV1.set(velocity).scl(delta));
        } else {
            state = State.AIR;
            Vector3 windAtHeight = BallPhysics.getWindAtHeight(baseWind, position.y);
            velocity.add(tempV1.setZero()
                    .add(BallPhysics.getGravityForce(GRAVITY))
                    .add(BallPhysics.getAirDrag(velocity, windAtHeight, AIR_DRAG_COEFF))
                    .add(BallPhysics.getMagnusForce(velocity, windAtHeight, spin, LIFT_COEFF, SIDE_COEFF))
                    .scl(delta));

            if (!handleAirCollisions(terrain)) applyAirMovement(delta, terrain);
            if (velocity.len() > 0.1f) spin.scl(Math.max(0f, 1.0f - ((0.05f + (velocity.len2() * 0.0002f)) * delta)));
        }
    }

    private boolean handleAirCollisions(Terrain terrain) {
        tempV1.set(velocity).scl(0.016f);
        for (float t = 0.33f; t <= 1.0f; t += 0.33f) {
            float cx = position.x + (tempV1.x * t), cz = position.z + (tempV1.z * t), cy = position.y + (tempV1.y * t);
            float h = terrain.getHeightAt(cx, cz) + BALL_RADIUS / 3;
            if (cy < h) {
                Vector3 n = terrain.getNormalAt(cx, cz);
                if (BallPhysics.isWallCollision(n, 45f) || (h - cy) > MAX_STEP_UP) {
                    if (hitCooldown <= 0f) {
                        Vector3 wn = new Vector3(n.x, 0, n.z).nor();
                        velocity.set(BallPhysics.calculateBounceWithSpin(velocity, wn, spin, BOUNCE_RESTITUTION, 0.4f, 0f));
                        position.add(tempV1.set(wn).scl(0.15f));
                        hitCooldown = 0.05f;
                        lastInteraction = Interaction.TERRAIN;
                        if (!isGoodShot) renderer.lerpTrailColor(Color.GRAY, 0.4f);
                        particleManager.spawnRatingBurst(position, Color.LIGHT_GRAY, 3, 0.8f);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private void applyAirMovement(float delta, Terrain terrain) {
        float nextH = terrain.getHeightAt(position.x + velocity.x * delta, position.z + velocity.z * delta) + BALL_RADIUS / 3;
        if (position.y + velocity.y * delta < nextH && hitCooldown <= 0f) {
            handleTerrainPhysics(delta, terrain, nextH);
            position.add(tempV1.set(velocity).scl(delta));
        } else {
            position.add(velocity.x * delta, velocity.y * delta, velocity.z * delta);
        }
    }

    private void handleTerrainPhysics(float delta, Terrain terrain, float terrainHeight) {
        Vector3 normal = terrain.getNormalAt(position.x, position.z).nor();
        Terrain.TerrainType type = terrain.getTerrainTypeAt(position.x, position.z);

        if (terrainHeight - position.y > MAX_STEP_UP) {
            tempV1.set(normal.x, 0, normal.z).nor();
            if (tempV1.len() < 0.1f) position.y = terrainHeight;
            else {
                position.add(tempV1.scl(0.4f));
                velocity.set(BallPhysics.calculateBounceWithSpin(velocity, tempV1, spin, 0.3f, type.kineticFriction, type.softness));
                particleManager.spawnRatingBurst(position, Color.BROWN, 2, 0.5f);
            }
            return;
        }

        if (state == State.ROLLING && velocity.dot(normal) > 1.5f) { state = State.AIR; return; }
        if (position.y < terrainHeight && !BallPhysics.isWallCollision(normal, 45f)) position.y = terrainHeight;

        if (state == State.AIR && velocity.dot(normal) < -MIN_BOUNCE_VY) {
            if (isInitialFlight) particleManager.spawnRatingBurst(position, renderer.getActiveTrailColor(), switch (shotRating) { case PERFECTION -> 8; case SUPER -> 5; case GREAT -> 3; default -> 1; }, 1.2f);
            isInitialFlight = false;
            velocity.set(BallPhysics.calculateBounceWithSpin(velocity, normal, spin, type.restitution, type.kineticFriction, type.softness));
            state = State.CONTACT;
            lastInteraction = Interaction.TERRAIN;
        } else {
            state = State.ROLLING;
            vTangent.set(velocity).sub(tempV1.set(normal).scl(velocity.dot(normal)));
            BallPhysics.resolveRollingSpin(vTangent, spin, normal, type, delta);
            vTangent.add(BallPhysics.getSlopeForce(BallPhysics.getGravityForce(GRAVITY), normal).scl(delta))
                    .add(BallPhysics.getRollingFriction(vTangent, normal, type, GRAVITY).scl(delta));
            velocity.set(vTangent);
        }

        float slopeMag = BallPhysics.getSlopeForce(BallPhysics.getGravityForce(GRAVITY), normal).len();
        if (velocity.len() < STOP_SPEED && slopeMag < (type.kineticFriction * Math.abs(normal.y * GRAVITY) * type.staticMultiplier)) {
            velocity.setZero(); spin.setZero(); state = State.STATIONARY; lastStationaryPosition.set(position);
        }
    }

    private void handleTreeCollisions(Terrain terrain, float delta) {
        if (hitCooldown > 0) return;
        for (Tree tree : terrain.getTreesAt(position.x, position.z)) {
            if (tree.checkTrunkCollision(position, BALL_RADIUS)) {
                if (BallPhysics.handleTrunkCollision(position, velocity, spin, position.x - tree.getPosition().x, position.z - tree.getPosition().z)) {
                    lastInteraction = Interaction.TERRAIN; isGoodShot = false; hitCooldown = 0.1f;
                    particleManager.spawnRatingBurst(position, Color.BROWN, 4, 1.0f);
                }
                continue;
            }
            if (tree.isInsideFoliage(position, BALL_RADIUS)) {
                lastInteraction = Interaction.LEAVES;
                BallPhysics.applyFoliagePhysics(velocity, spin, delta, FOLIAGE_DRAG_LIN, FOLIAGE_DRAG_SQU, DEFLECTION_CHANCE_PER_METER, DEFLECTION_MAGNITUDE, random);
                if (random.nextFloat() < 0.1f) particleManager.spawnRatingBurst(position, Color.FOREST, 1, 0.3f);
            }
        }
    }

    private void handleFlagPoleCollision(Terrain terrain) {
        if (hitCooldown > 0) return;
        Vector3 holePos = terrain.getHolePosition();
        float dx = position.x - holePos.x;
        float dz = position.z - holePos.z;
        if (dx * dx + dz * dz > 9f) return; // Cull: more than 3 units away
        float speed = velocity.len();
        if (BallPhysics.handleFlagPoleCollision(position, velocity, spin, holePos, 0.1f, 5f)) {
            Gdx.app.log("FlagCollision", String.format("impact=%.2f m/s  post=%.2f m/s", speed, velocity.len()));
            hitCooldown = 0.05f;
            lastInteraction = Interaction.TERRAIN;
            isGoodShot = false;
            particleManager.spawnRatingBurst(position, Color.WHITE, 3, 0.8f);
            terrain.triggerFlagWobble(speed);
        }
    }

    private void handleMonolithCollisions(Terrain terrain) {
        if (hitCooldown > 0) return;
        for (Monolith m : terrain.getMonolithsAt(position.x, position.z)) {
            if (BallPhysics.handleMonolithCollision(position, velocity, spin, m.getPosition(), m.getWidth(), m.getHeight(), m.getDepth(), m.getRotationY())) {
                hitCooldown = 0.05f; lastInteraction = Interaction.TERRAIN; isGoodShot = false;
                particleManager.spawnRatingBurst(position, Color.GRAY, 5, 1.1f);
                break;
            }
        }
    }

    public void hit(Vector3 shootDir, float power, float loft, float powerMult, MinigameResult.Rating rating) {
        if (state != State.STATIONARY) return;
        capturePosition();
        this.shotRating = rating;
        this.isGoodShot = (rating != MinigameResult.Rating.POOR);
        this.isInitialFlight = true;
        renderer.setShotRatingColor(rating);
        particleManager.spawnRatingBurst(position, renderer.getActiveTrailColor(), switch (rating) { case PERFECTION -> 15; case SUPER -> 10; case GREAT -> 5; default -> 2; }, (power / 50f) * 2.0f);
        velocity.set(shootDir).scl(power * powerMult);
        state = State.AIR;
        position.y += 0.05f;
        hitCooldown = 0.1f;
        renderer.resetTrail(renderer.getActiveTrailColor());
        lastTrailPos.set(position);
    }

    public void capturePosition() {
        lastShotPosition.set(this.position);
    }

    public void resetToLastPosition() {
        this.position.set(lastShotPosition);
        this.lastStationaryPosition.set(lastShotPosition);
        velocity.setZero(); spin.setZero(); this.state = State.STATIONARY;
        renderer.updateVisuals(position, state);
    }

    public boolean isInWater(Terrain terrain) {
        return position.y < terrain.getWaterLevel();
    }

    public boolean checkVictory(Terrain terrain) {
        Vector3 hp = terrain.getHolePosition();
        return position.dst(hp) < (terrain.getHoleSize() / 2f) * 1.5 && position.y < hp.y - 0.3f;
    }

    public float getFlatDistanceToHole(Terrain terrain) {
        if (terrain == null) return 0;
        return Vector2.dst(position.x, position.z, terrain.getHolePosition().x, terrain.getHolePosition().z);
    }

    public float getShotDistance() {
        return Vector2.dst(lastStationaryPosition.x, lastStationaryPosition.z, position.x, position.z);
    }

    public Vector3 getPosition() { return position; }
    public Vector3 getVelocity() { return velocity; }
    public Vector3 getSpin() { return spin; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public Interaction getLastInteraction() { return lastInteraction; }
    public void clearInteraction() { lastInteraction = Interaction.NONE; }
    public void render(ModelBatch batch, Environment env) { renderer.render(batch, env); }
    public void renderTrail(ModelBatch batch, Environment env) { renderer.renderTrail(batch, env); }
    public void dispose() { renderer.dispose(); }
}