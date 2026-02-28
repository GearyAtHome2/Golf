package org.example;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
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
    private float spin = 0f;

    private static final float RADIUS = 0.2f;
    private static final float GRAVITY = -9.81f;
    private static final float BOUNCE_RESTITUTION = 0.35f;

    private static final float FOLIAGE_DRAG_SQU = 0.04f;
    private static final float FOLIAGE_DRAG_LIN = 0.04f;

    private static final float DEFLECTION_CHANCE_PER_METER = 0.6f;
    private static final float DEFLECTION_MAGNITUDE = 10f;

    private static final float STOP_SPEED = 0.15f;
    private static final float MIN_BOUNCE_VY = 0.3f;

    private boolean wet;
    private float waterCountdown = 5;
    private boolean wasInFoliageLastFrame = false;

    public enum State {AIR, CONTACT, ROLLING, STATIONARY}
    private State state = State.AIR;

    private float loftMagnusEffectPower = 0.00004f;
    private float hitCooldown = 0f;
    private static final float HIT_COOLDOWN_TIME = 0.1f;

    public enum Interaction { NONE, LEAVES, WATER, TERRAIN }
    private Interaction lastInteraction = Interaction.NONE;

    public Ball(Vector3 startPosition) {
        ModelBuilder builder = new ModelBuilder();
        Material mat = new Material(ColorAttribute.createDiffuse(Color.WHITE));
        model = builder.createSphere(RADIUS, RADIUS, RADIUS, 20, 20, mat,
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

    private void handleTerrainCollision(float delta, Terrain terrain, float terrainHeight) {
        Vector3 terrainNormal = terrain.getNormalAt(position.x, position.z).nor();

        if (position.y < terrainHeight) position.y = terrainHeight;

        float dot = velocity.dot(terrainNormal);
        Vector3 velNormalComp = terrainNormal.cpy().scl(dot);
        Vector3 velTangentComp = velocity.cpy().sub(velNormalComp);

        float vVert = Math.abs(dot);

        if (vVert > MIN_BOUNCE_VY) {
            velNormalComp.scl(-BOUNCE_RESTITUTION);
            state = State.CONTACT;
            lastInteraction = Interaction.TERRAIN;
        } else {
            velNormalComp.setZero();
            state = State.ROLLING;
            spin *= MathUtils.clamp(1.0f - (delta * 5.0f), 0, 1);
        }

        Vector3 gravityVec = new Vector3(0, GRAVITY, 0);
        Vector3 slopeForce = gravityVec.cpy().sub(terrainNormal.cpy().scl(gravityVec.dot(terrainNormal)));
        velTangentComp.add(slopeForce.scl(delta));

        Terrain.TerrainType currentType = terrain.getTerrainTypeAt(position.x, position.z);
        applyRealisticFriction(velTangentComp, terrainNormal, delta, currentType);

        velocity.set(velTangentComp).add(velNormalComp);

        updateState(velNormalComp, slopeForce, terrainNormal, currentType);

        spin = Math.max(0, spin - (spin * 0.5f * delta));
    }

    private void applyAirPhysics(float delta) {
        state = State.AIR;
        velocity.y += GRAVITY * delta;
        float speed = velocity.len();
        if (speed > 0f) {
            Vector3 drag = velocity.cpy().nor().scl(-0.003f * speed * speed);
            velocity.add(drag.scl(delta));

            // New Spin Physics: Spin decays faster when ball speed is high (Driver)
            // Slow Wedge shots preserve spin much better.
            float spinDecay = 0.01f + (speed * speed * 0.00005f);
            spin = Math.max(0, spin - (spin * spinDecay * delta));

            if (spin > 0) {
                float liftMagnitude = (spin * speed) * loftMagnusEffectPower;
                velocity.y += liftMagnitude * delta;
            }
        }
    }

    public void hit(Vector3 shootDir, float power, float loft, float powerMult) {
        if (state != State.STATIONARY) return;
        Vector3 dir = shootDir.cpy().nor();
        float angleRad = loft * MathUtils.degreesToRadians;
        dir.y = MathUtils.sin(angleRad);
        float hLen = MathUtils.cos(angleRad);
        dir.x *= hLen; dir.z *= hLen;

        float finalVelocity = power * powerMult;
        velocity.set(dir.scl(finalVelocity));

        // Power and Loft both generate spin
        float powerRatio = MathUtils.clamp(finalVelocity / 45.0f, 0.1f, 1.2f);
        this.spin = (finalVelocity) * (loft * 0.8f) * powerRatio;

        state = State.AIR;
        hitCooldown = HIT_COOLDOWN_TIME;
    }

    public boolean checkVictory(Vector3 holePos, float holeSize) {
        float holeRadius = holeSize / 2f;
        float distToHole = position.dst(holePos);
        if (distToHole < holeRadius) {
            if (state == State.ROLLING || state == State.CONTACT) {
                if (velocity.len() < 6f) return true;
            }
            if (state == State.AIR && (position.y - RADIUS - holePos.y) < 0f) return true;
        }
        return false;
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
            spin = 0;
            state = State.STATIONARY;
        } else if (velNormal.len() < MIN_BOUNCE_VY) {
            state = State.ROLLING;
        }
    }

    private void handleWater(float delta, Terrain terrain) {
        if (position.y < terrain.getWaterLevel()) {
            lastInteraction = Interaction.WATER;
            wet = true;
            waterCountdown -= delta;
            velocity.scl(0.9f);
            velocity.y += -4f * GRAVITY * delta;
            spin *= 0.5f;
        } else {
            wet = false;
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
                Vector3 hDir = new Vector3(dx, 0, dz).nor();
                float sH = new Vector3(velocity.x, 0, velocity.z).len();
                velocity.x = hDir.x * sH; velocity.z = hDir.z * sH;
                velocity.scl(0.8f); spin *= 0.2f;
                position.x += hDir.x * 0.01f; position.z += hDir.z * 0.01f;
                lastInteraction = Interaction.TERRAIN;
                continue;
            }
            float fY = treePos.y + trunkHeight + tree.getFoliageRadius();
            if (position.dst(treePos.x, fY, treePos.z) < tree.getFoliageRadius() + RADIUS) {
                currentlyInFoliage = true;
                applyFoliagePhysics(delta);
            }
        }
        if (wasInFoliageLastFrame && !currentlyInFoliage) lastInteraction = Interaction.LEAVES;
        wasInFoliageLastFrame = currentlyInFoliage;
    }

    private void applyFoliagePhysics(float delta) {
        lastInteraction = Interaction.LEAVES;
        float speed = velocity.len();
        if (speed < 0.1f) return;
        velocity.scl(Math.max(0, 1 - (FOLIAGE_DRAG_LIN + FOLIAGE_DRAG_SQU * speed) * delta));
        spin *= 0.8f;
        if (MathUtils.random() < (1.0f - (float) Math.pow(1.0f - DEFLECTION_CHANCE_PER_METER, speed * delta))) {
            velocity.rotate(Vector3.X, MathUtils.random(-DEFLECTION_MAGNITUDE, DEFLECTION_MAGNITUDE));
            velocity.rotate(Vector3.Y, MathUtils.random(-DEFLECTION_MAGNITUDE, DEFLECTION_MAGNITUDE));
            velocity.rotate(Vector3.Z, MathUtils.random(-DEFLECTION_MAGNITUDE, DEFLECTION_MAGNITUDE));
            velocity.scl(0.95f);
        }
    }

    public void setGhostColor(Color color) {
        for (Material m : instance.materials) {
            m.set(ColorAttribute.createDiffuse(color));
            // Optional: make them slightly transparent
            m.set(new BlendingAttribute(0.7f));
        }
    }

    private void reduceCooldown(float delta) { if (hitCooldown > 0f) hitCooldown -= delta; }
    private void moveBall(float delta) { position.add(velocity.x * delta, velocity.y * delta, velocity.z * delta); }
    private void updateColor() {
        Color color = switch (state) { case AIR -> Color.WHITE; case CONTACT -> Color.GREEN; case ROLLING -> Color.RED; case STATIONARY -> Color.ORANGE; };
        for (Material m : instance.materials) { ((ColorAttribute) m.get(ColorAttribute.Diffuse)).color.set(color); }
    }
    private void updateTransform() { instance.transform.setToTranslation(position); }
    public void render(ModelBatch batch, Environment env) { batch.render(instance, env); }
    public void dispose() { model.dispose(); }
    public Vector3 getPosition() { return position; }
    public Vector3 getVelocity() { return velocity; }
    public float getSpin() { return spin; }
    public State getState() { return state; }
    public void clearInteraction() { lastInteraction = Interaction.NONE; }
    public Interaction getLastInteraction() { return lastInteraction; }
}