package org.example.ball;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.CatmullRomSpline;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

public class BallRenderer {

    private final Model ballModel;
    private final ModelInstance ballInstance;
    private final Array<TrailPoint> trail = new Array<>();
    private final Color activeTrailColor = new Color(Color.WHITE);

    private final CatmullRomSpline<Vector3> spline = new CatmullRomSpline<>(new Vector3[]{new Vector3(), new Vector3(), new Vector3(), new Vector3()}, false);
    private final Vector3 tempV1 = new Vector3();
    private final Vector3 tempV2 = new Vector3();
    private final Vector3 direction = new Vector3();
    private final Vector3 camDir = new Vector3();
    private final Vector3 perpendicular = new Vector3();
    private final Vector3 leftPos = new Vector3();
    private final Vector3 rightPos = new Vector3();

    private Model trailMeshModel;
    private ModelInstance trailMeshInstance;

    private static final float TRAIL_MAX_LIFE = 2.0f;
    private static final float BASE_WIDTH = 0.12f;

    private static class TrailPoint {
        Vector3 pos = new Vector3();
        float life = TRAIL_MAX_LIFE;
        Color color = new Color();
    }

    public BallRenderer(float radius) {
        ModelBuilder builder = new ModelBuilder();
        ballModel = builder.createSphere(radius, radius, radius, 16, 16,
                new Material(ColorAttribute.createDiffuse(Color.WHITE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        ballInstance = new ModelInstance(ballModel);
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
            if (p.life <= 0) trail.removeIndex(i);
        }
    }

    public void recordTrailPoint(Vector3 position, float ignoredScale) {
        if (trail.size > 0) {
            if (trail.peek().pos.dst2(position) < 0.001f) {
                return;
            }
            if (trail.size > 1) {
                tempV1.set(trail.peek().pos).sub(trail.get(trail.size - 2).pos).nor(); // Prev Dir
                tempV2.set(position).sub(trail.peek().pos).nor(); // New Dir
                if (tempV1.dot(tempV2) < -0.5f) {
                    return;
                }
            }
        }

        TrailPoint p = new TrailPoint();
        p.pos.set(position.cpy());
        p.color.set(activeTrailColor);
        trail.add(p);
        if (trail.size > 150) trail.removeIndex(0);
    }

    // RESTORED: Helper for hit effects and ground contact
    public void spawnBurst(Vector3 position, int count, float scale) {
        for (int i = 0; i < count; i++) {
            Vector3 burstPos = position.cpy().add(
                    MathUtils.random(-0.1f * scale, 0.1f * scale),
                    MathUtils.random(-0.1f * scale, 0.1f * scale),
                    MathUtils.random(-0.1f * scale, 0.1f * scale)
            );
            recordTrailPoint(burstPos, 1.0f);
        }
    }

    // RESTORED: Dynamic color transitions
    public void lerpTrailColor(Color target, float alpha) {
        activeTrailColor.lerp(target, alpha);
    }

    // RESTORED: Shot rating colors
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

    public void resetTrail(Color color) {
        trail.clear();
        activeTrailColor.set(color);
    }

    public Color getActiveTrailColor() {
        return activeTrailColor;
    }

    public void render(ModelBatch batch, Environment env) {
        batch.render(ballInstance, env);
    }

    public void renderTrail(ModelBatch batch, Environment env) {
        if (trail.size < 4) return;

        if (trailMeshModel != null) trailMeshModel.dispose();

        ModelBuilder modelBuilder = new ModelBuilder();
        modelBuilder.begin();

        // Ensure blending is active for the fade effect
        MeshPartBuilder builder = modelBuilder.part("trail", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorUnpacked,
                new Material(new BlendingAttribute(0.6f), IntAttribute.createCullFace(GL20.GL_NONE)));

        Vector3[] controlPoints = new Vector3[trail.size];
        for (int i = 0; i < trail.size; i++) controlPoints[i] = trail.get(i).pos;
        spline.set(controlPoints, false);

        camDir.set(batch.getCamera().direction).scl(-1);

        int segments = (trail.size - 1) * 4;
        Vector3 prevLeft = new Vector3();
        Vector3 prevRight = new Vector3();

        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            spline.valueAt(tempV1, t);

            float tAhead = Math.min(t + 0.001f, 1.0f);
            spline.valueAt(tempV2, tAhead);
            direction.set(tempV2).sub(tempV1).nor();

            perpendicular.set(direction).crs(camDir).nor();

            int ptIdx = MathUtils.clamp((int) (t * (trail.size - 1)), 0, trail.size - 1);
            TrailPoint p = trail.get(ptIdx);

            // 1. Calculate Life Factor: 1.0 when new, 0.0 when expiring
            float lifeFactor = MathUtils.clamp(p.life / TRAIL_MAX_LIFE, 0f, 1f);

            // 2. Tapering: Combine 't' (position in trail) with lifeFactor (age)
            // This makes the tail thin out as it gets older
            float widthTaper = (0.1f + 0.9f * t) * lifeFactor;
            float width = BASE_WIDTH * widthTaper;

            leftPos.set(tempV1).mulAdd(perpendicular, width);
            rightPos.set(tempV1).mulAdd(perpendicular, -width);

            if (i > 0) {
                Color c = p.color.cpy();
                // 3. Fading: The alpha is determined by how much life is left
                // Squaring lifeFactor makes the fade-out feel more "organic" and less linear
                c.a = (lifeFactor * lifeFactor) * 0.5f * (t * t);

                builder.rect(prevLeft, prevRight, rightPos, leftPos, camDir);
            }

            prevLeft.set(leftPos);
            prevRight.set(rightPos);
        }

        trailMeshModel = modelBuilder.end();
        trailMeshInstance = new ModelInstance(trailMeshModel);
        batch.render(trailMeshInstance, env);
    }

    public void dispose() {
        ballModel.dispose();
        if (trailMeshModel != null) trailMeshModel.dispose();
    }

    public boolean hasVisibleTrail() {
        return trail.size > 0;
    }
}