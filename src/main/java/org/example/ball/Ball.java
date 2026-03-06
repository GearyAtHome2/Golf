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

    private final Model model;
    private final ModelInstance instance;
    private final Vector3 position = new Vector3();
    private final Vector3 velocity = new Vector3();
    private final Vector3 spin = new Vector3();
    private final Vector3 lastStationaryPosition = new Vector3();

    private Model trailPointModel;
    private ModelInstance trailInstance;
    private final Array<TrailPoint> trail = new Array<>();

    private Color activeTrailColor = new Color(Color.WHITE);
    private boolean isGoodShot = false;

    private static class TrailPoint {
        Vector3 pos = new Vector3();
        float life = 4.0f;
        Color color = new Color();
        float scaleMod = 1.0f;
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
                new Material(ColorAttribute.createDiffuse(Color.WHITE), new BlendingAttribute(0.6f)),
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
        recordTrailPoint(1.0f);

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

        if (preStepY >= waterLevel && (position.y + velocity.y * delta) < waterLevel) {
            lastInteraction = Interaction.WATER;
            activeTrailColor.set(Color.CYAN);
            spawnBurst(3, 1.5f);
        }

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

        handleTreeCollisions(terrain, delta);
        handleMonolithCollisions(terrain);

        float stepYDelta = position.y - preStepY;
        if (Math.abs(stepYDelta) > 2.0f && hitCooldown <= 0f) {
            position.y = preStepY + MathUtils.clamp(stepYDelta, -1f, 1f);
        }
    }

    private void handleMonolithCollisions(Terrain terrain) {
        if (hitCooldown > 0) return;

        for (Terrain.Monolith m : terrain.getMonoliths()) {
            boolean hit = BallPhysics.handleMonolithCollision(
                    position, velocity, spin,
                    m.getPosition(), m.getWidth(), m.getHeight(), m.getDepth(), m.getRotationY()
            );

            if (hit) {
                hitCooldown = 0.05f;
                lastInteraction = Interaction.TERRAIN;
                activeTrailColor.set(Color.DARK_GRAY);
                break;
            }
        }
    }

    private void handleAirPhysicsWithSweep(float delta, Terrain terrain, Vector3 baseWind) {
        state = State.AIR;
        Vector3 windAtHeight = BallPhysics.getWindAtHeight(baseWind, position.y);

        tempV1.setZero().add(BallPhysics.getGravityForce(GRAVITY))
                .add(BallPhysics.getAirDrag(velocity, windAtHeight, AIR_DRAG_COEFF))
                .add(BallPhysics.getMagnusForce(velocity, windAtHeight, spin, LIFT_COEFF, SIDE_COEFF));

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
                if (BallPhysics.isWallCollision(nAtPoint, 45f) || (hAtPoint - checkY) > MAX_STEP_UP) {
                    collisionFound = true;
                    bestNormal.set(nAtPoint);
                    break;
                }
            }
        }

        if (collisionFound && hitCooldown <= 0f) {
            Vector3 wallNormal = new Vector3(bestNormal.x, 0, bestNormal.z).nor();
            if (wallNormal.len() < 0.1f) wallNormal.set(-velocity.x, 0, -velocity.z).nor();

            velocity.set(BallPhysics.calculateBounceWithSpin(velocity, wallNormal, spin, BOUNCE_RESTITUTION, 0.4f, 0f));
            position.add(tempV1.set(wallNormal).scl(0.15f));
            hitCooldown = 0.05f;
            lastInteraction = Interaction.TERRAIN;
            activeTrailColor.lerp(Color.GRAY, 0.4f);
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
        boolean isWall = BallPhysics.isWallCollision(normal, 45f);

        // LOGGING: State of entry
        float horizSpeedPre = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        Gdx.app.log("BOUNCE_LOG", String.format("--- Terrain Impact --- State: %s | HorizSpeed: %.4f | Vy: %.4f", state, horizSpeedPre, velocity.y));

        if (terrainHeight - position.y > MAX_STEP_UP) {
            tempV1.set(normal.x, 0, normal.z).nor();
            if (tempV1.len() < 0.1f) position.y = terrainHeight;
            else {
                position.add(tempV1.scl(0.4f));
                velocity.set(BallPhysics.calculateBounceWithSpin(velocity, tempV1, spin, 0.3f, type.kineticFriction, type.softness));
            }
            return;
        }

        if (position.y < terrainHeight && !isWall) position.y = terrainHeight;

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

        float horizSpeedPost = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        Gdx.app.log("BOUNCE_LOG", String.format("--- Post Terrain --- HorizSpeed: %.4f | Vy: %.4f", horizSpeedPost, velocity.y));
    }

    private void applyBounce(Vector3 normal, Terrain.TerrainType type) {
        float horizSpeedBeforeBounce = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        float friction = type.kineticFriction;
        float frictionImpact = (friction - 0.2f) * 0.08f;
        float localRestitution = MathUtils.clamp(BOUNCE_RESTITUTION - frictionImpact, 0.05f, BOUNCE_RESTITUTION);

        velocity.set(BallPhysics.calculateBounceWithSpin(velocity, normal, spin, localRestitution, friction ,type.softness ));

        float vDotN = velocity.dot(normal);
        vNormal.set(normal).scl(vDotN);
        vTangent.set(velocity).sub(vNormal);

        state = State.CONTACT;
        lastInteraction = Interaction.TERRAIN;

        float spinLoss = 0.4f + (friction * 0.3f);
        spin.scl(MathUtils.clamp(1.0f - spinLoss, 0f, 1.0f));

        if (isGoodShot) spawnBurst(5, 2.0f);
        activeTrailColor.lerp(Color.GRAY, 0.25f);

        float horizSpeedAfterBounce = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        Gdx.app.log("BOUNCE_LOG", String.format("   ApplyBounce DONE | Horiz Change: %.4f -> %.4f | Normal: %s", horizSpeedBeforeBounce, horizSpeedAfterBounce, normal.toString()));
    }

    private void applyRollingPhysics(Vector3 tangentVel, Vector3 normal, Terrain.TerrainType type, float delta) {
        Vector3 slopeForce = BallPhysics.getSlopeForce(BallPhysics.getGravityForce(GRAVITY), normal);
        Vector3 friction = BallPhysics.getRollingFriction(tangentVel, normal, type, GRAVITY);

        float horizSpeedBeforeRolling = tangentVel.len();
        tangentVel.add(slopeForce.scl(delta)).add(friction.scl(delta));
        float horizSpeedAfterRolling = tangentVel.len();

        spin.setZero();

        if (activeTrailColor.r > 0.3f || activeTrailColor.g > 0.3f) {
            activeTrailColor.lerp(new Color(0.2f, 0.2f, 0.2f, 1f), delta * 1.5f);
        }

        Gdx.app.log("BOUNCE_LOG", String.format("   Rolling Step | Tangent Change: %.4f -> %.4f", horizSpeedBeforeRolling, horizSpeedAfterRolling));
    }

    private void checkStationaryCondition(Terrain.TerrainType type, Vector3 normal) {
        float slopeMag = BallPhysics.getSlopeForce(BallPhysics.getGravityForce(GRAVITY), normal).len();
        float staticFriction = type.kineticFriction * Math.abs(normal.y * GRAVITY) * type.staticMultiplier;
        if (velocity.len() < STOP_SPEED && slopeMag < staticFriction) {
            velocity.setZero();
            spin.setZero();
            state = State.STATIONARY;
            activeTrailColor.set(Color.WHITE);
            Gdx.app.log("BOUNCE_LOG", "   STATE: STATIONARY");
        }
    }

    private void handleTreeCollisions(Terrain terrain, float delta) {
        for (Terrain.Tree tree : terrain.getTrees()) {
            Vector3 treePos = tree.getPosition();
            float dx = position.x - treePos.x, dz = position.z - treePos.z;
            float distXZ = (float) Math.sqrt(dx * dx + dz * dz);

            if (distXZ < BALL_RADIUS + tree.getTrunkRadius() && position.y < treePos.y + tree.getTrunkHeight() && position.y > treePos.y) {
                tempV1.set(dx, 0, dz).nor();
                float speedXZ = tempV2.set(velocity.x, 0, velocity.z).len();
                velocity.x = tempV1.x * speedXZ;
                velocity.z = tempV1.z * speedXZ;
                velocity.scl(0.8f);
                spin.scl(0.2f);
                position.x += tempV1.x * 0.05f;
                position.z += tempV1.z * 0.05f;
                lastInteraction = Interaction.TERRAIN;
                activeTrailColor.set(Color.BROWN);
                continue;
            }

            float fY = treePos.y + tree.getTrunkHeight() + tree.getFoliageRadius();
            if (tree.isInsideFoliage(position, BALL_RADIUS)) {
                lastInteraction = Interaction.LEAVES;
                BallPhysics.applyFoliagePhysics(velocity, spin, delta, FOLIAGE_DRAG_LIN, FOLIAGE_DRAG_SQU, DEFLECTION_CHANCE_PER_METER, DEFLECTION_MAGNITUDE);
                activeTrailColor.lerp(Color.FOREST, 0.2f);
            }
        }
    }

    public void hit(Vector3 shootDir, float power, float loft, float powerMult, MinigameResult.Rating rating) {
        if (state != State.STATIONARY) return;
        lastStationaryPosition.set(position);
        spin.setZero();

        isGoodShot = false;
        switch (rating) {
            case PERFECTION -> { activeTrailColor.set(Color.PURPLE); isGoodShot = true; }
            case SUPER -> { activeTrailColor.set(Color.PINK); isGoodShot = true; }
            case GREAT -> { activeTrailColor.set(Color.GOLD); isGoodShot = true; }
            case GOOD -> activeTrailColor.set(Color.GREEN);
            case MEH -> activeTrailColor.set(Color.GRAY);
            case WANK -> activeTrailColor.set(Color.ORANGE);
            case SHIT -> activeTrailColor.set(Color.BROWN);
            default -> activeTrailColor.set(Color.WHITE);
        }

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
        trail.clear();
    }

    public void resetToLastPosition() {
        this.position.set(lastStationaryPosition);
        this.velocity.setZero();
        this.spin.setZero();
        this.state = State.STATIONARY;
        this.trail.clear();
        this.activeTrailColor.set(Color.WHITE);
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

    public void render(ModelBatch batch, Environment env) {
        batch.render(instance, env);
    }

    public void renderTrail(ModelBatch batch, Environment env) {
        for (TrailPoint p : trail) {
            float lifeRatio = MathUtils.clamp(p.life / 4.0f, 0f, 1f);
            float scale = (0.2f + (lifeRatio * 0.8f)) * p.scaleMod;
            trailInstance.transform.setToTranslation(p.pos).scale(scale, scale, scale);
            ((ColorAttribute) trailInstance.materials.get(0).get(ColorAttribute.Diffuse)).color.set(p.color);
            ((BlendingAttribute) trailInstance.materials.get(0).get(BlendingAttribute.Type)).opacity = lifeRatio * 0.7f;
            batch.render(trailInstance, env);
        }
    }

    private void updateTrail(float delta) {
        for (int i = trail.size - 1; i >= 0; i--) {
            trail.get(i).life -= delta;
            if (trail.get(i).scaleMod > 1.0f) trail.get(i).life -= delta * 2f;
            if (trail.get(i).life <= 0) trail.removeIndex(i);
        }
    }

    private void recordTrailPoint(float scale) {
        TrailPoint p = new TrailPoint();
        p.pos.set(position);
        p.color.set(activeTrailColor);
        p.scaleMod = scale;
        trail.add(p);
    }

    private void spawnBurst(int count, float scale) {
        for (int i = 0; i < count; i++) {
            recordTrailPoint(scale + MathUtils.random(-0.2f, 0.5f));
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
        trailPointModel.dispose();
    }
}