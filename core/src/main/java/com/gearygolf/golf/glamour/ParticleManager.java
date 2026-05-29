package com.gearygolf.golf.glamour;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.gearygolf.golf.ball.Ball;
import com.gearygolf.golf.terrain.Terrain;

public class ParticleManager {
    private final Array<GameParticle> particles = new Array<>();
    private final Model boxModel;
    /** 1/3-size model used for divot particles (zoomed-in swing view). */
    private final Model divotBoxModel;
    private SoundManager soundManager;
    private boolean wasInWater = false;

    public void setSoundManager(SoundManager sm) { this.soundManager = sm; }

    private static final float PARTICLE_SIZE = 0.12f;
    private static final float DEFAULT_GRAVITY = 9.81f;
    private static final float BOUNCE_RESTITUTION = 0.4f;
    private static final float GROUND_FRICTION = 0.8f;

    private static final Color COLOR_WATER   = new Color(0.85f, 0.92f, 1.0f,  0.6f);
    private static final Color COLOR_GREEN   = new Color(0.2f,  0.8f,  0.2f,  1f);
    private static final Color COLOR_FAIRWAY = new Color(0.1f,  0.6f,  0.1f,  1f);
    private static final Color COLOR_ROUGH   = new Color(0.05f, 0.4f,  0.05f, 1f);
    private static final Color COLOR_STONE   = new Color(0.85f, 0.85f, 0.85f, 1f);
    private static final Color COLOR_DIRT    = new Color(0.4f,  0.25f, 0.15f, 1f);

    public ParticleManager() {
        ModelBuilder mb = new ModelBuilder();
        boxModel = mb.createBox(PARTICLE_SIZE, PARTICLE_SIZE, PARTICLE_SIZE,
                new Material(ColorAttribute.createDiffuse(Color.WHITE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        float divotSize = PARTICLE_SIZE / 3f;
        divotBoxModel = mb.createBox(divotSize, divotSize, divotSize,
                new Material(ColorAttribute.createDiffuse(Color.WHITE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    }

    public void spawnRatingBurst(Vector3 pos, Color color, int count, float forceScale) {
        for (int i = 0; i < count; i++) {
            GameParticle p = new GameParticle(pos, color, boxModel, 0, 1.5f, DEFAULT_GRAVITY * 0.5f);
            p.velocity.set(MathUtils.random(-1f, 1f), MathUtils.random(0.5f, 2f), MathUtils.random(-1f, 1f))
                    .nor()
                    .scl(MathUtils.random(2f, 5f) * forceScale);
            particles.add(p);
        }
    }

    public void handleBallInteraction(Ball ball, Terrain terrain, Vector3 cameraPos) {
        Ball.Interaction interaction = ball.getLastInteraction();
        if (interaction == Ball.Interaction.NONE) {
            wasInWater = false;
            return;
        }

        float speed = ball.getVelocity().len();
        if (speed < 1.0f) {
            ball.clearInteraction();
            return;
        }

        ParticleParams params = getInteractionParams(interaction, ball, terrain, speed);

        if (params.count > 0) {
            if (interaction == Ball.Interaction.WATER) {
                float verticalSpeed = Math.abs(ball.getVelocity().y);
                boolean isSkimming = verticalSpeed < speed * 0.35f;

                if (isSkimming) {
                    Vector3 skimDir = ball.getVelocity().cpy().nor().scl(-0.4f).add(0, 0.7f, 0).nor();
                    spawnDirectional(ball.getPosition(), skimDir, params.color, params.count / 2, speed * 0.25f, 0.7f);
                } else {
                    spawnSplash(ball.getPosition(), params.color, params.count, speed * params.forceMult);
                }
                if (!wasInWater && soundManager != null) soundManager.playSplash(cameraPos, ball.getPosition(), speed);
                wasInWater = true;
            } else {
                wasInWater = false;
                Vector3 splashDir = ball.getVelocity().cpy().nor().scl(-1).add(0, 0.5f, 0).nor();
                spawnDirectional(ball.getPosition(), splashDir, params.color, params.count, speed * params.forceMult, params.life);
            }
        }

        ball.clearInteraction();
    }

    public void spawn(Vector3 pos, Color color, int count, float force, float lifeBase, float gravity) {
        for (int i = 0; i < count; i++) {
            particles.add(new GameParticle(pos, color, boxModel, force, lifeBase, gravity));
        }
    }

    private void spawnSplash(Vector3 pos, Color color, int count, float force) {
        for (int i = 0; i < count; i++) {
            GameParticle p = new GameParticle(pos, color, boxModel, 0, 1.2f, DEFAULT_GRAVITY);
            float angle = MathUtils.random(0, MathUtils.PI2);
            float ringWidth = 0.45f;
            p.velocity.set(MathUtils.cos(angle) * ringWidth, 1.0f, MathUtils.sin(angle) * ringWidth)
                    .nor()
                    .scl(MathUtils.random(0.6f, 1.4f) * force);
            particles.add(p);
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

    /**
     * Spawns a small divot burst at the ball's world position using 1/3-size particles.
     * Called from GolfGame when the swing gesture crosses the ball in the swing view.
     *
     * @param pos      Ball world position.
     * @param aimDir   Horizontal aim/forward direction (used to bias particle direction).
     * @param type     Terrain type at impact (determines colour + stone sparks).
     */
    public void spawnDivot(Vector3 pos, Vector3 aimDir, Terrain.TerrainType type) {
        Color baseColor = getDivotParticleColor(type);
        // Particles fly mostly upward with a slight forward (target-direction) bias.
        Vector3 baseDir = new Vector3(0, 1.4f, 0)
                .add(aimDir.x * 0.35f, 0f, aimDir.z * 0.35f)
                .nor();

        int count = 12;
        float spread = 0.65f;
        for (int i = 0; i < count; i++) {
            GameParticle p = new GameParticle(pos, baseColor, divotBoxModel, 0, 0.75f, DEFAULT_GRAVITY);
            p.velocity.set(baseDir)
                    .add(MathUtils.random(-spread, spread),
                         MathUtils.random(-spread * 0.4f, spread * 0.4f),
                         MathUtils.random(-spread, spread))
                    .nor()
                    .scl(MathUtils.random(1.5f, 3.8f));
            particles.add(p);
        }

        // Stone: add a handful of fast orange/white sparks.
        if (type == Terrain.TerrainType.STONE) {
            for (int i = 0; i < 6; i++) {
                Color spark = MathUtils.random() < 0.5f ? Color.ORANGE : Color.WHITE;
                GameParticle p = new GameParticle(pos, spark, divotBoxModel, 0, 0.35f, DEFAULT_GRAVITY * 1.6f);
                p.velocity.set(MathUtils.random(-1f, 1f), MathUtils.random(0.8f, 2f), MathUtils.random(-1f, 1f))
                        .nor()
                        .scl(MathUtils.random(3f, 7f));
                particles.add(p);
            }
        }
    }

    private Color getDivotParticleColor(Terrain.TerrainType type) {
        return switch (type) {
            case SAND  -> Color.TAN;
            case STONE -> COLOR_STONE;
            case MUD   -> new Color(0.22f, 0.13f, 0.07f, 1f);
            default    -> COLOR_DIRT;
        };
    }

    private ParticleParams getInteractionParams(Ball.Interaction interaction, Ball ball, Terrain terrain, float speed) {
        ParticleParams p = new ParticleParams();
        switch (interaction) {
            case WATER -> {
                p.color = COLOR_WATER;
                p.count = (int) (speed * 4);
                p.forceMult = 0.45f;
                p.life = 1.4f;
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
            case SAND -> 1.2f;
            case STONE -> 0.8f;
            default -> 0.5f;
        };
    }

    private Color getTerrainColor(Terrain.TerrainType type, Ball.State state) {
        if (type == Terrain.TerrainType.SAND) return Color.TAN;
        Color surfaceColor = switch (type) {
            case GREEN   -> COLOR_GREEN;
            case FAIRWAY -> COLOR_FAIRWAY;
            case ROUGH   -> COLOR_ROUGH;
            case STONE   -> COLOR_STONE;
            default      -> Color.GREEN;
        };
        if (state == Ball.State.ROLLING) return surfaceColor;
        return (MathUtils.random() < 0.8f) ? surfaceColor : COLOR_DIRT;
    }

    public void update(float delta, Terrain terrain) {
        if (delta <= 0) return;

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
        divotBoxModel.dispose();
    }

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

            float terrainHeight = terrain.getHeightAt(pos.x, pos.z);
            if (pos.y < terrainHeight + PARTICLE_SIZE) {
                pos.y = terrainHeight + PARTICLE_SIZE;
                velocity.y = -velocity.y * BOUNCE_RESTITUTION;
                velocity.x *= GROUND_FRICTION;
                velocity.z *= GROUND_FRICTION;
            }

            float scale = Math.max(0, lifeTime / maxLife);

            instance.transform.setToTranslation(pos);
            instance.transform.scale(scale, scale, scale);

            return true;
        }
    }
}