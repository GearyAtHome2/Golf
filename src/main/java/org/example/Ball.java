package org.example;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class Ball {

    private final Model model;
    private final ModelInstance instance;

    private final Vector3 position = new Vector3(0, 20, 0);
    private final Vector3 velocity = new Vector3(0, 0, 0);

    private static final float RADIUS = 0.2f;
    private static final float GRAVITY = -9.81f;
    private static final float BOUNCE_RESTITUTION = 0.3f;
    private static final float FRICTION = 0.999f;
    private static final float FLAT_FRICTION = 0.35f;
    private static final float STOP_SPEED = 0.145f;
    private static final float MIN_BOUNCE_VY = 0.1f;

    private enum State {AIR, CONTACT, ROLLING, STATIONARY}

    private State state = State.AIR;

    // --- NEW: cooldown timer to prevent instant terrain snap ---
    private float hitCooldown = 0f;
    private static final float HIT_COOLDOWN_TIME = 0.1f; // 0.1s grace

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

        // ✅ Set starting position from parameter
        this.position.set(startPosition);

        // Ensure transform matches starting position
        instance.transform.setToTranslation(this.position);

        // Start stationary if spawning on tee
        state = State.STATIONARY;
    }

    public void update(float delta, Terrain terrain) {
        // Reduce cooldown
        if (hitCooldown > 0f) hitCooldown -= delta;

        // If already stationary, just update transform and return early
        if (state == State.STATIONARY) {
            instance.transform.setToTranslation(position);
            return;
        }

        float terrainHeight = terrain.getHeightAt(position.x, position.z) + RADIUS/3;
        Vector3 terrainNormal = terrain.getNormalAt(position.x, position.z).nor();

        // --- only apply terrain logic if cooldown expired ---
        boolean onTerrain = hitCooldown <= 0f && position.y <= terrainHeight;

        if (onTerrain) {
            if (position.y < terrainHeight) {
                position.y = terrainHeight;
            }

            // Split velocity into perpendicular and parallel components
            Vector3 velPerp = terrainNormal.cpy().scl(velocity.dot(terrainNormal));
            Vector3 velParallel = velocity.cpy().sub(velPerp);

            // Bounce along normal
            if (velPerp.len() > MIN_BOUNCE_VY) {
                velPerp.scl(-BOUNCE_RESTITUTION);
            } else {
                velPerp.setZero();
            }

            // Gravity projected along slope tangent
            Vector3 slopeAccel = new Vector3(0, GRAVITY, 0).sub(terrainNormal.cpy().scl(GRAVITY));
            velParallel.add(slopeAccel.scl(delta));

            // Add to existing velocity
            velocity.set(velParallel).add(velPerp);

            // --- Apply friction & determine state ---
            float speedBefore = velocity.len();

            if (speedBefore > 0f) {
                velocity.scl(Math.max((speedBefore - FLAT_FRICTION) / speedBefore, 0f));
            }

            velocity.scl(FRICTION);

            if (velocity.len() < STOP_SPEED) {
                velocity.setZero();
                state = State.STATIONARY; // lock here
            } else {
                state = (velPerp.len() < MIN_BOUNCE_VY) ? State.ROLLING : State.CONTACT;
            }

            System.out.println(String.format(
                    "FRICTION DEBUG | speedBefore=%.5f | velPerp=%.5f | finalVel=%.5f | state=%s",
                    speedBefore, velPerp.len(), velocity.len(), state
            ));

        } else {
            // Airborne
            velocity.y += GRAVITY * delta;
            state = State.AIR;
        }

        // Move ball
        position.add(velocity.cpy().scl(delta));

        // Update color based on state
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

        instance.transform.setToTranslation(position);

        // --- DEBUG: log velocity every frame ---
        System.out.println(String.format(
                "UPDATE | pos=(%.3f, %.3f, %.3f) velocity=(%.3f, %.3f, %.3f) state=%s",
                position.x, position.y, position.z,
                velocity.x, velocity.y, velocity.z,
                state
        ));
    }

    public void hit(Vector3 shootDir) {
        if (state != State.STATIONARY) return;

        // Rotate direction upward by 60 degrees
        Vector3 dir = shootDir.cpy().nor();
        float angleRad = 40 * MathUtils.degreesToRadians;
        dir.y = MathUtils.sin(angleRad);
        float horizontalLen = MathUtils.cos(angleRad);
        dir.x *= horizontalLen;
        dir.z *= horizontalLen;

        // Apply initial speed
        float initialSpeed = 41f; // tweak as needed
        velocity.set(dir.scl(initialSpeed));

        // Set state back to airborne
        state = State.AIR;

        // Start cooldown to prevent terrain snap
        hitCooldown = HIT_COOLDOWN_TIME;

        // --- DEBUG: show hit info ---
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