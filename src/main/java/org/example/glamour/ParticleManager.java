package org.example.glamour;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import org.example.Ball;
import org.example.terrain.Terrain;

public class ParticleManager {
    private final Array<GameParticle> particles = new Array<>();
    private final Model boxModel;
    private final Vector3 tempPos = new Vector3(); // Reuse vector to avoid GC pressure

    public ParticleManager() {
        ModelBuilder mb = new ModelBuilder();
        boxModel = mb.createBox(0.12f, 0.12f, 0.12f,
                new Material(ColorAttribute.createDiffuse(Color.WHITE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    }

    public void handleBallInteraction(Ball ball, Terrain terrain) {
        Ball.Interaction interaction = ball.getLastInteraction();
        if (interaction == Ball.Interaction.NONE) return;

        float speed = ball.getVelocity().len();
        if (speed < 1.0f) {
            ball.clearInteraction();
            return;
        }

        Color pColor = Color.WHITE;
        int count = 0;
        float forceMult = 0.15f;
        float life = 0.6f;

        switch (interaction) {
            case WATER -> {
                pColor = Color.CYAN;
                count = (int)(speed * 3);
                forceMult = 0.4f;
                life = 1.2f;
            }
            case LEAVES -> {
                pColor = Color.FOREST;
                count = (int)(speed * 2.5f);
                forceMult = 0.3f;
            }
            case TERRAIN -> {
                Terrain.TerrainType type = terrain.getTerrainTypeAt(ball.getPosition().x, ball.getPosition().z);
                float traitScale = getTraitScale(type);
                pColor = getTerrainColor(type, ball.getState());
                count = (int)(speed * traitScale * 3);
                forceMult = 0.15f * traitScale;
            }
        }

        if (count > 0) {
            Vector3 splashDir = ball.getVelocity().cpy().scl(-1).nor().add(0, 0.5f, 0).nor();
            spawnDirectional(ball.getPosition(), splashDir, pColor, count, speed * forceMult, life);
        }
        ball.clearInteraction();
    }

    private float getTraitScale(Terrain.TerrainType type) {
        return switch (type) {
            case GREEN -> 0.15f;
            case FAIRWAY -> 0.4f;
            case ROUGH -> 0.8f;
            case BUNKER -> 1.2f;
            default -> 0.5f;
        };
    }

    private Color getTerrainColor(Terrain.TerrainType type, Ball.State state) {
        if (type == Terrain.TerrainType.BUNKER) return Color.TAN;

        Color surfaceGreen = switch (type) {
            case GREEN -> new Color(0.2f, 0.8f, 0.2f, 1f);
            case FAIRWAY -> new Color(0.1f, 0.6f, 0.1f, 1f);
            case ROUGH -> new Color(0.05f, 0.4f, 0.05f, 1f);
            default -> Color.GREEN;
        };

        if (state == Ball.State.ROLLING) return surfaceGreen;
        return (MathUtils.random() < 0.8f) ? surfaceGreen : new Color(0.4f, 0.25f, 0.15f, 1f);
    }

    public void spawn(Vector3 pos, Color color, int count, float force) {
        spawn(pos, color, count, force, 0.6f, 9.8f);
    }

    public void spawn(Vector3 pos, Color color, int count, float force, float lifeBase, float gravity) {
        for (int i = 0; i < count; i++) {
            particles.add(new GameParticle(pos, color, boxModel, force, lifeBase, gravity));
        }
    }

    // Now accepts Terrain to handle collisions
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

    public void clear() {
        particles.clear();
    }

    public void dispose() {
        boxModel.dispose();
    }

    private static class GameParticle {
        public ModelInstance instance;
        public Vector3 velocity = new Vector3();
        public float lifeTime, maxLife;
        public float gravity;
        private final Vector3 pos = new Vector3();

        public GameParticle(Vector3 pos, Color color, Model model, float force, float lifeBase, float gravity) {
            this.instance = new ModelInstance(model);
            this.instance.transform.setToTranslation(pos);
            ((ColorAttribute)instance.materials.get(0).get(ColorAttribute.Diffuse)).color.set(color);

            this.gravity = gravity;
            velocity.set(MathUtils.random(-1f, 1f), MathUtils.random(0.5f, 2f), MathUtils.random(-1f, 1f))
                    .nor().scl(MathUtils.random(0.5f, force));

            this.maxLife = lifeBase + MathUtils.random(0f, 0.5f);
            this.lifeTime = maxLife;
        }

        public boolean update(float delta, Terrain terrain) {
            lifeTime -= delta;
            if (lifeTime <= 0) return false;

            // Apply gravity
            velocity.y -= gravity * delta;

            // Move
            instance.transform.getTranslation(pos);
            pos.add(velocity.x * delta, velocity.y * delta, velocity.z * delta);

            // Terrain Collision (Physics)
            float terrainHeight = terrain.getHeightAt(pos.x, pos.z);
            if (pos.y < terrainHeight) {
                pos.y = terrainHeight;
                // Bounce: flip Y velocity and reduce it (friction/restitution)
                velocity.y = -velocity.y * 0.4f;
                // Slow down horizontal movement on impact
                velocity.x *= 0.8f;
                velocity.z *= 0.8f;
            }

            // Apply transformation with scaling
            float scale = Math.max(0, lifeTime / maxLife);
            instance.transform.setToTranslation(pos);
            instance.transform.scale(scale, scale, scale);

            return true;
        }
    }

    public void spawnDirectional(Vector3 pos, Vector3 direction, Color color, int count, float force, float life) {
        for (int i = 0; i < count; i++) {
            GameParticle p = new GameParticle(pos, color, boxModel, 0, life, 9.8f);

            // Set velocity based on the splash direction + random spread
            float spread = 0.5f;
            p.velocity.set(direction)
                    .add(MathUtils.random(-spread, spread),
                            MathUtils.random(-spread, spread),
                            MathUtils.random(-spread, spread))
                    .nor()
                    .scl(MathUtils.random(0.5f, 1.5f) * force);

            particles.add(p);
        }
    }
}