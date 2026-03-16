package org.example.terrain.objects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class Monolith extends TerrainObject {
    private final ModelInstance instance;
    public final float width, height, depth;
    private final float rotationY;
    private final BlendingAttribute blend;

    public Monolith(float x, float y, float z, float w, float h, float d, float rotation) {
        super(x, y, z);
        this.width = w;
        this.height = h;
        this.depth = d;
        this.rotationY = rotation;
        this.blend = new BlendingAttribute(true, 1.0f);

        ModelBuilder mb = new ModelBuilder();
        Material mat = new Material(ColorAttribute.createDiffuse(new Color(0.15f, 0.15f, 0.16f, 1f)), blend);
        instance = new ModelInstance(mb.createBox(w, h, d, mat, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));
        instance.transform.setToTranslation(pos.x, pos.y + height / 2f, pos.z);
        instance.transform.rotate(Vector3.Y, rotationY);
    }

    @Override
    public void update(float delta) {
        if (Math.abs(currentAlpha - targetAlpha) > 0.01f) {
            currentAlpha = MathUtils.lerp(currentAlpha, targetAlpha, delta * 6f);
            blend.opacity = currentAlpha;
            instance.materials.get(0).set(blend);
        }
    }

    @Override
    public void render(ModelBatch b, Environment e) {
        b.render(instance, e);
    }

    @Override
    public void dispose() {
        if (instance.model != null) instance.model.dispose();
    }

    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public float getDepth() { return depth; }
    public float getRotationY() { return rotationY; }
}