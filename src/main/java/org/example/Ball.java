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
    private static final float FRICTION = 0.98f;
    private static final float FLAT_FRICTION = 0.35f;
    private static final float STOP_SPEED = 0.145f;
    private static final float MIN_BOUNCE_VY = 0.1f;

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

        boolean onTerrain = hitCooldown <= 0f && position.y <= terrain.getHeightAt(position.x, position.z) + RADIUS / 3;

        if (onTerrain) handleTerrainCollision(delta, terrain);
        else applyAirPhysics(delta);

        handleTreeCollisions(terrain);
        moveBall(delta);
        updateColor();
        updateTransform();

        debugLog();
    }

    // ===================== Helper Methods =====================

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

    private void handleTerrainCollision(float delta, Terrain terrain) {
        float terrainHeight = terrain.getHeightAt(position.x, position.z) + RADIUS / 3;
        Vector3 terrainNormal = terrain.getNormalAt(position.x, position.z).nor();

        if (position.y < terrainHeight) position.y = terrainHeight;

        Vector3 velPerp = terrainNormal.cpy().scl(velocity.dot(terrainNormal));
        Vector3 velParallel = velocity.cpy().sub(velPerp);

        if (velPerp.len() > MIN_BOUNCE_VY) velPerp.scl(-BOUNCE_RESTITUTION);
        else velPerp.setZero();

        Vector3 slopeAccel = new Vector3(0, GRAVITY, 0).sub(terrainNormal.cpy().scl(GRAVITY));
        velParallel.add(slopeAccel.scl(delta));

        velocity.set(velParallel).add(velPerp);

        applyFriction();
        updateState(velPerp);
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

    private void applyFriction() {
        float speedBefore = velocity.len();
        if (speedBefore > 0f) velocity.scl(Math.max((speedBefore - FLAT_FRICTION) / speedBefore, 0f));
        velocity.scl(FRICTION);
    }

    private void applyCustomFriction(float friction) {
        float speedBefore = velocity.len();
        if (speedBefore > 0f) velocity.scl(Math.max((speedBefore - FLAT_FRICTION) / speedBefore, 0f));
        velocity.scl(friction);
    }

    private void updateState(Vector3 velPerp) {
        if (velocity.len() < STOP_SPEED) {
            velocity.setZero();
            state = State.STATIONARY;
        } else {
            state = (velPerp.len() < MIN_BOUNCE_VY) ? State.ROLLING : State.CONTACT;
        }
    }

    private void handleTreeCollisions(Terrain terrain) {
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
                System.out.println("in foliage");
                applyCustomFriction(0.8f);
            }
        }
    }

    private void moveBall(float delta) {
        position.add(velocity.cpy().scl(delta));
    }

    private void updateColor() {
        Color color;
        switch (state) {
            case AIR -> color = Color.WHITE;
            case CONTACT -> color = Color.GREEN;
            case ROLLING -> color = Color.RED;
            case STATIONARY -> color = Color.ORANGE;
            default -> color = Color.WHITE;
        }
        for (Material m : instance.materials) {
            ColorAttribute ca = (ColorAttribute) m.get(ColorAttribute.Diffuse);
            ca.color.set(color);
        }
    }

    private void updateTransform() {
        instance.transform.setToTranslation(position);
    }

    private void debugLog() {
        System.out.println(String.format(
                "UPDATE | pos=(%.3f, %.3f, %.3f) velocity=(%.3f, %.3f, %.3f) state=%s",
                position.x, position.y, position.z,
                velocity.x, velocity.y, velocity.z,
                state
        ));
    }

    // ===================== Hit / Render / Dispose =====================

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

        System.out.println(String.format(
                "HIT | pos=(%.3f, %.3f, %.3f) dir=(%.3f, %.3f, %.3f) velocity=(%.3f, %.3f, %.3f) state=%s",
                position.x, position.y, position.z,
                dir.x, dir.y, dir.z,
                velocity.x, velocity.y, velocity.z,
                state
        ));
    }

    public void render(ModelBatch batch, Environment env) {
        batch.render(instance, env);
    }

    public void dispose() {
        model.dispose();
    }

    public Vector3 getPosition() {
        return position;
    }

    public Vector3 getVelocity() {
        return velocity;
    }
}