package org.example.glamour;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import org.example.ball.Ball;
import org.example.terrain.Terrain;

public class ParticleManager {
    private final Array<GameParticle> particles = new Array<>();
    private final Model boxModel;

    // Configuration Constants
    private static final float PARTICLE_SIZE = 0.12f;
    private static final float DEFAULT_GRAVITY = 9.81f;
    private static final float BOUNCE_RESTITUTION = 0.4f;
    private static final float GROUND_FRICTION = 0.8f;

    public ParticleManager() {
        ModelBuilder mb = new ModelBuilder();
        boxModel = mb.createBox(PARTICLE_SIZE, PARTICLE_SIZE, PARTICLE_SIZE,
                new Material(ColorAttribute.createDiffuse(Color.WHITE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    }

    /**
     * Translates high-level ball interactions into specific particle bursts.
     */
    public void handleBallInteraction(Ball ball, Terrain terrain) {
        Ball.Interaction interaction = ball.getLastInteraction();
        if (interaction == Ball.Interaction.NONE) return;

        float speed = ball.getVelocity().len();
        if (speed < 1.0f) {
            ball.clearInteraction();
            return;
        }

        // Determine particle traits based on interaction type
        ParticleParams params = getInteractionParams(interaction, ball, terrain, speed);

        if (params.count > 0) {
            // Calculate splash direction (opposite of ball travel + upward bias)
            Vector3 splashDir = ball.getVelocity().cpy().nor().scl(-1).add(0, 0.5f, 0).nor();
            spawnDirectional(ball.getPosition(), splashDir, params.color, params.count, speed * params.forceMult, params.life);
        }

        ball.clearInteraction();
    }

    private ParticleParams getInteractionParams(Ball.Interaction interaction, Ball ball, Terrain terrain, float speed) {
        ParticleParams p = new ParticleParams();

        switch (interaction) {
            case WATER -> {
                p.color = Color.CYAN;
                p.count = (int) (speed * 3);
                p.forceMult = 0.4f;
                p.life = 1.2f;
            }
            case LEAVES -> {
                p.color = Color.FOREST;
                p.count = (int) (speed * 2.5f);
                p.forceMult = 0.3f;
            }
            case TERRAIN -> {
                Terrain.TerrainType type = terrain.getTerrainTypeAt(ball.getPosition().x, ball.getPosition().z);
                float traitScale = getTerrainScale(type);
                p.color = getTerrainColor(type, ball.getState());
                p.count = (int) (speed * traitScale * 3);
                p.forceMult = 0.15f * traitScale;
            }
        }
        return p;
    }

    private float getTerrainScale(Terrain.TerrainType type) {
        return switch (type) {
            case GREEN -> 0.15f;
            case FAIRWAY -> 0.4f;
            case ROUGH -> 0.8f;
            case BUNKER -> 1.2f;
            case STONE -> 0.8f;
            default -> 0.5f;
        };
    }

    private Color getTerrainColor(Terrain.TerrainType type, Ball.State state) {
        if (type == Terrain.TerrainType.BUNKER) return Color.TAN;

        Color surfaceGreen = switch (type) {
            case GREEN -> new Color(0.2f, 0.8f, 0.2f, 1f);
            case FAIRWAY -> new Color(0.1f, 0.6f, 0.1f, 1f);
            case ROUGH -> new Color(0.05f, 0.4f, 0.05f, 1f);
            case STONE -> new Color(0.85f, 0.85f, 0.85f, 1f);
            default -> Color.GREEN;
        };

        // If rolling, just grass colors. If hitting, mix in some dirt (brown).
        if (state == Ball.State.ROLLING) return surfaceGreen;
        return (MathUtils.random() < 0.8f) ? surfaceGreen : new Color(0.4f, 0.25f, 0.15f, 1f);
    }

    public void spawn(Vector3 pos, Color color, int count, float force, float lifeBase, float gravity) {
        for (int i = 0; i < count; i++) {
            particles.add(new GameParticle(pos, color, boxModel, force, lifeBase, gravity));
        }
    }

    public void spawnDirectional(Vector3 pos, Vector3 direction, Color color, int count, float force, float life) {
        for (int i = 0; i < count; i++) {
            GameParticle p = new GameParticle(pos, color, boxModel, 0, life, DEFAULT_GRAVITY);
            float spread = 0.5f;
            p.velocity.set(direction)
                    .add(MathUtils.random(-spread, spread), MathUtils.random(-spread, spread), MathUtils.random(-spread, spread))
                    .nor()
                    .scl(MathUtils.random(0.5f, 1.5f) * force);
            particles.add(p);
        }
    }

    public void update(float delta, Terrain terrain) {
        for (int i = particles.size - 1; i >= 0; i--) {
            if (!particles.get(i).update(delta, terrain)) {
                particles.removeIndex(i);
            }
        }
    }

    public void render(ModelBatch batch, Environment env) {
        for (GameParticle p : particles) {
            batch.render(p.instance, env);
        }
    }

    public void clear() { particles.clear(); }

    public void dispose() { boxModel.dispose(); }

    /**
     * Internal data structure to clean up the switch-case logic.
     */
    private static class ParticleParams {
        Color color = Color.WHITE;
        int count = 0;
        float forceMult = 0.15f;
        float life = 0.6f;
    }

    private static class GameParticle {
        public final ModelInstance instance;
        public final Vector3 velocity = new Vector3();
        public final Vector3 pos = new Vector3();
        public float lifeTime, maxLife;
        public float gravity;

        public GameParticle(Vector3 startPos, Color color, Model model, float force, float lifeBase, float gravity) {
            this.instance = new ModelInstance(model);
            this.pos.set(startPos);
            this.instance.transform.setToTranslation(pos);
            ((ColorAttribute) instance.materials.get(0).get(ColorAttribute.Diffuse)).color.set(color);

            this.gravity = gravity;
            if (force > 0) {
                velocity.set(MathUtils.random(-1f, 1f), MathUtils.random(0.5f, 2f), MathUtils.random(-1f, 1f))
                        .nor().scl(MathUtils.random(0.5f, force));
            }

            this.maxLife = lifeBase + MathUtils.random(0f, 0.5f);
            this.lifeTime = maxLife;
        }

        public boolean update(float delta, Terrain terrain) {
            lifeTime -= delta;
            if (lifeTime <= 0) return false;

            velocity.y -= gravity * delta;
            pos.add(velocity.x * delta, velocity.y * delta, velocity.z * delta);

            // Terrain Collision
            float terrainHeight = terrain.getHeightAt(pos.x, pos.z);
            if (pos.y < terrainHeight + PARTICLE_SIZE) {
                pos.y = terrainHeight + PARTICLE_SIZE;
                velocity.y = -velocity.y * BOUNCE_RESTITUTION;
                velocity.x *= GROUND_FRICTION;
                velocity.z *= GROUND_FRICTION;
            }

            // Update transform: Translation + Shrinking over time
            float scale = Math.max(0, lifeTime / maxLife);
            instance.transform.setToTranslation(pos);
            instance.transform.scale(scale, scale, scale);

            return true;
        }
    }
}