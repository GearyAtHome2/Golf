package org.example;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain;

public class Ball {

    private final Model model;
    private final ModelInstance instance;

    private final Vector3 position = new Vector3(0, 20, 0);
    private final Vector3 velocity = new Vector3(0, 0, 0);

    private static final float RADIUS = 0.2f;
    private static final float GRAVITY = -9.81f;
    private static final float BOUNCE_RESTITUTION = 0.3f;

    private static final float FOLIAGE_DRAG_SQU = 0.04f;
    private static final float FOLIAGE_DRAG_LIN = 0.04f;

    private static final float DEFLECTION_CHANCE_PER_METER = 0.6f;
    private static final float DEFLECTION_MAGNITUDE = 10f;

    private static final float STOP_SPEED = 0.15f;
    private static final float MIN_BOUNCE_VY = 0.2f;

    private boolean wet;
    private float waterCountdown = 5;
    private boolean wasInFoliageLastFrame = false;

    public enum State {AIR, CONTACT, ROLLING, STATIONARY}
    private State state = State.AIR;

    private float hitCooldown = 0f;
    private static final float HIT_COOLDOWN_TIME = 0.1f;

    public enum Interaction { NONE, LEAVES, WATER, TERRAIN }
    private Interaction lastInteraction = Interaction.NONE;

    public Ball(Vector3 startPosition) {
        ModelBuilder builder = new ModelBuilder();
        Material mat = new Material(ColorAttribute.createDiffuse(Color.WHITE));
        model = builder.createSphere(
                RADIUS, RADIUS, RADIUS,
                20, 20,
                mat,
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position |
                        com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal
        );
        instance = new ModelInstance(model);

        this.position.set(startPosition);
        instance.transform.setToTranslation(this.position);
        state = State.STATIONARY;
    }

    public void update(float delta, Terrain terrain) {
        reduceCooldown(delta);

        if (state == State.STATIONARY) {
            updateTransform();
            return;
        }

        handleWater(delta, terrain);

        float terrainHeight = terrain.getHeightAt(position.x, position.z) + RADIUS / 3;
        boolean onTerrain = hitCooldown <= 0f && position.y <= terrainHeight;

        if (onTerrain) {
            handleTerrainCollision(delta, terrain, terrainHeight);
        } else {
            applyAirPhysics(delta);
        }

        handleTreeCollisions(terrain, delta);
        moveBall(delta);
        updateColor();
        updateTransform();
    }

    public boolean checkVictory(Vector3 holePos, float holeSize) {
        float holeRadius = holeSize / 2f;
        float distToHole = position.dst(holePos);
        float speed = velocity.len();

        if (distToHole < holeRadius) {
            if (state == State.ROLLING || state == State.CONTACT) {
                if (speed < 6f) return true;
            }
            if (state == State.AIR) {
                float heightAboveHole = position.y - holePos.y;
                if (heightAboveHole < 0.6f) return true;
            }
        }
        return false;
    }

    public State getState() { return state; }

    private void reduceCooldown(float delta) {
        if (hitCooldown > 0f) hitCooldown -= delta;
    }

    private void handleWater(float delta, Terrain terrain) {
        if (position.y < terrain.getWaterLevel()) {
            lastInteraction = Interaction.WATER;
            wet = true;
            waterCountdown -= delta;
            velocity.scl(0.9f);
            velocity.y += -4f * GRAVITY * delta;
        } else {
            wet = false;
        }
    }

    private void handleTerrainCollision(float delta, Terrain terrain, float terrainHeight) {
        Vector3 terrainNormal = terrain.getNormalAt(position.x, position.z).nor();
        if (position.y < terrainHeight) position.y = terrainHeight;

        Vector3 velNormalComp = terrainNormal.cpy().scl(velocity.dot(terrainNormal));
        Vector3 velTangentComp = velocity.cpy().sub(velNormalComp);

        if (velNormalComp.len() > MIN_BOUNCE_VY) {
            velNormalComp.scl(-BOUNCE_RESTITUTION);
            state = State.CONTACT;
            lastInteraction = Interaction.TERRAIN; // Trigger particle on bounce
        } else {
            velNormalComp.setZero();
            state = State.ROLLING;
            // Only trigger terrain particles while rolling if moving fast enough
            if (velocity.len() > 1.5f) lastInteraction = Interaction.TERRAIN;
        }

        Vector3 gravityVec = new Vector3(0, GRAVITY, 0);
        Vector3 slopeForce = gravityVec.cpy().sub(terrainNormal.cpy().scl(gravityVec.dot(terrainNormal)));
        velTangentComp.add(slopeForce.scl(delta));

        Terrain.TerrainType currentType = terrain.getTerrainTypeAt(position.x, position.z);
        applyRealisticFriction(velTangentComp, terrainNormal, delta, currentType);

        velocity.set(velTangentComp).add(velNormalComp);
        updateState(velNormalComp, slopeForce, terrainNormal, currentType);
    }

    private void applyRealisticFriction(Vector3 tangentVel, Vector3 normal, float delta, Terrain.TerrainType type) {
        float speed = tangentVel.len();
        if (speed < 0.001f) return;
        float normalForceMag = Math.abs(normal.y * GRAVITY);
        float totalReduction = (type.kineticFriction * normalForceMag * delta) + (type.rollingResistance * speed * delta);

        if (totalReduction >= speed) tangentVel.setZero();
        else tangentVel.scl((speed - totalReduction) / speed);
    }

    private void updateState(Vector3 velNormal, Vector3 slopeForce, Vector3 normal, Terrain.TerrainType type) {
        float speed = velocity.len();
        float slopeMag = slopeForce.len();
        float maxStaticFriction = type.kineticFriction * Math.abs(normal.y * GRAVITY) * type.staticMultiplier;

        if (speed < STOP_SPEED && slopeMag < maxStaticFriction) {
            velocity.setZero();
            state = State.STATIONARY;
        } else if (velNormal.len() < MIN_BOUNCE_VY) {
            state = State.ROLLING;
        }
    }

    private void applyAirPhysics(float delta) {
        state = State.AIR;
        velocity.y += GRAVITY * delta;
        float speed = velocity.len();
        if (speed > 0f) {
            Vector3 drag = velocity.cpy().nor().scl(-0.003f * speed * speed);
            velocity.add(drag.scl(delta));
        }
    }

    private void applyFoliagePhysics(float delta) {
        lastInteraction = Interaction.LEAVES;
        float speed = velocity.len();
        if (speed < 0.1f) return;

        velocity.scl(Math.max(0, 1 - (FOLIAGE_DRAG_LIN + FOLIAGE_DRAG_SQU * speed) * delta));

        float frameChance = 1.0f - (float) Math.pow(1.0f - DEFLECTION_CHANCE_PER_METER, speed * delta);
        if (MathUtils.random() < frameChance) {
            velocity.rotate(Vector3.X, MathUtils.random(-DEFLECTION_MAGNITUDE, DEFLECTION_MAGNITUDE));
            velocity.rotate(Vector3.Y, MathUtils.random(-DEFLECTION_MAGNITUDE, DEFLECTION_MAGNITUDE));
            velocity.rotate(Vector3.Z, MathUtils.random(-DEFLECTION_MAGNITUDE, DEFLECTION_MAGNITUDE));
            velocity.scl(0.95f);
        }
    }

    private void handleTreeCollisions(Terrain terrain, float delta) {
        boolean currentlyInFoliage = false;

        for (Terrain.Tree tree : terrain.getTrees()) {
            Vector3 treePos = tree.getPosition();
            float trunkRad = tree.getTrunkRadius();
            float trunkHeight = tree.getTrunkHeight();
            float foliageRad = tree.getFoliageRadius();

            // Trunk Collision
            float dx = position.x - treePos.x;
            float dz = position.z - treePos.z;
            float distXZ = (float) Math.sqrt(dx * dx + dz * dz);

            if (distXZ < RADIUS + trunkRad && position.y < treePos.y + trunkHeight && position.y > treePos.y) {
                Vector3 hDir = new Vector3(dx, 0, dz).nor();
                float sH = new Vector3(velocity.x, 0, velocity.z).len();
                velocity.x = hDir.x * sH;
                velocity.z = hDir.z * sH;
                velocity.scl(0.8f);
                position.x += hDir.x * ((RADIUS + trunkRad - distXZ) + 0.001f);
                position.z += hDir.z * ((RADIUS + trunkRad - distXZ) + 0.001f);
                lastInteraction = Interaction.TERRAIN; // Bark hit is like terrain
                continue;
            }

            // Foliage Collision
            float fY = treePos.y + trunkHeight + foliageRad;
            float d3D = position.dst(treePos.x, fY, treePos.z);

            if (d3D < foliageRad + RADIUS) {
                currentlyInFoliage = true;
                applyFoliagePhysics(delta);
            }
        }

        // Logic for Exit Particles
        if (wasInFoliageLastFrame && !currentlyInFoliage) {
            lastInteraction = Interaction.LEAVES; // Trigger "exit explosion"
        }
        wasInFoliageLastFrame = currentlyInFoliage;
    }

    private void moveBall(float delta) {
        position.add(velocity.x * delta, velocity.y * delta, velocity.z * delta);
    }

    private void updateColor() {
        Color color = switch (state) {
            case AIR -> Color.WHITE;
            case CONTACT -> Color.GREEN;
            case ROLLING -> Color.RED;
            case STATIONARY -> Color.ORANGE;
        };
        for (Material m : instance.materials) {
            ((ColorAttribute) m.get(ColorAttribute.Diffuse)).color.set(color);
        }
    }

    private void updateTransform() {
        instance.transform.setToTranslation(position);
    }

    public void hit(Vector3 shootDir, float power) {
        if (state != State.STATIONARY) return;
        Vector3 dir = shootDir.cpy().nor();
        float angleRad = 40 * MathUtils.degreesToRadians;
        dir.y = MathUtils.sin(angleRad);
        float hLen = MathUtils.cos(angleRad);
        dir.x *= hLen;
        dir.z *= hLen;
        velocity.set(dir.scl(power * 25));
        state = State.AIR;
        hitCooldown = HIT_COOLDOWN_TIME;
    }

    public void render(ModelBatch batch, Environment env) {
        batch.render(instance, env);
    }

    public void dispose() {
        model.dispose();
    }

    public Vector3 getPosition() { return position; }
    public Vector3 getVelocity() { return velocity; }
    public void clearInteraction() { lastInteraction = Interaction.NONE; }
    public Interaction getLastInteraction() { return lastInteraction; }
}