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

    // Friction Coefficients
    private static final float KINETIC_FRICTION = 0.45f;    // Constant deceleration (m/s^2)
    private static final float ROLLING_RESISTANCE = 2.15f;  // Proportional to velocity
    private static final float STATIC_FRICTION_MULT = 1.2f; // Must overcome this to start rolling

    private static final float FOLIAGE_DRAG_SQU = 0.04f; // Massive v^2 drag
    private static final float FOLIAGE_DRAG_LIN = 0.04f;  // High linear drag
    private static final float DEFLECTION_CHANCE = 1f; // 100% for testing as requested
    private static final float DEFLECTION_MAGNITUDE = 5.95f; // How "wild" the bounce is

    private static final float STOP_SPEED = 0.15f;
    private static final float MIN_BOUNCE_VY = 0.2f;

    private boolean wet;
    private float waterCountdown = 5;

    private enum State {AIR, CONTACT, ROLLING, STATIONARY}
    private State state = State.AIR;

    private float hitCooldown = 0f;
    private static final float HIT_COOLDOWN_TIME = 0.1f;

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

        // Ground check
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

        debugLog();
    }

    private void reduceCooldown(float delta) {
        if (hitCooldown > 0f) hitCooldown -= delta;
    }

    private void handleWater(float delta, Terrain terrain) {
        if (position.y < terrain.getWaterLevel()) {
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

        // Split velocity into Normal and Tangential components
        Vector3 velNormalComp = terrainNormal.cpy().scl(velocity.dot(terrainNormal));
        Vector3 velTangentComp = velocity.cpy().sub(velNormalComp);

        // Handle Bouncing
        if (velNormalComp.len() > MIN_BOUNCE_VY) {
            velNormalComp.scl(-BOUNCE_RESTITUTION);
            state = State.CONTACT;
        } else {
            velNormalComp.setZero();
            state = State.ROLLING;
        }

        // Apply Slope Acceleration (Gravity along the tangent)
        Vector3 gravityVec = new Vector3(0, GRAVITY, 0);
        Vector3 slopeForce = gravityVec.cpy().sub(terrainNormal.cpy().scl(gravityVec.dot(terrainNormal)));
        velTangentComp.add(slopeForce.scl(delta));

        // Apply Improved Friction to the tangential velocity
        applyRealisticFriction(velTangentComp, terrainNormal, slopeForce, delta);

        velocity.set(velTangentComp).add(velNormalComp);

        updateState(velNormalComp, slopeForce, terrainNormal);
    }

    private void applyRealisticFriction(Vector3 tangentVel, Vector3 normal, Vector3 slopeForce, float delta) {
        float speed = tangentVel.len();
        if (speed < 0.001f) return;

        // Friction is proportional to the normal force (gravity pressing down)
        float normalForceMag = Math.abs(normal.y * GRAVITY);

        // 1. Coulomb Friction (Constant deceleration)
        float coulombDecel = KINETIC_FRICTION * normalForceMag * delta;

        // 2. Rolling Resistance (Linear speed reduction)
        float rollingDecel = ROLLING_RESISTANCE * speed * delta;

        float totalReduction = coulombDecel + rollingDecel;

        if (totalReduction >= speed) {
            tangentVel.setZero();
        } else {
            tangentVel.scl((speed - totalReduction) / speed);
        }
    }

    private void updateState(Vector3 velNormal, Vector3 slopeForce, Vector3 normal) {
        float speed = velocity.len();
        float slopeMag = slopeForce.len();
        float maxStaticFriction = KINETIC_FRICTION * Math.abs(normal.y * GRAVITY) * STATIC_FRICTION_MULT;

        // If moving slow enough AND slope isn't steep enough to overcome static friction
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

        float dragCoefficient = 0.003f;
        float speed = velocity.len();
        if (speed > 0f) {
            Vector3 drag = velocity.cpy().nor().scl(-dragCoefficient * speed * speed);
            velocity.add(drag.scl(delta));
        }
    }

    private void applyFoliagePhysics(float delta) {
        float speed = velocity.len();
        if (speed < 0.1f) return;

        // 1. Delta-Safe Drag
        // We use the drag formula: v_new = v_old * (1 - drag * delta)
        // This is more stable than subtracting forces manually.
        float totalDrag = (FOLIAGE_DRAG_LIN + FOLIAGE_DRAG_SQU * speed);
        float frictionScale = Math.max(0, 1 - totalDrag * delta);
        velocity.scl(frictionScale);

        // 2. Delta-Safe Deflection Chance
        // To make a chance delta-safe, we adjust the probability:
        // P_delta = 1 - (1 - P_base)^delta
        float frameIndependentChance = (float) (1.0f - Math.pow(1.0f - DEFLECTION_CHANCE, delta));

        // For 100% testing, we can skip the math and just check > 0
        if (DEFLECTION_CHANCE >= 1.0f || MathUtils.random() < frameIndependentChance) {

            // Instead of adding a vector, we ROTATE the current velocity.
            // This preserves speed but changes direction significantly.
            float spr = DEFLECTION_MAGNITUDE * 90f * delta; // Max degrees to rotate per second

            velocity.rotate(Vector3.X, MathUtils.random(-spr, spr));
            velocity.rotate(Vector3.Y, MathUtils.random(-spr, spr));
            velocity.rotate(Vector3.Z, MathUtils.random(-spr, spr));
        }
    }

    private void handleTreeCollisions(Terrain terrain, float delta) {
        for (Terrain.Tree tree : terrain.getTrees()) {
            Vector3 treePos = tree.getPosition();
            float trunkRadius = tree.getTrunkRadius();
            float trunkHeight = tree.getTrunkHeight();

            float dx = position.x - treePos.x;
            float dz = position.z - treePos.z;
            float distXZ = (float) Math.sqrt(dx * dx + dz * dz);

            if (distXZ < RADIUS + trunkRadius) {
                if (position.y < treePos.y + trunkHeight && position.y > treePos.y) {
                    Vector3 horizontalDir = new Vector3(dx, 0, dz).nor();
                    float speedHorizontal = new Vector3(velocity.x, 0, velocity.z).len();

                    velocity.x = horizontalDir.x * speedHorizontal;
                    velocity.z = horizontalDir.z * speedHorizontal;
                    velocity.scl(0.8f);

                    float pushOut = (RADIUS + trunkRadius - distXZ) + 0.001f;
                    position.x += horizontalDir.x * pushOut;
                    position.z += horizontalDir.z * pushOut;
                }
            } else if (distXZ < tree.getFoliageRadius() && (position.y > treePos.y + trunkHeight && position.y < treePos.y + trunkHeight + tree.getFoliageRadius()*2)) {
                applyFoliagePhysics(delta); // Slightly slowed by leaves
            }
        }
    }

    private void moveBall(float delta) {
        position.add(velocity.cpy().scl(delta));
    }

    private void updateColor() {
        Color color = switch (state) {
            case AIR -> Color.WHITE;
            case CONTACT -> Color.GREEN;
            case ROLLING -> Color.RED;
            case STATIONARY -> Color.ORANGE;
        };
        for (Material m : instance.materials) {
            ColorAttribute ca = (ColorAttribute) m.get(ColorAttribute.Diffuse);
            ca.color.set(color);
        }
    }

    private void updateTransform() {
        instance.transform.setToTranslation(position);
    }

    private void debugLog() {
        if (velocity.len() > 0) {
            System.out.printf("PHYSICS | State: %s | Speed: %.3f | Pos: (%.2f, %.2f, %.2f)%n",
                    state, velocity.len(), position.x, position.y, position.z);
        }
    }

    public void hit(Vector3 shootDir, float power) {
        if (state != State.STATIONARY) return;

        Vector3 dir = shootDir.cpy().nor();
        float angleRad = 40 * MathUtils.degreesToRadians;
        dir.y = MathUtils.sin(angleRad);
        float horizontalLen = MathUtils.cos(angleRad);
        dir.x *= horizontalLen;
        dir.z *= horizontalLen;

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
}