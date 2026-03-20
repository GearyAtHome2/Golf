package org.example.terrain.objects;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.Vector3;

public abstract class TerrainObject {
    protected final Vector3 pos;
    protected float currentAlpha = 1.0f;
    protected float targetAlpha = 1.0f;

    public TerrainObject(float x, float y, float z) {
        this.pos = new Vector3(x, y, z);
    }

    public abstract void update(float delta);

    public abstract void render(ModelBatch b, Environment e);

    public abstract void dispose();

    public void setTargetAlpha(float alpha) {
        this.targetAlpha = alpha;
    }

    public Vector3 getPosition() {
        return pos;
    }
}