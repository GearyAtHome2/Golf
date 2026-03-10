package org.example.ball;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

public class BallRenderer {

    private final Model ballModel;
    private final ModelInstance ballInstance;
    private final Model trailPointModel;
    private final ModelInstance trailInstance;
    private final Array<TrailPoint> trail = new Array<>();
    private final Color activeTrailColor = new Color(Color.WHITE);

    private static class TrailPoint {
        Vector3 pos = new Vector3();
        float life = 4.0f;
        Color color = new Color();
        float scaleMod = 1.0f;
    }

    public BallRenderer(float radius) {
        ModelBuilder builder = new ModelBuilder();
        ballModel = builder.createSphere(radius, radius, radius, 20, 20,
                new Material(ColorAttribute.createDiffuse(Color.WHITE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        ballInstance = new ModelInstance(ballModel);

        trailPointModel = builder.createSphere(0.08f, 0.08f, 0.08f, 8, 8,
                new Material(ColorAttribute.createDiffuse(Color.WHITE), new BlendingAttribute(0.6f)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        trailInstance = new ModelInstance(trailPointModel);
    }

    public void updateVisuals(Vector3 position, Ball.State state) {
        ballInstance.transform.setToTranslation(position);
        Color color = switch (state) {
            case AIR -> Color.WHITE;
            case CONTACT -> Color.GREEN;
            case ROLLING -> Color.RED;
            case STATIONARY -> Color.ORANGE;
        };
        ((ColorAttribute) ballInstance.materials.get(0).get(ColorAttribute.Diffuse)).color.set(color);
    }

    public void updateTrail(float delta) {
        for (int i = trail.size - 1; i >= 0; i--) {
            TrailPoint p = trail.get(i);
            p.life -= delta;
            if (p.scaleMod > 1.0f) p.life -= delta * 2f;
            if (p.life <= 0) trail.removeIndex(i);
        }
    }

    public void recordTrailPoint(Vector3 position, float scale) {
        TrailPoint p = new TrailPoint();
        p.pos.set(position);
        p.color.set(activeTrailColor);
        p.scaleMod = scale;
        trail.add(p);
    }

    public void spawnBurst(Vector3 position, int count, float scale) {
        for (int i = 0; i < count; i++) {
            recordTrailPoint(position, scale + MathUtils.random(-0.2f, 0.5f));
        }
    }

    public void setShotRatingColor(MinigameResult.Rating rating) {
        switch (rating) {
            case PERFECTION -> activeTrailColor.set(Color.PURPLE);
            case SUPER -> activeTrailColor.set(Color.PINK);
            case GREAT -> activeTrailColor.set(Color.GOLD);
            case GOOD -> activeTrailColor.set(Color.GREEN);
            case POOR -> activeTrailColor.set(Color.GRAY);
            case WANK -> activeTrailColor.set(Color.ORANGE);
            case SHIT -> activeTrailColor.set(Color.BROWN);
            default -> activeTrailColor.set(Color.WHITE);
        }
    }

    public void lerpTrailColor(Color target, float alpha) {
        activeTrailColor.lerp(target, alpha);
    }

    public void resetTrail(Color color) {
        trail.clear();
        activeTrailColor.set(color);
    }

    public void render(ModelBatch batch, Environment env) {
        batch.render(ballInstance, env);
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

    public Color getActiveTrailColor() { return activeTrailColor; }

    public void dispose() {
        ballModel.dispose();
        trailPointModel.dispose();
    }
}