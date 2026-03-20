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
    private final Color tempColor = new Color();

    private final CatmullRomSpline<Vector3> spline = new CatmullRomSpline<>(new Vector3[]{new Vector3(), new Vector3(), new Vector3(), new Vector3()}, false);
    private final Vector3 tempV1 = new Vector3();
    private final Vector3 tempV2 = new Vector3();
    private final Vector3 direction = new Vector3();
    private final Vector3 camDir = new Vector3();
    private final Vector3 perpendicular = new Vector3();
    private final Vector3 leftPos = new Vector3();
    private final Vector3 rightPos = new Vector3();
    private final Vector3 currentBallPos = new Vector3();

    private Model trailMeshModel;
    private ModelInstance trailMeshInstance;

    private boolean trailDirty = true;
    private float timeElapsed = 0;
    private boolean isPerfection = false;

    private static final float TRAIL_MAX_LIFE = 2.0f;
    private static final float BASE_WIDTH = 0.12f;
    private static final int MAX_TRAIL_POINTS = 120;

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
        if (!currentBallPos.equals(position)) {
            currentBallPos.set(position);
            trailDirty = true;
        }

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
        timeElapsed += delta;

        if (trail.size > 0) {
            for (int i = trail.size - 1; i >= 0; i--) {
                TrailPoint p = trail.get(i);
                p.life -= delta;
                if (p.life <= 0) trail.removeIndex(i);
            }
            trailDirty = true;
        }
    }

    public void recordTrailPoint(Vector3 position, float ignoredScale) {
        if (trail.size > 0 && trail.peek().pos.dst2(position) < 0.0001f) return;

        TrailPoint p = new TrailPoint();
        p.pos.set(position.cpy());
        p.color.set(activeTrailColor);
        trail.add(p);

        trailDirty = true;
        if (trail.size > MAX_TRAIL_POINTS) trail.removeIndex(0);
    }

    /**
     * Rating bursts are now handled by ParticleManager.
     * We only record a single point here to ensure the spline has a starting anchor.
     */
    public void spawnBurst(Vector3 position, int count, float scale) {
        recordTrailPoint(position, scale);
    }

    public void lerpTrailColor(Color target, float alpha) {
        activeTrailColor.lerp(target, alpha);
        trailDirty = true;
    }

    public void setShotRatingColor(MinigameResult.Rating rating) {
        isPerfection = (rating == MinigameResult.Rating.PERFECTION);
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
        trailDirty = true;
    }

    public void resetTrail(Color color) {
        trail.clear();
        activeTrailColor.set(color);
        trailDirty = true;
    }

    public Color getActiveTrailColor() {
        return activeTrailColor;
    }

    public void render(ModelBatch batch, Environment env) {
        batch.render(ballInstance, env);
    }

    public void renderTrail(ModelBatch batch, Environment env) {
        if (trail.size < 3) return;

        if (trailDirty || trailMeshInstance == null) {
            rebuildMesh(batch.getCamera().direction);
            trailDirty = false;
        }

        if (trailMeshInstance != null) {
            batch.render(trailMeshInstance, env);
        }
    }

    private void rebuildMesh(Vector3 cameraDirection) {
        if (trailMeshModel != null) trailMeshModel.dispose();

        ModelBuilder modelBuilder = new ModelBuilder();
        modelBuilder.begin();

        MeshPartBuilder builder = modelBuilder.part("trail", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorUnpacked,
                new Material(new BlendingAttribute(0.6f), IntAttribute.createCullFace(GL20.GL_NONE)));

        Vector3[] controlPoints = new Vector3[trail.size + 1];
        for (int i = 0; i < trail.size; i++) controlPoints[i] = trail.get(i).pos;
        controlPoints[trail.size] = currentBallPos;
        spline.set(controlPoints, false);

        camDir.set(cameraDirection).scl(-1);

        // Dynamic segments based on trail size for performance
        int segments = MathUtils.clamp(trail.size * 3, 40, 300);

        Vector3 prevLeft = new Vector3(), prevRight = new Vector3();

        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            spline.valueAt(tempV1, t);

            float tAhead = Math.min(t + 0.001f, 1.0f);
            spline.valueAt(tempV2, tAhead);
            direction.set(tempV2).sub(tempV1).nor();
            perpendicular.set(direction).crs(camDir).nor();

            float floatIdx = t * (trail.size);
            int idx1 = MathUtils.clamp(MathUtils.floor(floatIdx), 0, trail.size - 1);
            int idx2 = MathUtils.clamp(MathUtils.ceil(floatIdx), 0, trail.size - 1);
            float lerp = floatIdx - idx1;

            Color c1 = trail.get(idx1).color;
            Color c2 = (idx2 >= trail.size) ? activeTrailColor : trail.get(idx2).color;
            tempColor.set(c1).lerp(c2, lerp);

            float lifeFactor = MathUtils.clamp(trail.get(idx1).life / TRAIL_MAX_LIFE, 0f, 1f);

            float ballTaper = MathUtils.clamp((1.0f - t) * 15.0f, 0f, 1f);
            float tailTaper = MathUtils.clamp(t * 5.0f, 0f, 1f);
            float pulse = isPerfection ? 1.0f + (MathUtils.sin(timeElapsed * 10f - t * 5f) * 0.2f) : 1.0f;
            float width = BASE_WIDTH * lifeFactor * ballTaper * tailTaper * pulse;

            leftPos.set(tempV1).mulAdd(perpendicular, width);
            rightPos.set(tempV1).mulAdd(perpendicular, -width);

            if (i > 0) {
                tempColor.a = (lifeFactor * 0.5f) * ballTaper;
                builder.setColor(tempColor);
                builder.rect(prevLeft, prevRight, rightPos, leftPos, camDir);
            }
            prevLeft.set(leftPos);
            prevRight.set(rightPos);
        }

        trailMeshModel = modelBuilder.end();
        trailMeshInstance = new ModelInstance(trailMeshModel);
    }

    public void dispose() {
        ballModel.dispose();
        if (trailMeshModel != null) trailMeshModel.dispose();
    }

    public boolean hasVisibleTrail() {
        return trail.size > 0;
    }
}