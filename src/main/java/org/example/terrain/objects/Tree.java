package org.example.terrain.objects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.*;

public class Tree {
    private final ModelInstance trunk, foliage;
    private final Vector3 pos;
    private final float tH, tR;
    public final float fR;
    private final TreeScheme scheme;
    private final float mapRotation;
    private float actualFoliageHeight;

    private float currentAlpha = 1.0f;
    private float targetAlpha = 1.0f;
    private final BlendingAttribute trunkBlend, foliageBlend;

    public enum TreeScheme {
        OAK(new Color(0.35f, 0.25f, 0.15f, 1f), new Color(0.45f, 0.35f, 0.25f, 1f), new Color(0.1f, 0.4f, 0.1f, 1f), new Color(0.2f, 0.5f, 0.2f, 1f)),
        BIRCH(new Color(0.85f, 0.85f, 0.85f, 1f), new Color(1f, 1f, 1f, 1f), new Color(0.3f, 0.6f, 0.1f, 1f), new Color(0.5f, 0.8f, 0.2f, 1f)),
        REDWOOD(new Color(0.3f, 0.15f, 0.1f, 1f), new Color(0.5f, 0.2f, 0.15f, 1f), new Color(0.05f, 0.25f, 0.05f, 1f), new Color(0.15f, 0.35f, 0.15f, 1f)),
        FIR(new Color(0.2f, 0.15f, 0.1f, 1f), new Color(0.3f, 0.25f, 0.2f, 1f), new Color(0.05f, 0.2f, 0.05f, 1f), new Color(0.1f, 0.3f, 0.1f, 1f)),
        AUTUMN_MAPLE(new Color(0.25f, 0.2f, 0.15f, 1f), new Color(0.35f, 0.3f, 0.25f, 1f), new Color(0.8f, 0.2f, 0.1f, 1f), new Color(1f, 0.6f, 0.0f, 1f)),
        CHERRY_BLOSSOM(new Color(0.2f, 0.18f, 0.18f, 1f), new Color(0.3f, 0.25f, 0.25f, 1f), new Color(1f, 0.7f, 0.75f, 1f), new Color(1f, 0.85f, 0.9f, 1f)),
        DEAD_GRAY(new Color(0.2f, 0.2f, 0.2f, 1f), new Color(0.4f, 0.4f, 0.4f, 1f), new Color(0.1f, 0.1f, 0.1f, 1f), new Color(0.3f, 0.3f, 0.3f, 1f)),
        GRAPEVINE(new Color(0.30f, 0.22f, 0.18f, 1f), new Color(0.40f, 0.30f, 0.25f, 1f), new Color(0.15f, 0.45f, 0.05f, 1f), new Color(0.25f, 0.55f, 0.15f, 1f));

        private final Color barkMin, barkMax, leafMin, leafMax;

        TreeScheme(Color bMin, Color bMax, Color lMin, Color lMax) {
            this.barkMin = bMin;
            this.barkMax = bMax;
            this.leafMin = lMin;
            this.leafMax = lMax;
        }

        public Color getRandomBark() { return barkMin.cpy().lerp(barkMax, MathUtils.random()); }
        public Color getRandomFoliage() { return leafMin.cpy().lerp(leafMax, MathUtils.random()); }
    }

    public Tree(float x, float y, float z, float th, float tr, float fr, TreeScheme scheme, java.util.Random rng, float mapRotation) {
        this.pos = new Vector3(x, y, z);
        this.tH = th;
        this.tR = tr;
        this.fR = fr;
        this.scheme = scheme;
        this.mapRotation = mapRotation;

        this.trunkBlend = new BlendingAttribute(true, 1.0f);
        this.foliageBlend = new BlendingAttribute(true, 1.0f);

        ModelBuilder mb = new ModelBuilder();
        Material barkMat = new Material(ColorAttribute.createDiffuse(scheme.getRandomBark()), trunkBlend);
        Material leafMat = new Material(ColorAttribute.createDiffuse(scheme.getRandomFoliage()), foliageBlend);

        mb.begin();
        long attributes = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
        MeshPartBuilder part = mb.part("trunk", GL20.GL_TRIANGLES, attributes, barkMat);
        part.cylinder(tr * 2, th, tr * 2, 16);

        if (scheme == TreeScheme.GRAPEVINE) {
            float armWidth = fr * 3.0f;
            float armThickness = tr * 1.4f;
            Matrix4 transform = new Matrix4();
            transform.translate(0, th / 2f, 0).rotate(0, 0, 1, 90);
            part.setVertexTransform(transform);
            part.cylinder(armThickness, armWidth, armThickness, 12);
            part.setVertexTransform(null);
        }

        trunk = new ModelInstance(mb.end());
        trunk.transform.setToTranslation(x, y + th / 2, z);
        trunk.transform.rotate(0, 1, 0, mapRotation);

        if (scheme == TreeScheme.FIR) {
            float skirtMultiplier = 0.6f + (rng.nextFloat() * 0.31f);
            this.actualFoliageHeight = (th * skirtMultiplier) + (fr * 2);
            foliage = new ModelInstance(mb.createCone(fr * 2.5f, actualFoliageHeight, fr * 2.5f, 16, leafMat, attributes));
            foliage.transform.setToTranslation(x, y + th + (fr * 2) - (actualFoliageHeight / 2f), z);
        } else if (scheme == TreeScheme.GRAPEVINE) {
            float vineWidth = fr * 3.0f * (0.9f + (rng.nextFloat() * 0.3f));
            float vineHeight = tr * 1.6f * (0.8f + (rng.nextFloat() * 0.4f));
            this.actualFoliageHeight = vineHeight;
            foliage = new ModelInstance(mb.createBox(vineWidth, vineHeight, tr * 2.2f, leafMat, attributes));
            foliage.transform.setToTranslation(x, y + th + (vineHeight / 2f), z);
        } else {
            this.actualFoliageHeight = fr * 2;
            foliage = new ModelInstance(mb.createSphere(fr * 2, fr * 2, fr * 2, 16, 16, leafMat, attributes));
            foliage.transform.setToTranslation(x, y + th + fr / 4f, z);
        }
        foliage.transform.rotate(0, 1, 0, mapRotation);
    }

    public void update(float delta) {
        if (Math.abs(currentAlpha - targetAlpha) > 0.001f) {
            currentAlpha = MathUtils.lerp(currentAlpha, targetAlpha, delta * 6f);
        } else {
            currentAlpha = targetAlpha;
        }
        trunkBlend.opacity = currentAlpha;
        foliageBlend.opacity = currentAlpha;

        // Force material sync for LibGDX
        trunk.materials.get(0).set(trunkBlend);
        foliage.materials.get(0).set(foliageBlend);
    }

    public boolean checkTrunkCollision(Vector3 ballPos, float ballRadius) {
        float collisionRadius = tR + ballRadius;
        float distSq = Vector2.dst2(ballPos.x, ballPos.z, pos.x, pos.z);
        if (distSq < (collisionRadius * collisionRadius)) {
            if (ballPos.y < pos.y + tH && ballPos.y > pos.y) return true;
        }
        return false;
    }

    public boolean isInsideFoliage(Vector3 ballPos, float ballRadius) {
        if (scheme == TreeScheme.FIR) {
            float foliageBottom = pos.y + tH + (fR * 2) - actualFoliageHeight;
            float foliageTop = pos.y + tH + (fR * 2);

            if (ballPos.y < foliageBottom || ballPos.y > foliageTop) return false;

            float heightPercent = 1.0f - ((ballPos.y - foliageBottom) / actualFoliageHeight);
            float coneRadiusAtHeight = (fR * 2.5f / 2f) * heightPercent;

            float distSq = Vector2.dst2(ballPos.x, ballPos.z, pos.x, pos.z);
            return distSq < Math.pow(coneRadiusAtHeight + ballRadius, 2);
        }

        if (scheme == TreeScheme.GRAPEVINE) {
            float halfW = (fR * 3.0f) / 2f;
            float halfH = actualFoliageHeight / 2f;
            float halfD = (tR * 2.2f) / 2f;
            float centerY = pos.y + tH + halfH;

            return Math.abs(ballPos.x - pos.x) < (halfW + ballRadius) &&
                    Math.abs(ballPos.y - centerY) < (halfH + ballRadius) &&
                    Math.abs(ballPos.z - pos.z) < (halfD + ballRadius);
        }

        return ballPos.dst(pos.x, pos.y + tH + fR / 4f, pos.z) < (fR + ballRadius);
    }
    public void setTargetAlpha(float alpha) { this.targetAlpha = alpha; }
    public void render(ModelBatch b, Environment e) { b.render(trunk, e); b.render(foliage, e); }
    public void dispose() { trunk.model.dispose(); foliage.model.dispose(); }

    public Vector3 getPosition() { return pos; }
    public float getTrunkHeight() { return tH; }
    public float getTrunkRadius() { return tR; }
    public TreeScheme getScheme() { return scheme; }
}